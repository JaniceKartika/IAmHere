package com.jkm.android.iamhere.service;

import android.app.IntentService;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.jkm.android.iamhere.R;
import com.jkm.android.iamhere.activity.RegistrationThree;
import com.jkm.android.iamhere.app.MyApplication;
import com.jkm.android.iamhere.app.SMSConfig;

import org.json.JSONException;
import org.json.JSONObject;

public class HTTPService extends IntentService {
    private static final String TAG = HTTPService.class.getSimpleName();

    public HTTPService() {
        super(HTTPService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        if (intent != null) {
            String OTP = intent.getStringExtra(getResources().getString(R.string.otp_key));
            String username = loadSavedPreferencesString(getResources().getString(R.string.username));
            String nomorHP = loadSavedPreferencesString(getResources().getString(R.string.phone_number_key));
            verifyOTP(username, OTP, nomorHP);
        }
    }

    private String loadSavedPreferencesString(String key) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getString(key, null);
    }

    private void savePreferences(String key, String value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    private void verifyOTP(String username, String OTP, String nomorHP) {
        Uri builtUri = Uri.parse(SMSConfig.URL_VERIFY_OTP).buildUpon()
                .appendQueryParameter("username", "\"" + username + "\"")
                .appendQueryParameter("otp", "\"" + OTP + "\"")
                .appendQueryParameter("nomorHP", "\"" + nomorHP + "\"")
                .build();
        StringRequest strRequest = new StringRequest(Request.Method.GET, builtUri.toString(), new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                Log.d(TAG, "response = " + response);
                try {
                    JSONObject responseObject = new JSONObject(response);

                    int success = responseObject.getInt("success");
                    String message = responseObject.getString("message");

                    if (success == 1) {
                        savePreferences(getResources().getString(R.string.reg2_valid), "VALID");
                        LocalBroadcastManager localBroadcastManager = LocalBroadcastManager.getInstance(HTTPService.this);
                        localBroadcastManager.sendBroadcast(new Intent("com.jkm.android.iamhere.activity.RegistrationTwo.action.close"));
                        Intent intent = new Intent(HTTPService.this, RegistrationThree.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(intent);
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
                    }
                } catch (JSONException e) {
                    Toast.makeText(getApplicationContext(), "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {
                Log.e(TAG, "Error: " + error.getMessage());
                Toast.makeText(getApplicationContext(), error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
        strRequest.setRetryPolicy(new DefaultRetryPolicy(15000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        //Adding request to request queue
        MyApplication.getInstance().addToRequestQueue(strRequest);
    }
}