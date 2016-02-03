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
import android.util.FloatMath;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Monitoring service for magnetometer.
 * Magnetometer metrics:
 * <ul>
 * <li>X - ambient magnetic field around X-axis (micro-Tesla) </li>
 * <li>Y - ambient magnetic field around Y-axis (micro-Tesla) </li>
 * <li>Z - ambient magnetic field around Z-axis (micro-Tesla) </li>
 * <li>Magnitude - magnitude of total magnetic field </li>
 * </ul>
 *
 * @author darts
 *
 * @see MetricService
 *
 */
public final class MagnetometerService extends MetricDevice<Float> {

    private static final String TAG = "NDroid";
    private static final int MAGNET_METRICS = 4;
    private static final long FIVE_SECONDS = 5000;
	/* measured update interval for FASTEST approx 20ms */
//	private static final long MAGNET_UPDATE_INTERVAL = 20;

    // NOTE: title and string array must be defined above instance,
    //   otherwise, they will be null in constructor
    private static final String title = "Magnetometer";
    private static final String[] metrics = {"X", "Y", "Z", "Magnitude"};
    private static final MagnetometerService INSTANCE = new MagnetometerService();
    private boolean valid = false;

    private static SensorManager mSensorManager;
    private static Sensor mMagnetometer;
    private static OrientationService orientationService = null;


    private MagnetometerService() {
        if (DebugLog.DEBUG) Log.d(TAG, "MagnetometerService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("MagnetometerService already instantiated");
        }
        groupId = Metrics.MAGNETOMETER;
        metricsCount = MAGNET_METRICS;
        values = new Float[MAGNET_METRICS];
    }

    public static MagnetometerService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "MagnetometerService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_SENSOR;
        this.period = period;
        Context context = MyApplication.getAppContext();
        mSensorManager = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (mMagnetometer == null) {
            if (DebugLog.INFO) Log.i(TAG, "MagnetometerService - sensor not supported on this system");
            supportedMetric = false;
            mSensorManager = null;
            return;
        }
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        SensorManager sensorManager = (SensorManager) params.get(PARAM_SENSOR_MANAGER);
        SensorEventListener sensorEventListener = (SensorEventListener) params.get(PARAM_SENSOR_EVENT_LISTENER);
        int mode = (int) params.get(PARAM_MODE);
        sensorManager.registerListener(sensorEventListener, mMagnetometer, mode);
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
        if (!supportedMetric) {
            database.insertOrReplaceMetricInfo(groupId, title, "", NOTSUPPORTED, 0, 0,
                    "", "", Metrics.TYPE_SENSOR);
            return;
        }

        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, mMagnetometer.getName(),
                SUPPORTED, mMagnetometer.getPower(), mMagnetometer.getMinDelay()/1000,
                mMagnetometer.getMaximumRange() + " " + context.getString(R.string.units_ut),
                mMagnetometer.getResolution() + " " + context.getString(R.string.units_ut),
                Metrics.TYPE_SENSOR);
        // insert information for metrics in group into database
        for (int i = 0; i < MAGNET_METRICS; i++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i],
                    context.getString(R.string.units_ut), mMagnetometer.getMaximumRange());
        }
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        SensorEvent event = (SensorEvent) params.get(PARAM_SENSOR_EVENT);
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (timestamp - timer < period - timeOffset) {
            return null;
        }
        setTimer(timestamp);
        float magnitude = 0;
        for (int i = 0; i < (MAGNET_METRICS - 1); i++) {
            values[i] = event.values[i];
            magnitude += event.values[i] * event.values[i];
        }
        values[MAGNET_METRICS - 1] = FloatMath.sqrt(magnitude);

        if (orientationService != null) {
            orientationService.onSensorUpdate(event);
        }

        List<DataEntry> dataList = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < MAGNET_METRICS; i ++) {
            sb.append(values[i]).append("|");
        }
        dataList.add(new DataEntry(Metrics.MAGNETOMETER, timestamp, sb.substring(0, sb.length() - 1)));
        return dataList;
    }

    /**
     * Register an active orientation monitor service with magnetometer service.
     * Orientation service requires both accelerometer and magnetometer data.  These
     * services must be activated and provide data to the orientation service when
     * there is an active orientation monitor.
     *
     * @param oService    reference to orientation service, used for providing updates
     */
    public void registerOrientation(OrientationService oService) {
        if (orientationService == null) {
            orientationService = oService;
        }
    }

}
