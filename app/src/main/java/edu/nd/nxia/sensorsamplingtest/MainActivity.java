package edu.nd.nxia.sensorsamplingtest;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


public class MainActivity extends Activity implements View.OnClickListener, SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String TAG = "NDroid";
    private static final String SHARED_PREFS = "CimonSharedPrefs";
    private static final String PREF_VERSION = "version";
    private static final String SENSOR_RESULT = "sensor_result";
    private static final String RUNNING_METRICS = "running_metrics";

    private Context context;
    private TextView textView;
    private Button button;

    MetricService metricService;

    SharedPreferences appPrefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        context = getApplicationContext();
        textView = (TextView) findViewById(R.id.text_view);
        button = (Button) findViewById(R.id.start_button);
        button.setOnClickListener(this);

        metricService = new MetricService(context);
        metricService.insertDatabaseEntries();

        appPrefs = getSharedPreferences(SHARED_PREFS, Context.MODE_PRIVATE);
        appPrefs.registerOnSharedPreferenceChangeListener(this);
        resumeStatus();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
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

    @Override
    public void onClick(View v) {
        if (button.getText().equals("Start")) {
            button.setText("Stop");
//            metricService.startMonitoring();
            startNDroidService();
            textView.setText("Working...");
        }
        else {
            button.setText("Start");
//            textView.setText(metricService.stopMonitoring());
            Intent intent = new Intent(context, NDroidService.class);
            stopService(intent);
        }
    }

    private void startNDroidService() {
        Intent intent = new Intent(context, NDroidService.class);
        startService(intent);
        if (DebugLog.DEBUG) Log.d(TAG, "MainActivity.startNDroidService - started");
    }

    private void resumeStatus() {
        if (appPrefs.getInt(RUNNING_METRICS, -1) == 1) {
            Toast.makeText(this, "Monitors are running...", Toast.LENGTH_LONG).show();
            button.setText("Stop");
            textView.setText("Working...");
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
