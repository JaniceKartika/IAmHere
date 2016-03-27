package com.jkm.android.iamhere;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

public class RegistrationTwo extends AppCompatActivity {
    Button btSendCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registration_two);

        btSendCode = (Button) findViewById(R.id.bt_reg2_send_code);

        btSendCode.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), RegistrationThree.class);
                startActivity(i);
            }
        });
    }
}
