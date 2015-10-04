package edu.nd.nxia.cimonlite;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.MediaStore;
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
public final class MediaImageService extends MetricDevice<String> {

    private static final String TAG = "NDroid";
    private static final int IMAGE_METRICS = 1;
    private static final long THIRTY_SECONDS = 30000;

    private static final String title = "MediaStore Image activity";
    private static final String[] metrics = {"Image Taken"};
    private static final int MEDIA_IMAGE = Metrics.MEDIA_IMAGE - Metrics.MEDIA_IMAGE_CATEGORY;
    private static final MediaImageService INSTANCE = new MediaImageService();
    private static String description;

    public static final String DISPLAY_NAME = MediaStore.Images.ImageColumns.DISPLAY_NAME;
    public static final String DATE_TAKEN = MediaStore.Images.ImageColumns.DATE_TAKEN;
    public static final String SIZE = MediaStore.Images.ImageColumns.SIZE;

    private static final Uri uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    private static final String[] image_projection = new String[]{BaseColumns._ID,
            DISPLAY_NAME, DATE_TAKEN, SIZE};

    private static final String SORT_ORDER = BaseColumns._ID + " DESC";
    private long prevSMSID  = -1;

    private ContentObserver imageObserver = null;
    private ContentResolver imageResolver;
    private List<DataEntry> tempData;

    private MediaImageService() {
        if (DebugLog.DEBUG) Log.d(TAG, "MediaImageService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("MediaImageService already instantiated");
        }
        groupId = Metrics.MEDIA_IMAGE_CATEGORY;
        metricsCount = IMAGE_METRICS;
        values = new String[IMAGE_METRICS];
        tempData = new ArrayList<>();
    }

    public static MediaImageService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "MediaImageService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_RECEIVER;
        this.period = period;
        this.timer = System.currentTimeMillis();
        imageResolver = MyApplication.getAppContext().getContentResolver();
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);

        description = "MediaStore image service";
        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, description,
                SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1", Metrics.TYPE_USER);
        // insert information for metrics in group into database
        for (int i = 0; i < IMAGE_METRICS; i ++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 1000);
        }
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        imageObserver = (ContentObserver) params.get(PARAM_IMAGE_OBSERVER);
        imageResolver.registerContentObserver(uri, true, imageObserver);
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (params.get(PARAM_IMAGE_STATE) != null) {
            getImageData(timestamp);
        }
        if (timestamp - timer < period - timeOffset) {
            return null;
        }
        setTimer(timestamp);
        if (DebugLog.DEBUG) Log.d(TAG, "MediaImageService.getData - data updated");
        List<DataEntry> dataList = new ArrayList<>();
        for (int i = 0; i < tempData.size(); i ++) {
            dataList.add(tempData.get(i));
        }
        tempData.clear();
        return dataList;
    }

    private void getImageData(long timestamp) {
        Cursor cur = imageResolver.query(uri, image_projection, null, null, SORT_ORDER);
        if (!cur.moveToFirst()) {
            cur.close();
            if (DebugLog.DEBUG) Log.d(TAG, "MediaImageService.getImageData - cursor empty?");
            return;
        }

        long firstID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        long nextID = firstID;
        StringBuilder sb = new StringBuilder();
        String displayName;
        long dateTaken;
        String size;
        while (nextID != prevSMSID) {
            displayName = cur.getString(cur.getColumnIndexOrThrow(DISPLAY_NAME));
            dateTaken = cur.getLong(cur.getColumnIndexOrThrow(DATE_TAKEN));
            size = getSize(cur.getLong(cur.getColumnIndexOrThrow(SIZE)));
            appendInfo(sb, displayName, dateTaken, size);
            tempData.add(new DataEntry(Metrics.MEDIA_IMAGE, timestamp, sb.toString()));
            if (DebugLog.DEBUG) Log.d(TAG, "MediaImageService.getImageData - " + sb.toString());
            if (!cur.moveToNext()) {
                break;
            }
            sb.setLength(0);
            nextID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        }
        cur.close();
        prevSMSID  = firstID;
    }

    private void appendInfo(StringBuilder sb, String displayName, long dateTaken, String size) {
        sb.append(displayName)
                .append("+")
                .append(dateTaken)
                .append("+")
                .append(size);
    }

    private String getSize(Long bytes) {
        Long KB= bytes / 1024;
        return KB + "KB";
    }

}
