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
import android.graphics.PixelFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Monitoring service for screen state.
 * Screen state metrics:
 * <li>Screen state
 *
 * @author darts
 *
 */
public final class ScreenService extends MetricDevice<Byte> {

    private static final String TAG = "NDroid";
    private static final int SCREEN_METRICS = 1;
    private static final long THIRTY_SECONDS = 30000;

    // NOTE: title and string array must be defined above instance,
    //   otherwise, they will be null in constructor
    private static final String title = "Screen state";
    private static final String metrics = "Screen on";
    private static final ScreenService INSTANCE = new ScreenService();

    private static final IntentFilter onFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
    private static final IntentFilter offFilter = new IntentFilter(Intent.ACTION_SCREEN_OFF);

    private Context context;
    private List<DataEntry> tempData;

    private ScreenService() {
        if (DebugLog.DEBUG) Log.d(TAG, "ScreenService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("ScreenService already instantiated");
        }
        groupId = Metrics.SCREEN_ON;
        metricsCount = SCREEN_METRICS;
        tempData = new ArrayList<>();
        values = new Byte[SCREEN_METRICS];
    }

    public static ScreenService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "ScreenService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_RECEIVER;
        this.period = period;
        this.timer = System.currentTimeMillis();
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);

        WindowManager mWindowManager = (WindowManager)context.getSystemService(
                Context.WINDOW_SERVICE);
        Display display = mWindowManager.getDefaultDisplay();
        String description = getDescription(display);
        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, description,
                SUPPORTED, 0, 0, "1 (boolean)", "1", Metrics.TYPE_USER);
        // insert information for metrics in group into database
        database.insertOrReplaceMetrics(groupId, groupId, metrics, "", 1);
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        context = (Context) params.get(PARAM_CONTEXT);
        BroadcastReceiver broadcastReceiver = (BroadcastReceiver) params.get(PARAM_BROADCAST_RECEIVER);
        context.registerReceiver(broadcastReceiver, onFilter);
        context.registerReceiver(broadcastReceiver, offFilter);
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if ( params.get(PARAM_SCREEN_INTENT) != null) {
            Intent intent = (Intent) params.get(PARAM_SCREEN_INTENT);
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                if (DebugLog.DEBUG) Log.d(TAG, "ScreenService.ScreenReceiver - screen on");
                values[0] = 1;
            }
            else if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                if (DebugLog.DEBUG) Log.d(TAG, "ScreenService.ScreenReceiver - screen off");
                values[0] = 0;
            }
            tempData.add(new DataEntry(Metrics.SCREEN_ON, timestamp, values[0]));
        }
        if (timestamp - timer < period) {
            return null;
        }
        if (DebugLog.DEBUG) Log.d(TAG, "ScrennService.getData - updating Screen values");
        setTimer(timestamp);
        List<DataEntry> dataList = new ArrayList<>();
        for (int i = 0; i < tempData.size(); i ++) {
            dataList.add(tempData.get(i));
        }
        tempData.clear();
        return dataList;
    }

    /**
     * Obtain description of device display.
     *
     * @param display    device display object
     * @return    string describing screen technology
     */
    private String getDescription(Display display) {
        DisplayMetrics dispMetrics = new DisplayMetrics();
        display.getMetrics(dispMetrics);
        String description = "Display: ";
        switch (display.getPixelFormat()) {
            case PixelFormat.A_8:
                description = description + "A 8 ";
                break;
            case PixelFormat.L_8:
                description = description + "L 8 ";
                break;
            case PixelFormat.LA_88:
                description = description + "LA 88 ";
                break;
            case PixelFormat.OPAQUE:
                description = description + "Opaque";
                break;
            case PixelFormat.RGB_332:
                description = description + "RGB 332 ";
                break;
            case PixelFormat.RGB_565:
                description = description + "RGB 565 ";
                break;
            case PixelFormat.RGB_888:
                description = description + "RGB 888 ";
                break;
            case PixelFormat.RGBA_4444:
                description = description + "RGBA 4444 ";
                break;
            case PixelFormat.RGBA_5551:
                description = description + "RGBA 5551 ";
                break;
            case PixelFormat.RGBA_8888:
                description = description + "RGBA 8888 ";
                break;
            case PixelFormat.RGBX_8888:
                description = description + "RGBX 8888 ";
                break;
            case PixelFormat.TRANSLUCENT:
                description = description + "Translucent ";
                break;
            case PixelFormat.TRANSPARENT:
                description = description + "Transparent ";
                break;
            default:
                description = description + "Unknown ";
                break;
        }
        description = description + dispMetrics.heightPixels + "x" +
                dispMetrics.widthPixels +
                "  DPI: " + dispMetrics.densityDpi;
        return description;
    }


}
