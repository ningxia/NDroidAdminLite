package edu.nd.nxia.cimonlite;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.ContentObserver;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.FileObserver;
import android.os.Handler;
import android.os.SystemClock;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.FloatMath;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;


/**
 * Class for managing sensors
 *
 * @author ningxia
 */
public class MetricService implements SensorEventListener {
    private static final String TAG = "NDroid";
    private static final String SHARED_PREFS = "CimonSharedPrefs";
    private static final String PREF_VERSION = "version";
    private static final String SENSOR_RESULT = "sensor_result";
    private static final String RUNNING_MONITOR = "running_monitor";
    private static final String SENSOR_DELAY_MODE = "sensor_delay_mode";
    private static final int SUPPORTED = 1;

    private Context context;
    private SparseArray<Object> parameters;

    private static final int BATCH_SIZE = 1000;
    private SensorManager mSensorManager;
    private LocationManager mLocationManager;
    private TelephonyManager mTelephonyManager;
    private ContentResolver mContentResolver;

    private long startTime;
    private long endTime;
    private boolean isActive;

    CimonDatabaseAdapter database;
    private SparseArray<MetricDevice<?>> mDeviceArray;
    private List<DataEntry> dataList;
    private SparseArray<Long> mPeriodArray;
    private int monitorId;

    SharedPreferences appPrefs;
    SharedPreferences.Editor editor;


    private static final int PARAM_CONTEXT = 0;
    private static final int PARAM_SENSOR_MANAGER = 1;
    private static final int PARAM_SENSOR_EVENT_LISTENER = 2;
    private static final int PARAM_BROADCAST_RECEIVER = 3;
    private static final int PARAM_MODE = 4;
    private static final int PARAM_TIMESTAMP = 5;
    private static final int PARAM_SENSOR_EVENT = 6;
    private static final int PARAM_BATTERY_INTENT = 7;
    private static final int PARAM_LOCATION_MANAGER = 8;
    private static final int PARAM_LOCATION_LISTENER = 9;
    private static final int PARAM_LOCATION = 10;
    private static final int PARAM_FILE_OBSERVER = 11;
    private static final int PARAM_FILE_EVENT = 12;
    private static final int PARAM_BLUETOOTH_INTENT = 13;
    private static final int PARAM_WIFI_INTENT = 14;
    private static final int PARAM_SCREEN_INTENT = 15;
    private static final int PARAM_PHONE_LISTENER = 16;
    private static final int PARAM_PHONE_STATE = 17;
    private static final int PARAM_SMS_OBSERVER = 18;
    private static final int PARAM_SMS_STATE = 19;
    private static final int PARAM_MMS_OBSERVER = 20;
    private static final int PARAM_MMS_STATE = 21;
    private static final int PARAM_BROWSER_OBSERVER = 22;
    private static final int PARAM_BROWSER_STATE = 23;
    private static final int PARAM_CALL_INTENT = 24;

    private static final IntentFilter batteryIntentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private static final IntentFilter bluetoothIntentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
    private static Intent batteryStatus = null;
    private static Intent bluetoothStatus = null;


    public MetricService(Context _context) {
        this.context = _context;
        this.parameters = new SparseArray<>();
        this.mDeviceArray = new SparseArray<>();
        this.mPeriodArray = new SparseArray<>();
        initPeriods();
        initDevices();
        database = CimonDatabaseAdapter.getInstance(context);
        appPrefs = context.getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        this.mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        this.mTelephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        this.mContentResolver = context.getContentResolver();
        initParameters();
    }

