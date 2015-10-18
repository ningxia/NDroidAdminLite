/*
 * Copyright (C) 2013 Chris Miller
 *
 * This file is part of CIMON.
 * 
 * CIMON is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *  
 * CIMON is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *  
 * You should have received a copy of the GNU General Public License
 * along with CIMON.  If not, see <http://www.gnu.org/licenses/>.
 *  
 */
package edu.nd.nxia.cimonlite;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;
import android.content.Context;
import android.os.Environment;
import android.os.FileObserver;
import android.os.StatFs;
import android.util.Log;
import android.util.SparseArray;

/**
 * Monitoring service providing updates on file accesses on the SD partition.
 * Events monitored are : read/write/create/delete
 * <p>
 * This service does not guarantee periodic updates, updates will not be sent
 * to requesting apps if there is no change to the value of the specified metric
 */
public final class FileAccessService extends MetricDevice<Long> {

    private static final String TAG = "NDroid";
    private static final int SD_METRICS = 4;
    private static final long FIVE_MINUTES = 300000;

    // NOTE: title and string array must be defined above instance,
    //   otherwise, they will be null in constructor
    private static final int READS = 0;
    private static final int WRITES = 1;
    private static final int CREATES = 2;
    private static final int DELETES = 3;
    private static final String title = "SDCard accesses";
    private static final String[] metrics = {"Reads", "Writes", "Creates", "Deletes"};
    private static final FileAccessService INSTANCE = new FileAccessService();
    private String description;
    private MetricService.AccessObserver accessObserver = null;

    private FileAccessService() {
        if (DebugLog.DEBUG) Log.d(TAG, "FileAccessService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("FileAccessService already instantiated");
        }

        if (!getSdStatus()) {
            if (DebugLog.INFO) Log.i(TAG, "FileAccessService - sdcard access not supported on this system");
            supportedMetric = false;
            return;
        }
        groupId = Metrics.SDCARD_CATEGORY;
        metricsCount = SD_METRICS;
        values = new Long[SD_METRICS];
        for (int i = 0; i < SD_METRICS; i++) {
            values[i] = (long) 0;
        }
    }

    public static FileAccessService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "FileAccessService.getInstance - get single instance");
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
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);

        if(!supportedMetric) {
            database.insertOrReplaceMetricInfo(groupId, title, description,
                    NOTSUPPORTED, 0, 0, "", "", Metrics.TYPE_SYSTEM);
            return;
        }
        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, description,
                SUPPORTED, 0, 0, "Not limited count (long)", "1", Metrics.TYPE_SYSTEM);
        // insert information for metrics in group into database
        for (int i = 0; i < SD_METRICS; i++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 100);
        }
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        accessObserver = (MetricService.AccessObserver) params.get(PARAM_FILE_OBSERVER);
        accessObserver.startWatching();
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (timestamp - timer < period - timeOffset || params.get(PARAM_FILE_EVENT) == null) {
            return null;
        }
        int event = (int) params.get(PARAM_FILE_EVENT);
        setTimer(timestamp);
        if (DebugLog.DEBUG) Log.d(TAG, "FileAccessService.getData");
        switch (event) {
            case FileObserver.ACCESS:
                values[READS] = Long.valueOf(values[READS].longValue() + 1);
                break;
            case FileObserver.MODIFY:
                values[WRITES] = Long.valueOf(values[WRITES].longValue() + 1);
                break;
            case FileObserver.CREATE:
                values[CREATES] = Long.valueOf(values[CREATES].longValue() + 1);
                break;
            case FileObserver.DELETE:
                values[DELETES] = Long.valueOf(values[DELETES].longValue() + 1);
                break;
            default:
        }
        List<DataEntry> dataList = new ArrayList<>();
        for (int i = 0; i < SD_METRICS; i++) {
            dataList.add(new DataEntry(Metrics.SDCARD_CATEGORY + i, timestamp, values[i]));
        }
        return dataList;
    }

    /**
     * Get status of SD card and populate a string with description of available SD card.
     * @return    true if SD card available
     */
    private boolean getSdStatus() {
        String state = Environment.getExternalStorageState();
        if ((Environment.MEDIA_MOUNTED.equals(state)) ||
                (Environment.MEDIA_MOUNTED_READ_ONLY.equals(state))) {
            try {
                File path = Environment.getExternalStorageDirectory();
                StatFs stat = new StatFs(path.getPath());
                long blockSize = stat.getBlockSizeLong();
                long totalBlocks = stat.getBlockCountLong();
                long availableBlocks = stat.getAvailableBlocksLong();
                description = "SD card mounted  |  Total size: " +
                        ((blockSize * totalBlocks) >> 20) + "MB Available: " +
                        ((blockSize * availableBlocks) >> 20) + "MB";
            } catch (IllegalArgumentException e) {
                // this can occur if the SD card is removed, but we haven't received the
                // ACTION_MEDIA_REMOVED Intent yet
                description = "SD card removed";
            }
            return true;
        }
        else if (Environment.MEDIA_NOFS.equals(state)) {
            description = "External storage : filesystem unsupported";
        }
        else if (Environment.MEDIA_BAD_REMOVAL.equals(state)) {
            description = "SD card removed";
        }
        else if (Environment.MEDIA_CHECKING.equals(state)) {
            description = "External storage : disk checking";
        }
        else if (Environment.MEDIA_REMOVED.equals(state)) {
            description = "External storage : not present";
        }
        else if (Environment.MEDIA_SHARED.equals(state)) {
            description = "External storage : shared, not mounted";
        }
        else if (Environment.MEDIA_UNMOUNTED.equals(state)) {
            description = "External storage : SD card not mounted";
        }
        else if (Environment.MEDIA_UNMOUNTABLE.equals(state)) {
            description = "External storage : unmountable";
        }
        return false;
    }

}
