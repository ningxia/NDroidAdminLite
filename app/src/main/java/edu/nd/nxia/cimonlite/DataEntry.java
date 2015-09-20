package edu.nd.nxia.cimonlite;

import android.os.SystemClock;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;

/**
 * Time and value pair for a single data entry in the Data table.
 * Used to batch inserts for efficiency.
 *
 * @author chris miller
 *
 * @see CimonDatabaseAdapter
 *
 */
public class DataEntry<T> {

    public long timestamp;
    public T value;
    public int metricId;
    /**
     * Time and value pair for a single data entry in the Data table.
     * Used to batch inserts for efficiency.
     *
     * @param timestamp    timestamp of data acquisition
     * @param value    value acquired for metric
     */
    public DataEntry(int metricId, long timestamp, T value) {
        this.metricId = metricId;
        this.timestamp = timestamp;
        this.value = value;
    }

    public boolean isByte() {
        return Byte.class.isAssignableFrom(value.getClass());
    }

    public boolean isDouble() {
        return Double.class.isAssignableFrom(value.getClass());
    }

    public boolean isFloat() {
        return Float.class.isAssignableFrom(value.getClass());
    }

    public boolean isInteger() {
        return Integer.class.isAssignableFrom(value.getClass());
    }

    public boolean isLong() {
        return Long.class.isAssignableFrom(value.getClass());
    }

    public boolean isString() {
        return String.class.isAssignableFrom(value.getClass());
    }

}