    private void initPeriods() {
        // Hard coded sampling period for now, will get configurations from server.

        // System
        mPeriodArray.put(Metrics.MEMORY_CATEGORY, 1000L);
        mPeriodArray.put(Metrics.CPULOAD_CATEGORY, 1000L);
        mPeriodArray.put(Metrics.PROCESSOR_CATEGORY, 1000L);
        mPeriodArray.put(Metrics.BATTERY_CATEGORY, 60000L);
        mPeriodArray.put(Metrics.NETBYTES_CATEGORY, 1000L);
        mPeriodArray.put(Metrics.NETSTATUS_CATEGORY, 1000L);
//        mPeriodArray.put(Metrics.INSTRUCTION_CNT, 1000L);                 // Deprecated API level 23
//        mPeriodArray.put(Metrics.SDCARD_CATEGORY, 1000L);

        // Sensors
        mPeriodArray.put(Metrics.LOCATION_CATEGORY, 2000L);
        mPeriodArray.put(Metrics.ACCELEROMETER, 20L);
        mPeriodArray.put(Metrics.MAGNETOMETER, 100L);
        mPeriodArray.put(Metrics.GYROSCOPE, 20L);
        mPeriodArray.put(Metrics.LINEAR_ACCEL, 100L);
        mPeriodArray.put(Metrics.ORIENTATION, 100L);
        mPeriodArray.put(Metrics.PROXIMITY, 1000L);
        mPeriodArray.put(Metrics.ATMOSPHERIC_PRESSURE, 20L);
        mPeriodArray.put(Metrics.LIGHT, 1000L);
        mPeriodArray.put(Metrics.HUMIDITY, 1000L);
        mPeriodArray.put(Metrics.TEMPERATURE, 1000L);

        // User
        mPeriodArray.put(Metrics.SCREEN_ON, 180000L);
        mPeriodArray.put(Metrics.BLUETOOTH_CATEGORY, 20000L);
        mPeriodArray.put(Metrics.WIFI_CATEGORY, 5000L);
        mPeriodArray.put(Metrics.APPLICATION_CATEGORY, 5000L);
        mPeriodArray.put(Metrics.BROWSER_HISTORY_CATEGORY, 24 * 360000L);
        mPeriodArray.put(Metrics.CALLSTATE_CATEGORY, 10000L);
        mPeriodArray.put(Metrics.SMS_INFO_CATEGORY, 1000L);
        mPeriodArray.put(Metrics.MMS_INFO_CATEGORY, 1000L);
        mPeriodArray.put(Metrics.PHONE_CALL_CATEGORY, 1000L);

    }

    private void initParameters() {
        parameters.put(PARAM_CONTEXT, context);
        parameters.put(PARAM_SENSOR_MANAGER, mSensorManager);
        parameters.put(PARAM_SENSOR_EVENT_LISTENER, this);
        parameters.put(PARAM_BROADCAST_RECEIVER, mBroadcastReceiver);
        parameters.put(PARAM_MODE, -1);
        parameters.put(PARAM_LOCATION_MANAGER, mLocationManager);
        parameters.put(PARAM_LOCATION_LISTENER, mLocationListener);
        parameters.put(PARAM_FILE_OBSERVER, mAccessObserver);
        parameters.put(PARAM_PHONE_LISTENER, mPhoneStateListener);
        parameters.put(PARAM_SMS_OBSERVER, mSmsContentObserver);
        parameters.put(PARAM_MMS_OBSERVER, mMmsContentObserver);
        parameters.put(PARAM_BROWSER_OBSERVER, mBrowserContentObserver);
    }

    public void initDevices() {
        List<MetricDevice<?>> serviceList;
        int[] categories = {Metrics.TYPE_SYSTEM, Metrics.TYPE_SENSOR, Metrics.TYPE_USER};
        for (int i = 0; i < categories.length; i ++) {
            serviceList = MetricDevice.getDevices(categories[i]);
            if (!serviceList.isEmpty()) {
                for (MetricDevice<?> metricDevice : serviceList) {
                    if (DebugLog.DEBUG) Log.d(TAG, "MetricService.initDevices: groupId " + metricDevice.getGroupId());
                    metricDevice.initDevice(mPeriodArray.get(metricDevice.getGroupId()));
                    mDeviceArray.put(metricDevice.getGroupId(), metricDevice);
                }
            }
            serviceList.clear();
        }
    }

