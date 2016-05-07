package com.jkm.android.iamhere;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.GeomagneticField;
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
    ArrayList<String> checkpointBearingLat = new ArrayList<>();
    ArrayList<String> checkpointBearingLng = new ArrayList<>();
    ArrayList<String> checkpointDistanceLat = new ArrayList<>();
    ArrayList<String> checkpointDistanceLng = new ArrayList<>();
    Location checkpointDistance = new Location("CheckpointDistance");
    Location checkpointBearing = new Location("CheckpointBearing");
    int distanceCount = 0, bearingCount = 0;
    boolean hasDeclination = false;
    float declination = 0;

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
        mLocationRequest.setSmallestDisplacement(2);

        mGoogleApiClient.connect();

        savePreferences(getResources().getString(R.string.checkpoint_bearing_lat), "0");
        savePreferences(getResources().getString(R.string.checkpoint_bearing_lng), "0");
        savePreferences(getResources().getString(R.string.checkpoint_distance_lat), "0");
        savePreferences(getResources().getString(R.string.checkpoint_distance_lng), "0");
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.i(TAG, "onStartCommand");
        distanceCount = 0;
        bearingCount = distanceCount;
        hasDeclination = false;
        checkpointBearingLat = loadSavedPreferences(getResources().getString(R.string.checkpoint_bearing_lat));
        checkpointBearingLng = loadSavedPreferences(getResources().getString(R.string.checkpoint_bearing_lng));
        checkpointDistanceLat = loadSavedPreferences(getResources().getString(R.string.checkpoint_distance_lat));
        checkpointDistanceLng = loadSavedPreferences(getResources().getString(R.string.checkpoint_distance_lng));
        Log.i(TAG, "checkpointBearingLat = " + checkpointBearingLat.toString());
        Log.i(TAG, "checkpointBearingLng = " + checkpointBearingLng.toString());
        Log.i(TAG, "checkpointDistanceLat = " + checkpointDistanceLat.toString());
        Log.i(TAG, "checkpointDistanceLng = " + checkpointDistanceLng.toString());
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
        if (!hasDeclination) {
            declination = getMagneticDeclination(location);
            Log.v(TAG, "declination = " + declination);
            hasDeclination = true;
        }
        float accuracy = location.getAccuracy();
        Log.v(TAG, "accuracy = " + accuracy);
        if (location.hasAccuracy() && accuracy < 25) {
            Intent i = new Intent(getResources().getString(R.string.location_change_intent));
            i.putExtra(getResources().getString(R.string.location_change_key), location);
            sendBroadcast(i);
            SendDataToHardware(location, checkpointDistanceLat, checkpointDistanceLng, checkpointBearingLat, checkpointBearingLng);
        }
    }

    private float getMagneticDeclination(Location location) {
        GeomagneticField geoField = new GeomagneticField((float) location.getLatitude(), (float) location.getLongitude(),
                (float) location.getAltitude(), System.currentTimeMillis());
        return geoField.getDeclination();
    }

    private void SendDataToHardware(Location startingPoint,
                                    ArrayList<String> checkpointDistanceLat, ArrayList<String> checkpointDistanceLng,
                                    ArrayList<String> checkpointBearingLat, ArrayList<String> checkpointBearingLng) {
        String sendData;
        float distance, bearing;
        int intDistance, intDirection;
        LatLng startLatLng, distanceEndLatLng, bearingEndLatLng;
        if (distanceCount < checkpointDistanceLat.size() || bearingCount < checkpointBearingLat.size()) {
            double dlat = Double.valueOf(checkpointDistanceLat.get(distanceCount));
            double dlng = Double.valueOf(checkpointDistanceLng.get(distanceCount));
            double blat = Double.valueOf(checkpointBearingLat.get(bearingCount));
            double blng = Double.valueOf(checkpointBearingLng.get(bearingCount));
            checkpointDistance.setLatitude(dlat);
            checkpointDistance.setLongitude(dlng);
            checkpointBearing.setLatitude(blat);
            checkpointBearing.setLongitude(blng);
            if (dlat != 0 && dlng != 0 && blat != 0 && blng != 0) {
                startLatLng = new LatLng(startingPoint.getLatitude(), startingPoint.getLongitude());
                distanceEndLatLng = new LatLng(checkpointDistance.getLatitude(), checkpointDistance.getLongitude());
                bearingEndLatLng = new LatLng(checkpointBearing.getLatitude(), checkpointBearing.getLongitude());

                distance = startingPoint.distanceTo(checkpointDistance);
                Log.v(TAG, "distance " + distanceCount + " = " + distance);
                Log.v(TAG, "distance with formula = " + getDistanceWithFormula(startLatLng, distanceEndLatLng));

                bearing = ((startingPoint.bearingTo(checkpointBearing)) + 360) % 360;
                int distanceToCheckpointBearing = (int) startingPoint.distanceTo(checkpointBearing);
                Log.v(TAG, "bearing " + bearingCount + " = " + bearing);
                Log.v(TAG, "bearing with if/else = " + getBearing(startLatLng, bearingEndLatLng));
                Log.v(TAG, "bearing with formula = " + getBearingWithFormula(startLatLng, bearingEndLatLng));

                intDistance = (int) distance;
                intDirection = (int) (bearing - declination);
                sendData = "#" + intDistance + "," + intDirection + "\n";
                String passData = "Distance " + distanceCount + " = " + intDistance +
                        ", direction " + bearingCount + " = " + intDirection;
                Log.v(TAG, "data = " + sendData);
                Intent i = new Intent(getResources().getString(R.string.data_sent_intent));
                i.putExtra(getResources().getString(R.string.data_sent_key), passData);
                sendBroadcast(i);
                if (distance < 10)
                    distanceCount++;
                if (distanceToCheckpointBearing < 10)
                    bearingCount++;
            }
        } else {
            String complete = "Navigation has finished.";
            Intent i = new Intent(getResources().getString(R.string.data_sent_intent));
            i.putExtra(getResources().getString(R.string.data_sent_key), complete);
            sendBroadcast(i);
            stopService(new Intent(this, MyService.class));
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

    private float getBearingWithFormula(LatLng begin, LatLng end) {
        double thetaA = Math.toRadians(begin.latitude);
        double lambdaA = Math.toRadians(begin.longitude);
        double thetaB = Math.toRadians(end.latitude);
        double lambdaB = Math.toRadians(end.longitude);

        double y = Math.sin(lambdaB - lambdaA) * Math.cos(thetaB);
        double x = Math.cos(thetaA) * Math.sin(thetaB) - Math.sin(thetaA) * Math.cos(thetaB) * Math.cos(lambdaB - lambdaA);
        return (float) ((Math.toDegrees(Math.atan2(y, x)) + 360) % 360);
    }

    private float getDistanceWithFormula(LatLng begin, LatLng end) {
        final int earthRadius = 6371000;
        double thetaA = Math.toRadians(begin.latitude);
        double lambdaA = Math.toRadians(begin.longitude);
        double thetaB = Math.toRadians(end.latitude);
        double lambdaB = Math.toRadians(end.longitude);
        double dTheta = thetaB - thetaA;
        double dLambda = lambdaB - lambdaA;

        double a = Math.sin(dTheta / 2) * Math.sin(dTheta / 2) +
                Math.cos(thetaA) * Math.cos(thetaB) * Math.sin(dLambda / 2) * Math.sin(dLambda / 2);
        return (float) (2 * earthRadius * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)));
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
