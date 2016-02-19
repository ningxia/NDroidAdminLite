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
import android.util.Log;
import android.util.SparseArray;

/**
 * Monitoring service for CPU loads.
 * CPU load metrics:
 * <li>Load (1 minute) - average CPU load over past minute
 * <li>Load (5 minute) - average CPU load over past five minutes
 * <li>Load (15 minute) - average CPU load over past fifteen minutes
 * <p>
 *
 * @author darts
 *
 * @see MetricService
 *
 */
public final class CpuService extends MetricDevice<Float> {

    private static final String TAG = "NDroid";
    private static final int CPU_LOADS = 3;
    private static final long SIXTY_SECONDS = 60000;
    private static final int ONE_SECOND = 1000;

    // NOTE: title and string array must be defined above instance,
    //   otherwise, they will be null in constructor
    private static final String title = "CPU load";
    private static final String[] metrics = {"Load (1 minute)",
            "Load (5 minutes)",
            "Load (15 minutes)"};
    private static final CpuService INSTANCE = new CpuService();

    private CpuService() {
        if (DebugLog.DEBUG) Log.d(TAG, "CpuService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("CpuService already instantiated");
        }
        groupId = Metrics.CPULOAD_CATEGORY;
        metricsCount = CPU_LOADS;
        values = new Float[CPU_LOADS];
    }

    public static CpuService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "CpuService.getInstance - get single instance");
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

        int cores = getCoreCount();
        String model = getModelInfo(cores);
        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, model, SUPPORTED, 0,
                ONE_SECOND, "Average active processes (" + cores + " cores)",
                "0.01 load units", Metrics.TYPE_SYSTEM);
        // insert information for metrics in group into database
        for (int i = 0; i < CPU_LOADS; i++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 2*cores);
        }
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
//        if (DebugLog.DEBUG) Log.d(TAG, "CpuService.fetchValues - updating cpu values");
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File("/proc/loadavg"))), 64);
            String line;

            if ( (line = reader.readLine()) != null) {
                String[] parameters = line.split("\\s+");
                for (int i = 0; (i < CPU_LOADS) && (i < parameters.length); i++) {
                    values[i] = Float.parseFloat(parameters[i]);
                }
            }
        }
        catch (Exception e) {
            if (DebugLog.WARNING) Log.w(TAG, "CpuService.fetchValues - read cpu values failed!");
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException ie) {
                    if (DebugLog.WARNING) Log.w(TAG, "CpuService.fetchValues - close reader failed!");
                }
            }
        }
        List<DataEntry> dataList = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < CPU_LOADS; i ++) {
            sb.append(values[i]).append("|");
        }
        if (sb.length() == 0) {
            dataList.add(new DataEntry(Metrics.CPULOAD_CATEGORY, timestamp, ""));
        }
        else {
            dataList.add(new DataEntry(Metrics.CPULOAD_CATEGORY, timestamp, sb.substring(0, sb.length() - 1)));
        }
        return dataList;
    }

    /**
     * Get count of CPU cores in processor.
     *
     * @return    count of cores in device
     */
    private int getCoreCount() {
        if (DebugLog.DEBUG) Log.d(TAG, "CpuService.getCoreCount - getting cpu core count");
        BufferedReader reader = null;
        int cores = 0;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File("/sys/devices/system/cpu/kernel_max"))), 16);
            String line;
            if ( (line = reader.readLine()) != null) {
                cores = Integer.parseInt(line) + 1;	// 0-indexed value
            }
        }
        catch (Exception e) {
            if (DebugLog.WARNING) Log.w(TAG, "CpuService.getCoreCount - read cpu values failed!");
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException ie) {
                    if (DebugLog.WARNING) Log.w(TAG, "CpuService.getCoreCount - close reader failed!");
                }
            }
        }
        return cores;
    }

    /**
     * Get info on type of processor in device.
     *
     * @param cores    number of cores in processor
     * @return    string describing model and type of processor
     */
    private String getModelInfo(int cores) {
        if (DebugLog.DEBUG) Log.d(TAG, "CpuService.getModelInfo - getting cpu model info");
        BufferedReader reader = null;
        String model = null;
        try {
            reader = new BufferedReader(new InputStreamReader(
                    new FileInputStream(new File("/proc/cpuinfo"))), 128);
            String line;
            while ( (line = reader.readLine()) != null) {
                if (line.startsWith("Processor") || line.startsWith("model name")) {
                    int index = line.indexOf(':');
                    if (index > 0) {
                        model = line.substring(index + 1).trim();
                        break;
                    }
                }
            }
        }
        catch (Exception e) {
            if (DebugLog.WARNING) Log.w(TAG, "CpuService.getModelInfo - read cpuinfo failed!");
        }
        finally {
            if (reader != null) {
                try {
                    reader.close();
                }
                catch (IOException ie) {
                    if (DebugLog.WARNING) Log.w(TAG, "CpuService.getModelInfo - close reader failed!");
                }
            }
        }
        if (cores == 2) {
            model = model.concat("(Dual-core)");
        }
        else if (cores == 4) {
            model = model.concat("(Quad-core)");
        }
        else if (cores == 6) {
            model = model.concat("(Hexa-core)");
        }
        else if (cores == 8) {
            model = model.concat("(Octa-core)");
        }
        if (model == null) {
            model = "Single core";
        }
        return model;
    }

}