    public void initDatabase() {
        int storedVersion = appPrefs.getInt(PREF_VERSION, -1);
        int appVersion = -1;
        try {
            appVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        if (DebugLog.DEBUG) Log.d(TAG, "MetricService.initDatabase - appVersion:" + appVersion +
                " storedVersion:" + storedVersion);

        if (appVersion > storedVersion) {
            new Thread(new Runnable() {
                public void run() {
                    MetricDevice<?> metricDevice;
                    for (int i = 0; i < mDeviceArray.size(); i ++) {
                        int key = mDeviceArray.keyAt(i);
                        metricDevice = mDeviceArray.get(key);
                        metricDevice.insertDatabaseEntries();
                    }
                }
            }).start();

            SharedPreferences.Editor editor = appPrefs.edit();
            editor.putInt(PREF_VERSION, appVersion);
            editor.commit();
        }
    }

    private void registerDevices(int mode) {
        parameters.put(PARAM_MODE, mode);
        int key;
        for (int i = 0; i < mDeviceArray.size(); i ++) {
            key = mDeviceArray.keyAt(i);
            mDeviceArray.get(key).registerDevice(parameters);
        }

        // OrientationService is associated with AccelerometerService and MagnetometerService
        OrientationService orientationService = (OrientationService) mDeviceArray.get(Metrics.ORIENTATION);
        if (orientationService != null) {
            AccelerometerService accelerometerService = (AccelerometerService) mDeviceArray.get(Metrics.ACCELEROMETER);
            MagnetometerService magnetometerService = (MagnetometerService) mDeviceArray.get(Metrics.MAGNETOMETER);
            accelerometerService.registerOrientation(orientationService);
            magnetometerService.registerOrientation(orientationService);
        }

    }

    public void startMonitoring(int mode) {
        if (DebugLog.DEBUG) Log.d(TAG, "MetricService.startMonitoring - started");
        dataList = new ArrayList<>();
        registerDevices(mode);
        isActive = true;
        final long curTime = System.currentTimeMillis();
        final long upTime = SystemClock.uptimeMillis();
        int runningMonitor = appPrefs.getInt(RUNNING_MONITOR, -1);
        if (runningMonitor == -1) {
            monitorId = database.insertMonitor(curTime - upTime);
            editor = appPrefs.edit();
            editor.putInt(RUNNING_MONITOR, monitorId);
            editor.commit();
        }
        else {
            monitorId = runningMonitor;
        }
        startTime = System.currentTimeMillis();
    }

    public String stopMonitoring() {
        if (DebugLog.DEBUG) Log.d(TAG, "MetricService.stopMonitoring - stopped");
        endTime = System.currentTimeMillis();
        mSensorManager.unregisterListener(this);
        context.unregisterReceiver(mBroadcastReceiver);
        mLocationManager.removeUpdates(mLocationListener);
        mAccessObserver.stopWatching();
        mTelephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
        mContentResolver.unregisterContentObserver(mSmsContentObserver);
        double offset = (endTime - startTime) / 1000.0;
        AccelerometerService accelerometerService = (AccelerometerService) mDeviceArray.get(Metrics.ACCELEROMETER);
        double rateAccelerometer = accelerometerService.getCount() / offset;
        accelerometerService.resetCount();
        GyroscopeService gyroscopeService = (GyroscopeService) mDeviceArray.get(Metrics.GYROSCOPE);
        double rateGyroscope = gyroscopeService.getCount() / offset;
        gyroscopeService.resetCount();
        PressureService pressureService = (PressureService) mDeviceArray.get(Metrics.ATMOSPHERIC_PRESSURE);
        double rateBarometer = pressureService.getCount() / offset;
        pressureService.resetCount();
        isActive = false;
        String result = String.format(
                "Accelerometer Sampling Rate: %.2fHz\n" +
                        "Gyroscope Sampling Rate: %.2fHz\n" +
                        "Barometer Sampling Rate: %.2fHz",
                rateAccelerometer, rateGyroscope, rateBarometer
        );
        if (dataList.size() > 0) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    database.insertBatchGroupData(monitorId, (ArrayList<DataEntry>) dataList);
                }
            }).start();
        }

        editor = appPrefs.edit();
        editor.remove(RUNNING_MONITOR);
        editor.remove(SENSOR_DELAY_MODE);
        editor.putString(SENSOR_RESULT, result);
        editor.commit();
        return result;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        if (isActive) {
            long curTime = System.currentTimeMillis();
            SparseArray<Object> params = new SparseArray<>();
            params.put(PARAM_SENSOR_EVENT, event);
            params.put(PARAM_TIMESTAMP, curTime);
            params.put(PARAM_LOCATION, null);
            batteryStatus = context.registerReceiver(null, batteryIntentFilter);
            bluetoothStatus = context.registerReceiver(null, bluetoothIntentFilter);
            params.put(PARAM_BATTERY_INTENT, batteryStatus);
            params.put(PARAM_BLUETOOTH_INTENT, bluetoothStatus);
            List<DataEntry> tempData = null;
            switch (sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    tempData = mDeviceArray.get(Metrics.ACCELEROMETER).getData(params);
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    tempData = mDeviceArray.get(Metrics.MAGNETOMETER).getData(params);
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    tempData = mDeviceArray.get(Metrics.GYROSCOPE).getData(params);
                    break;
                case Sensor.TYPE_LINEAR_ACCELERATION:
                    tempData = mDeviceArray.get(Metrics.LINEAR_ACCEL).getData(params);
                    break;
                case Sensor.TYPE_PROXIMITY:
                    tempData = mDeviceArray.get(Metrics.PROXIMITY).getData(params);
                    break;
                case Sensor.TYPE_PRESSURE:
                    tempData = mDeviceArray.get(Metrics.ATMOSPHERIC_PRESSURE).getData(params);
                    break;
                case Sensor.TYPE_LIGHT:
                    tempData = mDeviceArray.get(Metrics.LIGHT).getData(params);
                    break;
                case Sensor.TYPE_RELATIVE_HUMIDITY:
                    tempData = mDeviceArray.get(Metrics.HUMIDITY).getData(params);
                    break;
                case Sensor.TYPE_AMBIENT_TEMPERATURE:
                    tempData = mDeviceArray.get(Metrics.TEMPERATURE).getData(params);
                    break;
                default:
            }
            if (tempData != null) {
                dataList.addAll(tempData);
                tempData.clear();
            }

            // Handle other devices with periodic property, such as BatteryService
            int key;
            MetricDevice<?> metricDevice;
            for (int i = 0; i < mDeviceArray.size(); i ++) {
                key = mDeviceArray.keyAt(i);
                metricDevice = mDeviceArray.get(key);
                if (metricDevice.getType() > MetricDevice.TYPE_SENSOR) {
                    tempData = metricDevice.getData(params);
                    if (tempData != null) {
                        dataList.addAll(tempData);
                        tempData.clear();
                    }
                }
            }

            // Insert data into database.
            if (dataList.size() >= BATCH_SIZE) {
                final ArrayList<DataEntry> dl = new ArrayList<>(dataList);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        database.insertBatchGroupData(monitorId, dl);
                    }
                }).start();
                dataList.clear();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    /**
     * BroadcastReceiver for receiving battery data updates.
     */
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isActive) {
                SparseArray<Object> params = new SparseArray<>();
                params.put(PARAM_TIMESTAMP, System.currentTimeMillis());
                List<DataEntry> tempData = null;
                if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                    params.put(PARAM_BATTERY_INTENT, intent);
                    tempData = mDeviceArray.get(Metrics.BATTERY_CATEGORY).getData(params);
                }
                else if (intent.getAction().equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                    tempData = mDeviceArray.get(Metrics.NETSTATUS_CATEGORY).getData(params);
                }
                else if (intent.getAction().equals(BluetoothDevice.ACTION_FOUND)) {
                    params.put(PARAM_BLUETOOTH_INTENT, intent);
                    tempData = mDeviceArray.get(Metrics.BLUETOOTH_CATEGORY).getData(params);
                }
                else if (intent.getAction().equals(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)) {
                    params.put(PARAM_WIFI_INTENT, intent);
                    tempData = mDeviceArray.get(Metrics.WIFI_CATEGORY).getData(params);
                }
                else if (intent.getAction().equals(Intent.ACTION_SCREEN_ON) || intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                    params.put(PARAM_SCREEN_INTENT, intent);
                    tempData = mDeviceArray.get(Metrics.SCREEN_ON).getData(params);
                }
                else if (intent.getAction().equals(AudioManager.RINGER_MODE_CHANGED_ACTION)) {
                    params.put(PARAM_CALL_INTENT, intent);
                    tempData = mDeviceArray.get(Metrics.CALLSTATE_CATEGORY).getData(params);
                }
                if (tempData != null) {
                    dataList.addAll(tempData);
                }
            }
        }
    };

    private LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
