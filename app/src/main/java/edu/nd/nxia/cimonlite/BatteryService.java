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
import android.os.BatteryManager;
import android.os.Messenger;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Monitoring service for battery related data.
 * Battery metrics:
 * <ul>
 * <li>Level - battery level as a percentage </li>
 * <li>Status - status of battery </li>
 * <li>Plugged - status of power source (plugged-in/usb/battery) </li>
 * <li>Health - health status of battery </li>
 * <li>Temperature - temperature of battery </li>
 * <li>Voltage - current voltage of battery </li>
 * </ul>
 * <p>
 * See android documentation of {@link BatteryManager} for complete
 * description of metric values.
 * 
 * @author darts
 * 
 * @see MetricService
 * @see BatteryManager
 *
 */
public final class BatteryService extends MetricDevice<Integer> {

	private static final String TAG = "NDroid";
	private static final int BATT_METRICS = 6;
	private static final int BATT_INT_METRICS = 4;
	private static final long THIRTY_MINUTES = 1800000;
	
	// NOTE: title and string array must be defined above instance,//
	//   otherwise, they will be null in constructor
	private static final IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
	private static final int LEVEL = 		Metrics.BATTERY_PERCENT - Metrics.BATTERY_CATEGORY;
	private static final int STATUS = 		Metrics.BATTERY_STATUS - Metrics.BATTERY_CATEGORY;
	private static final int PLUGGED = 		Metrics.BATTERY_PLUGGED - Metrics.BATTERY_CATEGORY;
	private static final int HEALTH = 		Metrics.BATTERY_HEALTH - Metrics.BATTERY_CATEGORY;
	private static final String title = "Battery";
	private static final BatteryService INSTANCE = new BatteryService();
	private Float temperature;
	private Float voltage;
    private static Intent batteryStatus = null;
    private static IntentFilter batteryIntentFilter;
	
	private BatteryService() {
		if (DebugLog.DEBUG) Log.d(TAG, "BatteryService - constructor");
		groupId = Metrics.BATTERY_CATEGORY;
		metricsCount = BATT_METRICS;
		values = new Integer[BATT_INT_METRICS];
	}
	
	public static BatteryService getInstance() {
		if (DebugLog.DEBUG) Log.d(TAG, "BatteryService.getInstance - get single instance");
		if (!INSTANCE.supportedMetric) return null;
		return INSTANCE;
	}
	
	/**
	 * Get string describing battery used with device.
	 * 
	 * @return    technology description of battery
	 */
	private String getTechnology() {
		batteryStatus = MyApplication.getAppContext().registerReceiver(null, ifilter);
		String technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
		if (technology == null)
			return " ";
		return technology;
	}

    @Override
    void initDevice(long period) {
        this.type = TYPE_RECEIVER;
        this.period = period;
        this.timer = System.currentTimeMillis();
        this.batteryIntentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        Context context = (Context) params.get(PARAM_CONTEXT);
        BroadcastReceiver broadcastReceiver = (BroadcastReceiver) params.get(PARAM_BROADCAST_RECEIVER);
        context.registerReceiver(broadcastReceiver, batteryIntentFilter);
    }

    @Override
	void insertDatabaseEntries() {
		Context context = MyApplication.getAppContext();
		CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
		
		// insert metric group information in database
		database.insertOrReplaceMetricInfo(groupId, title, getTechnology(), 
				SUPPORTED, 0, 0, "100 %", "1 %", Metrics.TYPE_SYSTEM);
		// insert information for metrics in group into database
		database.insertOrReplaceMetrics(Metrics.BATTERY_PERCENT, groupId, 
				"Battery level", context.getString(R.string.units_percent), 100);
		database.insertOrReplaceMetrics(Metrics.BATTERY_STATUS, groupId, 
				"Status", "", 5);
		database.insertOrReplaceMetrics(Metrics.BATTERY_PLUGGED, groupId, 
				"Plugged status", "", 2);
		database.insertOrReplaceMetrics(Metrics.BATTERY_HEALTH, groupId, 
				"Health", "", 7);
		database.insertOrReplaceMetrics(Metrics.BATTERY_TEMPERATURE, groupId, 
				"Temperature", context.getString(R.string.units_celcius), 100);
		database.insertOrReplaceMetrics(Metrics.BATTERY_VOLTAGE, groupId, 
				"Voltage", context.getString(R.string.units_volts), 10);
	}

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        Intent intent = (Intent) params.get(PARAM_INTENT);
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (timestamp - timer < period - timeOffset) {
            return null;
        }
        setTimer(timestamp);
        Integer values[] = new Integer[BATT_INT_METRICS];
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        values[Metrics.BATTERY_STATUS - Metrics.BATTERY_CATEGORY] = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        values[Metrics.BATTERY_PLUGGED - Metrics.BATTERY_CATEGORY] = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
        values[Metrics.BATTERY_HEALTH - Metrics.BATTERY_CATEGORY] = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
        // temperature returned is tenths of a degree centigrade.
        //  temp = value / 10 (degrees celcius)
        float temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        float temperature = temp / 10.0f;
        // voltage returned is millivolts
        float volt = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        float voltage = volt / 1000.0f;
        values[Metrics.BATTERY_PERCENT - Metrics.BATTERY_CATEGORY] = level * 100 / scale;
//        for (int i = 0; i < BATTERY_METRICS; i ++) {
//            dataList.add(new DataEntry(Metrics.BATTERY_PERCENT, timestamp, values[i]));
//        }
        if (DebugLog.DEBUG)
            Log.d(TAG, "BatteryService.batteryReceiver - updating battery values: " + values[0]);
        List<DataEntry> dataList = new ArrayList<>();
        dataList.add(new DataEntry(Metrics.BATTERY_PERCENT, timestamp, values[0]));
        return dataList;
    }

}
