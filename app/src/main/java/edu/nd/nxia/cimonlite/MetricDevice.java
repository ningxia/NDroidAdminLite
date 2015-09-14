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
    protected int groupId;
    protected int metricsCount;
    protected long period = 0;
    protected long timer = 0;

    protected T[] values;

    protected static final int PARAM_CONTEXT = 0;
    protected static final int PARAM_SENSOR_MANAGER = 1;
    protected static final int PARAM_SENSOR_EVENT_LISTENER = 2;
    protected static final int PARAM_BROADCAST_RECEIVER = 3;
    protected static final int PARAM_MODE = 4;
    protected static final int PARAM_TIMESTAMP = 5;
    protected static final int PARAM_SENSOR_EVENT = 6;
    protected static final int PARAM_INTENT = 7;
    protected static final int PARAM_LOCATION_MANAGER = 8;
    protected static final int PARAM_LOCATION_LISTENER = 9;
    protected static final int PARAM_LOCATION = 10;


    /**
     * Initialize device
     */
    abstract void initDevice(long period);

    /**
     * Register device
     */
    abstract void registerDevice(SparseArray<Object> params);

    /**
     * Insert entries for metric group and metrics into database.
     */
    abstract void insertDatabaseEntries();

    /**
     * Get device data
     */
    abstract List<DataEntry> getData(SparseArray<Object> params);

    public int getGroupId() {
        return this.groupId;
    }

    public long getPeriod() {
        return this.period;
    }

    public long getTimer() {
        return this.timer;
    }

    public void setTimer(long timestamp) {
        this.timer = timestamp;
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
//            case Metrics.MEMORY_TOTAL:
//            case Metrics.MEMORY_AVAIL:
//            case Metrics.MEMORY_CACHED:
//            case Metrics.MEMORY_ACTIVE:
//            case Metrics.MEMORY_INACTIVE:
//            case Metrics.MEMORY_DIRTY:
//            case Metrics.MEMORY_BUFFERS:
//            case Metrics.MEMORY_ANONPAGES:
//            case Metrics.MEMORY_SWAPTOTAL:
//            case Metrics.MEMORY_SWAPFREE:
//            case Metrics.MEMORY_SWAPCACHED:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch memory service");
//                return MemoryService.getInstance();
//            case Metrics.CPU_LOAD1:
//            case Metrics.CPU_LOAD5:
//            case Metrics.CPU_LOAD15:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch cpu service");
//                return CpuService.getInstance();
//            case Metrics.PROC_TOTAL:
//            case Metrics.PROC_USER:
//            case Metrics.PROC_NICE:
//            case Metrics.PROC_SYSTEM:
//            case Metrics.PROC_IDLE:
//            case Metrics.PROC_IOWAIT:
//            case Metrics.PROC_IRQ:
//            case Metrics.PROC_SOFTIRQ:
//            case Metrics.PROC_CTXT:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch proc service");
//                return CpuUtilService.getInstance();
            case Metrics.BATTERY_CATEGORY:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch battery service");
                return BatteryService.getInstance();
//            case Metrics.MOBILE_RX_BYTES:
//            case Metrics.MOBILE_TX_BYTES:
//            case Metrics.TOTAL_RX_BYTES:
//            case Metrics.TOTAL_TX_BYTES:
//            case Metrics.MOBILE_RX_PACKETS:
//            case Metrics.MOBILE_TX_PACKETS:
//            case Metrics.TOTAL_RX_PACKETS:
//            case Metrics.TOTAL_TX_PACKETS:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch net bytes service");
//                return NetBytesService.getInstance();
//            case Metrics.ROAMING:
//            case Metrics.NET_CONNECTED:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch net connected service");
//                return NetConnectedService.getInstance();
//            case Metrics.SDCARD_READS:
//            case Metrics.SDCARD_WRITES:
//            case Metrics.SDCARD_CREATES:
//            case Metrics.SDCARD_DELETES:
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
//            case Metrics.LIGHT:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch light sensor service");
//                return LightService.getInstance();
//            case Metrics.HUMIDITY:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch relative humidity service");
//                return HumidityService.getInstance();
//            case Metrics.TEMPERATURE:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch ambient temperature service");
//                return TemperatureService.getInstance();
            case Metrics.ATMOSPHERIC_PRESSURE:
                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch atmospheric pressure service");
                return PressureService.getInstance();
//            case Metrics.PROXIMITY:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch proximity service");
//                return ProximityService.getInstance();
//            case Metrics.SCREEN_ON:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch screen state service");
//                return ScreenService.getInstance();
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
//            case Metrics.BLUETOOTH_DEVICE:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch Bluetooth activity service");
//                return BluetoothService.getInstance();
//            case Metrics.WIFI_NETWORK:
//                if (DebugLog.DEBUG) Log.d(TAG, "MetricDevice.getDevice - fetch Wifi activity service");
//                return WifiService.getInstance();
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
