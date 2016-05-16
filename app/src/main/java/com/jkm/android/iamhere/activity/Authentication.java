package com.jkm.android.iamhere.activity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.jkm.android.iamhere.R;
import com.jkm.android.iamhere.helper.JSONParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class Authentication extends AppCompatActivity {
    private static final String TAG = Authentication.class.getSimpleName();

    EditText etUsername, etPassword;
    Button btLogIn;
    TextView tvToSignUp;

    String username, password;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.authentication);

        String isLogIn = loadSavedPreferencesString(getResources().getString(R.string.keep_login));
        if (isLogIn != null && isLogIn.equals("TRUE")) {
            Intent i = new Intent(getApplicationContext(), MapsActivity.class);
            startActivity(i);
            finish();
        }

        etUsername = (EditText) findViewById(R.id.et_auth_username);
        etPassword = (EditText) findViewById(R.id.et_auth_password);
        btLogIn = (Button) findViewById(R.id.bt_auth_log_in);
        tvToSignUp = (TextView) findViewById(R.id.tv_auth_sign_up);

        btLogIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                username = etUsername.getText().toString();
                password = etPassword.getText().toString();
                if (username.length() == 0) {
                    Toast.makeText(getApplicationContext(), "Please input your username", Toast.LENGTH_SHORT).show();
                } else if (password.length() == 0) {
                    Toast.makeText(getApplicationContext(), "Please input a correct password", Toast.LENGTH_SHORT).show();
                } else {
                    new GetAsync().execute("\"" + username + "\"", "\"" + password + "\"");
                }
            }
        });

        tvToSignUp.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePreferences(getResources().getString(R.string.reg1_valid), "NOT_VALID");
                savePreferences(getResources().getString(R.string.reg2_valid), "NOT_VALID");
                savePreferences(getResources().getString(R.string.reg3_valid), "NOT_VALID");
                Intent i = new Intent(getApplicationContext(), RegistrationOne.class);
                startActivity(i);
                finish();
            }
        });
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

    class GetAsync extends AsyncTask<String, String, JSONObject> {
        JSONParser jsonParser = new JSONParser();
        private ProgressDialog pDialog;

        private static final String AUTH_URL = "http://192.168.43.123:8000/find_my_way/fmw_db_authentication.php";

        private static final String TAG_SUCCESS = "success";
        private static final String TAG_MESSAGE = "message";
        private static final String TAG_NOMOR_HP = "nomorHP";
        private static final String TAG_ID_MODULE = "idModule";

        int success;
        String message;

        @Override
        protected void onPreExecute() {
            pDialog = new ProgressDialog(Authentication.this);
            pDialog.setMessage("Logging in. Please wait...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(true);
            pDialog.show();
        }

        @Override
        protected JSONObject doInBackground(String... args) {
            String nomorHP, idModule;
            try {
                HashMap<String, String> params = new HashMap<>();
                params.put("username", args[0]);
                params.put("userPassword", args[1]);

                JSONObject json = jsonParser.makeHttpRequest(AUTH_URL, "GET", params);
                if (json != null) {
                    Log.d(TAG, json.toString());
                    success = json.getInt(TAG_SUCCESS);

                    if (success == 1) {
                        JSONArray allData = json.getJSONArray(TAG_MESSAGE);
                        JSONObject data = allData.getJSONObject(0);

                        nomorHP = data.getString(TAG_NOMOR_HP);
                        idModule = data.getString(TAG_ID_MODULE);

                        savePreferences(getResources().getString(R.string.username), username);
                        savePreferences(getResources().getString(R.string.password), password);
                        savePreferences(getResources().getString(R.string.phone_number_key), nomorHP);
                        savePreferences(getResources().getString(R.string.bt_address), idModule);
                    } else {
                        message = json.getString(TAG_MESSAGE);
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
                savePreferences(getResources().getString(R.string.keep_login), "TRUE");
                Intent i = new Intent(getApplicationContext(), MapsActivity.class);
                startActivity(i);
                finish();
            } else if (success == 0) {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            } else if (success == -1) {
                Toast.makeText(getApplicationContext(), "Can't connect to database", Toast.LENGTH_LONG).show();
            }
        }
    }
}
