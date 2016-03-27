package com.jkm.android.iamhere;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;

public class RegistrationThree extends AppCompatActivity {
    Button btConnect;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.registration_three);

        btConnect = (Button) findViewById(R.id.bt_reg3_connect);

        btConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(getApplicationContext(), Authentication.class);
                startActivity(i);
            }
        });
    }
}
