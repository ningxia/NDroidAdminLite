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
 * MediaStore Audio service
 * @author ningxia
 */
public final class MediaAudioService extends MetricDevice<String> {

    private static final String TAG = "NDroid";
    private static final int AUDIO_METRICS = 1;
    private static final long THIRTY_SECONDS = 30000;

    private static final String title = "MediaStore Audio activity";
    private static final String[] metrics = {"Audio Added"};
    private static final int MEDIA_AUDIO = Metrics.MEDIA_AUDIO - Metrics.MEDIA_AUDIO_CATEGORY;
    private static final MediaAudioService INSTANCE = new MediaAudioService();
    private static String description;

    public static final String DISPLAY_NAME = MediaStore.Audio.AudioColumns.DISPLAY_NAME;
    public static final String DATE_ADDED = MediaStore.Audio.AudioColumns.DATE_ADDED;
    public static final String DURATION = MediaStore.Audio.AudioColumns.DURATION;
    public static final String SIZE = MediaStore.Audio.AudioColumns.SIZE;

    private static final Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
    private static final String[] projection = new String[]{BaseColumns._ID,
            DISPLAY_NAME, DATE_ADDED, SIZE};

    private static final String SORT_ORDER = BaseColumns._ID + " DESC";
    private long prevID  = -1;

    private ContentObserver observer = null;
    private ContentResolver resolver;
    private List<DataEntry> tempData;

    private MediaAudioService() {
        if (DebugLog.DEBUG) Log.d(TAG, "MediaAudioService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("MediaAudioService already instantiated");
        }
        groupId = Metrics.MEDIA_AUDIO_CATEGORY;
        metricsCount = AUDIO_METRICS;
        values = new String[AUDIO_METRICS];
        tempData = new ArrayList<>();
    }

    public static MediaAudioService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "MediaAudioService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_RECEIVER;
        this.period = period;
        this.timer = System.currentTimeMillis();
        resolver = MyApplication.getAppContext().getContentResolver();
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);

        description = "MediaStore audio service";
        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, description,
                SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1", Metrics.TYPE_USER);
        // insert information for metrics in group into database
        for (int i = 0; i < AUDIO_METRICS; i ++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 1000);
        }
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        observer = (ContentObserver) params.get(PARAM_AUDIO_OBSERVER);
        resolver.registerContentObserver(uri, true, observer);
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (params.get(PARAM_AUDIO_STATE) != null) {
            getAudioData(timestamp);
        }
        if (timestamp - timer < period - timeOffset) {
            return null;
        }
        setTimer(timestamp);
        if (DebugLog.DEBUG) Log.d(TAG, "MediaAudioService.getData - data updated");
        List<DataEntry> dataList = new ArrayList<>();
        for (int i = 0; i < tempData.size(); i ++) {
            dataList.add(tempData.get(i));
        }
        tempData.clear();
        return dataList;
    }

    private void getAudioData(long timestamp) {
        Cursor cur = resolver.query(uri, projection, null, null, SORT_ORDER);
        if (!cur.moveToFirst()) {
            cur.close();
            if (DebugLog.DEBUG) Log.d(TAG, "MediaAudioService.getAudioData - cursor empty?");
            return;
        }

        long firstID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        long nextID = firstID;
        StringBuilder sb = new StringBuilder();
        String displayName;
        long dateAdded;
        String size;
        while (nextID != prevID) {
            displayName = cur.getString(cur.getColumnIndexOrThrow(DISPLAY_NAME));
            dateAdded = cur.getLong(cur.getColumnIndexOrThrow(DATE_ADDED)) * 1000;
            size = getSize(cur.getLong(cur.getColumnIndexOrThrow(SIZE)));
            appendInfo(sb, displayName, dateAdded, size);
            Log.d(TAG, "MediaAudioService.getAudioData - dateAdded: " + dateAdded);
            tempData.add(new DataEntry(Metrics.MEDIA_AUDIO, timestamp, sb.toString()));
            if (DebugLog.DEBUG) Log.d(TAG, "MediaAudioService.getAudioData - " + sb.toString());
            if (!cur.moveToNext()) {
                break;
            }
            sb.setLength(0);
            nextID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
        }
        cur.close();
        prevID  = firstID;
    }

    private void appendInfo(StringBuilder sb, String displayName, long dateAdded, String size) {
        sb.append(displayName)
                .append("+")
                .append(dateAdded)
                .append("+")
                .append(size);
    }

    private String getSize(Long bytes) {
        Long KB= bytes / 1024;
        return KB + "KB";
    }

}