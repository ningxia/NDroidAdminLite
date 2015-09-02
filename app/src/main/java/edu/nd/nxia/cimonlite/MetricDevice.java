package edu.nd.nxia.cimonlite;

import java.util.Comparator;

/**
 * Definition of abstract class for metric monitoring devices. These devices provide
 * sensor or device readings.
 *
 * @author Ning Xia
 *
 * @param <T>    value type for metrics monitored (typically subclass of Number)
 */
public abstract class MetricDevice<T extends Comparator<T>> {
    private static final String TAG = "NDroid";
    protected static final int SUPPORTED = 1;
    protected static final int NOTSUPPORTED = 0;

    protected boolean supportedMetric = true;
    protected int groupId;
    protected int metricsCount;

    protected T[] values;

    /**
     * Initialize device
     */
    abstract void initDevice();

    /**
     * Insert entries for metric group and metrics into database.
     */
    abstract void insertDatabaseEntries();

}
