package edu.nd.nxia.cimonlite;


import android.hardware.SensorManager;
import android.util.Log;
import android.util.SparseArray;

import java.util.ArrayList;
import java.util.EventListener;
import java.util.List;


/**
 * Definition of abstract class for metric monitoring devices. These devices provide
 * sensor or device readings.
 *
 * @author Ning Xia
 *
 * @param <T>    value type for metrics monitored (typically subclass of Number)
 */
public abstract class MetricDevice<T extends Comparable<T>> implements EventListener {
    /** Tag for log messages. */
    protected static final String TAG = "NDroid";
    protected static final int SUPPORTED = 1;
    protected static final int NOTSUPPORTED = 0;

    protected boolean supportedMetric = true;
    protected int type;
    protected int groupId;
    protected int metricsCount;
    protected long period = 0;
    protected long timeOffset = 3;
    protected long timer = 0;

    protected T[] values;

    protected static final int TYPE_SENSOR = 0;
    protected static final int TYPE_RECEIVER = 1;
    protected static final int TYPE_OTHER = 2;

    protected static final int PARAM_CONTEXT = 0;
    protected static final int PARAM_SENSOR_MANAGER = 1;
    protected static final int PARAM_SENSOR_EVENT_LISTENER = 2;
    protected static final int PARAM_BROADCAST_RECEIVER = 3;
    protected static final int PARAM_MODE = 4;
    protected static final int PARAM_TIMESTAMP = 5;
    protected static final int PARAM_SENSOR_EVENT = 6;
    protected static final int PARAM_BATTERY_INTENT = 7;
    protected static final int PARAM_LOCATION_MANAGER = 8;
    protected static final int PARAM_LOCATION_LISTENER = 9;
    protected static final int PARAM_LOCATION = 10;
    protected static final int PARAM_FILE_OBSERVER = 11;
    protected static final int PARAM_FILE_EVENT = 12;
    protected static final int PARAM_BLUETOOTH_INTENT = 13;
    protected static final int PARAM_WIFI_INTENT = 14;
    protected static final int PARAM_SCREEN_INTENT = 15;
    protected static final int PARAM_PHONE_LISTENER = 16;
    protected static final int PARAM_PHONE_STATE = 17;

    /**
     * Initialize device
     */
    abstract void initDevice(long period);

    /**
     * Insert entries for metric group and metrics into database.
     */
    abstract void insertDatabaseEntries();

    /**
     * Register device
     */
    abstract void registerDevice(SparseArray<Object> params);

    /**
     * Get device data
     */
    abstract List<DataEntry> getData(SparseArray<Object> params);

    public int getType() {
        return this.type;
    }

    public int getGroupId() {
        return this.groupId;
    }

    public long getPeriod() {
        return this.period;
    }

    public long getTimer() {
        if (period > 0) {
            return this.timer;
        }
        else {
            return 0;
        }
    }

    public void setTimer(long timestamp) {
        if (period > 0) {
            this.timer = timestamp;
        }
    }

