package com.jkm.android.iamhere.activity;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.jkm.android.iamhere.R;
import com.jkm.android.iamhere.app.MyApplication;
import com.jkm.android.iamhere.app.SMSConfig;

import org.json.JSONException;
import org.json.JSONObject;

public class RegistrationTwo extends AppCompatActivity {
    private static final String TAG = RegistrationTwo.class.getSimpleName();

    Button btSendCode;
    EditText etPhoneCode, etPhoneNumber;
    ProgressBar pbReg2;

    LocalBroadcastManager localBroadcastManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registration_two);

        String isReg2Valid = loadSavedPreferencesString(getResources().getString(R.string.reg2_valid));
        if (isReg2Valid != null && isReg2Valid.equals("VALID")) {
            Intent i = new Intent(getApplicationContext(), RegistrationThree.class);
            startActivity(i);
            finish();
        }

        btSendCode = (Button) findViewById(R.id.bt_reg2_send_code);
        etPhoneCode = (EditText) findViewById(R.id.et_reg2_phone_code);
        etPhoneNumber = (EditText) findViewById(R.id.et_reg2_phone_number);
        pbReg2 = (ProgressBar) findViewById(R.id.pb_reg2);

        String displayPhoneCode = "+" + getCountryPhoneCode();
        etPhoneCode.setText(displayPhoneCode);
        etPhoneNumber.requestFocus();

        btSendCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                validatePhoneNumber();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        localBroadcastManager = LocalBroadcastManager.getInstance(this);
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.jkm.android.iamhere.activity.RegistrationTwo.action.close");
        localBroadcastManager.registerReceiver(broadcastReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        localBroadcastManager.unregisterReceiver(broadcastReceiver);
    }

    BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals("com.jkm.android.iamhere.activity.RegistrationTwo.action.close")) {
                finish();
            }
        }
    };

    private void validatePhoneNumber() {
        String nomorHP = etPhoneCode.getText().toString() + etPhoneNumber.getText().toString();
        if (isValidPhoneNumber(nomorHP)) {
            pbReg2.setVisibility(View.VISIBLE);
            savePreferences(getResources().getString(R.string.phone_number_key), nomorHP);
            String username = loadSavedPreferencesString(getResources().getString(R.string.username));
            requestForSMS(username, nomorHP);
        } else {
            Toast.makeText(getApplicationContext(), "Please enter a valid mobile number", Toast.LENGTH_LONG).show();
        }
    }

    private static boolean isValidPhoneNumber(String nomorHP) {
        String regEx = "^\\+[0-9]{10,15}$";
        return nomorHP.matches(regEx);
    }

    private void requestForSMS(String username, String nomorHP) {
        Uri builtUri = Uri.parse(SMSConfig.URL_REG_TWO).buildUpon()
                .appendQueryParameter("username", "\"" + username + "\"")
                .appendQueryParameter("nomorHP", "\"" + nomorHP + "\"")
                .build();
        Log.i(TAG, builtUri.toString());
        StringRequest strRequest = new StringRequest(Request.Method.GET, builtUri.toString(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.d(TAG, response);
                try {
                    JSONObject responseObject = new JSONObject(response);
                    int success = responseObject.getInt("success");
                    String message = responseObject.getString("message");

                    if (success == 1) {
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                        pbReg2.setVisibility(View.GONE);
                    }
                } catch (JSONException e) {
                    Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    pbReg2.setVisibility(View.GONE);
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Error: " + error.getMessage());
                Toast.makeText(getApplicationContext(), error.getMessage(), Toast.LENGTH_LONG).show();
                pbReg2.setVisibility(View.GONE);
            }
        });

        strRequest.setRetryPolicy(new DefaultRetryPolicy(15000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        //Adding request to request queue
        MyApplication.getInstance().addToRequestQueue(strRequest);
    }

    private String getCountryPhoneCode() {
        String countryPhoneCode = "62"; //default is Indonesia
        TelephonyManager manager = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        String countryID = manager.getNetworkCountryIso().toUpperCase();
        Log.i(TAG, "countryID = " + countryID);
        if (countryID.length() == 2) {
            String[] listCode = this.getResources().getStringArray(R.array.country_phone_codes);
            for (String code : listCode) {
                String[] g = code.split(",");
                if (g[1].trim().equals(countryID.trim())) {
                    countryPhoneCode = g[0];
                    break;
                }
            }
        }
        return countryPhoneCode;
    }

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
}