package edu.nd.nxia.cimonlite;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;
import android.util.SparseArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;

/**
 * SMS information service
 * @author ningxia
 */
public final class SMSInfoService extends MetricDevice<String> {

    private static final String TAG = "NDroid";
    private static final int SMS_METRICS = 2;
    private static final long THIRTY_SECONDS = 30000;

    private static final String title = "SMS information activity";
    private static final String[] metrics = {"SMS Sent", "SMS Received"};
    private static final int SMS_SENT = Metrics.SMSSENT - Metrics.SMS_INFO_CATEGORY;
    private static final int SMS_RECEIVED = Metrics.SMSRECEIVED - Metrics.SMS_INFO_CATEGORY;
    private static final SMSInfoService INSTANCE = new SMSInfoService();
    private static String description;

    public static final String SMS_ADDRESS = "address";
    public static final String SMS_DATE = "date";
    public static final String SMS_TYPE = "type";

    private static final int MESSAGE_TYPE_INBOX  = 1;
    private static final int MESSAGE_TYPE_SENT   = 2;

    private static final Uri uri = Uri.parse("content://sms/");
    private static final String[] sms_projection = new String[]{BaseColumns._ID,
            SMS_DATE, SMS_TYPE};

    private static final String SORT_ORDER = BaseColumns._ID + " DESC";
    private long prevSMSID  = -1;

    private ContentObserver smsObserver = null;
    private ContentResolver smsResolver;
    private List<DataEntry> tempData;

    private SMSInfoService() {
        if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("SMSInfoService already instantiated");
        }
        groupId = Metrics.SMS_INFO_CATEGORY;
        metricsCount = SMS_METRICS;
        values = new String[SMS_METRICS];
        tempData = new ArrayList<>();
    }

    public static SMSInfoService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_RECEIVER;
        this.period = period;
        this.timer = System.currentTimeMillis();
        smsResolver = MyApplication.getAppContext().getContentResolver();
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);

        description = "Short message information service";
        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, description,
                SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1", Metrics.TYPE_USER);
        // insert information for metrics in group into database
        for (int i = 0; i < SMS_METRICS; i ++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 1000);
        }
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        smsObserver = (ContentObserver) params.get(PARAM_SMS_OBSERVER);
        smsResolver.registerContentObserver(uri, true, smsObserver);
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (params.get(PARAM_SMS_STATE) != null) {
            updateSmsData();
            getSmsData();
            for (int i = 0; i < SMS_METRICS; i ++) {
                tempData.add(new DataEntry(Metrics.SMS_INFO_CATEGORY + i, timestamp, values[i]));
            }
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

    private void updateSmsData() {
        Cursor cur = smsResolver.query(uri, sms_projection, SMS_TYPE + "=?",
                new String[]{String.valueOf(MESSAGE_TYPE_INBOX)}, SORT_ORDER);
        if (!cur.moveToFirst()) {
            if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.updateSmsData - incoming sms cursor empty?");
            values[SMS_RECEIVED] = "";
        }
        else {
            prevSMSID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        }
        if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.updateSmsData - received: " + values[SMS_RECEIVED]);
        cur.close();

        cur = smsResolver.query(uri, sms_projection, SMS_TYPE + "=?",
                new String[] {String.valueOf(MESSAGE_TYPE_SENT)}, SORT_ORDER);
        if (!cur.moveToFirst()) {
            if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.updateSmsData - sent sms cursor empty?");
            values[SMS_SENT] = "";
        }
        else {
            long topID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
            if (topID > prevSMSID) {
                prevSMSID = topID;
            }
        }
        if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.updateSmsData - sent: " + values[SMS_SENT]);
        cur.close();
    }

    private void getSmsData() {
        Cursor cur = smsResolver.query(uri, sms_projection, null, null, SORT_ORDER);
        if (!cur.moveToFirst()) {
            cur.close();
            if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.getSmsData - cursor empty?");
            return;
        }

        long firstID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        long nextID = firstID;
        final int TYPE_COLUMN = cur.getColumnIndex(SMS_TYPE);
        StringBuilder sbReceived = new StringBuilder();
        StringBuilder sbSent = new StringBuilder();
        while (nextID != prevSMSID) {
            int type = cur.getInt(TYPE_COLUMN);
            String smsAddress = cur.getString(cur.getColumnIndexOrThrow(SMS_ADDRESS));
            String smsDate = getDate(cur.getLong(cur.getColumnIndexOrThrow(SMS_DATE)), "hh:ss MM/dd/yyyy");
            switch (type) {
                case MESSAGE_TYPE_INBOX:
                    appendInfo(sbReceived, smsAddress, smsDate);
                    break;
                case MESSAGE_TYPE_SENT:
                    appendInfo(sbSent, smsAddress, smsDate);
                    break;
                default:
                    break;
            }

            if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.getSmsData - type: " + type);

            if (!cur.moveToNext()) {
                values[SMS_RECEIVED] = sbReceived.substring(0, sbReceived.length() - 1);
                if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.updateSmsData - received: " + values[SMS_RECEIVED]);
                values[SMS_SENT] = sbSent.substring(0, sbSent.length() - 1);
                if (DebugLog.DEBUG) Log.d(TAG, "SMSInfoService.updateSmsData - sent: " + values[SMS_SENT]);
                break;
            }

            nextID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        }

        cur.close();
        prevSMSID  = firstID;
    }

    private void appendInfo(StringBuilder sb, String smsAddress, String smsDate) {
        sb.append(smsAddress)
                .append("+")
                .append(smsDate)
                .append("|");
    }

    private String getDate(long milliSeconds, String dateFormat) {
        SimpleDateFormat formatter = new SimpleDateFormat(dateFormat, Locale.US);
        return formatter.format(milliSeconds);
    }

}
