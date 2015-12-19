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
 * Monitoring service for gyroscope.
 * Gyroscope metrics:
 * <ul>
 * <li>X - rotation around X-axis (rad/s) </li>
 * <li>Y - rotation around Y-axis (rad/s)</li>
 * <li>Z - rotation around Z-axis (rad/s) </li>
 * <li>Magnitude - magnitude of total rotation </li>
 * </ul>
 * 
 * @author darts
 * 
 * @see MetricService
 *
 */
public final class GyroscopeService extends MetricDevice<Float> {

	private static final String TAG = "NDroid";
	private static final int GYRO_METRICS = 4;
	private static final long FIVE_SECONDS = 5000;
	/* measured update interval for GAME approx 10ms (1ms on FASTEST) */
//	private static final long GYRO_UPDATE_INTERVAL = 10;
	
	// NOTE: title and string array must be defined above instance,
	//   otherwise, they will be null in constructor
	private static final String title = "Gyroscope";
	private static final String[] metrics = {"X", "Y", "Z", "Magnitude"};
	private static final GyroscopeService INSTANCE = new GyroscopeService();

	private static SensorManager mSensorManager;
	private static Sensor mGyroscope;
    private int mCounter;

	private GyroscopeService() {
		if (DebugLog.DEBUG) Log.d(TAG, "GyroscopeService - constructor");
		groupId = Metrics.GYROSCOPE;
		metricsCount = GYRO_METRICS;
		values = new Float[GYRO_METRICS];
        mCounter = 0;
	}
	
	public static GyroscopeService getInstance() {
		if (DebugLog.DEBUG) Log.d(TAG, "GyroscopeService.getInstance - get single instance");
		if (!INSTANCE.supportedMetric) return null;
		return INSTANCE;
	}

    @Override
    void initDevice(long period) {
        this.type = TYPE_SENSOR;
        this.period = period;
        this.timer = System.currentTimeMillis();
        mSensorManager = (SensorManager) MyApplication.getAppContext().getSystemService(Context.SENSOR_SERVICE);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        if (mGyroscope == null) {
            if (DebugLog.INFO) Log.i(TAG, "GyroscopeService - sensor not supported on this system");
            supportedMetric = false;
            return;
        }
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        SensorManager sensorManager = (SensorManager) params.get(PARAM_SENSOR_MANAGER);
        SensorEventListener sensorEventListener = (SensorEventListener) params.get(PARAM_SENSOR_EVENT_LISTENER);
        int mode = (int) params.get(PARAM_MODE);
        Log.d(TAG, "GyroscopeService.registerService mode: " + mode);
        sensorManager.registerListener(sensorEventListener, mGyroscope, mode);
    }

    @Override
	void insertDatabaseEntries() {
        if (DebugLog.DEBUG) Log.d(TAG, "AccelerometerService.insertDatabaseEntries - insert entries");
		Context context = MyApplication.getAppContext();
		CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
		if (!supportedMetric) {
			database.insertOrReplaceMetricInfo(groupId, title, "", 
					NOTSUPPORTED, 0, 0, "", "", Metrics.TYPE_SENSOR);
			return;
		}
		
		// insert metric group information in database
		database.insertOrReplaceMetricInfo(groupId, title, mGyroscope.getName(), 
				SUPPORTED, mGyroscope.getPower(), mGyroscope.getMinDelay()/1000, 
				mGyroscope.getMaximumRange() + " " + context.getString(R.string.units_rads), 
				mGyroscope.getResolution() + " " + context.getString(R.string.units_rads), 
				Metrics.TYPE_SENSOR);
		// insert information for metrics in group into database
		for (int i = 0; i < GYRO_METRICS; i++) {
			database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i],
                    context.getString(R.string.units_rads), mGyroscope.getMaximumRange());
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
        mCounter++;
        float magnitude = 0;
        Float values[] = new Float[GYRO_METRICS];
        for (int i = 0; i < (GYRO_METRICS - 1); i++) {
            values[i] = event.values[i];
            magnitude += event.values[i] * event.values[i];
        }
        values[GYRO_METRICS - 1] = FloatMath.sqrt(magnitude);
        List<DataEntry> dataList = new ArrayList<>();
/*        for (int i = 0; i < GYRO_METRICS; i++) {
            dataList.add(new DataEntry(Metrics.GYROSCOPE + i, timestamp, values[i]));
        }*/
        StringBuilder valueString = new StringBuilder();
        for (int i = 0; i <GYRO_METRICS  - 1; i ++){
            valueString.append(values[i].toString() + "|");
        }
        valueString.append(values[GYRO_METRICS - 1].toString());
        dataList.add(new DataEntry(Metrics.GYROSCOPE, timestamp, valueString.toString()));
        return dataList;
    }

    int getCount() {
        return this.mCounter;
    }

    public void resetCount() {
        this.mCounter = 0;
    }
}
