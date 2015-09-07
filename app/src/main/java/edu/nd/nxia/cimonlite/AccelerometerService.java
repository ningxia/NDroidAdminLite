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

import java.util.ArrayList;
import java.util.List;

/**
 * Monitoring service for accelerometer.
 * Accelerometer metrics:
 * <ul>
 * <li>X - acceleration along X-axis (m/s^2) </li>//
 * <li>Y - acceleration along Y-axis (m/s^2) </li>
 * <li>Z - acceleration along Z-axis (m/s^2) </li>
 * <li>Magnitude - magnitude of total acceleration </li>
 * </ul>
 * 
 * @author darts
 * 
 * @see MetricService
 *
 */
public final class AccelerometerService extends MetricDevice<Float> {

	/** Number of acclerometer metrics: 4. */
	private static final int ACCEL_METRICS = 4;
	private static final long FIVE_SECONDS = 5000;
	/* measured update interval for FASTEST approx 20ms */
//	private static final long ACCEL_UPDATE_INTERVAL = 20;
	
	// NOTE: title and string array must be defined above instance,
	//   otherwise, they will be null in constructor
	private static final String title = "Accelerometer";
	private static final String[] metrics = {"X", "Y", "Z", "Magnitude"};
	private static AccelerometerService INSTANCE = new AccelerometerService();

    private static SensorManager mSensorManager;
	private static Sensor mAccelerometer;
    private int mCounter;
	
	private AccelerometerService() {
		if (DebugLog.DEBUG) Log.d(TAG, "AccelerometerService - constructor");
		groupId = Metrics.ACCELEROMETER;
		metricsCount = ACCEL_METRICS;
		values = new Float[ACCEL_METRICS];
        mCounter = 0;
	}
	
	public static AccelerometerService getInstance() {
		if (DebugLog.DEBUG) Log.d(TAG, "AccelerometerService.getInstance - get single instance. ");
		if (!INSTANCE.supportedMetric) return null;
		return INSTANCE;
	}

    @Override
    void initDevice() {
        mSensorManager = (SensorManager) MyApplication.getAppContext().getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (mAccelerometer == null) {
            if (DebugLog.INFO) Log.i(TAG, "AccelerometerService - sensor not supported on this system");
            supportedMetric = false;
            return;
        }
    }

    @Override
    void registerDevice(SensorManager sensorManager, SensorEventListener eventListener, int mode) {
        sensorManager.registerListener(eventListener, mAccelerometer, mode);
    }

    @Override
	void insertDatabaseEntries() {
		if (DebugLog.DEBUG) Log.d(TAG, "AccelerometerService.insertDatabaseEntries - insert entries");
		Context context = MyApplication.getAppContext();
		CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
		if (!supportedMetric) {
			database.insertOrReplaceMetricInfo(groupId, title, "", NOTSUPPORTED, 0, 0, 
					"", "", Metrics.TYPE_SENSOR);
			return;
		}
		
		// insert metric group information in database
		database.insertOrReplaceMetricInfo(groupId, title, mAccelerometer.getName(), 
				SUPPORTED, mAccelerometer.getPower(), mAccelerometer.getMinDelay()/1000, 
				mAccelerometer.getMaximumRange() + " " + context.getString(R.string.units_ms2), 
				mAccelerometer.getResolution() + " " + context.getString(R.string.units_ms2), 
				Metrics.TYPE_SENSOR);
		// insert information for metrics in group into database
		for (int i = 0; i < ACCEL_METRICS; i++) {
			database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i],
                    context.getString(R.string.units_ms2), mAccelerometer.getMaximumRange());
		}
	}

    /**
     * Process SensorEvent data obtained from onSensorChanged() event.
     * Update values, and push to orientation monitor if it is active.
     *
     * @param event         accelerometer data received from onSensorChanged() event
     * @param timestamp     the timestamp when the event occurred
     * @return              list of data of type {@link DataEntry}
     */
    @Override
    List<DataEntry> getData(SensorEvent event, long timestamp) {
        mCounter ++;
        float magnitude = 0;
        Float values[] = new Float[ACCEL_METRICS];
        for (int i = 0; i < (ACCEL_METRICS - 1); i++) {
            values[i] = event.values[i];
            magnitude += event.values[i] * event.values[i];
        }
        values[ACCEL_METRICS - 1] = FloatMath.sqrt(magnitude);
        List<DataEntry> dataList = new ArrayList<>();
        for (int i = 0; i < ACCEL_METRICS; i ++) {
            dataList.add(new DataEntry(Metrics.ACCELEROMETER + i, timestamp, values[i]));
        }
        return dataList;
    }

    public int getGroupId() {
        return this.groupId;
    }

    public int getCount() {
        return this.mCounter;
    }

    public void resetCount() {
        this.mCounter = 0;
    }

//	/**
//	 * Register an active orientation monitor service with accelerometer service.
//	 * Orientation service requires both accelerometer and magnetometer data.  These
//	 * services must be activated and provide data to the orientation service when
//	 * there is an active orientation monitor.
//	 *
//	 * @param oService    reference to orientation service, used for providing updates
//	 * @return    minimum update interval (milliseconds) of accelerometer
//	 */
//	public long registerOrientation(OrientationService oService) {
//		if (orientService == null) {
//			orientService = oService;
//			getMetricInfo();
//		}
//		return minInterval;
//	}

}
