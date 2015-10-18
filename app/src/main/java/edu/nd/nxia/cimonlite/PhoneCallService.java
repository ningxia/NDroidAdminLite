package edu.nd.nxia.cimonlite;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.util.SparseArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;

/**
 * Monitoring service for phone call information metrics
 *
 * <li>Outgoing phone calls</li>
 * <li>Incoming phone calls</li>
 * <li>Missed phone calls</li>
 *
 * @author ningxia
 */
public final class PhoneCallService extends MetricDevice<String> {

    private static final String TAG = "NDroid";
    private static final int PHONE_METRICS = 3;
    private static final long THIRTY_SECONDS = 30000;

    // NOTE: title and string array must be defined above instance,
    //   otherwise, they will be null in constructor
    private static final String title = "Phone call activity";
    private static final String[] metrics = {"Outgoing calls", "Incoming calls", "Missed calls"};
    private static final int OUTGOING =		Metrics.PHONE_CALL_OUTGOING - Metrics.PHONE_CALL_CATEGORY;
    private static final int INCOMING =		Metrics.PHONE_CALL_INCOMING - Metrics.PHONE_CALL_CATEGORY;
    private static final int MISSED =		Metrics.PHONE_CALL_MISSED - Metrics.PHONE_CALL_CATEGORY;
    private static final PhoneCallService INSTANCE = new PhoneCallService();
    private static String description;
    private static int PHONE_STATE =	0;

    private static final Uri phone_uri = CallLog.Calls.CONTENT_URI;
    private static final String[] phone_projection = new String[] {
            CallLog.Calls._ID,
            CallLog.Calls.NUMBER,
            CallLog.Calls.TYPE,
            CallLog.Calls.DATE,
            CallLog.Calls.DURATION
    };

    TelephonyManager telephonyManager;
    private static final String SORTORDER = CallLog.Calls._ID + " DESC";
    private long prevPhoneID  = -1;

    private PhoneStateListener phoneStateListener = null;
    private List<DataEntry> tempData;


