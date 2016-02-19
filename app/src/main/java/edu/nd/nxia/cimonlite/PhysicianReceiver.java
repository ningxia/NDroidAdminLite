package edu.nd.nxia.cimonlite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;


public class PhysicianReceiver extends BroadcastReceiver {
    private static final String TAG = "PhysicianReceiver";

    private static final String ACTION_START = "android.intent.action.BOOT_COMPLETED";
    private static final String ACTION_SHUTDOWN = "android.intent.action.ACTION_SHUTDOWN";


    private static final String SHARED_PREFS = "CimonSharedPrefs";
    private static final String MONITOR_STARTED = "monitor_started";
    private static final String MONITOR_SLEEP = "monitor_sleep";
    private static boolean monitorStarted;
    private static Intent ndroidService;
    private static Intent uploadingService;
    private static Intent labelingReminderService;
    private static Intent pingService;
    private static Intent schedulingService;

    SharedPreferences appPrefs;
    SharedPreferences.Editor editor;

    @Override
    public void onReceive(Context context, Intent intent) {
        appPrefs = context.getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        monitorStarted = appPrefs.getBoolean(MONITOR_STARTED, false);
        if (DebugLog.DEBUG) Log.d(TAG,"Monitor Started: " + Boolean.toString(monitorStarted));
        if (monitorStarted) {
            ndroidService = new Intent(context, NDroidService.class);
            uploadingService = new Intent(context, UploadingService.class);
            labelingReminderService = new Intent(context, LabelingReminderService.class);
            pingService = new Intent(context, PingService.class);
            schedulingService = new Intent(context, SchedulingService.class);
            if (ACTION_SHUTDOWN.equals(intent.getAction())) {
                editor = appPrefs.edit();
                editor.remove(MONITOR_SLEEP);
                editor.commit();
                if (DebugLog.DEBUG) Log.d(TAG, "+ stop Services +");
                context.stopService(schedulingService);
                context.stopService(uploadingService);
                context.stopService(labelingReminderService);
                context.stopService(pingService);
            }
            else if (ACTION_START.equals(intent.getAction())) {
                if (DebugLog.DEBUG) Log.d(TAG, "+ start Services +");
                context.startService(schedulingService);
                context.startService(uploadingService);
                context.startService(labelingReminderService);
                context.startService(pingService);
            }
        }
    }
}
