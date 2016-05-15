package com.jkm.android.iamhere.activity;

import android.content.Context;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import com.jkm.android.iamhere.R;

public class RegistrationTwo extends AppCompatActivity {
    private static final String TAG = RegistrationTwo.class.getSimpleName();

    Button btSendCode;
    EditText etPhoneCode, etPhoneNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registration_two);

        btSendCode = (Button) findViewById(R.id.bt_reg2_send_code);
        etPhoneCode = (EditText) findViewById(R.id.et_reg2_phone_code);
        etPhoneNumber = (EditText) findViewById(R.id.et_reg2_phone_number);

        String displayPhoneCode = "+" + getCountryPhoneCode();
        etPhoneCode.setText(displayPhoneCode);
        etPhoneNumber.requestFocus();

        btSendCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

            }
        });
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
}