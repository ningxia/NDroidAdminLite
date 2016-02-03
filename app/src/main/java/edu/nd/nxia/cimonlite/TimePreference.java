package edu.nd.nxia.cimonlite;

import android.content.Context;
import android.content.res.TypedArray;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.TimePicker;

/**
 * Created by ningxia on 1/18/16.
 */
public class TimePreference extends DialogPreference {

    private int lastHour;
    private int lastMinute;
    private TimePicker timePicker = null;

    public TimePreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        setPositiveButtonText(context.getString(android.R.string.ok));
        setNegativeButtonText(context.getString(android.R.string.cancel));
    }

    public static int getHour(String time) {
        String[] tokens = time.split(":");
        return(Integer.parseInt(tokens[0]));
    }

    public static int getMinute(String time) {
        String[] tokens = time.split(":");
        return(Integer.parseInt(tokens[1]));
    }

    @Override
    protected View onCreateDialogView() {
        timePicker = new TimePicker(getContext());
        timePicker.setIs24HourView(true);
        return timePicker;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);

        timePicker.setCurrentHour(lastHour);
        timePicker.setCurrentMinute(lastMinute);
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);

        if (positiveResult) {
            lastHour = timePicker.getCurrentHour();
            lastMinute = timePicker.getCurrentMinute();
            if (DebugLog.DEBUG)
                Log.d("CimonPreference", "lastHour: " + lastHour + " - " + "lastMinute: " + lastMinute);
            String hourStr = lastHour < 10 ? "0" + String.valueOf(lastHour) : String.valueOf(lastHour);
            String minuteStr = lastMinute < 10 ? "0" + String.valueOf(lastMinute) : String.valueOf(lastMinute);
            String time = hourStr + ":" + minuteStr;
            if (callChangeListener(time)) {
                persistString(time);
            }
        }
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return(a.getString(index));
    }

    @Override
    protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
        String time = null;

        if (restorePersistedValue) {
            if (defaultValue == null) {
                time = getPersistedString("08:00");
            }
            else {
                time = getPersistedString(defaultValue.toString());
            }
        }
        else {
            time = defaultValue.toString();
        }

        lastHour = getHour(time);
        lastMinute = getMinute(time);

        setSummary(time);
    }
}
