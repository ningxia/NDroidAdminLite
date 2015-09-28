package edu.nd.nxia.cimonlite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;

/**
 * Monitoring service for Call State
 *
 * <li>Ringer mode: Normal</li>
 * <li>Ringer mode: Silent</li>
 * <li>Ringer mode: Vibrate</li>
 */
public class CallStateService extends MetricDevice<String> {
    private static final String TAG = "NDroid";
    private static final int CALLSTATE_METRICS = 1;
    private static final long FIVE_MINUTES = 300000;

    private static final String title = "Call state activity";
    private static final String[] metrics = {"Ringer Mode"};
    private static final int CALLSTATE = Metrics.CALLSTATE - Metrics.CALLSTATE_CATEGORY;
    private static final CallStateService INSTANCE = new CallStateService();
    private static final IntentFilter filter = new IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION);
    private Context context;
    private AudioManager mAudioManager;
    private List<DataEntry> tempData;

    private CallStateService() {
        if (DebugLog.DEBUG) Log.d(TAG, "CallStateService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("CallStateService already instantiated");
        }
        groupId = Metrics.CALLSTATE_CATEGORY;
        metricsCount = CALLSTATE_METRICS;
        values = new String[CALLSTATE_METRICS];
        tempData = new ArrayList<>();
    }

    public static CallStateService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "BluetoothService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_RECEIVER;
        this.period = period;
        this.timer = System.currentTimeMillis();
    }

    @Override
    void insertDatabaseEntries() {
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
        database.insertOrReplaceMetricInfo(groupId, title, "Ringer mode", SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1", Metrics.TYPE_USER);
        database.insertOrReplaceMetrics(groupId + 0, groupId, metrics[0], "", 1000);
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        Context context = (Context) params.get(PARAM_CONTEXT);
        BroadcastReceiver broadcastReceiver = (BroadcastReceiver) params.get(PARAM_BROADCAST_RECEIVER);
        context.registerReceiver(broadcastReceiver, filter);
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (params.get(PARAM_CALL_INTENT) != null) {
            fetchValues();
            tempData.add(new DataEntry(Metrics.CALLSTATE_CATEGORY, timestamp, values[0]));
        }
        if (timestamp - timer < period) {
            return null;
        }
        if (DebugLog.DEBUG) Log.d(TAG, "CallStateService.getData - updating values");
        setTimer(timestamp);
        List<DataEntry> dataList = new ArrayList<>();
        for (int i = 0; i < tempData.size(); i ++) {
            dataList.add(tempData.get(i));
        }
        tempData.clear();
        return dataList;
    }

    private void fetchValues() {
        if (DebugLog.DEBUG) Log.d(TAG, "CallStateService.fetchValues - updating Call State values");
        switch (mAudioManager.getRingerMode()) {
            case AudioManager.RINGER_MODE_SILENT:
                values[CALLSTATE] = "Silent";
                break;
            case AudioManager.RINGER_MODE_VIBRATE:
                values[CALLSTATE] = "Vibrate";
                break;
            case AudioManager.RINGER_MODE_NORMAL:
                values[CALLSTATE] = "Normal";
                break;
            default:
                values[CALLSTATE] = "Unknown";
                break;
        }
    }

}
