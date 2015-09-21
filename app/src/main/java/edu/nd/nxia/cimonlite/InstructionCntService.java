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

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;
import android.content.Context;
import android.os.Debug;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Monitoring service for instruction executions.
 * Instruction metrics:
 * <li>Instructions executed
 * <p>
 *
 * @author darts
 *
 * @see MetricService
 *
 */
public final class InstructionCntService extends MetricDevice<Long> {

    private static final String TAG = "NDroid";
    private static final int INSTRUCT_METRICS = 1;
    private static final long THIRTY_SECONDS = 30000;

    // NOTE: title and string array must be defined above instance,
    //   otherwise, they will be null in constructor
    private static final String title = "Instruction count";
    private static final String metrics = "Instructions";
    private static String description = "Total number of instructions executed globally";
    private static final InstructionCntService INSTANCE = new InstructionCntService();
    private Debug.InstructionCount icount = null;

    private InstructionCntService() {
        if (DebugLog.DEBUG) Log.d(TAG, "InstructionCntService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("InstructionCntService already instantiated");
        }
        groupId = Metrics.INSTRUCTION_CNT;
        metricsCount = INSTRUCT_METRICS;

        values = new Long[INSTRUCT_METRICS];
        values[0] = (long) 0;
    }

    public static InstructionCntService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "InstructionCntService.getInstance - get single instance");
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

        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, description,
                SUPPORTED, 0, 0, "Not limited count (long)", "1", Metrics.TYPE_SYSTEM);
        // insert information for metrics in group into database
        database.insertOrReplaceMetrics(groupId, groupId, metrics, "", 1000000);
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
        if (DebugLog.DEBUG) Log.d(TAG, "InstructionCntService.getData - updating instruction cnt values");
        if (icount == null) {
            if (DebugLog.DEBUG) Log.d(TAG, "InstructionCntService.getData - reset and start");
            icount = new Debug.InstructionCount();
            icount.resetAndStart();
        }
//        if (!(icount.collect())) {
//            icount = null;
//            if (DebugLog.INFO) Log.i(TAG, "InstructionCntService.getData - collection failed");
//            return null;
//        }
        values[0] = values[0] + icount.globalTotal();
//        if (DebugLog.DEBUG) Log.d(TAG, "InstructionCntService.getData - value: " + values[0]);
        icount.resetAndStart();
        List<DataEntry> dataList = new ArrayList<>();
        for (int i = 0; i < INSTRUCT_METRICS; i ++) {
            dataList.add(new DataEntry(Metrics.INSTRUCTION_CNT + i, timestamp, values[i]));
        }
        return dataList;
    }

}
