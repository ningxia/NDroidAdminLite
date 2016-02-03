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
import android.hardware.SensorManager;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Monitoring service for orientation sensor.
 * The orientation of the device, as calculated through Android sensor services,
 * using accelerometer and magnetometer data. This service will register with 
 * Accelerometer service and Magnetometer service in order to coordinate updates.
 * Orientation metrics:
 * <li>Azimuth (radians)
 * <li>Pitch (radians)
 * <li>Roll (radians)
 *
 * @author darts
 *
 * @see MetricService
 *
 */
public final class OrientationService extends MetricDevice<Float> {

    private static final String TAG = "NDroid";
    private static final int ORIENT_METRICS = 3;
    private static final int MATRIX_SIZE = 9;
    private static final long FIVE_SECONDS = 5000;
	/* measured update interval for GAME approx 10ms (1ms on FASTEST) */
//	private static final long GYRO_UPDATE_INTERVAL = 10;

    // NOTE: title and string array must be defined above instance,
    //   otherwise, they will be null in constructor
    private static final String title = "Orientation";
    private static final String[] metrics = {"Azimuth", "Pitch", "Roll"};
    private static final OrientationService INSTANCE = new OrientationService();
    private static SensorManager mSensorManager;
    private static Sensor mAccelerometer;
    private static Sensor mMagnetometer;

    private float[] orientation = new float[ORIENT_METRICS];
    private float[] acceleration = null;
    private float[] magnet = null;
    private float[] rotation = new float[MATRIX_SIZE];


    private OrientationService() {
        if (DebugLog.DEBUG) Log.d(TAG, "OrientationService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("OrientationService already instantiated");
        }
        groupId = Metrics.ORIENTATION;
        metricsCount = ORIENT_METRICS;
        values = new Float[ORIENT_METRICS];
    }

    public static OrientationService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "OrientationService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_OTHER;
        this.period = period;
        mSensorManager = (SensorManager) MyApplication.getAppContext().getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagnetometer = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        if (AccelerometerService.getInstance() == null) {
            if (DebugLog.INFO) Log.i(TAG, "OrientationService - sensor not supported on this system (accelerometer)");
            supportedMetric = false;
            return;
        }
        if (MagnetometerService.getInstance() == null) {
            if (DebugLog.INFO) Log.i(TAG, "OrientationService - sensor not supported on this system (magnetometer)");
            supportedMetric = false;
            return;
        }
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        // Registration is handled in MetricService.registerDevices
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

        SensorManager mSensorManager = (SensorManager)context.getSystemService(
                Context.SENSOR_SERVICE);
        Sensor mOrientation = mSensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION);
        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, mOrientation.getName(),
                SUPPORTED, mOrientation.getPower(), mOrientation.getMinDelay()/1000,
                mOrientation.getMaximumRange() + " " + context.getString(R.string.units_degrees),
                mOrientation.getResolution() + " " + context.getString(R.string.units_degrees),
                Metrics.TYPE_SENSOR);
        // insert information for metrics in group into database
        for (int i = 0; i < ORIENT_METRICS; i++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i],
                    context.getString(R.string.units_radians), (float) Math.PI);
        }
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        if (values == null || values[0] == null) {
            return null;
        }
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (timestamp - timer < period - timeOffset) {
            return null;
        }
        setTimer(timestamp);
        List<DataEntry> dataList = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ORIENT_METRICS; i ++) {
            sb.append(values[i]).append("|");
        }
        dataList.add(new DataEntry(Metrics.ORIENTATION, timestamp, sb.substring(0, sb.length() - 1)));
//        if (DebugLog.DEBUG) Log.d(TAG, "OrientationService.getData: " + values[0] + " " + values[1] + " " + values[2]);
        return dataList;
    }

    /**
     * Callback method for updates of accelerometer and magnetometer readings.
     * This method mimics that of the Android sensor framework, allowing the
     * accelerometer and magnetometer services to provide updates to this service
     * when new data is available.
     *
     * @param event    data from new accelerometer or magnetometer reading
     * @return    time for next update of orientation metrics
     */
    public void onSensorUpdate(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            acceleration = event.values.clone();
        }
        else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
            magnet = event.values.clone();
        }
        if ((acceleration != null) && (magnet != null)) {
            getOrientationData();
        }
    }

    /**
     * Obtain updated orientation data.
     * Calculate updated orientation from acceleration and magnetic field data.
     */
    private void getOrientationData() {
        if (!SensorManager.getRotationMatrix(rotation, null, acceleration, magnet)) {
            return;
        }
        SensorManager.getOrientation(rotation, orientation);
        acceleration = null;
        magnet = null;
        for (int i = 0; i < ORIENT_METRICS; i++) {
            values[i] = orientation[i];
        }
    }
}