    /**
     * Static method to return instance of implementation of abstract class for
     * the desired device.
     *
     * @param groupId   integer representing metric (per {@link Metrics})
     * @return          metric monitoring agent for this metric, null if monitoring of
     *             metric not supported on this system
     */
    public static MetricDevice<?> getDevice(int groupId) {
        switch(groupId) {
//            case Metrics.TIME_DAY:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch time service");
//                return null;
            case Metrics.MEMORY_CATEGORY:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch memory service");
                return MemoryService.getInstance();
            case Metrics.CPULOAD_CATEGORY:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch cpu service");
                return CpuService.getInstance();
            case Metrics.PROCESSOR_CATEGORY:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch proc service");
                return CpuUtilService.getInstance();
            case Metrics.BATTERY_CATEGORY:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch battery service");
                return BatteryService.getInstance();
            case Metrics.NETBYTES_CATEGORY:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch net bytes service");
                return NetBytesService.getInstance();
            case Metrics.NETSTATUS_CATEGORY:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch net connected service");
                return NetConnectedService.getInstance();
//            case Metrics.SDCARD_CATEGORY:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch sdcard file access service");
//                return FileAccessService.getInstance();
//            case Metrics.INSTRUCTION_CNT:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch instruction count service");
//                return InstructionCntService.getInstance();
            case Metrics.LOCATION_CATEGORY:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch location service");
                return LocationService.getInstance();
            case Metrics.ACCELEROMETER:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch accelerometer service");
                return AccelerometerService.getInstance();
            case Metrics.MAGNETOMETER:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch magnetometer service");
                return MagnetometerService.getInstance();
            case Metrics.GYROSCOPE:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch gyroscope service");
                return GyroscopeService.getInstance();
            case Metrics.LINEAR_ACCEL:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch linear acceleration service");
                return LinearAccelService.getInstance();
            case Metrics.ORIENTATION:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch orientation service");
                return OrientationService.getInstance();
            case Metrics.LIGHT:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch light sensor service");
                return LightService.getInstance();
            case Metrics.HUMIDITY:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch relative humidity service");
                return HumidityService.getInstance();
            case Metrics.TEMPERATURE:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch ambient temperature service");
                return TemperatureService.getInstance();
            case Metrics.ATMOSPHERIC_PRESSURE:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch atmospheric pressure service");
                return PressureService.getInstance();
            case Metrics.PROXIMITY:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch proximity service");
                return ProximityService.getInstance();
            case Metrics.SCREEN_ON:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch screen state service");
                return ScreenService.getInstance();
//            case Metrics.PHONESTATE:
//            case Metrics.OUTGOINGCALLS:
//            case Metrics.INCOMINGCALLS:
//            case Metrics.MISSEDCALLS:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch telephony activity service");
//                return PhoneStateService.getInstance();
//            case Metrics.OUTGOINGSMS:
//            case Metrics.INCOMINGSMS:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch sms activity service");
//                return SMSService.getInstance();
//            case Metrics.OUTGOINGMMS:
//            case Metrics.INCOMINGMMS:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch mms activity service");
//                return MMSService.getInstance();
            case Metrics.BLUETOOTH_CATEGORY:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch Bluetooth activity service");
                return BluetoothService.getInstance();
            case Metrics.WIFI_CATEGORY:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch Wifi activity service");
                return WifiService.getInstance();
//            case Metrics.SMSSENT:
//            case Metrics.SMSRECEIVED:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch SMS information service");
//                return SMSInfoService.getInstance();
//            case Metrics.MMSSENT:
//            case Metrics.MMSRECEIVED:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch MMS information service");
//                return MMSInfoService.getInstance();
//            case Metrics.PHONE_CALL_OUTGOING:
//            case Metrics.PHONE_CALL_INCOMING:
//            case Metrics.PHONE_CALL_MISSED:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch Phone call service");
//                return PhoneCallService.getInstance();
//            case Metrics.CALLSTATE:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch Call state service");
//                return CallStateService.getInstance();
//            case Metrics.BROWSING_HISTORY:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch Browser history service");
//                return BrowserHistoryService.getInstance();
//            case Metrics.APPLICATION:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch Application service");
//                return ApplicationService.getInstance();
//            case Metrics.CELL_CID:
//            case Metrics.CELL_LAC:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch Cell Location service");
//                return CellLocationService.getInstance();
            default:
                if (DebugLog.INFO) Log.i(TAG, "MetricDevice.getDevice - unrecognized group: " + groupId);
                return null;
        }
    }

    public static List<MetricDevice<?>> getDevices(int category) {
        ArrayList<MetricDevice<?>> devices = new ArrayList<>();
        int[] deviceList;
        switch (category) {
            case Metrics.TYPE_SYSTEM:
                deviceList = Metrics.SYSTEM_METRICS;
                break;
            case Metrics.TYPE_SENSOR:
                deviceList = Metrics.SENSOR_METRICS;
                break;
            case Metrics.TYPE_USER:
                deviceList = Metrics.USER_METRICS;
                break;
            default:
                if (DebugLog.INFO) Log.i(TAG, "MetricDevice.getDevices - unrecognized category");
                return null;
        }

        for (int i = 0; i < deviceList.length; i ++) {
            MetricDevice<?> metricDevice = getDevice(deviceList[i]);
            if (metricDevice != null) {
                devices.add(metricDevice);
            }
        }
        return devices;
    }

}
