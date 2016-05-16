package edu.nd.nxia.cimonlite;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.crashlytics.android.Crashlytics;

/**
 * Class to allow global access to application context within app.
 * @author ningxia
 *
 */
public class MyApplication extends Application {
    private static final String TAG = "NDroid";
    private static Context context;

    @Override
    public void onCreate(){
        super.onCreate();
        MyApplication.context = getApplicationContext();

        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

            @Override
            public void uncaughtException(Thread thread, Throwable ex) {
                handleUncaughtException(thread, ex);
            }
        });
    }

    public void handleUncaughtException(Thread thread, Throwable e) {
        Log.e(TAG, "App crashed for unknown reason!");
        e.printStackTrace();
        Crashlytics.logException(e);
        System.exit(1);
    }

    /**
     * Get application context.
     * @return   context of application
     */
    public static Context getAppContext() {
        return context;
    }

}
