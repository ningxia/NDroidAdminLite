package edu.nd.nxia.cimonlite;

import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;
import android.util.Log;
import android.util.SparseArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.StringTokenizer;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;

/**
 * Monitoring service for running applications
 *
 * @author ningxia
 */
public class ApplicationService extends MetricDevice<String> {

    private static final String TAG = "NDroid";
    private static final int APPLICATION_METRICS = 1;
    private static final long FIVE_MINUTES = 300000;

    private static final String title = "Application activity";
    private static final String[] metrics = {"Running Applications"};
    private static final int APPLICATION = Metrics.APPLICATION_CATEGORY - Metrics.APPLICATION;
    private static final ApplicationService INSTANCE = new ApplicationService();

    private ActivityManager activityManager;
    private List<ActivityManager.RunningServiceInfo> results;

    private ApplicationService() {
        if (DebugLog.DEBUG) Log.d(TAG, "ApplicationService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("ApplicationService already instantiated");
        }
        groupId = Metrics.APPLICATION_CATEGORY;
        metricsCount = APPLICATION_METRICS;
        values = new String[APPLICATION_METRICS];
    }

    public static ApplicationService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "ApplicationService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_OTHER;
        this.period = period;
        this.timer = System.currentTimeMillis();
    }

    @Override
    void insertDatabaseEntries() {
        if (DebugLog.DEBUG) Log.d(TAG, "ApplicationService.insertDatabaseEntries - insert entries");
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
        database.insertOrReplaceMetricInfo(groupId, title, "Application", SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1 application", Metrics.TYPE_USER);
        database.insertOrReplaceMetrics(groupId + 0, groupId, metrics[0], "", 1000);
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        Context context = MyApplication.getAppContext();
        activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (timestamp - timer < period - timeOffset) {
            return null;
        }
        fetchValues();
        setTimer(timestamp);
        List<DataEntry> dataList = new ArrayList<>();
        for (int i = 0; i < APPLICATION_METRICS; i++) {
            dataList.add(new DataEntry(Metrics.APPLICATION_CATEGORY + i, timestamp, values[i]));
        }
        return dataList;
    }

    private void fetchValues() {
//        if (DebugLog.DEBUG) Log.d(TAG, "ApplicationService.fetchValues - updating Application values");
        if (activityManager == null) {
            return;
        }

        results = activityManager.getRunningServices(Integer.MAX_VALUE);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < results.size(); i ++) {
            sb.append(results.get(i).process)
                    .append("+")
                    .append(getDate(results.get(i).lastActivityTime + System.currentTimeMillis() - SystemClock.elapsedRealtime(), "hh:ss MM/dd/yyyy"))
                    .append("+")
                    .append(results.get(i).foreground)
                    .append("|");
        }
//        if (DebugLog.DEBUG) Log.d(TAG, "ApplicationService.fetchValues: " + sb.substring(0, sb.length() - 1));
        // format: PROCESS+DATE+FOREGROUND|
        values[APPLICATION] = sb.substring(0, sb.length() - 1);
        results.clear();
    }

    private String getDate(long milliSeconds, String dateFormat) {
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat, Locale.US);
        return formatter.format(milliSeconds);
    }

}
