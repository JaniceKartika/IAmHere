package com.jkm.android.iamhere.activity;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.jkm.android.iamhere.R;
import com.jkm.android.iamhere.helper.JSONParser;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class RegistrationThree extends AppCompatActivity {
    private static final String TAG = RegistrationThree.class.getSimpleName();

    Button btConnect;
    TextView tvLetsLogin, tvLogin;

    private BluetoothAdapter mBluetoothAdapter;
    boolean mScanning = false;
    private Handler mHandler;
    private Runnable mRunnable;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final long SCAN_PERIOD = 10000;
    String deviceAddress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registration_three);

        String isReg3Valid = loadSavedPreferencesString(getResources().getString(R.string.reg3_valid));
        if (isReg3Valid != null && isReg3Valid.equals("VALID")) {
            Intent i = new Intent(getApplicationContext(), Authentication.class);
            startActivity(i);
            finish();
        }

        btConnect = (Button) findViewById(R.id.bt_reg3_connect);
        tvLetsLogin = (TextView) findViewById(R.id.tv_lets_login);
        tvLogin = (TextView) findViewById(R.id.tv_login);

        tvLetsLogin.setVisibility(View.GONE);
        tvLogin.setVisibility(View.GONE);

        mHandler = new Handler();
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, getResources().getString(R.string.ble_not_supported), Toast.LENGTH_LONG).show();
            finish();
        }
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = bluetoothManager.getAdapter();
        if (mBluetoothAdapter == null) {
            Toast.makeText(this, getResources().getString(R.string.bluetooth_not_supported), Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        btConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mBluetoothAdapter.isEnabled()) {
                    Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                    startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                } else {
                    scanLeDevice(true);
                }
            }
        });

        tvLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), Authentication.class);
                startActivity(i);
                finish();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_reg_three, menu);
        if (mScanning) {
            menu.findItem(R.id.reg_scanning_bt).setVisible(true).setActionView(R.layout.actionbar_indeterminate_progress);
        } else {
            menu.findItem(R.id.reg_scanning_bt).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        //to wait until BLE is found.
        return id == R.id.reg_scanning_bt || super.onOptionsItemSelected(item);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mRunnable = new Runnable() {
                @Override
                public void run() {
                    mScanning = false;
                    btConnect.setEnabled(true);
                    mBluetoothAdapter.stopLeScan(mLeScanCallback);
                    invalidateOptionsMenu();
                }
            };
            mHandler.postDelayed(mRunnable, SCAN_PERIOD);
            mScanning = true;
            btConnect.setEnabled(false);
            mBluetoothAdapter.startLeScan(mLeScanCallback);
        } else {
            mScanning = false;
            btConnect.setEnabled(true);
            mBluetoothAdapter.stopLeScan(mLeScanCallback);
        }
        invalidateOptionsMenu();
    }

    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(final BluetoothDevice device, int rssi, byte[] scanRecord) {
            String deviceName = device.getName();
            Log.i(TAG, "deviceName = " + deviceName);

            if (deviceName.equals(getResources().getString(R.string.bt_name))) {
                deviceAddress = device.getAddress();
                scanLeDevice(false);
                mHandler.removeCallbacks(mRunnable);

                String username = loadSavedPreferencesString(getResources().getString(R.string.username));
                new GetAsync().execute("\"" + username + "\"", "\"" + deviceAddress + "\"");
            }
        }
    };

    private void savePreferences(String key, String value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    private String loadSavedPreferencesString(String key) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getString(key, null);
    }

    class GetAsync extends AsyncTask<String, String, JSONObject> {
        JSONParser jsonParser = new JSONParser();
        private ProgressDialog pDialog;

        private static final String URL_REG_THREE = "http://192.168.43.123:8000/find_my_way/fmw_db_registration_three.php";
        private static final String TAG_SUCCESS = "success";
        private static final String TAG_MESSAGE = "message";

        int success;
        String message;

        @Override
        protected void onPreExecute() {
            pDialog = new ProgressDialog(RegistrationThree.this);
            pDialog.setMessage("Finishing configuration...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(true);
            pDialog.show();
        }

        @Override
        protected JSONObject doInBackground(String... args) {
            try {
                HashMap<String, String> params = new HashMap<>();
                params.put("username", args[0]);
                params.put("idModule", args[1]);

                JSONObject json = jsonParser.makeHttpRequest(URL_REG_THREE, "GET", params);
                if (json != null) {
                    Log.d(TAG, json.toString());
                    success = json.getInt(TAG_SUCCESS);
                    message = json.getString(TAG_MESSAGE);
                    if (success == 1) {
                        Log.d(TAG, "Success!");
                    } else {
                        Log.d(TAG, "message = " + message);
                    }
                } else {
                    success = -1;
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute(JSONObject json) {
            if (pDialog != null && pDialog.isShowing()) {
                pDialog.dismiss();
            }
            if (success == 1) {
                savePreferences(getResources().getString(R.string.bt_address), deviceAddress);
                savePreferences(getResources().getString(R.string.reg3_valid), "VALID");
                tvLetsLogin.setVisibility(View.VISIBLE);
                tvLogin.setVisibility(View.VISIBLE);
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            } else if (success == 0) {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            } else if (success == -1) {
                Toast.makeText(getApplicationContext(), "Can't connect to database", Toast.LENGTH_LONG).show();
            }
        }
    }
}