package edu.nd.nxia.cimonlite;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.BaseColumns;
import android.util.Log;
import android.util.SparseArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;

/**
 * MMS information service
 * @author ningxia
 */
public final class MMSInfoService extends MetricDevice<String> {

    private static final String TAG = "NDroid";
    private static final int MMS_METRICS = 2;
    private static final long THIRTY_SECONDS = 30000;

    private static final String title = "MMS information activity";
    private static final String[] metrics = {"MMS Sent", "MMS Received"};
    private static final int MMS_SENT = Metrics.MMSSENT - Metrics.MMS_INFO_CATEGORY;
    private static final int MMS_RECEIVED = Metrics.MMSRECEIVED - Metrics.MMS_INFO_CATEGORY;
    private static final MMSInfoService INSTANCE = new MMSInfoService();
    private static String description;

    public static final String MMS_ADDRESS = "address";
    public static final String MMS_DATE = "date";
    public static final String MMS_TYPE = "m_type";

    private static final int MESSAGE_TYPE_INBOX  = 1;
    private static final int MESSAGE_TYPE_SENT   = 2;

    private static final Uri uri = Uri.parse("content://mms/");
    private static final String[] mms_projection = new String[]{BaseColumns._ID,
            MMS_ADDRESS, MMS_DATE, MMS_TYPE};

    private static final String SORT_ORDER = BaseColumns._ID + " DESC";
    private long prevMMSID  = -1;

    private ContentObserver mmsObserver = null;
    private ContentResolver mmsResolver;
    private List<DataEntry> tempData;

    private MMSInfoService() {
        if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("MMSInfoService already instantiated");
        }
        groupId = Metrics.MMS_INFO_CATEGORY;
        metricsCount = MMS_METRICS;
        values = new String[MMS_METRICS];
        tempData = new ArrayList<>();
    }

    public static MMSInfoService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_RECEIVER;
        this.period = period;
        this.timer = System.currentTimeMillis();
        mmsResolver = MyApplication.getAppContext().getContentResolver();
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
        for (int i = 0; i < MMS_METRICS; i ++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 1000);
        }
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        mmsObserver = (ContentObserver) params.get(PARAM_MMS_OBSERVER);
        mmsResolver.registerContentObserver(Uri.parse("content://mms-sms"), true, mmsObserver);
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (params.get(PARAM_MMS_STATE) != null) {
            getMMSData();
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

    private void updateMMSData() {
        Cursor cur = mmsResolver.query(uri, mms_projection, MMS_TYPE + "=?",
                new String[]{String.valueOf(MESSAGE_TYPE_INBOX)}, SORT_ORDER);
        if (!cur.moveToFirst()) {
            if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.updateMMSData - incoming MMS cursor empty?");
            values[MMS_RECEIVED] = "";
        }
        else {
            prevMMSID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        }
        if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.updateMMSData - received: " + values[MMS_RECEIVED]);
        cur.close();

        cur = mmsResolver.query(uri, mms_projection, MMS_TYPE + "=?",
                new String[] {String.valueOf(MESSAGE_TYPE_SENT)}, SORT_ORDER);
        if (!cur.moveToFirst()) {
            if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.updateMMSData - sent MMS cursor empty?");
            values[MMS_SENT] = "";
        }
        else {
            long topID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
            if (topID > prevMMSID) {
                prevMMSID = topID;
            }
        }
        if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.updateMMSData - sent: " + values[MMS_SENT]);
        cur.close();
    }

    private void getMMSData() {
        Cursor cur = mmsResolver.query(uri, null, "msg_box = 1 or msg_box = 4", null, SORT_ORDER);
        if (!cur.moveToFirst()) {
            cur.close();
            if (DebugLog.DEBUG) Log.d(TAG, "MMSInfoService.getMMSData - cursor empty?");
            return;
        }

        long firstID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        long nextID = firstID;
        final int TYPE_COLUMN = cur.getColumnIndex(MMS_TYPE);
        int type;
        long mmsDate;
        while (nextID > prevMMSID) {
            type = cur.getInt(TYPE_COLUMN);
            mmsDate = cur.getLong(cur.getColumnIndexOrThrow(MMS_DATE));
            handleMessage(nextID, type, mmsDate);
            if (!cur.moveToNext()) {
                break;
            }

            nextID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        }

        cur.close();
        prevMMSID  = firstID;
    }

    private void appendInfo(StringBuilder sb, String mmsAddress, long mmsDate) {
        sb.append(mmsAddress)
                .append("+")
                .append(mmsDate);
    }

    private void handleMessage(final long nextID, final int type, final long mmsDate) {
        new Timer().schedule(new TimerTask() {
            @Override
            public void run() {
                String mmsAddress = getAddress(nextID);
                StringBuilder sbReceived = new StringBuilder();
                StringBuilder sbSent = new StringBuilder();
                switch (type) {
                    case 132:
                        appendInfo(sbReceived, mmsAddress, mmsDate);
                        tempData.add(new DataEntry(Metrics.MMSRECEIVED, mmsDate, sbReceived.toString()));
                        if (DebugLog.DEBUG)
                            Log.d(TAG, "MMSInfoService.getMmsData - received: " + sbReceived.toString());
                        sbReceived.setLength(0);
                        break;
                    case 128:
                        appendInfo(sbSent, mmsAddress, mmsDate);
                        tempData.add(new DataEntry(Metrics.MMSSENT, mmsDate, sbSent.toString()));
                        if (DebugLog.DEBUG)
                            Log.d(TAG, "MMSInfoService.getMmsData - sent: " + sbSent.toString());
                        sbSent.setLength(0);
                        break;
                    default:
                        break;
                }
            }
        }, 100);
    }

    private String getAddress(long id) {
        Uri uriAddress = Uri.parse("content://mms/" + id + "/addr");
        String[] selectAddr = {"address"};
        Cursor curAddress = mmsResolver.query(uriAddress, selectAddr, "msg_id=" + id, null, null);
        String address = "";
        String val;
        if (curAddress.moveToFirst()) {
            do {
                val = curAddress.getString(curAddress.getColumnIndex("address"));
                if (val != null) {
                    val = val.replaceAll("[^0-9]", "");
                    if (!val.equals("")) {
                        address = val;
                        break;
                    }
                }
            } while (curAddress.moveToNext());
        }
        if (curAddress != null) {
            curAddress.close();
        }
        return address;
    }

}
