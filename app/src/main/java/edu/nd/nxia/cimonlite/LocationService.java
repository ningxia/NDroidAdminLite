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

import java.util.ArrayList;
import java.util.List;

import edu.nd.nxia.cimonlite.database.CimonDatabaseAdapter;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;

/**
 * Monitoring service for geo-location sensors.
 * Uses GPS and cellular triangulation data to obtain geo-location.
 * Location metrics:
 * <li>Latitude (degrees)
 * <li>Longitude (degrees)
 * <li>Accuracy (meters)
 * <p>
 * @author darts
 * 
 * @see MetricService
 *
 */
public final class LocationService extends MetricDevice<Double> {
	
	private static final String TAG = "NDroid";
	private static final float ONE_SECOND = 1000;
	private static final int LOCATION_METRICS = 4;
	private static final int ALL_METRICS = 4;
	private static final long FIVE_MINUTES = 300000;
	
	// NOTE: title and string array must be defined above instance,
	//   otherwise, they will be null in constructor
	private static final String title = "Geo-location";
    private static final String[] metrics = {"Latitude",
												"Longitude",
												"Accuracy",
												"Speed"};
	private static final LocationService INSTANCE = new LocationService();
	private Location mCoordinate;
	private LocationManager mLocationManager;
    private LocationListener mLocationListener;
    private List<String> mProviders;
    private List<DataEntry> tempData;
    private long lastUpdate = 0;


	private LocationService() {
		if (DebugLog.DEBUG) Log.d(TAG, "LocationService - constructor");
		if (INSTANCE != null) {
			throw new IllegalStateException("LocationService already instantiated");
		}
		groupId = Metrics.LOCATION_CATEGORY;
		metricsCount = ALL_METRICS;
		values = new Double[LOCATION_METRICS];
        tempData = new ArrayList<>();
	}
	
	public static LocationService getInstance() {
		if (DebugLog.DEBUG) Log.d(TAG, "LocationService.getInstance - get single instance");
		if (!INSTANCE.supportedMetric) return null;
		return INSTANCE;
	}

    @Override
    void initDevice(long period) {
        this.type = TYPE_RECEIVER;
        this.period = period;
        this.timer = System.currentTimeMillis();
        mLocationManager = (LocationManager) MyApplication.getAppContext().getSystemService(Context.LOCATION_SERVICE);
        mProviders = mLocationManager.getProviders(true);
        if ((mProviders == null) || (mProviders.isEmpty())) {
            if (DebugLog.INFO) Log.i(TAG, "LocationService - sensor not supported on this system");
            supportedMetric = false;
            mLocationManager = null;
            return;
        }
    }

