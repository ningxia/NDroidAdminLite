package edu.nd.nxia.cimonlite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;

/**
 * Monitoring service for Bluetooth activity
 *
 * @author ningxia
 *
 */
public class WifiService extends MetricDevice<String> {

    private static final String TAG = "NDroid";
    private static final int WIFI_METRICS = 1;
    private static final long FIVE_MINUTES = 300000;

    private static final String title = "Wifi activity";
    private static final String[] metrics = {"Discovered Wifi Networks"};
    private static final int WIFI_NETWORK = Metrics.WIFI_CATEGORY - Metrics.WIFI_NETWORK;
    private static final WifiService INSTANCE = new WifiService();

    private Context context;
    private WifiManager mWifiManager;
    private IntentFilter mWifiIntentFilter;
    private List<DataEntry> tempData;

    private WifiService() {
        if (DebugLog.DEBUG) Log.d(TAG, "WifiService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("WifiService already instantiated");
        }
        groupId = Metrics.WIFI_CATEGORY;
        metricsCount = WIFI_METRICS;
        tempData = new ArrayList<>();
        values = new String[WIFI_METRICS];
    }

    public static WifiService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "WifiService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_OTHER;
        this.period = period;
        this.timer = System.currentTimeMillis();
        mWifiIntentFilter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
    }

    @Override
    void insertDatabaseEntries() {
        if (DebugLog.DEBUG) Log.d(TAG, "WifiService.insertDatabaseEntries - insert entries");
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
        database.insertOrReplaceMetricInfo(groupId, title, "WIFI", SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1 network", Metrics.TYPE_USER);
        database.insertOrReplaceMetrics(groupId + 0, groupId, metrics[0], "", 1000);
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        context = (Context) params.get(PARAM_CONTEXT);
        BroadcastReceiver broadcastReceiver = (BroadcastReceiver) params.get(PARAM_BROADCAST_RECEIVER);
        context.registerReceiver(broadcastReceiver, mWifiIntentFilter);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mWifiManager.startScan();
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if ( params.get(PARAM_WIFI_INTENT) != null) {
            if (DebugLog.DEBUG) Log.d(TAG, "WifiService.getData - finish scanning");
            String str = "";
            for (ScanResult result: mWifiManager.getScanResults()) {
                str += result.SSID + "+" + result.BSSID + "|";
            }
            tempData.add(new DataEntry(Metrics.WIFI_CATEGORY, timestamp, str.substring(0, str.length() - 1)));
        }
        if (timestamp - timer < period) {
            return null;
        }
        if (DebugLog.DEBUG) Log.d(TAG, "WifiService.getData - updating Wifi network values");
        setTimer(timestamp);
        mWifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
        mWifiManager.startScan();
        List<DataEntry> dataList = new ArrayList<>();
        for (int i = 0; i < tempData.size(); i ++) {
            dataList.add(tempData.get(i));
        }
        tempData.clear();
        return dataList;
    }

}
