package edu.nd.nxia.sensorsamplingtest;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

/**
 * CIMON Sensor Service
 *
 * @author ningxia
 */

public class NDroidService extends Service {

    private static final String TAG = "NDroid";
    private static final String THREADTAG = "NDroidServiceThread";
    private static final String PACKAGE_NAME = "edu.nd.nxia.sensorsamplingtest";
    private static final String SHARED_PREFS = "CimonSharedPrefs";
    private static final String RUNNING_METRICS = "running_metrics";

    private static SharedPreferences settings;
    private static SharedPreferences.Editor editor;

    private Context context;
    MetricService metricService;

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
        if (DebugLog.DEBUG) Log.d(TAG, "PhysicianService.onBind - bind");
        return new LocalBinder();
    }

    @Override
    public void onCreate() {
        if (DebugLog.DEBUG) Log.d(TAG, "NDroidService.onCreate - created");
        super.onCreate();
        this.context = getApplicationContext();
        metricService = new MetricService(context);
        settings = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        editor = settings.edit();
        if (!serviceThread.isAlive()) {
            serviceThread.start();
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DebugLog.DEBUG) Log.d(TAG, "NDroidService.onStartCommand - started");
        metricService.startMonitoring();
        editor.putInt(RUNNING_METRICS, 1);
        editor.commit();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        if (DebugLog.DEBUG) Log.d(TAG, "NDroidService.onDestroy - stopped");
        metricService.stopMonitoring();
        editor.remove(RUNNING_METRICS);
        editor.commit();
        super.onDestroy();
    }
}