//            if (DebugLog.DEBUG) Log.d(TAG, "LocationService.onLocationChanged - new location");
//            if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
//                if (DebugLog.DEBUG) Log.d(TAG, "LocationService.onLocationChanged - from gps");
//            }
//            else if (location.getProvider().equals(LocationManager.NETWORK_PROVIDER)) {
//                if (DebugLog.DEBUG) Log.d(TAG, "LocationService.onLocationChanged - from network");
//            }
            SparseArray<Object> params = new SparseArray<>();
            params.put(PARAM_LOCATION, location);
            params.put(PARAM_TIMESTAMP, System.currentTimeMillis());
            List<DataEntry> tempData = mDeviceArray.get(Metrics.LOCATION_CATEGORY).getData(params);
            if (tempData != null) {
                dataList.addAll(tempData);
            }
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {

        }

        @Override
        public void onProviderEnabled(String provider) {

        }

        @Override
        public void onProviderDisabled(String provider) {
            if (DebugLog.DEBUG) Log.d(TAG, "LocationService.onProviderDisabled - " + provider);
            if (provider.equals(LocationManager.GPS_PROVIDER)) {
                if (DebugLog.DEBUG) Log.d(TAG, "LocationService.onProviderDisabled - from gps");
            }
            else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
                if (DebugLog.DEBUG) Log.d(TAG, "LocationService.onProviderDisabled - from network");
            }
        }
    };

    /**
     * Extends file observer to handle file access events.
     * Updates event counts in onEvent() handler.
     *
     * @author darts
     *
     */
    public class AccessObserver extends FileObserver {

        public AccessObserver(String path, int mask) {
            super(path, mask);
        }

        @Override
        public void onEvent(int event, String path) {
            if (DebugLog.DEBUG) Log.d(TAG, "MetricService.AccessObserver.onEvent - access triggered");
            SparseArray<Object> params = new SparseArray<>();
            params.put(PARAM_FILE_EVENT, event);
            params.put(PARAM_TIMESTAMP, System.currentTimeMillis());
            List<DataEntry> tempData = mDeviceArray.get(Metrics.SDCARD_CATEGORY).getData(params);
            if (tempData != null) {
                dataList.addAll(tempData);
            }
        }
    }
    private AccessObserver mAccessObserver = new AccessObserver(Environment.getExternalStorageDirectory().getPath(),
            FileObserver.ACCESS | FileObserver.MODIFY | FileObserver.CREATE | FileObserver.DELETE);


    private class MyPhoneStateListener extends PhoneStateListener {

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            if (state == TelephonyManager.CALL_STATE_IDLE) {
                SparseArray<Object> params = new SparseArray<>();
                params.put(PARAM_TIMESTAMP, System.currentTimeMillis());
                params.put(PARAM_PHONE_STATE, state);
                List<DataEntry> tempData = mDeviceArray.get(Metrics.PHONE_CALL_CATEGORY).getData(params);
                if (tempData != null) {
                    dataList.addAll(tempData);
                }
            }
        }
    }
    private MyPhoneStateListener mPhoneStateListener = new MyPhoneStateListener();


    public class SmsContentObserver extends ContentObserver {

        /**
         * Creates a content observer.
         *
         * @param handler The handler to run {@link #onChange} on, or null if none.
         */
        public SmsContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (DebugLog.DEBUG) Log.d(TAG, "MetricService.SmsContentObserver: changed");
            SparseArray<Object> params = new SparseArray<>();
            params.put(PARAM_TIMESTAMP, System.currentTimeMillis());
            params.put(PARAM_SMS_STATE, selfChange);
            List<DataEntry> tempData = mDeviceArray.get(Metrics.SMS_INFO_CATEGORY).getData(params);
            if (tempData != null) {
                dataList.addAll(tempData);
            }
        }
    }
    private SmsContentObserver mSmsContentObserver = new SmsContentObserver(null);


    public class MmsContentObserver extends ContentObserver {

        /**
         * Creates a content observer.
         *
         * @param handler The handler to run {@link #onChange} on, or null if none.
         */
        public MmsContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (DebugLog.DEBUG) Log.d(TAG, "MetricService.SmsContentObserver: changed");
            SparseArray<Object> params = new SparseArray<>();
            params.put(PARAM_TIMESTAMP, System.currentTimeMillis());
            params.put(PARAM_MMS_STATE, selfChange);
            List<DataEntry> tempData = mDeviceArray.get(Metrics.MMS_INFO_CATEGORY).getData(params);
            if (tempData != null) {
                dataList.addAll(tempData);
            }
        }
    }
    private MmsContentObserver mMmsContentObserver = new MmsContentObserver(null);


    /**
     * Content observer to be notified of changes to SMS database tables.
     * @author ningxia
     */
    private class BrowserContentObserver extends ContentObserver {

        public BrowserContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            if (DebugLog.DEBUG) Log.d(TAG, "MetricService.BrowserContentObserver: changed");
            SparseArray<Object> params = new SparseArray<>();
            params.put(PARAM_TIMESTAMP, System.currentTimeMillis());
            params.put(PARAM_BROWSER_STATE, selfChange);
            List<DataEntry> tempData = mDeviceArray.get(Metrics.BROWSER_HISTORY_CATEGORY).getData(params);
            if (tempData != null) {
                dataList.addAll(tempData);
            }
        }
    }
    private BrowserContentObserver mBrowserContentObserver = new BrowserContentObserver(null);

}
