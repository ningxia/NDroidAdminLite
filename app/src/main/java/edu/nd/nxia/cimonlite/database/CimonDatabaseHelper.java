package edu.nd.nxia.cimonlite.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import edu.nd.nxia.cimonlite.DebugLog;

/**
 * Database helper
 *
 * @author ningxia
 */
public class CimonDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "NDroid";

    private static final String DATABASE_NAME = "cimon.db";
    private static final int DATABASE_VERSION = 1;

    public CimonDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        if (DebugLog.DEBUG) Log.d(TAG, "CimonDatabaseHelper.CimonDatabaseHelper - opening database : "
                + DATABASE_NAME + " version " + DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        if (DebugLog.DEBUG) Log.d(TAG, "CimonDatabaseHelper.onCreate - creating tables");
        MetricInfoTable.onCreate(db);
        MetricsTable.onCreate(db);
        MonitorTable.onCreate(db);
        DataTable.onCreate(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (DebugLog.DEBUG) Log.d(TAG, "CimonDatabaseHelper.onUpgrade - upgrading tables");
        MetricInfoTable.onUpgrade(db, oldVersion, newVersion);
        MetricsTable.onUpgrade(db, oldVersion, newVersion);
        MonitorTable.onUpgrade(db, oldVersion, newVersion);
        DataTable.onUpgrade(db, oldVersion, newVersion);
    }
}