    private PhoneCallService() {
        if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("PhoneStateService already instantiated");
        }
        groupId = Metrics.PHONE_CALL_CATEGORY;
        metricsCount = PHONE_METRICS;
        values = new String[PHONE_METRICS];
        tempData = new ArrayList<>();
    }

    public static PhoneCallService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_OTHER;
        this.period = period;
        this.timer = System.currentTimeMillis();
        telephonyManager = (TelephonyManager) MyApplication.getAppContext(
        ).getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);

        switch (telephonyManager.getPhoneType()) {
            case TelephonyManager.PHONE_TYPE_NONE:
                description = "No cellular radio ";
                break;
            case TelephonyManager.PHONE_TYPE_GSM:
                description = "GSM ";
                break;
            case TelephonyManager.PHONE_TYPE_CDMA:
                description = "CDMA ";
                break;
            default:
                description = "SIP ";
                break;
        }
        String operator = telephonyManager.getNetworkOperatorName();
        if ((operator != null) && (operator.length() > 0)) {
            description = description + " (" + operator + ")";
        }
        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, description,
                SUPPORTED, 0, 0, String.valueOf(TelephonyManager.CALL_STATE_OFFHOOK),
                "1", Metrics.TYPE_USER);
        // insert information for metrics in group into database
        for (int i = 0; i < PHONE_METRICS; i++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 200);
        }
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        phoneStateListener = (PhoneStateListener) params.get(PARAM_PHONE_LISTENER);
        telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (params.get(PARAM_PHONE_STATE) != null) {
            getTelephonyData();
        }
        if (timestamp - timer < period - timeOffset) {
            return null;
        }
        setTimer(timestamp);
        List<DataEntry> dataList = new ArrayList<>();
        for (int i = 0; i < tempData.size(); i ++) {
            dataList.add(tempData.get(i));
        }
        tempData.clear();
        return dataList;
    }

    /**
     * Update the values for telephony metrics from telephony database tables.
     */
    private void updateTelephonyData() {
        ContentResolver resolver = MyApplication.getAppContext().getContentResolver();
        Cursor cur = resolver.query(phone_uri, phone_projection, CallLog.Calls.TYPE + "=?",
                new String[]{String.valueOf(CallLog.Calls.INCOMING_TYPE)}, SORTORDER);
        if (!cur.moveToFirst()) {
            //do we really want to close the cursor?
            if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService.updateTelephonyData - incoming call cursor empty?");
            values[INCOMING] = "";
        }
        else {
            prevPhoneID = cur.getLong(cur.getColumnIndex(CallLog.Calls._ID));
        }
        cur.close();

        cur = resolver.query(phone_uri, phone_projection, CallLog.Calls.TYPE + "=?",
                new String[] {String.valueOf(CallLog.Calls.OUTGOING_TYPE)}, SORTORDER);
        if (!cur.moveToFirst()) {
            //do we really want to close the cursor?
            if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService.updateTelephonyData - outgoing call cursor empty?");
            values[OUTGOING] = "";
        }
        else {
            long topID = cur.getLong(cur.getColumnIndex(CallLog.Calls._ID));
            if (topID > prevPhoneID) {
                prevPhoneID = topID;
            }
        }
        cur.close();

        cur = resolver.query(phone_uri, phone_projection, CallLog.Calls.TYPE + "=?",
                new String[] {String.valueOf(CallLog.Calls.MISSED_TYPE)}, SORTORDER);
        if (!cur.moveToFirst()) {
            //do we really want to close the cursor?
            if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService.updateTelephonyData - missed call cursor empty?");
            values[MISSED] = "";
        }
        else {
            long topID = cur.getLong(cur.getColumnIndex(CallLog.Calls._ID));
            if (topID > prevPhoneID) {
                prevPhoneID = topID;
            }
        }
        cur.close();
    }

    /**
     * Update the values for telephony metrics from telephony database tables.
     */
    private void getTelephonyData() {
        ContentResolver resolver = MyApplication.getAppContext().getContentResolver();
        Cursor cur = resolver.query(phone_uri, phone_projection, null, null, SORTORDER);
        if (!cur.moveToFirst()) {
            //do we really want to close the cursor?
            cur.close();
            if (DebugLog.DEBUG) Log.d(TAG, "PhoneStateService.getTelephonyData - cursor empty?");
            return;
        }

        final int NUMBER_COLUMN = cur.getColumnIndex(CallLog.Calls.NUMBER);
        final int DATE_COLUMN = cur.getColumnIndex(CallLog.Calls.DATE);
        final int DURATION_COLUMN = cur.getColumnIndex(CallLog.Calls.DURATION);
        final int TYPE_COLUMN = cur.getColumnIndex(CallLog.Calls.TYPE);

        long firstID = cur.getLong(cur.getColumnIndex(CallLog.Calls._ID));
        long nextID = firstID;
        StringBuilder sbIncoming = new StringBuilder();
        StringBuilder sbOutgoing = new StringBuilder();
        StringBuilder sbMissed = new StringBuilder();
        while (nextID != prevPhoneID) {
            String phoneNumber = cur.getString(NUMBER_COLUMN);
            long startTime = cur.getLong(DATE_COLUMN);
            long endTime = startTime + cur.getLong(DURATION_COLUMN) * 1000;

            int type = cur.getInt(TYPE_COLUMN);

            switch (type) {
                case CallLog.Calls.OUTGOING_TYPE:
                    appendCalls(sbOutgoing, phoneNumber, startTime, endTime);
                    tempData.add(new DataEntry(Metrics.PHONE_CALL_OUTGOING, startTime, sbOutgoing.toString()));
//                    if (DebugLog.DEBUG) Log.d(TAG, "PhoneCallService.getTelephonyData - incoming: " + sbOutgoing.toString());
                    sbOutgoing.setLength(0);
                    break;
                case CallLog.Calls.INCOMING_TYPE:
                    appendCalls(sbIncoming, phoneNumber, startTime, endTime);
                    tempData.add(new DataEntry(Metrics.PHONE_CALL_INCOMING, startTime, sbIncoming.toString()));
//                    if (DebugLog.DEBUG) Log.d(TAG, "PhoneCallService.getTelephonyData - outgoing: " + sbIncoming.toString());
                    sbIncoming.setLength(0);
                    break;
                case CallLog.Calls.MISSED_TYPE:
                    appendCalls(sbMissed, phoneNumber, startTime, endTime);
                    tempData.add(new DataEntry(Metrics.PHONE_CALL_MISSED, startTime, sbMissed.toString()));
//                    if (DebugLog.DEBUG) Log.d(TAG, "PhoneCallService.getTelephonyData - missed: " + sbMissed.toString());
                    sbMissed.setLength(0);
                    break;
                default:
                    break;
            }

            if (!cur.moveToNext()) {
                break;
            }
            nextID = cur.getLong(cur.getColumnIndex(CallLog.Calls._ID));
        }

        cur.close();
        prevPhoneID = firstID;

    }

    private void appendCalls(StringBuilder sb, String phoneNumber, long startTime, long endTime) {
        sb.append(phoneNumber)
                .append("+")
                .append(startTime)
                .append("+")
                .append(endTime);
    }

}
