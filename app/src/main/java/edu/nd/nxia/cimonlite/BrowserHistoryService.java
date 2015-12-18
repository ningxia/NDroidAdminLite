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

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Debug;
import android.provider.BaseColumns;
import android.provider.Browser;
import android.util.Log;
import android.util.SparseArray;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;


/**
 * Monitoring service for browser history
 * @author ningxia
 *
 */
public final class BrowserHistoryService extends MetricDevice<String> {

    private static final String TAG = "NDroid";
    private static final int BROWSING_METRICS = 1;
    private static final long THIRTY_SECONDS = 30000;

    private static final String title = "Browser history";
    private static final String[] metrics = {"Browsing History"};
    private static final int BROWSING_HISTORY = Metrics.BROWSING_HISTORY - Metrics.BROWSER_HISTORY_CATEGORY;
    private static final BrowserHistoryService INSTANCE = new BrowserHistoryService();
    private static String description;

    private static final String BROWSING_TYPE = Browser.BookmarkColumns.BOOKMARK + " = 0"; // 0 = history, 1 = bookmark
    private static final String BROWSING_TITLE = Browser.BookmarkColumns.TITLE;
    private static final String BROWSING_DATE = Browser.BookmarkColumns.DATE;
    private static final String BROWSING_URL = Browser.BookmarkColumns.URL;

    private static final Uri uri = Browser.BOOKMARKS_URI;
    private static final Uri BOOKMARKS_URI_DEFAULT = Uri.parse("content://com.android.chrome.browser/history");
    private static final String[] browsing_projection = new String[]{BaseColumns._ID,
            BROWSING_TITLE, BROWSING_DATE, BROWSING_URL};

    private static final String SORT_ORDER = BaseColumns._ID + " DESC";
    private long prevID = -1;

    private ContentObserver browserObserver = null;
    private ContentResolver browserResolver;
    private List<DataEntry> tempData;

    private BrowserHistoryService() {
        if (DebugLog.DEBUG) Log.d(TAG, "BrowserHistoryService - constructor");
        if (INSTANCE != null) {
            throw new IllegalStateException("BrowserHistoryService already instantiated");
        }
        groupId = Metrics.BROWSER_HISTORY_CATEGORY;
        metricsCount = BROWSING_METRICS;
        values = new String[BROWSING_METRICS];
        tempData = new ArrayList<>();
    }

    public static BrowserHistoryService getInstance() {
        if (DebugLog.DEBUG) Log.d(TAG, "BrowserHistoryService.getInstance - get single instance");
        if (!INSTANCE.supportedMetric) return null;
        return INSTANCE;
    }

    @Override
    void initDevice(long period) {
        this.type = TYPE_RECEIVER;
        this.period = period;
        this.timer = System.currentTimeMillis();
        browserResolver = MyApplication.getAppContext().getContentResolver();
    }

    @Override
    void insertDatabaseEntries() {
        Context context = MyApplication.getAppContext();
        CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);

        description = "Browser History service";
        // insert metric group information in database
        database.insertOrReplaceMetricInfo(groupId, title, description,
                SUPPORTED, 0, 0, String.valueOf(Integer.MAX_VALUE), "1", Metrics.TYPE_USER);
        // insert information for metrics in group into database
        for (int i = 0; i < BROWSING_METRICS; i ++) {
            database.insertOrReplaceMetrics(groupId + i, groupId, metrics[i], "", 1000);
        }
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        browserObserver = (ContentObserver) params.get(PARAM_BROWSER_OBSERVER);
        browserResolver.registerContentObserver(BOOKMARKS_URI_DEFAULT, true, browserObserver);
    }

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (params.get(PARAM_BROWSER_STATE) != null) {
            getBrowserData(timestamp);
        }
        if (timestamp - timer < period - timeOffset) {
            return null;
        }
        setTimer(timestamp);
        if (DebugLog.DEBUG) Log.d(TAG, "BrowserHistoryService.getData: browser history updated...");
        List<DataEntry> dataList = new ArrayList<>();
        for (int i = 0; i < tempData.size(); i ++) {
            dataList.add(tempData.get(i));
        }
        tempData.clear();
        return dataList;
    }

    private void getBrowserData(long timestamp) {
        Cursor cur = browserResolver.query(uri, browsing_projection, BROWSING_TYPE, null, SORT_ORDER);
        if (cur == null) {
            return;
        }
        else {
            if (!cur.moveToFirst()) {
                cur.close();
                if (DebugLog.DEBUG)
                    Log.d(TAG, "BrowserHistoryService.getBrowserData - cursor empty?");
                return;
            }

            long firstID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
            long nextID = firstID;
            StringBuilder sb = new StringBuilder();
            String title;
            long date;
            String url;
            while (nextID != prevID) {
                title = cur.getString(cur.getColumnIndexOrThrow(BROWSING_TITLE));
                date = cur.getLong(cur.getColumnIndexOrThrow(BROWSING_DATE));
                url = cur.getString(cur.getColumnIndexOrThrow(BROWSING_URL));
                appendInfo(sb, title, date, url);
                tempData.add(new DataEntry(Metrics.BROWSING_HISTORY, timestamp, sb.toString()));
                sb.setLength(0);

                if (!cur.moveToNext()) {
                    break;
                }

                nextID = cur.getLong(cur.getColumnIndex(BaseColumns._ID));
            }
            cur.close();
            prevID = firstID;
        }
    }

    private void appendInfo(StringBuilder sb, String title, long date, String url) {
        title = title.replaceAll("\\|", "");
        sb.append(title)
                .append("+")
                .append(date)
                .append("+")
                .append(url);
    }

}
