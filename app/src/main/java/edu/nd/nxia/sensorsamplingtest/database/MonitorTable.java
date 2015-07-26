package edu.nd.nxia.sensorsamplingtest.database;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import edu.nd.nxia.sensorsamplingtest.DebugLog;

/**
 * Defines layout of Monitor table.
 * This table generates a unique id for all new monitoring requests. This id
 * is used in the Data table to allow later filtering per monitor. This table
 * also provides a time offset per monitor.  This offset can be applied to
 * the times entered in the Data table (which are based on uptime) to acquire
 * the system time from epoch in milliseconds.
 *
 * @author ningxia
 *
 * @see DataTable
 *
 */
public final class MonitorTable {

    private static final String TAG = "NDroid";

    // Database table
    public static final String TABLE_MONITOR = "monitor";
    // Table columns
    /** Unique id (Long) */
    public static final String COLUMN_ID = "_id";
    /** Offset for monitor data times to acquire system time
     *  [as milliseconds from epoch] (Long). */
    public static final String COLUMN_TIME_OFFSET = "timeoffset";
    /** End time of monitor, system time in milliseconds from epoch (Long). */
    public static final String COLUMN_ENDTIME = "endtime";

    // Database creation SQL statement
    private static final String DATABASE_CREATE = "create table "
            + TABLE_MONITOR
            + "("
            + COLUMN_ID + " integer primary key autoincrement, "
            + COLUMN_TIME_OFFSET + " integer not null, "
            + COLUMN_ENDTIME + " integer not null"
            + ");";

    public static void onCreate(SQLiteDatabase database) {
        database.execSQL(DATABASE_CREATE);
    }

    public static void onUpgrade(SQLiteDatabase database, int oldVersion,
                                 int newVersion) {
        if (DebugLog.INFO) Log.i(TAG, TABLE_MONITOR + ": Upgrading database from version "
                + oldVersion + " to " + newVersion
                + ", which will destroy all old data");
        database.execSQL("DROP TABLE IF EXISTS " + TABLE_MONITOR);
        onCreate(database);
    }

}

