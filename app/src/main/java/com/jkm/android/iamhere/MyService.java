package com.jkm.android.iamhere;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Arrays;

public class MyService extends Service implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    private static final String TAG = MyService.class.getSimpleName();
    ArrayList<String> checkpointLat = new ArrayList<>();
    ArrayList<String> checkpointLng = new ArrayList<>();
    Location checkpoint = new Location("Checkpoint");
    int count = 0;

    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        mGoogleApiClient = new GoogleApiClient
                .Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(500);

        mGoogleApiClient.connect();

        savePreferences(getResources().getString(R.string.checkpoint_lat), "0");
        savePreferences(getResources().getString(R.string.checkpoint_lng), "0");
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "onStartCommand");
        count = 0;
        checkpointLat = loadSavedPreferences(getResources().getString(R.string.checkpoint_lat));
        checkpointLng = loadSavedPreferences(getResources().getString(R.string.checkpoint_lng));
        Log.i(TAG, "checkpointLat = " + checkpointLat.toString());
        Log.i(TAG, "checkpointLng = " + checkpointLng.toString());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.i(TAG, "onLocationChanged: " + location.getLatitude() + ", " + location.getLongitude());
        Intent i = new Intent(getResources().getString(R.string.location_change_intent));
        i.putExtra(getResources().getString(R.string.location_change_key), location);
        sendBroadcast(i);
        SendDataToHardware(location, checkpointLat, checkpointLng);
    }

    private void SendDataToHardware(Location startingPoint, ArrayList<String> checkpointLat, ArrayList<String> checkpointLng) {
        String sendData;
        float distance, bearing;
        int intDistance, intBearing;
        LatLng startLatLng, destinationLatLng;
        if (count < checkpointLat.size()) {
            double lat = Double.valueOf(checkpointLat.get(count));
            double lng = Double.valueOf(checkpointLng.get(count));
            checkpoint.setLatitude(lat);
            checkpoint.setLongitude(lng);
            if (checkpoint.getLatitude() != 0 && checkpoint.getLongitude() != 0) {
                distance = startingPoint.distanceTo(checkpoint);
                Log.v(TAG, "distance = " + distance);

                startLatLng = new LatLng(startingPoint.getLatitude(), startingPoint.getLongitude());
                destinationLatLng = new LatLng(checkpoint.getLatitude(), checkpoint.getLongitude());

                bearing = getBearing(startLatLng, destinationLatLng);
                Log.v(TAG, "bearing = " + bearing);

                intDistance = (int) distance;
                intBearing = (int) bearing;
                sendData = "#" + intDistance + "," + intBearing + "\n";
                Log.v(TAG, "data = " + sendData);
                Intent i = new Intent(getResources().getString(R.string.data_sent_intent));
                i.putExtra(getResources().getString(R.string.data_sent_key), sendData);
                sendBroadcast(i);
                if (distance < 10)
                    count++;
            }
        }
    }

    private void savePreferences(String key, String value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(key, value);
        editor.apply();
    }

    private ArrayList<String> loadSavedPreferences(String key) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String set = sharedPreferences.getString(key, "0");
        //Log.i(TAG, "set = " + set);
        return convertToArrayList(set);
    }

    private ArrayList<String> convertToArrayList(String dataString) {
        String temp1 = dataString.replace("[", "");
        String temp2 = temp1.replace("]", "");
        String[] dataArray = temp2.split(",");
        return new ArrayList<>(Arrays.asList(dataArray));
    }

    private float getBearing(LatLng begin, LatLng end) {
        double lat = Math.abs(begin.latitude - end.latitude);
        double lng = Math.abs(begin.longitude - end.longitude);
        if (begin.latitude < end.latitude && begin.longitude < end.longitude)
            return (float) (Math.toDegrees(Math.atan(lng / lat)));
        else if (begin.latitude >= end.latitude && begin.longitude < end.longitude)
            return (float) ((90 - Math.toDegrees(Math.atan(lng / lat))) + 90);
        else if (begin.latitude >= end.latitude && begin.longitude >= end.longitude)
            return (float) (Math.toDegrees(Math.atan(lng / lat)) + 180);
        else if (begin.latitude < end.latitude && begin.longitude >= end.longitude)
            return (float) ((90 - Math.toDegrees(Math.atan(lng / lat))) + 270);
        return -1;
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult((Activity) getApplicationContext(), CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }
}
