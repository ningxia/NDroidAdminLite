package edu.nd.nxia.cimonlite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.SystemClock;
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

    private static final int BATTERY_PERIOD = 1000 * 60;
    private static final int BATCH_SIZE = 1000;
    private SensorManager mSensorManager;

    private long startTime;
    private long endTime;
    private long batteryTimer;
    private boolean isActive;

    CimonDatabaseAdapter database;
    private SparseArray<MetricDevice<?>> mDeviceArray;
    private List<DataEntry> dataList;
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
    private static final int PARAM_INTENT = 7;

    private static final IntentFilter batteryIntentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private static Intent batteryStatus = null;


    public MetricService(Context _context) {
        this.context = _context;
        this.parameters = new SparseArray<>();
        this.mDeviceArray = new SparseArray<>();
        initDevices();
        database = CimonDatabaseAdapter.getInstance(context);
        appPrefs = context.getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        this.mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        initParameters();
    }

    private void initParameters() {
        parameters.put(PARAM_CONTEXT, context);
        parameters.put(PARAM_SENSOR_MANAGER, mSensorManager);
        parameters.put(PARAM_SENSOR_EVENT_LISTENER, this);
        parameters.put(PARAM_BROADCAST_RECEIVER, mBroadcastReceiver);
        parameters.put(PARAM_MODE, -1);
    }

    public void initDevices() {
        List<MetricDevice<?>> serviceList;
        int[] categories = {Metrics.TYPE_SYSTEM, Metrics.TYPE_SENSOR, Metrics.TYPE_USER};
        for (int i = 0; i < categories.length; i ++) {
            serviceList = MetricDevice.getDevices(categories[i]);
            if (serviceList.size() != 0) {
                for (MetricDevice<?> metricDevice : serviceList) {
                    if (DebugLog.DEBUG) Log.d(TAG, "MetricService.initDevices: groupId " + metricDevice.getGroupId());
                    metricDevice.initDevice();
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
                    for (int i = 0; i < mDeviceArray.size(); i ++) {
                        int key = mDeviceArray.keyAt(i);
                        MetricDevice<?> metricDevice = mDeviceArray.get(key);
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
        for (int i = 0; i < mDeviceArray.size(); i ++) {
            int key = mDeviceArray.keyAt(i);
            mDeviceArray.get(key).registerDevice(parameters);
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
        batteryTimer = startTime;
    }

    public String stopMonitoring() {
        if (DebugLog.DEBUG) Log.d(TAG, "MetricService.startMonitoring - stopped");
        mSensorManager.unregisterListener(this);
        context.unregisterReceiver(mBroadcastReceiver);
        endTime = System.currentTimeMillis();
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
            long upTime = SystemClock.uptimeMillis();
            SparseArray<Object> params = new SparseArray<>();
            params.put(PARAM_SENSOR_EVENT, event);
            params.put(PARAM_TIMESTAMP, upTime);
            switch (sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    dataList.addAll(mDeviceArray.get(Metrics.ACCELEROMETER).getData(params));
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    dataList.addAll(mDeviceArray.get(Metrics.GYROSCOPE).getData(params));
                    break;
                case Sensor.TYPE_PRESSURE:
                    dataList.addAll(mDeviceArray.get(Metrics.ATMOSPHERIC_PRESSURE).getData(params));
                    break;
                default:
            }

            long curTime = System.currentTimeMillis();
            if (curTime - batteryTimer >= BATTERY_PERIOD) {
                batteryStatus = context.registerReceiver(null, batteryIntentFilter);
                params.put(PARAM_INTENT, batteryStatus);
                dataList.addAll(mDeviceArray.get(Metrics.BATTERY_CATEGORY).getData(params));
                batteryTimer = curTime;
            }

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
                long upTime = SystemClock.uptimeMillis();
                SparseArray<Object> params = new SparseArray<>();
                params.put(PARAM_INTENT, intent);
                params.put(PARAM_TIMESTAMP, upTime);
                if (intent.getAction().equals(Intent.ACTION_BATTERY_CHANGED)) {
                    dataList.addAll(mDeviceArray.get(Metrics.BATTERY_CATEGORY).getData(params));
                }
            }
        }
    };

}
