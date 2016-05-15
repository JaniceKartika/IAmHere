package com.jkm.android.iamhere.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.jkm.android.iamhere.R;
import com.jkm.android.iamhere.app.SMSConfig;
import com.jkm.android.iamhere.service.HTTPService;

public class SMSReceiver extends BroadcastReceiver {
    private static final String TAG = SMSReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {
        final Bundle bundle = intent.getExtras();
        try {
            if (bundle != null) {
                Object[] pdusObject = (Object[]) bundle.get("pdus");
                if (pdusObject != null) {
                    for (Object pdu : pdusObject) {
                        SmsMessage currentMessage = SmsMessage.createFromPdu((byte[]) pdu);
                        String senderAddress = currentMessage.getDisplayOriginatingAddress();
                        String message = currentMessage.getDisplayMessageBody();

                        Log.i(TAG, "Received SMS: " + message + ", Sender: " + senderAddress);

                        if (!senderAddress.contains(SMSConfig.SMS_ORIGIN_1) && !senderAddress.contains(SMSConfig.SMS_ORIGIN_2))
                            return;

                        //Verification code from SMS
                        String verificationCode = getVerificationCode(message);
                        Log.i(TAG, "OTP received: " + verificationCode);

                        Intent httpIntent = new Intent(context, HTTPService.class);
                        httpIntent.putExtra(context.getResources().getString(R.string.otp_key), verificationCode);
                        context.startService(httpIntent);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception: " + e.getMessage());
        }
    }

    private String getVerificationCode(String message) {
        String code = "";
        int index = message.indexOf(SMSConfig.OTP_DELIMITER);
        if (index != -1) {
            int start = index + 2;
            int length = 6;
            code = message.substring(start, start + length);
            return code;
        }
        return code;
    }
}
