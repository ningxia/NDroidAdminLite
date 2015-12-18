package edu.nd.nxia.cimonlite;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;


public class SamplingInterface extends Activity implements View.OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "NDroid";
    private static final String SHARED_PREFS = "CimonSharedPrefs";
    private static final String PREF_VERSION = "version";
    private static final String SENSOR_RESULT = "sensor_result";
    private static final String SENSOR_DELAY_MODE = "sensor_delay_mode";
    private static final String RUNNING_MONITOR = "running_monitor";
    private static final String MONITOR_STARTED = "monitor_started";

    private Context context;
    private RadioGroup radioGroup;
    private TextView textView;
    private Button button;

    MetricService metricService;
    SharedPreferences appPrefs;
    SharedPreferences.Editor editor;

    private int sensorDelayMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = getApplicationContext();
        radioGroup = (RadioGroup) findViewById(R.id.radio_group);
        textView = (TextView) findViewById(R.id.text_view);
        button = (Button) findViewById(R.id.start_button);
        button.setOnClickListener(this);

        metricService = new MetricService(context);
        metricService.initDatabase();

        appPrefs = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        appPrefs.registerOnSharedPreferenceChangeListener(this);
        editor = appPrefs.edit();
        resumeStatus();

        startUploadingService();
        startLabelingReminderService();

        getDefaultCheckedValue();

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        menu.findItem(R.id.action_settings).setVisible(false);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public void getDefaultCheckedValue() {
        if (radioGroup.getCheckedRadioButtonId() != -1) {
            int checkedId = radioGroup.getCheckedRadioButtonId();
            RadioButton checkedRadio = (RadioButton) radioGroup.findViewById(checkedId);
            boolean checked = checkedRadio.isChecked();
            switch(checkedId) {
                case R.id.radio_fastest:
                    if (checked)
                        this.sensorDelayMode = SensorManager.SENSOR_DELAY_FASTEST;
                    break;
                case R.id.radio_game:
                    if (checked)
                        this.sensorDelayMode = SensorManager.SENSOR_DELAY_GAME;
                    break;
            }
        }
        Log.d(TAG, "SamplingInterface.getDefaultCheckedValue: " + sensorDelayMode);
    }

    public void onRadioButtonClicked(View view) {
        boolean checked = ((RadioButton) view).isChecked();
        switch(view.getId()) {
            case R.id.radio_fastest:
                if (checked)
                    this.sensorDelayMode = SensorManager.SENSOR_DELAY_FASTEST;
                break;
            case R.id.radio_game:
                if (checked)
                    this.sensorDelayMode = SensorManager.SENSOR_DELAY_GAME;
                break;
        }
        Log.d(TAG, "SamplingInterface.onRadioButtonClicked - mode: " + sensorDelayMode);
    }

    @Override
    public void onClick(View v) {
        if (button.getText().equals("Start")) {
            button.setText("Stop");
            startNDroidService();
            setRadioGroupEnabled(false);
            textView.setText("Working...");
        }
        else {
            button.setText("Start");
            Intent intent = new Intent(context, NDroidService.class);
            stopService(intent);
            editor.remove(MONITOR_STARTED);
            editor.remove(SENSOR_DELAY_MODE);
            editor.commit();
            setRadioGroupEnabled(true);
        }
    }

    private void startUploadingService(){
        Intent intent = new Intent(context,UploadingService.class);
        startService(intent);
        if (DebugLog.DEBUG) Log.d(TAG, "MainActivity.startUploadingService - started");
    }

    private void startLabelingReminderService(){
        Intent intent = new Intent(context,LabelingReminderService.class);
        startService(intent);
        if (DebugLog.DEBUG) Log.d(TAG, "MainActivity.startLabelingReminderService - started");
    }

    private void startNDroidService() {
        Log.d(TAG, "SamplingInterface.startNDroidService - sensorDelayMode: " + sensorDelayMode);
        editor.putInt(SENSOR_DELAY_MODE, sensorDelayMode);
        editor.putBoolean(MONITOR_STARTED, true);
        editor.commit();
        Intent intent = new Intent(context, NDroidService.class);
        intent.putExtra(SENSOR_DELAY_MODE, sensorDelayMode);
        startService(intent);
        if (DebugLog.DEBUG) Log.d(TAG, "MainActivity.startNDroidService - started");
    }

    private void resumeStatus() {
        if (appPrefs.getInt(RUNNING_MONITOR, -1) != -1) {
            Toast.makeText(this, "Monitor #" + appPrefs.getInt(RUNNING_MONITOR, -1) + " is running...", Toast.LENGTH_LONG).show();
            button.setText("Stop");
            textView.setText("Working...");
            int mode = appPrefs.getInt(SENSOR_DELAY_MODE, -1);
            switch (mode) {
                case SensorManager.SENSOR_DELAY_FASTEST:
                    radioGroup.check(R.id.radio_fastest);
                    break;
                case SensorManager.SENSOR_DELAY_GAME:
                    radioGroup.check(R.id.radio_game);
                    break;
            }
            setRadioGroupEnabled(false);
        }
    }

    private void setRadioGroupEnabled(boolean enabled) {
        for (int i = 0; i < radioGroup.getChildCount(); i ++) {
            radioGroup.getChildAt(i).setEnabled(enabled);
        }
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(SENSOR_RESULT)) {
            String result = sharedPreferences.getString(key, "");
            textView.setText(result);
        }
    }
}
