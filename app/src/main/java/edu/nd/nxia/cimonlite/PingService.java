package edu.nd.nxia.cimonlite;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import org.json.JSONObject;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;
import edu.nd.nxia.cimonlite.database.DataCommunicator;
import edu.nd.nxia.cimonlite.database.DataTable;

/**
 * Created by xiaobo on 9/15/15.
 */
public class PingService extends Service {

    private static final String TAG = "CimonReminderService";
    private static final String WAKE_LOCK = "UploadingServiceWakeLock";
    private static final int period = 1000 * 15;
    private static final String appVersion = "1.0";

    PowerManager powerManager;
    PowerManager.WakeLock wakeLock;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
//        pingServer();
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        Context context = MyApplication.getAppContext();
        powerManager = (PowerManager) context.getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK);
        wakeLock.acquire();
    }

    @Override
    public void onDestroy() {
        if (DebugLog.DEBUG) Log.d(TAG, "PingService.onDestroy - stopped");
        wakeLock.release();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    private void pingServer() {
        Log.d(TAG, "Start Ping Service");
        final Handler handler = new Handler();
        final Runnable worker = new Runnable() {
            public void run() {
                new Thread(new Runnable() {
                    public void run() {
                        JSONObject mainPackage = new JSONObject();
                        try {
                            DataCommunicator comm = new DataCommunicator();
                            mainPackage.put("table", "Ping");
                            String deviceID = UploadingService.getDeviceID();
                            mainPackage.put("device_id", deviceID);
                            mainPackage.put("version", appVersion);
                            long totalRowCount = getTotalRowCount();
                            mainPackage.put("total_row", totalRowCount);
                            long phoneTimeStamp = System.currentTimeMillis();
                            mainPackage.put("phone_time", phoneTimeStamp);
                            String callBack = comm.postData(mainPackage.toString().getBytes());
                            if (DebugLog.DEBUG) Log.d(TAG, "Ping Callback:" + callBack);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
                handler.postDelayed(this, period);
            }
        };
        handler.postDelayed(worker, period);
    }

    private long getTotalRowCount() {
        String query = "select count(*) from " + DataTable.TABLE_DATA;
        Cursor res = CimonDatabaseAdapter.database.rawQuery(query, null);
        res.moveToFirst();
        long totalRowCount = res.getLong(0);
        return totalRowCount;
    }
}
