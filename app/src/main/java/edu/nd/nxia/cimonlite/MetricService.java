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
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.FloatMath;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

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

    private static final int sensorDelay = SensorManager.SENSOR_DELAY_FASTEST;
    private static final int ACCEL_METRICS = 4;
    private static final int GYRO_METRICS = 4;
    private static final int BARO_METRICS = 1;
    private static final int BATTERY_METRICS = 6;
    private static final int LOCATION_METRICS = 4;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private Sensor mBarometer;
    private LocationManager mLocationManager;
    private List<String> mProviders;
    private Location coordinate;
    private static final int BATCH_SIZE = 1000;
    private static final float ONE_SECOND = 1000;
    private static final long FIVE_MINUTES = 300000;

    private long startTime;
    private long endTime;
    private long batteryTimer;
    private boolean isActive;
    private int numAccelerometer;
    private int numGyroscope;
    private int numBarometer;

    CimonDatabaseAdapter database;
    private List<DataEntry> dataList;
    private int monitorId;
    private long lastUpdate = 0;

    SharedPreferences appPrefs;
    SharedPreferences.Editor editor;

    private static final IntentFilter batteryIntentFilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private static Intent batteryStatus = null;
    private static final int BATTERY_PERIOD = 1000 * 60;

    public MetricService(Context ctx) {
        this.context = ctx;
        initSensors();
        database = CimonDatabaseAdapter.getInstance(context);
        appPrefs = context.getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
    }

    private void initSensors() {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mBarometer = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        batteryStatus = context.registerReceiver(batteryReceiver, batteryIntentFilter);
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    private void registerSensors(int mode) {
        mSensorManager.registerListener(this, mAccelerometer, mode);
        mSensorManager.registerListener(this, mGyroscope, mode);
        mSensorManager.registerListener(this, mBarometer, mode);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);
    }

    public void startMonitoring(int mode) {
        if (DebugLog.DEBUG) Log.d(TAG, "MetricService.startMonitoring - started");
        //dataList = new ArrayList<>();
        dataList = new ArrayList();
        registerSensors(mode);
        numAccelerometer = 0;
        numGyroscope = 0;
        numBarometer = 0;
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
        context.unregisterReceiver(batteryReceiver);
        mLocationManager.removeUpdates(locationListener);
        endTime = System.currentTimeMillis();
        double offset = (endTime - startTime) / 1000.0;
        double rateAccelerometer = numAccelerometer / offset;
        double rateGyroscope = numGyroscope / offset;
        double rateBarometer = numBarometer / offset;
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

    public void insertDatabaseEntries() {
        int storedVersion = appPrefs.getInt(PREF_VERSION, -1);
        int appVersion = -1;
        try {
            appVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        if (DebugLog.DEBUG) Log.d(TAG, "NDroidAdmin.onCreate - appVersion:" + appVersion +
                " storedVersion:" + storedVersion);

        if (appVersion > storedVersion) {
            new Thread(new Runnable() {
                public void run() {
                    String[] metrics = {"X", "Y", "Z", "Magnitude"};

                    // Accelerometer
                    // insert metric group information in database
                    if (mAccelerometer == null) {
                        if (DebugLog.INFO) Log.i(TAG, "AccelerometerService - sensor not supported on this system");
                    }
                    else {
                        if (DebugLog.DEBUG) Log.d(TAG, "AccelerometerService.insertDatabaseEntries - insert entries");
                        database.insertOrReplaceMetricInfo(Metrics.ACCELEROMETER, "Accelerometer", mAccelerometer.getName(),
                                SUPPORTED, mAccelerometer.getPower(), mAccelerometer.getMinDelay()/1000,
                                mAccelerometer.getMaximumRange() + " " + context.getString(R.string.units_ms2),
                                mAccelerometer.getResolution() + " " + context.getString(R.string.units_ms2),
                                Metrics.TYPE_SENSOR);
                        // insert information for metrics in group into database
                        for (int i = 0; i < ACCEL_METRICS; i++) {
                            database.insertOrReplaceMetrics(Metrics.ACCELEROMETER + i, Metrics.ACCELEROMETER, metrics[i],
                                    context.getString(R.string.units_ms2), mAccelerometer.getMaximumRange());
                        }
                    }

                    // Gyroscope
                    // insert metric group information in database
                    if (mGyroscope == null) {
                        if (DebugLog.INFO) Log.i(TAG, "GyroscopeService - sensor not supported on this system");
                    }
                    else {
                        if (DebugLog.DEBUG) Log.d(TAG, "GyroscopeService.insertDatabaseEntries - insert entries");
                        database.insertOrReplaceMetricInfo(Metrics.GYROSCOPE, "Gyroscope", mGyroscope.getName(),
                                SUPPORTED, mGyroscope.getPower(), mGyroscope.getMinDelay() / 1000,
                                mGyroscope.getMaximumRange() + " " + context.getString(R.string.units_rads),
                                mGyroscope.getResolution() + " " + context.getString(R.string.units_rads),
                                Metrics.TYPE_SENSOR);
                        // insert information for metrics in group into database
                        for (int i = 0; i < GYRO_METRICS; i++) {
                            database.insertOrReplaceMetrics(Metrics.GYROSCOPE + i, Metrics.GYROSCOPE, metrics[i],
                                    context.getString(R.string.units_rads), mGyroscope.getMaximumRange());
                        }
                    }

                    // Barometer
                    // insert metric group information in database
                    if (mBarometer == null) {
                        if (DebugLog.INFO) Log.i(TAG, "BarometerService - sensor not supported on this system");
                    }
                    else {
                        if (DebugLog.DEBUG) Log.d(TAG, "BarometerService.insertDatabaseEntries - insert entries");
                        database.insertOrReplaceMetricInfo(Metrics.ATMOSPHERIC_PRESSURE, "Pressure", mBarometer.getName(),
                                SUPPORTED, mBarometer.getPower(), mBarometer.getMinDelay() / 1000,
                                mBarometer.getMaximumRange() + " " + context.getString(R.string.units_hpa),
                                mBarometer.getResolution() + " " + context.getString(R.string.units_hpa),
                                Metrics.TYPE_SENSOR);
                        // insert information for metrics in group into database
                        database.insertOrReplaceMetrics(Metrics.ATMOSPHERIC_PRESSURE, Metrics.ATMOSPHERIC_PRESSURE, "Atmosphere pressure",
                                context.getString(R.string.units_hpa), mBarometer.getMaximumRange());
                    }

                    // Battery
                    // insert metric group information in database
                    database.insertOrReplaceMetricInfo(Metrics.BATTERY_CATEGORY, "Battery", getTechnology(),
                            SUPPORTED, 0, 0, "100 %", "1 %", Metrics.TYPE_SYSTEM);
                    // insert information for metrics in group into database
                    database.insertOrReplaceMetrics(Metrics.BATTERY_PERCENT, Metrics.BATTERY_CATEGORY,
                            "Battery level", context.getString(R.string.units_percent), 100);
                    database.insertOrReplaceMetrics(Metrics.BATTERY_STATUS, Metrics.BATTERY_CATEGORY,
                            "Status", "", 5);
                    database.insertOrReplaceMetrics(Metrics.BATTERY_PLUGGED, Metrics.BATTERY_CATEGORY,
                            "Plugged status", "", 2);
                    database.insertOrReplaceMetrics(Metrics.BATTERY_HEALTH, Metrics.BATTERY_CATEGORY,
                            "Health", "", 7);
                    database.insertOrReplaceMetrics(Metrics.BATTERY_TEMPERATURE, Metrics.BATTERY_CATEGORY,
                            "Temperature", context.getString(R.string.units_celcius), 100);
                    database.insertOrReplaceMetrics(Metrics.BATTERY_VOLTAGE, Metrics.BATTERY_CATEGORY,
                            "Voltage", context.getString(R.string.units_volts), 10);

                    // Geo-Location
                    float power = 0;
                    String description = null;
                    List<String> providers = mLocationManager.getProviders(true);
                    for (String provider : providers) {
                        LocationProvider locProvider = mLocationManager.getProvider(provider);
                        power += locProvider.getPowerRequirement();
                        if (description == null) {
                            description = locProvider.getName();
                        }
                        else {
                            description = description + " | " + locProvider.getName();
                        }
                    }
                    // insert metric group information in database
                    database.insertOrReplaceMetricInfo(Metrics.LOCATION_CATEGORY, "Geo-Location", description,
                            SUPPORTED, power, mLocationManager.getGpsStatus(null).getTimeToFirstFix(),
                            "Global coordinate", "1" + context.getString(R.string.units_degrees),
                            Metrics.TYPE_SENSOR);
                    // insert information for metrics in group into database
                    database.insertOrReplaceMetrics(Metrics.LOCATION_LATITUDE, Metrics.LOCATION_CATEGORY,
                            "Latitude", context.getString(R.string.units_degrees), 90);
                    database.insertOrReplaceMetrics(Metrics.LOCATION_LONGITUDE, Metrics.LOCATION_CATEGORY,
                            "Longitude", context.getString(R.string.units_degrees), 180);
                    database.insertOrReplaceMetrics(Metrics.LOCATION_ACCURACY, Metrics.LOCATION_CATEGORY,
                            "Accuracy", context.getString(R.string.units_meters), 500);
                    database.insertOrReplaceMetrics(Metrics.LOCATION_SPEED, Metrics.LOCATION_CATEGORY,
                            "Speed", "m/s", 500);
                }
            }).start();

            SharedPreferences.Editor editor = appPrefs.edit();
            editor.putInt(PREF_VERSION, appVersion);
            editor.commit();
        }
        Log.d(TAG,"AppVersion:" + Integer.toString(appVersion) + " " + Integer.toString(storedVersion));
    }

    private void getAccelData(SensorEvent event, long timestamp) {
        float magnitude = 0;
        Float values[] = new Float[ACCEL_METRICS];
        for (int i = 0; i < (ACCEL_METRICS - 1); i++) {
            values[i] = event.values[i];
            magnitude += event.values[i] * event.values[i];
        }
        values[ACCEL_METRICS - 1] = FloatMath.sqrt(magnitude);
        for (int i = 0; i < ACCEL_METRICS; i ++) {
            dataList.add(new DataEntry(Metrics.ACCELEROMETER + i, timestamp, values[i]));
        }
    }

    private void getGyroData(SensorEvent event, long timestamp) {
        float magnitude = 0;
        Float values[] = new Float[GYRO_METRICS];
        for (int i = 0; i < (GYRO_METRICS - 1); i++) {
            values[i] = event.values[i];
            magnitude += event.values[i] * event.values[i];
        }
        values[GYRO_METRICS - 1] = FloatMath.sqrt(magnitude);
        for (int i = 0; i < GYRO_METRICS; i ++) {
            dataList.add(new DataEntry(Metrics.GYROSCOPE + i, timestamp, values[i]));
        }
    }

    private void getBaroData(SensorEvent event, long timestamp) {
        dataList.add(new DataEntry(Metrics.ATMOSPHERIC_PRESSURE + 0, timestamp, event.values[0]));
    }

    /**
     * Get string describing battery used with device.
     *
     * @return    technology description of battery
     */
    private String getTechnology() {
        String technology = batteryStatus.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY);
        if (technology == null)
            return " ";
        return technology;
    }

    private void getBatteryData(long timestamp, Intent intent) {
        if (DebugLog.DEBUG) Log.d(TAG, "BatteryService.batteryReceiver - updating battery values: " + timestamp);
        Integer values[] = new Integer[BATTERY_METRICS];
        if (intent == null) return;
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
        dataList.add(new DataEntry(Metrics.BATTERY_PERCENT, timestamp, values[0]));
    }

    /**
     * BroadcastReceiver for receiving battery data updates.
     */
    private BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (isActive) {
                long upTime = SystemClock.uptimeMillis();
                getBatteryData(upTime, intent);
            }
        }
    };

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (DebugLog.DEBUG) Log.d(TAG, "LocationService.onLocationChanged - new location");
            if (location.getProvider().equals(LocationManager.GPS_PROVIDER)) {
                if (DebugLog.DEBUG) Log.d(TAG, "LocationService.onLocationChanged - from gps");
            }
            else if (location.getProvider().equals(LocationManager.NETWORK_PROVIDER)) {
                if (DebugLog.DEBUG) Log.d(TAG, "LocationService.onLocationChanged - from network");
            }
            checkLocation(location);
            getLocationData();
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
     * Obtain last known location.
     * Queries GPS and network data for last known locations.  Returns the location
     * which is most accurate or recent, using a formula which essentially equates
     * one second to one meter.
     *
     * @return    best last known location
     */
    private Location getLastLocation() {
        if (DebugLog.DEBUG) Log.d(TAG, "LocationService.getLastLocation - getting last known location");
        Location gps = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location network = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        if (gps != null) {
            if (network != null) {
                // this is my little formula for determining higher quality reading,
                //  which essentially equates one second to one meter
                long timeDelta = gps.getTime() - network.getTime();
                float accuracyDelta = gps.getAccuracy() - network.getAccuracy();
                if (!(gps.hasAccuracy() && network.hasAccuracy())) {
                    accuracyDelta = 0;
                }
                if (((float)timeDelta / ONE_SECOND) > accuracyDelta) {
                    return gps;
                }
                return network;
            }
            return gps;
        }
        return network;
    }

    private boolean checkLocation(Location newCoordinate) {
        if (DebugLog.DEBUG) Log.d(TAG, "LocationService.checkLocation - check quality of location");
        if (newCoordinate == null) {
            return false;
        }
        if (coordinate != null) {
            if (coordinate.equals(newCoordinate)) {
                return false;
            }
        }
        else {
            coordinate = newCoordinate;
            return true;
        }
        // this is my little formula for determining higher quality reading,
        //  which essentially equates one second to one meter
        long timeDelta = coordinate.getTime() - newCoordinate.getTime();
        float accuracyDelta = coordinate.getAccuracy() - newCoordinate.getAccuracy();
        if (!(coordinate.hasAccuracy() && newCoordinate.hasAccuracy())) {
            accuracyDelta = 0;
        }
        if (((float)timeDelta / ONE_SECOND) > accuracyDelta) {
            return false;
        }
        coordinate = newCoordinate;
        return true;
    }

    private void getLocationData() {
        if (isActive) {
            long upTime = SystemClock.uptimeMillis();
            if ((upTime - lastUpdate) > FIVE_MINUTES) {
                coordinate = getLastLocation();
            }
            Double values[] = new Double[LOCATION_METRICS];
            values[0] = coordinate.getLatitude();
            values[1] = coordinate.getLongitude();
            values[2] = (double) coordinate.getAccuracy();
            values[3] = (double) coordinate.getSpeed();
            lastUpdate = upTime;
            for (int i = 0; i < LOCATION_METRICS; i ++) {
                dataList.add(new DataEntry(Metrics.LOCATION_CATEGORY + i, upTime, values[i]));
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        Sensor sensor = event.sensor;

        if (isActive) {
            long upTime = SystemClock.uptimeMillis();
            switch (sensor.getType()) {
                case Sensor.TYPE_ACCELEROMETER:
                    getAccelData(event, upTime);
                    numAccelerometer ++;
//                    Log.d(TAG, "Accelerometer: " + curTime);
                    break;
                case Sensor.TYPE_GYROSCOPE:
                    getGyroData(event, upTime);
                    numGyroscope ++;
//                    Log.d(TAG, "Gyroscope: " + curTime);
                    break;
                case Sensor.TYPE_PRESSURE:
                    getBaroData(event, upTime);
                    numBarometer ++;
//                    Log.d(TAG, "Barometer: " + curTime);
                    break;
                default:
            }

            long curTime = System.currentTimeMillis();
            if (curTime - batteryTimer >= BATTERY_PERIOD) {
                batteryStatus = context.registerReceiver(null, batteryIntentFilter);
                getBatteryData(upTime, batteryStatus);
                batteryTimer = curTime;
            }

            if (dataList.size() >= BATCH_SIZE) {
                //final ArrayList<DataEntry> dl = new ArrayList<>(dataList);
                final ArrayList<DataEntry> dl = new ArrayList(dataList);
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
}
