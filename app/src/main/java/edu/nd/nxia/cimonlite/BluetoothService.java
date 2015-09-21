package edu.nd.nxia.cimonlite;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;

/**
 * Monitoring service for Bluetooth activity
 *
 * @author ningxia
 */
public class BluetoothService extends MetricDevice<String> {

    private static final String TAG = "NDroid";
    private static final int BLUETOOTH_METRICS = 1;
    private static final long FIVE_MINUTES = 300000;

    private static final String title = "Bluetooth activity";
    private static final String[] metrics = {"Discovered Bluetooth Devices"};
    private static final int BLUETOOTH_DEVICE = Metrics.BLUETOOTH_CATEGORY - Metrics.BLUETOOTH_DEVICE;
    private static final BluetoothService INSTANCE = new BluetoothService();

    private static BluetoothAdapter mBluetoothAdapter;
    private static IntentFilter mBluetoothIntentFilter;
    private static List<BluetoothDevice> devices;

    private BluetoothService() {
        if (DebugLog.DEBUG) Log.d(TAG, "BluetoothService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("BluetoothService already instantiated");
        }
        groupId = Metrics.BLUETOOTH_CATEGORY;
        metricsCount = BLUETOOTH_METRICS;
        devices = new ArrayList<>();

        values = new String[BLUETOOTH_METRICS];
    }

    public static BluetoothService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "BluetoothService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_RECEIVER;
        this.period = period;
        this.timer = System.currentTimeMillis();
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        mBluetoothIntentFilter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
        database.insertOrReplaceMetricInfo(groupId, title, mBluetoothAdapter.getName(), SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1 device", Metrics.TYPE_USER);
        database.insertOrReplaceMetrics(groupId + 0, groupId, metrics[0], "", 1000);
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        Context context = (Context) params.get(PARAM_CONTEXT);
        BroadcastReceiver broadcastReceiver = (BroadcastReceiver) params.get(PARAM_BROADCAST_RECEIVER);
        context.registerReceiver(broadcastReceiver, mBluetoothIntentFilter);
        mBluetoothAdapter.startDiscovery();
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (params.get(PARAM_BLUETOOTH_INTENT) != null) {
            Intent intent = (Intent) params.get(PARAM_BLUETOOTH_INTENT);
            BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            if (DebugLog.DEBUG) Log.d(TAG, "BluetoothService.getData - discovered Bluetooth device: " + device.getAddress());
            devices.add(device);
        }
        if (timestamp - timer < period) {
            return null;
        }
        if (DebugLog.DEBUG) Log.d(TAG, "BluetoothService.getData - updating Bluetooth values");
        mBluetoothAdapter.cancelDiscovery();
        setTimer(timestamp);
        mBluetoothAdapter.startDiscovery();
        List<DataEntry> dataList = new ArrayList<>();
        for (int i = 0; i < devices.size(); i ++) {
            dataList.add(new DataEntry(Metrics.BLUETOOTH_DEVICE, timestamp, devices.get(i).getAddress()));
        }
        return dataList;
    }

}
