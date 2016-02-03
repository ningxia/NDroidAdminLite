package edu.nd.nxia.cimonlite;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.util.Calendar;

/**
 * Created by ningxia on 2/1/16.
 */
public class SchedulingService extends Service {

    private static final String TAG = "NDroid";
    private static final String THREADTAG = "SchedulingServiceThread";
    private static final String PACKAGE_NAME = "edu.nd.nxia.cimonlite";
    private static final String SHARED_PREFS = "CimonSharedPrefs";
    private static final String SENSOR_DELAY_MODE = "sensor_delay_mode";
    private static final String MONITOR_STARTED = "monitor_started";
    private static final String MONITOR_START_TIME = "monitor_start_time";
    private static final String MONITOR_DURATION = "monitor_duration";
    private static final String MONITOR_SLEEP = "monitor_sleep";
    private static final String WAKE_LOCK = "SchedulingServiceWakeLock";

    private static SharedPreferences appPrefs;
    private static SharedPreferences.Editor appEditor;
    private static String START_TIME;
    private static String END_TIME;
    private static long DURATION_IN_MILLIS;
    private static Intent INTENT;
    private static long INTERVAL = 10 * 60 * 1000;

    private Context context;

    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;
    Handler handler;
    Runnable worker;

    @Override
    public void onCreate() {
        if (DebugLog.DEBUG) Log.d(TAG, "SchedulingService.onCreate - created");
        super.onCreate();
        this.context = getApplicationContext();
        INTENT = new Intent(context, NDroidService.class);
        appPrefs = getSharedPreferences(SHARED_PREFS, MODE_PRIVATE);
        appEditor = appPrefs.edit();
        powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK);
        wakeLock.acquire();
        if (!serviceThread.isAlive()) {
            serviceThread.start();
        }

        START_TIME = appPrefs.getString(MONITOR_START_TIME, "");
        DURATION_IN_MILLIS = appPrefs.getLong(MONITOR_DURATION, -1);
    }

    private static final HandlerThread serviceThread = new HandlerThread(THREADTAG) {
        @Override
        protected void onLooperPrepared() {
            if (DebugLog.DEBUG) Log.d(TAG, "SchedulingService.onLooperPrepared - " + THREADTAG);
            super.onLooperPrepared();
        }
    };

    public class LocalBinder extends Binder {
        SchedulingService getService() {
            return SchedulingService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DebugLog.DEBUG) Log.d(TAG, "SchedulingService.onBind - bind");
        return new LocalBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DebugLog.DEBUG) Log.d(TAG, "SchedulingService.onStartCommand - started");
        boolean monitorStarted = appPrefs.getBoolean(MONITOR_STARTED, false);
        if (!monitorStarted) {
            appEditor.putBoolean(MONITOR_STARTED, true);
            appEditor.commit();
        }
        scheduleService();
        return super.onStartCommand(intent, flags, startId);
    }


    private void scheduleService() {
        if (DebugLog.DEBUG) Log.d(TAG, "SchedulingService.scheduleService - started");
        handler = new Handler();
        worker = new Runnable() {
            @Override
            public void run() {
                if (DebugLog.DEBUG) Log.d(TAG, "SchedulingService.scheduleService - run called");
                long currentInMillis = System.currentTimeMillis();
                String[] startTimeTokens = START_TIME.split(":");
                Calendar calendarStart = Calendar.getInstance();
                calendarStart.setTimeInMillis(System.currentTimeMillis());
                calendarStart.set(Calendar.HOUR_OF_DAY, Integer.parseInt(startTimeTokens[0]));
                calendarStart.set(Calendar.MINUTE, Integer.parseInt(startTimeTokens[1]));
                long startInMillis = calendarStart.getTimeInMillis();
                long endInMillis = startInMillis + DURATION_IN_MILLIS;
                /* Schedule monitor on daily basis. */
                if (currentInMillis >= startInMillis && currentInMillis <= endInMillis) {
//                /* Periodically schedule monitor. */
//                long val = (currentInMillis - startInMillis) % INTERVAL;
//                if (val >= 0 && val <= DURATION_IN_MILLIS) {
                    boolean monitorSleep = appPrefs.getBoolean(MONITOR_SLEEP, true);
                    if (monitorSleep) {
                        context.startService(INTENT);
                        appEditor.putBoolean(MONITOR_SLEEP, false);
                    }
                }
                else {
                    context.stopService(INTENT);
                    appEditor.putBoolean(MONITOR_SLEEP, true);
                }
                appEditor.commit();
                handler.postDelayed(this, 60 * 1000);
            }
        };
        handler.postDelayed(worker, 60  * 1000);
    }

    @Override
    public void onDestroy() {
        if (DebugLog.DEBUG) Log.d(TAG, "SchedulingService.onDestroy - stopped");
        wakeLock.release();
        context.stopService(INTENT);
        handler.removeCallbacks(worker);
        super.onDestroy();
    }

}
