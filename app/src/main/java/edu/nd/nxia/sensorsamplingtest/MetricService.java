package edu.nd.nxia.sensorsamplingtest;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.SystemClock;
import android.util.FloatMath;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import edu.nd.nxia.sensorsamplingtest.database.CimonDatabaseAdapter;

/**
 * Class for managing sensors
 *
 * @author ningxia
 */
public class MetricService implements SensorEventListener {
    private static final String TAG = "NDroid";
    private static final String SHARED_PREFS = "CimonSharedPrefs";
    private static final String PREF_VERSION = "version";
    private static final int SUPPORTED = 1;

    private Context context;

    private static final int ACCEL_METRICS = 4;
    private static final int GYRO_METRICS = 4;
    private static final int BARO_METRICS = 1;
    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private Sensor mBarometer;

    private long startTime;
    private long endTime;
    private boolean isActive;
    private int numAccelerometer;
    private int numGyroscope;
    private int numBarometer;

    CimonDatabaseAdapter database;
    private static final int BATCH_SIZE = 500;
    private List<DataEntry> dataList;
    private int monitorId;

    public MetricService(Context context) {
        this.context = context;
        this.mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        registerSensors();
        database = CimonDatabaseAdapter.getInstance(context);
    }

    private void registerSensors() {
        mSensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mGyroscope = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mBarometer = mSensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE);
        int sensorDelay = SensorManager.SENSOR_DELAY_FASTEST;

        mSensorManager.registerListener(this, mAccelerometer, sensorDelay);
        mSensorManager.registerListener(this, mGyroscope, sensorDelay);
        mSensorManager.registerListener(this, mBarometer, sensorDelay);
    }

    public void startMonitoring() {
        dataList = new ArrayList<>();
        registerSensors();
        numAccelerometer = 0;
        numGyroscope = 0;
        numBarometer = 0;
        isActive = true;
        final long curTime = System.currentTimeMillis();
        final long upTime = SystemClock.uptimeMillis();
        monitorId = database.insertMonitor(curTime - upTime);
        startTime = System.currentTimeMillis();
    }

    public String stopMonitoring() {
        mSensorManager.unregisterListener(this);
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
            database.insertBatchGroupData(monitorId, (ArrayList<DataEntry>) dataList);
        }
        return result;
    }

    public void insertDatabaseEntries() {
        SharedPreferences appPrefs = context.getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
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

                    if (DebugLog.DEBUG) Log.d(TAG, "AccelerometerService.insertDatabaseEntries - insert entries");
                    // Accelerometer
                    // insert metric group information in database
                    String[] metrics = {"X", "Y", "Z", "Magnitude"};
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

                    // Gyroscope
                    // insert metric group information in database
                    database.insertOrReplaceMetricInfo(Metrics.GYROSCOPE, "Gyroscope", mGyroscope.getName(),
                            SUPPORTED, mGyroscope.getPower(), mGyroscope.getMinDelay()/1000,
                            mGyroscope.getMaximumRange() + " " + context.getString(R.string.units_rads),
                            mGyroscope.getResolution() + " " + context.getString(R.string.units_rads),
                            Metrics.TYPE_SENSOR);
                    // insert information for metrics in group into database
                    for (int i = 0; i < GYRO_METRICS; i++) {
                        database.insertOrReplaceMetrics(Metrics.GYROSCOPE + i, Metrics.GYROSCOPE, metrics[i],
                                context.getString(R.string.units_rads), mGyroscope.getMaximumRange());
                    }

                    // Barometer
                    // insert metric group information in database
                    database.insertOrReplaceMetricInfo(Metrics.ATMOSPHERIC_PRESSURE, "Pressure", mBarometer.getName(),
                            SUPPORTED, mBarometer.getPower(), mBarometer.getMinDelay() / 1000,
                            mBarometer.getMaximumRange() + " " + context.getString(R.string.units_hpa),
                            mBarometer.getResolution() + " " + context.getString(R.string.units_hpa),
                            Metrics.TYPE_SENSOR);
                    // insert information for metrics in group into database
                    database.insertOrReplaceMetrics(Metrics.ATMOSPHERIC_PRESSURE, Metrics.ATMOSPHERIC_PRESSURE, "Atmosphere pressure",
                            context.getString(R.string.units_hpa), mBarometer.getMaximumRange());
                }
            }).start();
            SharedPreferences.Editor editor = appPrefs.edit();
            editor.putInt(PREF_VERSION, appVersion);
            editor.commit();
        }
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
}
