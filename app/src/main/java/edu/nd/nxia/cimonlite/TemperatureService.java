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
 * Monitoring service for temperature sensor.
 * Temperature metrics:
 * <li>Ambient temperature
 *
 * @author darts
 *
 */
public final class TemperatureService extends MetricDevice<Float> {

    private static final String TAG = "NDroid";
    private static final int TEMP_METRICS = 1;
    private static final long FIVE_SECONDS = 5000;

    // NOTE: title and string array must be defined above instance,
    //   otherwise, they will be null in constructor
    private static final String title = "Temperature";
    private static final String metrics = "Ambient temperature";
    private static final TemperatureService INSTANCE = new TemperatureService();
    private static byte counter = 0;

    private static SensorManager mSensorManager;
    private static Sensor mTemperature;

    private TemperatureService() {
        if (DebugLog.DEBUG) Log.d(TAG, "TemperatureService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("TemperatureService already instantiated");
        }
        groupId = Metrics.TEMPERATURE;
        metricsCount = TEMP_METRICS;
        values = new Float[TEMP_METRICS];
    }

    public static TemperatureService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "TemperatureService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.period = period;
        this.timer = System.currentTimeMillis();
        Context context = MyApplication.getAppContext();
        mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        mTemperature = mSensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE);
        if (mTemperature == null) {
            if (DebugLog.INFO) Log.i(TAG, "TemperatureService - TYPE_TEMPERATURE not supported.  Trying " +
                    "TYPE_AMBIENT_TEMPERATURE");
            if (DebugLog.INFO) Log.i(TAG, "TemperatureService - sensor not supported on this system.");
            supportedMetric = false;
            mSensorManager = null;
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
        database.insertOrReplaceMetricInfo(groupId, title, mTemperature.getName(),
                SUPPORTED, mTemperature.getPower(), mTemperature.getMinDelay()/1000,
                mTemperature.getMaximumRange() + " " + context.getString(R.string.units_celcius),
                mTemperature.getResolution() + " " + context.getString(R.string.units_celcius),
                Metrics.TYPE_SENSOR);
        // insert information for metrics in group into database
        database.insertOrReplaceMetrics(groupId, groupId, metrics,
                context.getString(R.string.units_celcius), mTemperature.getMaximumRange());
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        SensorManager sensorManager = (SensorManager) params.get(PARAM_SENSOR_MANAGER);
        SensorEventListener sensorEventListener = (SensorEventListener) params.get(PARAM_SENSOR_EVENT_LISTENER);
        int mode = (int) params.get(PARAM_MODE);
        sensorManager.registerListener(sensorEventListener, mTemperature, mode);
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        SensorEvent event = (SensorEvent) params.get(PARAM_SENSOR_EVENT);
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        values[0] = event.values[0];
        List<DataEntry> dataList = new ArrayList<>();
        dataList.add(new DataEntry(Metrics.TEMPERATURE, timestamp, values[0]));
        return dataList;
    }

}