    @Override
    void registerDevice(SparseArray<Object> params) {
        mLocationManager = (LocationManager) params.get(PARAM_LOCATION_MANAGER);
        mLocationListener = (LocationListener) params.get(PARAM_LOCATION_LISTENER);
        mLocationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, mLocationListener);
    }

    @Override
	void insertDatabaseEntries() {
		Context context = MyApplication.getAppContext();
		CimonDatabaseAdapter database = CimonDatabaseAdapter.getInstance(context);
		if (!supportedMetric) {
			database.insertOrReplaceMetricInfo(groupId, title, "", NOTSUPPORTED, 0, 0, 
					"", "", Metrics.TYPE_SENSOR);
			return;
		}
		
		float power = 0;
		String description = null;
        LocationProvider locProvider;
		for (String provider : mProviders) {
			locProvider = mLocationManager.getProvider(provider);
			power += locProvider.getPowerRequirement();
			if (description == null) {
				description = locProvider.getName();
			}
			else {
				description = description + " | " + locProvider.getName();
			}
		}
		// insert metric group information in database
		database.insertOrReplaceMetricInfo(groupId, title, description, 
				SUPPORTED, power, mLocationManager.getGpsStatus(null).getTimeToFirstFix(),
				"Global coordinate", "1" + context.getString(R.string.units_degrees), 
				Metrics.TYPE_SENSOR);
		// insert information for metrics in group into database
		database.insertOrReplaceMetrics(Metrics.LOCATION_LATITUDE, groupId, 
				"Latitude", context.getString(R.string.units_degrees), 90);
		database.insertOrReplaceMetrics(Metrics.LOCATION_LONGITUDE, groupId, 
				"Longitude", context.getString(R.string.units_degrees), 180);
		database.insertOrReplaceMetrics(Metrics.LOCATION_ACCURACY, groupId, 
				"Accuracy", context.getString(R.string.units_meters), 500);
        database.insertOrReplaceMetrics(Metrics.LOCATION_SPEED, groupId,
                "Speed", "m/s", 500);
	}

    @Override
    List<DataEntry> getData(SparseArray<Object> params) {
        long timestamp = (long) params.get(PARAM_TIMESTAMP);
        if (params.get(PARAM_LOCATION) != null) {
            Location location = (Location) params.get(PARAM_LOCATION);
            if ((timestamp - lastUpdate) >= FIVE_MINUTES) {
                mCoordinate = getLastLocation();
            }
            Double values[] = new Double[LOCATION_METRICS];
            values[0] = mCoordinate.getLatitude();
            values[1] = mCoordinate.getLongitude();
            values[2] = (double) mCoordinate.getAccuracy();
            values[3] = (double) mCoordinate.getSpeed();
            lastUpdate = timestamp;
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < LOCATION_METRICS; i++) {
                sb.append(values[i]).append("|");
            }
            tempData.add(new DataEntry(Metrics.LOCATION_CATEGORY, timestamp, sb.substring(0, sb.length() - 1)));
        }
        if (timestamp - timer < period - timeOffset) {
            return null;
        }
        setTimer(timestamp);
        List<DataEntry> dataList = new ArrayList<>();
        for (int i = 0; i < tempData.size(); i ++) {
            dataList.add(tempData.get(i));
        }
        tempData.clear();
        return dataList;
    }

	public void onProviderDisabled(String provider) {
		if (DebugLog.DEBUG) Log.d(TAG, "LocationService.onProviderDisabled - " + provider);
		if (provider.equals(LocationManager.GPS_PROVIDER)) {
			if (DebugLog.DEBUG) Log.d(TAG, "LocationService.onProviderDisabled - from gps");
		}
		else if (provider.equals(LocationManager.NETWORK_PROVIDER)) {
			if (DebugLog.DEBUG) Log.d(TAG, "LocationService.onProviderDisabled - from network");
		}
        Intent intent = new Intent("android.location.GPS_ENABLED_CHANGE");
        intent.putExtra("enabled", true);
        MyApplication.getAppContext().sendBroadcast(intent);
	}

	public void onProviderEnabled(String provider) {
		// TODO Auto-generated method stub

	}

	public void onStatusChanged(String provider, int status, Bundle extras) {
		// TODO Auto-generated method stub

	}

	/**
	 * Obtain last known location.
	 * Queries GPS and network data for last known locations.  Returns the location
	 * which is most accurate or recent, using a formula which essentially equates
	 * one second to one meter.
	 * 
	 * @return    best last known location
	 */
	private Location getLastLocation() {
//		if (DebugLog.DEBUG) Log.d(TAG, "LocationService.getLastLocation - getting last known location");
		Location gps = mLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
		Location network = mLocationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
		if (gps != null) {
			if (network != null) {
				// this is my little formula for determining higher quality reading,
				//  which essentially equates one second to one meter
				long timeDelta = gps.getTime() - network.getTime();
				float accuracyDelta = gps.getAccuracy() - network.getAccuracy();
				if (!(gps.hasAccuracy() && network.hasAccuracy())) {
					accuracyDelta = 0;
				}
				if (((float)timeDelta/ONE_SECOND) > accuracyDelta) {
					return gps;
				}
				return network;
			}
			return gps;
		}
		return network;
	}
	
	/**
	 * Update location with new coordinate if it is better quality than existing
	 * location.
	 * Checks validity of new coordinates and compares it to existing location.  If
	 * it is of higher quality (based on accuracy and age), update location to new
	 * coordinate.
	 *  
	 * @param newCoordinate    new coordinate obtained from onLocationChanged callback
	 * @return    true if location was set to new coordinate, false if new coordinate 
	 *             is invalid or considered lesser quality 
	 */
	private boolean checkLocation(Location newCoordinate) {
//        if (DebugLog.DEBUG) Log.d(TAG, "LocationService.checkLocation - check quality of location");
        if (newCoordinate == null) {
            return false;
        }
        if (mCoordinate != null) {
            if (mCoordinate.equals(newCoordinate)) {
                return false;
            }
        }
        else {
            mCoordinate = newCoordinate;
            return true;
        }
        // this is my little formula for determining higher quality reading,
        //  which essentially equates one second to one meter
        long timeDelta = mCoordinate.getTime() - newCoordinate.getTime();
        float accuracyDelta = mCoordinate.getAccuracy() - newCoordinate.getAccuracy();
        if (!(mCoordinate.hasAccuracy() && newCoordinate.hasAccuracy())) {
            accuracyDelta = 0;
        }
        if (((float)timeDelta / ONE_SECOND) > accuracyDelta) {
            return false;
        }
        mCoordinate = newCoordinate;
        return true;
	}

}
