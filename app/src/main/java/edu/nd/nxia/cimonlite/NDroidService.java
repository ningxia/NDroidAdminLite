package edu.nd.nxia.cimonlite;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/**
 * CIMON Sensor Service
 *
 * @author ningxia
 */

public class NDroidService extends Service {

    private static final String TAG = "NDroid";
    private static final String THREADTAG = "NDroidServiceThread";
    private static final String PACKAGE_NAME = "edu.nd.nxia.cimonlite";
    private static final String SHARED_PREFS = "CimonSharedPrefs";
    private static final String SENSOR_DELAY_MODE = "sensor_delay_mode";
    private static final String MONITOR_STARTED = "monitor_started";
    private static final String WAKE_LOCK = "NDroidServiceWakeLock";

    private static SharedPreferences appPrefs;

    private Context context;
    MetricService metricService;

    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;

    private static final HandlerThread serviceThread = new HandlerThread(THREADTAG) {
        @Override
        protected void onLooperPrepared() {
            if (DebugLog.DEBUG) Log.d(TAG, "NDroidService.onLooperPrepared - " + THREADTAG);
            super.onLooperPrepared();
        }
    };

    public class LocalBinder extends Binder {
        NDroidService getService() {
            return NDroidService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DebugLog.DEBUG) Log.d(TAG, "NDroidService.onBind - bind");
        return new LocalBinder();
    }

    @Override
    public void onCreate() {
        if (DebugLog.DEBUG) Log.d(TAG, "NDroidService.onCreate - created");
        super.onCreate();
        this.context = getApplicationContext();
        appPrefs = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK);
        wakeLock.acquire();
        if (!serviceThread.isAlive()) {
            serviceThread.start();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DebugLog.DEBUG) Log.d(TAG, "NDroidService.onStartCommand - started");
        metricService = new MetricService(context);
        int mode = appPrefs.getInt(SENSOR_DELAY_MODE, SensorManager.SENSOR_DELAY_FASTEST);
        metricService.startMonitoring(mode);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        if (DebugLog.DEBUG) Log.d(TAG, "NDroidService.onDestroy - stopped");
        metricService.stopMonitoring();
        wakeLock.release();
        super.onDestroy();
    }
}
