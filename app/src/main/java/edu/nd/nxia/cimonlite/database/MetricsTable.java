package edu.nd.nxia.cimonlite.database;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import edu.nd.nxia.cimonlite.DebugLog;

/**
 * Defines the layout of the Metrics table of the database.
 * This table stores information about a singular metric. It links
 * to readings in {@link DataTable}.
 *
 * @author ningxia
 *
 * @see DataTable
 *
 */
public final class MetricsTable {

    private static final String TAG = "NDroid";

    // Database table
    public static final String TABLE_METRICS = "metrics";
    // Table columns
    /** Unique id (Long) */
    public static final String COLUMN_ID = "_id";
    /** Title of metric (String). */
    public static final String COLUMN_METRIC = "metric";
    /** Index of group in MetricInfo table (Long). */
    public static final String COLUMN_INFO_ID = "infoid";
    /** Units of metric values (String). */
    public static final String COLUMN_UNITS = "units";
    /** Maximum possible value of metric [used by administration app] (Float). */
    public static final String COLUMN_MAX = "max";

    // Database creation SQL statement
    private static final String DATABASE_CREATE = "create table "
            + TABLE_METRICS
            + "("
            + COLUMN_ID + " integer primary key, "
            + COLUMN_METRIC + " text not null, "
            + COLUMN_INFO_ID + " integer not null,"
            + COLUMN_UNITS + " text not null,"
            + COLUMN_MAX + " real not null"
            + ");";

    public static void onCreate(SQLiteDatabase database) {
        database.execSQL(DATABASE_CREATE);
    }

    public static void onUpgrade(SQLiteDatabase database, int oldVersion,
                                 int newVersion) {
        if (DebugLog.DEBUG) Log.i(TAG, TABLE_METRICS + ": Upgrading database from version "
                + oldVersion + " to " + newVersion
                + ", which will destroy all old data");
        database.execSQL("DROP TABLE IF EXISTS " + TABLE_METRICS);
        onCreate(database);
    }

}
