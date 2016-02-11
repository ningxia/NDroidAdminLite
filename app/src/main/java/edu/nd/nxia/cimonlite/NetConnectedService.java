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
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Monitoring service for network connectivity status.
 * Network connectivity metrics:
 * <li>Roaming status
 * <li>Network connected status
 *
 * @author darts
 *
 * @see MetricService
 *
 */
public final class NetConnectedService extends MetricDevice<Byte> {

    private static final String TAG = "NDroid";
    private static final int NET_METRICS = 2;
    private static final long FIVE_MINUTES = 300000;

    // NOTE: title and string array must be defined above instance,
    //   otherwise, they will be null in constructor
    private static final String title = "Connectivity status";
    private static final String[] metrics = {"Roaming", "Connected"};
    private static final int ROAMING = 0;
    private static final int CONNECTED = 1;
    private static final NetConnectedService INSTANCE = new NetConnectedService();
    private static String description;

    ConnectivityManager connectivityManager;
    private static IntentFilter connectivityIntentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);

    private NetConnectedService() {
        if (DebugLog.DEBUG) Log.d(TAG, "NetConnectedService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("NetConnectedService already instantiated");
        }
        groupId = Metrics.NETSTATUS_CATEGORY;
        metricsCount = NET_METRICS;
        values = new Byte[NET_METRICS];
    }

    public static NetConnectedService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "NetConnectedService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_RECEIVER;
        this.period = period;
        this.timer = System.currentTimeMillis();
        connectivityManager = (ConnectivityManager) MyApplication.getAppContext(
        ).getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);

        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null) {
            description = "No active network";
        }
        else {
            description = "Active network: " + networkInfo.getTypeName();
            String subtype = networkInfo.getSubtypeName();
            if ((subtype != null) && (subtype.length() > 0))
                description = description + " : " + subtype;
        }
        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, description,
                SUPPORTED, 0, 0, "1 (boolean)","1", Metrics.TYPE_SYSTEM);
        // insert information for metrics in group into database
        for (int i = 0; i < NET_METRICS; i++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 1);
        }
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        Context context = (Context) params.get(PARAM_CONTEXT);
        BroadcastReceiver broadcastReceiver = (BroadcastReceiver) params.get(PARAM_BROADCAST_RECEIVER);
        context.registerReceiver(broadcastReceiver, connectivityIntentFilter);
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (timestamp - timer < period - timeOffset) {
            return null;
        }
//        if (DebugLog.DEBUG) Log.d(TAG, "NetConnectedServcie.getData - updating connectivity values");
        setTimer(timestamp);
        connectivityManager = (ConnectivityManager) MyApplication.getAppContext(
        ).getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();

        if (networkInfo == null) {
            values[CONNECTED] = -1;
            values[ROAMING] = -1;
        }
        else {
            values[CONNECTED] = (byte) (networkInfo.isConnected() ? 1 : 0);
            values[ROAMING] = (byte) (networkInfo.isRoaming() ? 1 : 0);
        }
        List<DataEntry> dataList = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < NET_METRICS; i ++) {
            sb.append(values[i]).append("|");
        }
        dataList.add(new DataEntry(Metrics.NETSTATUS_CATEGORY, timestamp, sb.substring(0, sb.length() - 1)));
        return dataList;
    }

}
