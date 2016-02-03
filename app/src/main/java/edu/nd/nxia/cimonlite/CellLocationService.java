package edu.nd.nxia.cimonlite;

import android.content.Context;
import android.os.SystemClock;
import android.telephony.TelephonyManager;
import android.telephony.cdma.CdmaCellLocation;
import android.telephony.gsm.GsmCellLocation;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.List;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;

/**
 * Monitoring service for Cell location
 *
 * @author ningxia
 */
public class CellLocationService extends MetricDevice<Integer> {

    private static final String TAG = "NDroid";
    private static final int CELL_METRICS = 2;
    private static final long THIRTY_SECONDS = 30000;

    private static final String title = "Cell location activity";
    private static final String[] metrics = {"GSM Cell ID", "GSM Location Area Code"};
    private static final int CELL_CID = Metrics.CELL_CID - Metrics.CELL_LOCATION_CATEGORY;
    private static final int CELL_LAC = Metrics.CELL_LAC - Metrics.CELL_LOCATION_CATEGORY;
    private static final CellLocationService INSTANCE = new CellLocationService();

    private TelephonyManager telephonyManager;

    private CellLocationService() {
        if (DebugLog.DEBUG) Log.d(TAG, "CellLocationService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("CellLocationService already instantiated");
        }
        groupId = Metrics.CELL_LOCATION_CATEGORY;
        metricsCount = CELL_METRICS;
        values = new Integer[CELL_METRICS];
    }

    public static CellLocationService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "CellLocationService.getInstance - get single instance");
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
        if (DebugLog.DEBUG) Log.d(TAG, "CellLocationService.insertDatabaseEntries - insert entries");
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
        database.insertOrReplaceMetricInfo(groupId, title, "Cell Location", SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1 application", Metrics.TYPE_USER);
        for (int i = 0; i < CELL_METRICS; i ++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 1000);
        }
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        Context context = MyApplication.getAppContext();
        telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (timestamp - timer < period) {
            return null;
        }
        setTimer(timestamp);
        fetchValues();
//        if (DebugLog.DEBUG) Log.d(TAG, "CellLocationService.getData - updating values");
        List<DataEntry> dataList = new ArrayList<>();
        for (int i = 0; i < CELL_METRICS; i ++) {
            dataList.add(new DataEntry(Metrics.CELL_LOCATION_CATEGORY + i, timestamp, values[i]));
        }
        return dataList;
    }

    private void fetchValues() {
        switch (telephonyManager.getPhoneType()) {
            case TelephonyManager.PHONE_TYPE_CDMA:
                CdmaCellLocation cdmaCellLocation = (CdmaCellLocation) telephonyManager.getCellLocation();
                values[CELL_CID] = cdmaCellLocation.getBaseStationId();
                values[CELL_LAC] = cdmaCellLocation.getNetworkId();
//                if (DebugLog.DEBUG) Log.d(TAG, "CellLocationService.fetchValues: " + " CDMA " + values[CELL_CID] + " " + values[CELL_LAC]);
                break;
            case TelephonyManager.PHONE_TYPE_GSM:
                GsmCellLocation gsmCellLocation = (GsmCellLocation) telephonyManager.getCellLocation();
                values[CELL_CID] = gsmCellLocation.getCid() & 0xffff;
                values[CELL_LAC] = gsmCellLocation.getLac() & 0xffff;
//                if (DebugLog.DEBUG) Log.d(TAG, "CellLocationService.fetchValues: " + " GSM "+ values[CELL_CID] + " " + values[CELL_LAC]);
                break;
            default:
                values[CELL_CID] = -1;
                values[CELL_LAC] = -1;
        }
    }
}
