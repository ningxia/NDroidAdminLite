package edu.nd.nxia.cimonlite;

import android.app.Activity;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;

/**
 * Created by ningxia on 1/18/16.
 */
public class CimonPreferenceActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction()
                .replace(android.R.id.content, new CimonPreferenceFragment())
                .commit();
    }
}
