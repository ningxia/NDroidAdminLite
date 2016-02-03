package edu.nd.nxia.cimonlite;

import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.util.Log;

/**
 * Created by ningxia on 1/18/16.
 */
public class CimonPreferenceFragment extends PreferenceFragment {

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
                    Log.d("CimonPreference", time);
                timePreference.setSummary(time);
                return true;
            }
        });

        durationPreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                String duration = (String) newValue;
                durationPreference.setSummary(duration);
                return true;
            }
        });
    }
}
