package edu.nd.nxia.sensorsamplingtest;

import android.app.Service;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;

/**
 * CIMON Sensor Service
 *
 * @author ningxia
 */

public class SensorService extends Service {

    private static final String TAG = "NDroid";
    private static final String THREADTAG = "SensorServiceThread";
    private static final String PACKAGE_NAME = "edu.nd.nxia.sensorsamplingtest";

    private static final HandlerThread serviceThread = new HandlerThread(THREADTAG);

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private Sensor mGyroscope;
    private Sensor mBarometer;

    public class LocalBinder extends Binder {
        SensorService getService() {
            return SensorService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (DebugLog.DEBUG) Log.d(TAG, "PhysicianService.onBind - bind");
        return new LocalBinder();
    }


}
