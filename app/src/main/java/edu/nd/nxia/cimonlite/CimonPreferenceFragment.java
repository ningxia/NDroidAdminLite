package edu.nd.nxia.cimonlite;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;

/**
 * Created by ningxia on 1/18/16.
 */
public class CimonPreferenceFragment extends PreferenceFragment {

    private static final String SHARED_PREFS = "CimonSharedPrefs";
    private static final String MONITOR_START_TIME = "monitor_start_time";
    private static final String MONITOR_DURATION = "monitor_duration";
    private TimePreference timePreference;
    private EditTextPreference durationPreference;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        timePreference = (TimePreference) findPreference(MONITOR_START_TIME);
        durationPreference = (EditTextPreference) findPreference(MONITOR_DURATION);

        timePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String time = (String) newValue;
                if (DebugLog.DEBUG)
                    Log.d("CimonPreferenceFragment", time);
                timePreference.setSummary(time);
                SharedPreferences appPrefs = MyApplication.getAppContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
                appPrefs.edit().putString(MONITOR_START_TIME, time).commit();
                return true;
            }
        });

        durationPreference.setSummary(durationPreference.getText());
        durationPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String duration = (String) newValue;
                if (DebugLog.DEBUG)
                    Log.d("CimonPreferenceFragment", duration);
                durationPreference.setSummary(duration);
                SharedPreferences appPrefs = MyApplication.getAppContext().getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
                appPrefs.edit().putString(MONITOR_DURATION, duration).commit();
                return true;
            }
        });
    }
}
