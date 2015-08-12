package edu.nd.nxia.cimonlite;

import android.app.Application;
import android.content.Context;

/**
 * Class to allow global access to application context within app.
 * @author ningxia
 *
 */
public class MyApplication extends Application {
    private static Context context;

    @Override
    public void onCreate(){
        MyApplication.context = getApplicationContext();
    }

    /**
     * Get application context.
     * @return   context of application
     */
    public static Context getAppContext() {
        return context;
    }

}
