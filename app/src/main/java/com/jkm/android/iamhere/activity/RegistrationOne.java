package com.jkm.android.iamhere.activity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.jkm.android.iamhere.helper.JSONParser;
import com.jkm.android.iamhere.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class RegistrationOne extends AppCompatActivity {
    private static final String TAG = RegistrationOne.class.getSimpleName();

    TextView tvUsernameFailed, tvPasswordFailed, tvConfirmPasswordFailed, tvToLogIn;
    EditText etUsername, etPassword, etConfirmPassword;
    Button btCreateAccount;
    String username, password;
    boolean usernameCheck = false, passwordCheck = false, cPasswordCheck = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registration_one);

        this.setTitle(getResources().getString(R.string.sign_up));

        String isReg1Valid = loadSavedPreferencesString(getResources().getString(R.string.reg1_valid));
        if (isReg1Valid != null && isReg1Valid.equals("VALID")) {
            Intent i = new Intent(getApplicationContext(), RegistrationTwo.class);
            startActivity(i);
            finish();
        }

        tvUsernameFailed = (TextView) findViewById(R.id.tv_reg1_username_failed);
        tvPasswordFailed = (TextView) findViewById(R.id.tv_reg1_password_failed);
        tvConfirmPasswordFailed = (TextView) findViewById(R.id.tv_reg1_confirm_password_failed);
        etUsername = (EditText) findViewById(R.id.et_reg1_username);
        etPassword = (EditText) findViewById(R.id.et_reg1_password);
        etConfirmPassword = (EditText) findViewById(R.id.et_reg1_confirm_password);
        btCreateAccount = (Button) findViewById(R.id.bt_reg1_create);
        tvToLogIn = (TextView) findViewById(R.id.tv_to_log_in);

        btCreateAccount.setEnabled(false);

        etUsername.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (etUsername.getText().toString().contains(" ")) {
                    tvUsernameFailed.setText(getResources().getString(R.string.username_failed));
                    usernameCheck = false;
                } else {
                    tvUsernameFailed.setText(" ");
                    usernameCheck = true;
                }
                if (usernameCheck && passwordCheck && cPasswordCheck) {
                    btCreateAccount.setEnabled(true);
                } else {
                    btCreateAccount.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (etPassword.getText().length() < 7) {
                    tvPasswordFailed.setText(getResources().getString(R.string.password_failed));
                    passwordCheck = false;
                } else {
                    tvPasswordFailed.setText(" ");
                    passwordCheck = true;
                }
                if (etPassword.getText().toString().equals(etConfirmPassword.getText().toString())) {
                    tvConfirmPasswordFailed.setText(" ");
                    cPasswordCheck = true;
                } else {
                    tvConfirmPasswordFailed.setText(getResources().getString(R.string.cpassword_failed));
                    cPasswordCheck = false;
                }
                if (usernameCheck && passwordCheck && cPasswordCheck) {
                    btCreateAccount.setEnabled(true);
                } else {
                    btCreateAccount.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        etConfirmPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (etConfirmPassword.getText().toString().equals(etPassword.getText().toString())) {
                    tvConfirmPasswordFailed.setText(" ");
                    cPasswordCheck = true;
                } else {
                    tvConfirmPasswordFailed.setText(getResources().getString(R.string.cpassword_failed));
                    cPasswordCheck = false;
                }
                if (usernameCheck && passwordCheck && cPasswordCheck) {
                    btCreateAccount.setEnabled(true);
                } else {
                    btCreateAccount.setEnabled(false);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        btCreateAccount.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                username = etUsername.getText().toString();
                password = etPassword.getText().toString();
                new GetAsync().execute("\"" + username + "\"", "\"" + password + "\"");
            }
        });

        tvToLogIn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                savePreferences(getResources().getString(R.string.reg1_valid), "VALID");
                savePreferences(getResources().getString(R.string.reg2_valid), "VALID");
                savePreferences(getResources().getString(R.string.reg3_valid), "VALID");
                Intent i = new Intent(getApplicationContext(), Authentication.class);
                startActivity(i);
                finish();
            }
        });
    }

    class GetAsync extends AsyncTask<String, String, JSONObject> {
        JSONParser jsonParser = new JSONParser();
        private ProgressDialog pDialog;

        private static final String URL_REG_ONE = "http://192.168.43.123:8000/find_my_way/fmw_db_registration_one.php";
        private static final String TAG_SUCCESS = "success";
        private static final String TAG_MESSAGE = "message";

        int success;
        String message;

        @Override
        protected void onPreExecute() {
            pDialog = new ProgressDialog(RegistrationOne.this);
            pDialog.setMessage("Creating your account...");
            pDialog.setIndeterminate(false);
            pDialog.setCancelable(true);
            pDialog.show();
        }

        @Override
        protected JSONObject doInBackground(String... args) {
            try {
                HashMap<String, String> params = new HashMap<>();
                params.put("username", args[0]);
                params.put("userPassword", args[1]);

                JSONObject json = jsonParser.makeHttpRequest(URL_REG_ONE, "GET", params);
                if (json != null) {
                    Log.d(TAG, json.toString());
                    success = json.getInt(TAG_SUCCESS);
                    if (success == 1) {
                        Log.d(TAG, "Success!");
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
                savePreferences(getResources().getString(R.string.reg1_valid), "VALID");
                savePreferences(getResources().getString(R.string.username), username);
                savePreferences(getResources().getString(R.string.password), password);
                Intent i = new Intent(getApplicationContext(), RegistrationTwo.class);
                startActivity(i);
                finish();
            } else if (success == 0) {
                Toast.makeText(getApplicationContext(), message, Toast.LENGTH_LONG).show();
            } else if (success == -1) {
                Toast.makeText(getApplicationContext(), "Can't connect to database", Toast.LENGTH_LONG).show();
            }
        }
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