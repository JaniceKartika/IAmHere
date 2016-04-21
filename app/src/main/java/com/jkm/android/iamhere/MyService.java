package com.jkm.android.iamhere;

import android.Manifest;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;
import java.util.Arrays;

public class MyService extends Service {
    private static final String TAG = MyService.class.getSimpleName();
    private LocationManager mLocationManager = null;
    private static final int LOCATION_INTERVAL = 300;
    private static final float LOCATION_DISTANCE = 0;
    ArrayList<String> checkpointLat = new ArrayList<>();
    ArrayList<String> checkpointLng = new ArrayList<>();
    Location checkpoint = new Location("Checkpoint");

    private class LocationListener implements android.location.LocationListener {
        Location mLastLocation;

        public LocationListener(String provider) {
            Log.i(TAG, "LocationListener " + provider);
            mLastLocation = new Location(provider);
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.i(TAG, "onLocationChanged: " + location.getLatitude() + ", " + location.getLongitude());
            mLastLocation.set(location);
            double lat = Double.valueOf(checkpointLat.get(0));
            double lng = Double.valueOf(checkpointLng.get(0));
            Log.i(TAG, "checkpointLat = " + String.valueOf(lat));
            Log.i(TAG, "checkpointLng = " + String.valueOf(lng));
            checkpoint.setLatitude(lat);
            checkpoint.setLongitude(lng);
            SendDataToHardware(location, checkpoint, 0);
        }

        @Override
        public void onProviderDisabled(String provider) {
            Log.i(TAG, "onProviderDisabled: " + provider);
        }

        @Override
        public void onProviderEnabled(String provider) {
            Log.i(TAG, "onProviderEnabled: " + provider);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
            Log.i(TAG, "onStatusChanged: " + provider);
        }
    }

    LocationListener[] mLocationListeners = new LocationListener[]{
            new LocationListener(LocationManager.GPS_PROVIDER),
            new LocationListener(LocationManager.NETWORK_PROVIDER)
    };

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.i(TAG, "onStartCommand");
        super.onStartCommand(intent, flags, startId);
        return START_STICKY;
    }

    @Override
    public void onCreate() {
        Log.i(TAG, "onCreate");
        initializeLocationManager();
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[1]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "network provider does not exist, " + ex.getMessage());
        }
        try {
            mLocationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, LOCATION_INTERVAL, LOCATION_DISTANCE,
                    mLocationListeners[0]);
        } catch (java.lang.SecurityException ex) {
            Log.i(TAG, "fail to request location update, ignore", ex);
        } catch (IllegalArgumentException ex) {
            Log.d(TAG, "gps provider does not exist " + ex.getMessage());
        }
        checkpointLat = loadSavedPreferences(getResources().getString(R.string.checkpoint_lat));
        checkpointLng = loadSavedPreferences(getResources().getString(R.string.checkpoint_lng));
        Log.i(TAG, "checkpointLat = " + checkpointLat.toString());
        Log.i(TAG, "checkpointLng = " + checkpointLng.toString());
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
        if (mLocationManager != null) {
            for (LocationListener mLocationListener : mLocationListeners) {
                try {
                    if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                            || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
                        mLocationManager.removeUpdates(mLocationListener);
                } catch (Exception ex) {
                    Log.i(TAG, "fail to remove location listeners, ignore", ex);
                }
            }
        }
    }

    private void initializeLocationManager() {
        Log.i(TAG, "initializeLocationManager");
        if (mLocationManager == null) {
            mLocationManager = (LocationManager) getApplicationContext().getSystemService(Context.LOCATION_SERVICE);
        }
    }

    private ArrayList<String> loadSavedPreferences(String key) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String set = sharedPreferences.getString(key, "0");
        Log.i(TAG, "set = " + set);
        return convertToArrayList(set);
    }

    private ArrayList<String> convertToArrayList(String dataString) {
        String temp1 = dataString.replace("[", "");
        String temp2 = temp1.replace("]", "");
        String[] dataArray = temp2.split(",");
        return new ArrayList<>(Arrays.asList(dataArray));
    }

    private void SendDataToHardware(Location startingPoint, Location destination, int SendCode) {
        String sendData;
        float distance, bearing;
        int intDistance, intBearing;
        LatLng startLatLng, destinationLatLng;
        if (destination.getLatitude() != 0 && destination.getLongitude() != 0) {
            distance = startingPoint.distanceTo(destination);
            Log.v(TAG, "distance = " + distance);

            startLatLng = new LatLng(startingPoint.getLatitude(), startingPoint.getLongitude());
            destinationLatLng = new LatLng(destination.getLatitude(), destination.getLongitude());

            bearing = getBearing(startLatLng, destinationLatLng);
            Log.v(TAG, "bearing = " + bearing);

            intDistance = (int) distance;
            intBearing = (int) bearing;
            sendData = "#" + intDistance + "," + intBearing + "," + SendCode + "\n";
            Log.v(TAG, "data = " + sendData);
        }
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
}
