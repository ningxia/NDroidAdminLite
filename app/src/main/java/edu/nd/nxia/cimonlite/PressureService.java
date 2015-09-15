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
 * Monitoring service for barometer.
 * Barometer metrics:
 * <li>Atmospheric pressure
 * 
 * @author darts
 *
 */
public final class PressureService extends MetricDevice<Float> {

	private static final String TAG = "NDroid";
	private static final int PRESSURE_METRICS = 1;
	private static final long FIVE_MINUTES = 300000;
	
	// NOTE: title and string array must be defined above instance,
	//   otherwise, they will be null in constructor
	private static final String title = "Pressure";
	private static final String metrics = "Atmospheric pressure";
	private static final PressureService INSTANCE = new PressureService();
	private static byte counter = 0;
	
	private static SensorManager mSensorManager;
	private static Sensor mPressure;
    private int mCounter;

	private PressureService() {
        if (DebugLog.DEBUG) Log.d(TAG, "PressureService - constructor");
        groupId = Metrics.ATMOSPHERIC_PRESSURE;
        metricsCount = PRESSURE_METRICS;
        values = new Float[PRESSURE_METRICS];
        mCounter = 0;
	}
	
	public static PressureService getInstance() {
		if (DebugLog.DEBUG) Log.d(TAG, "PressureService.getInstance - get single instance");
		if (!INSTANCE.supportedMetric) return null;
		return INSTANCE;
	}

    @Override
    void initDevice(long period) {
        this.type = TYPE_SENSOR;
        this.period = period;
        mSensorManager = (SensorManager) MyApplication.getAppContext().getSystemService(Context.SENSOR_SERVICE);
        mPressure = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        if (mPressure == null) {
            if (DebugLog.INFO) Log.i(TAG, "PressureService - sensor not supported on this system");
            supportedMetric = false;
            return;
        }
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        SensorManager sensorManager = (SensorManager) params.get(PARAM_SENSOR_MANAGER);
        SensorEventListener sensorEventListener = (SensorEventListener) params.get(PARAM_SENSOR_EVENT_LISTENER);
        int mode = (int) params.get(PARAM_MODE);
        sensorManager.registerListener(sensorEventListener, mPressure, mode);
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
		database.insertOrReplaceMetricInfo(groupId, title, mPressure.getName(), 
				SUPPORTED, mPressure.getPower(), mPressure.getMinDelay()/1000, 
				mPressure.getMaximumRange() + " " + context.getString(R.string.units_hpa), 
				mPressure.getResolution() + " " + context.getString(R.string.units_hpa), 
				Metrics.TYPE_SENSOR);
		// insert information for metrics in group into database
		database.insertOrReplaceMetrics(groupId, groupId, metrics, 
				context.getString(R.string.units_hpa), mPressure.getMaximumRange());
	}

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        SensorEvent event = (SensorEvent) params.get(PARAM_SENSOR_EVENT);
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        mCounter ++;
        List<DataEntry> dataList = new ArrayList<>();
        dataList.add(new DataEntry(Metrics.ATMOSPHERIC_PRESSURE + 0, timestamp, event.values[0]));
        return dataList;
    }

    public int getCount() {
        return this.mCounter;
    }

    public void resetCount() {
        this.mCounter = 0;
    }

}
