/*
 * Copyright (C) 2013 Chris Miller
 *
 * This file is part of CIMON.
 * 
 * CIMON is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * CIMON is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *  
 * You should have received a copy of the GNU General Public License
 * along with CIMON.  If not, see <http://www.gnu.org/licenses/>.
 *  
 */
package edu.nd.nxia.cimonlite;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

/**
 * Monitoring service for CPU utilization.
 * CPU utilization is measured as cumulative sum of jiffies by the underlying linux system, 
 * where 1 jiffy is commonly about 10 milliseconds (sometimes 1 millisecond). Utilization
 * rates can be determined by comparing the difference in count of jiffies for a particular 
 * action/state and a particular time frame versus the total number of jiffies over that same 
 * time frame.
 * <p>  
 * CPU utilization metrics:
 * <li>Total	: total jiffies measured (used for comparison to determine ratios)
 * <li>User 	: time spent in user mode
 * <li>Nice 	: time spent in user mode with low priority
 * <li>System	: time spent in system mode
 * <li>Idle 	: idle task time
 * <li>IO Wait	: time waiting for I/O to complete
 * <li>IRQ  	: time servicing interrupts
 * <li>Soft IRQ	: time servicing soft IRQs
 * <li>Context switches	: cumulative context switches
 *
 * @author darts
 *
 * @see MetricService
 *
 */
public final class CpuUtilService extends MetricDevice<Long> {

    private static final String TAG = "NDroid";
    private static final int PROC_METRICS = 9;
    private static final long SIXTY_SECONDS = 60000;

    // NOTE: title and string array must be defined above instance,
    //   otherwise, they will be null in constructor
    private static final String title = "CPU utilization";
    private static final String[] metrics = {"Total",
            "User",
            "Nice",
            "System",
            "Idle",
            "IOWait",
            "IRQ",
            "Soft IRQ",
            "Context switches"};
    private static final CpuUtilService INSTANCE = new CpuUtilService();

    /**
     * Previous values (jiffy counts) of metrics.
     * Used to determine difference in sum over fixed time.
     * <pre>
     * 0 - total	: total processing time
     * 1 - user 	: time spent in user mode
     * 2 - nice 	: time spent in user mode with low priority
     * 3 - system	: time spent in system mode
     * 4 - idle 	: idle task time
     * 5 - iowait	: time waiting for I/O to complete
     * 6 - irq  	: time servicing interrupts
     * 7 - softirq	: time servicing softirqs
     * 8 - context switches
     * (8) steal	: time spent in other OS when running virtualized environment
     * (9) guest	: time spent running virtual CPU for guest OS
     * </pre>
     */
    private long[] prevVals = new long[PROC_METRICS];

    private CpuUtilService() {
        if (DebugLog.DEBUG) Log.d(TAG, "CpuUtilService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("CpuUtilService already instantiated");
        }
        groupId = Metrics.PROCESSOR_CATEGORY;
        metricsCount = PROC_METRICS;
        values = new Long[PROC_METRICS];
    }

    public static CpuUtilService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "CpuUtilService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.period = period;
        this.type = TYPE_OTHER;
        this.timer = System.currentTimeMillis();
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);

        String description = getModelInfo();
        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, description, SUPPORTED, 0, 0,
                "Process time in jiffies (100 %)", "1 jiffie", Metrics.TYPE_SYSTEM);
        // insert information for metrics in group into database
        for (int i = 0; i < (PROC_METRICS - 1); i++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i],
                    context.getString(R.string.units_percent), 100);
        }
        database.insertOrReplaceMetrics(Metrics.PROC_CTXT, groupId,
                metrics[PROC_METRICS - 1], "", 5000);
    }

    @Override
    void registerDevice(SparseArray<Object> params) {

    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (timestamp - timer < period - timeOffset) {
            return null;
        }
        setTimer(timestamp);
        BufferedReader reader = null;
//        if (DebugLog.DEBUG) Log.d(TAG, "CpuUtilService.getData - updating proc values");
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File("/proc/stat"))), 1024);
            String line;
            if ( (line = reader.readLine()) != null) {
                String[] parameters = line.split("\\s+");
                if (parameters[0].contentEquals("cpu")) {
                    values[0] = (long) 0;
                    for (int i = 1; (i < (PROC_METRICS-1)) && (i < parameters.length); i++) {
                        values[i] = Long.parseLong(parameters[i]);
                        values[0] += values[i];
                    }
                }
                else {
                    if (DebugLog.ERROR) Log.e(TAG, "CpuUtilService.getProcInfo - failed to read cpu line");
                }
            }
            while ( (line = reader.readLine()) != null) {
                if (line.startsWith("ctxt")) {
                    String[] parameters = line.split("\\s+");
                    if (parameters.length == 2) {
                        values[PROC_METRICS - 1] = Long.parseLong(parameters[1]);
                    }
                    break;
                }
            }
        }
        catch (Exception e) {
            if (DebugLog.WARNING) Log.w(TAG, "CpuUtilService.getProcInfo - read proc values failed!");
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException ie) {
                    if (DebugLog.WARNING) Log.w(TAG, "CpuUtilService.getProcInfo - close reader failed!");
                }
            }
        }
        List<DataEntry> dataList = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < PROC_METRICS; i ++) {
            sb.append(values[i]).append("|");
        }
        if (sb.length() == 0) {
            dataList.add(new DataEntry(Metrics.PROCESSOR_CATEGORY, timestamp, ""));
        }
        else {
            dataList.add(new DataEntry(Metrics.PROCESSOR_CATEGORY, timestamp, sb.substring(0, sb.length() - 1)));
        }
        return dataList;
    }

    /**
     * Obtain information about CPU.
     *
     * @return    string describing CPU in use
     */
    private String getModelInfo() {
        if (DebugLog.DEBUG) Log.d(TAG, "CpuUtilService.getModelInfo - getting cpu model info");
        BufferedReader reader = null;
        String model = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File("/proc/cpuinfo"))), 128);
            String line;
            while ( (line = reader.readLine()) != null) {
                if (line.startsWith("BogoMIPS")) {
                    int index = line.indexOf(':');
                    if (index > 0) {
                        model = "BogoMIPS: " + line.substring(index + 1).trim();
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            if (DebugLog.WARNING) Log.w(TAG, "CpuUtilService.getModelInfo - read cpuinfo failed!");
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException ie) {
                    if (DebugLog.WARNING) Log.w(TAG, "CpuUtilService.getModelInfo - close reader failed!");
                }
            }
        }
        if (model == null) {
            model = "Utilization ratios";
        }
        return model;
    }

}
