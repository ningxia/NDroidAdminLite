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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Monitoring service for light sensor.
 * Light sensor metrics:
 * <li>Light intensity
 * <p>
 * @author darts
 *
 * @see MetricService
 *
 */
public final class LightService extends MetricDevice<Float> {

    private static final String TAG = "NDroid";
    private static final int LIGHT_METRICS = 1;
    private static final long FIVE_SECONDS = 5000;

    // NOTE: title and string array must be defined above instance,
    //   otherwise, they will be null in constructor
    private static final String title = "Light sensor";
    private static final String metrics = "Light level";
    private static final LightService INSTANCE = new LightService();
    private static byte counter = 0;

    private static SensorManager mSensorManager;
    private static Sensor mLight;

    private LightService() {
        if (DebugLog.DEBUG) Log.d(TAG, "LightService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("LightService already instantiated");
        }
        groupId = Metrics.LIGHT;
        metricsCount = LIGHT_METRICS;
        values = new Float[LIGHT_METRICS];
    }

    public static LightService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "LightService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_SENSOR;
        this.period = period;
        this.timer = System.currentTimeMillis();
        Context context = MyApplication.getAppContext();
        mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        mLight = mSensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        if (mLight == null) {
            if (DebugLog.INFO) Log.i(TAG, "LightService - sensor not supported on this system");
            mSensorManager = null;
            supportedMetric = false;
            return;
        }
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
        if (!supportedMetric) {
            database.insertOrReplaceMetricInfo(groupId, title, "",
                    NOTSUPPORTED, 0, 0, "", "", Metrics.TYPE_SENSOR);
            return;
        }

        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, mLight.getName(),
                SUPPORTED, mLight.getPower(), mLight.getMinDelay()/1000,
                mLight.getMaximumRange() + " " + context.getString(R.string.units_lux),
                mLight.getResolution() + " " + context.getString(R.string.units_lux),
                Metrics.TYPE_SENSOR);
        // insert information for metrics in group into database
        database.insertOrReplaceMetrics(groupId, groupId, metrics,
                context.getString(R.string.units_lux), mLight.getMaximumRange());
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        SensorManager sensorManager = (SensorManager) params.get(PARAM_SENSOR_MANAGER);
        SensorEventListener sensorEventListener = (SensorEventListener) params.get(PARAM_SENSOR_EVENT_LISTENER);
        int mode = (int) params.get(PARAM_MODE);
        sensorManager.registerListener(sensorEventListener, mLight, mode);
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        SensorEvent event = (SensorEvent) params.get(PARAM_SENSOR_EVENT);
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        values[0] = event.values[0];
        List<DataEntry> dataList = new ArrayList<>();
        dataList.add(new DataEntry(Metrics.LIGHT, timestamp, values[0]));
        return dataList;
    }

}
