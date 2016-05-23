package com.jkm.android.iamhere.service;

import android.Manifest;
import android.app.Activity;
import android.app.Service;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
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
import com.jkm.android.iamhere.R;
import com.jkm.android.iamhere.helper.KalmanLatLng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    String sendData;

    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    private String mDeviceAddress;
    private BLEService mBLEService;
    private boolean mConnected = false;
    private BluetoothGattCharacteristic characteristicTX;
    private BluetoothGattCharacteristic characteristicRX;

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
        mLocationRequest.setInterval(0);
        mLocationRequest.setFastestInterval(0);
        //mLocationRequest.setSmallestDisplacement(2);

        mGoogleApiClient.connect();

        Intent gattServiceIntent = new Intent(this, BLEService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        IntentFilter filter = new IntentFilter(getResources().getString(R.string.device_address_intent));
        filter.addAction(getResources().getString(R.string.device_disconnect_intent));
        filter.addAction(BLEService.ACTION_GATT_CONNECTED);
        filter.addAction(BLEService.ACTION_GATT_DISCONNECTED);
        filter.addAction(BLEService.ACTION_GATT_SERVICES_DISCOVERED);
        filter.addAction(BLEService.ACTION_DATA_AVAILABLE);
        registerReceiver(myBroadcast, filter);
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
        bearingCount = 1;
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
        savePreferences(getResources().getString(R.string.checkpoint_bearing_lat), "0");
        savePreferences(getResources().getString(R.string.checkpoint_bearing_lng), "0");
        savePreferences(getResources().getString(R.string.checkpoint_distance_lat), "0");
        savePreferences(getResources().getString(R.string.checkpoint_distance_lng), "0");
        savePreferences(getResources().getString(R.string.settings_has_pop), false);
        savePreferences(getResources().getString(R.string.string_polyline), null);
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        unregisterReceiver(myBroadcast);
        unbindService(mServiceConnection);
        mBLEService = null;
    }

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            Log.i(TAG, "OnServiceConnected");
            mBLEService = ((BLEService.LocalBinder) service).getService();
            if (!mBLEService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
            } else {
                Log.i(TAG, "Try to connect to BLE.");
                // Automatically connects to the device upon successful start-up initialization.
                if (mDeviceAddress != null) {
                    mBLEService.connect(mDeviceAddress);
                } else {
                    Log.i(TAG, "Can't connect because address is null.");
                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBLEService = null;
        }
    };

    /* Handles various events fired by the Service.
       ACTION_GATT_CONNECTED: connected to a GATT server.
       ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
       ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
       ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read or notification operations.
    */
    private final BroadcastReceiver myBroadcast = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BLEService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                Intent i = new Intent(getResources().getString(R.string.ble_connect));
                sendBroadcast(i);
            } else if (BLEService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                Intent i = new Intent(getResources().getString(R.string.ble_disconnect));
                sendBroadcast(i);
            } else if (BLEService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                GattServices(mBLEService.getSupportedGattServices());
            } else if (BLEService.ACTION_DATA_AVAILABLE.equals(action)) {
                Log.i(TAG, "Data from device: " + mBLEService.EXTRA_DATA);
            } else if (getResources().getString(R.string.device_address_intent).equals(action)) {
                mDeviceAddress = intent.getExtras().getString(getResources().getString(R.string.device_address_key));
                if (mBLEService != null) {
                    final boolean result = mBLEService.connect(mDeviceAddress);
                    Log.d(TAG, "Connect request result = " + result);
                }
            } else if (getResources().getString(R.string.device_disconnect_intent).equals(action)) {
                if (mConnected)
                    mBLEService.disconnect();
            } else if (getResources().getString(R.string.start_routing_intent).equals(action)) {
                sendData = "#-1@0,0\n";
                final byte[] tx = sendData.getBytes();
                if (mConnected) {
                    characteristicTX.setValue(tx);
                    mBLEService.writeCharacteristic(characteristicTX);
                    mBLEService.setCharacteristicNotification(characteristicRX, true);
                }
            }
        }
    };

    private void GattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            // get characteristic when UUID matches RX/TX UUID
            characteristicTX = gattService.getCharacteristic(BLEService.UUID_HM_RX_TX);
            characteristicRX = gattService.getCharacteristic(BLEService.UUID_HM_RX_TX);
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
        KalmanLatLng mKalmanLatLng = new KalmanLatLng(5);
        LatLng Kalman = mKalmanLatLng.Process(location.getLatitude(), location.getLongitude(), 1, System.currentTimeMillis());
        Log.v(TAG, "Kalman: Lat = " + Kalman.latitude + ", Lng = " + Kalman.longitude);
        if (location.hasAccuracy() && accuracy < 50) {
            location.setLatitude(Kalman.latitude);
            location.setLongitude(Kalman.longitude);
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
        float distance, bearing;
        int intDistance, intDirection;
        LatLng startLatLng, distanceEndLatLng, bearingEndLatLng;
        double nextBLat = 0, nextBLng = 0;
        Location nextBearing = new Location("NextBearing");
        if (!checkpointDistanceLat.toString().equals("[0]") && !checkpointDistanceLng.toString().equals("[0]")) {
            if (distanceCount < checkpointDistanceLat.size() && bearingCount < checkpointBearingLat.size()) {
                double dlat = Double.valueOf(checkpointDistanceLat.get(distanceCount));
                double dlng = Double.valueOf(checkpointDistanceLng.get(distanceCount));
                double blat = Double.valueOf(checkpointBearingLat.get(bearingCount));
                double blng = Double.valueOf(checkpointBearingLng.get(bearingCount));
                if (bearingCount < checkpointBearingLat.size() - 1) {
                    nextBLat = Double.valueOf(checkpointBearingLat.get(bearingCount + 1));
                    nextBLng = Double.valueOf(checkpointBearingLng.get(bearingCount + 1));
                }
                checkpointDistance.setLatitude(dlat);
                checkpointDistance.setLongitude(dlng);
                checkpointBearing.setLatitude(blat);
                checkpointBearing.setLongitude(blng);
                nextBearing.setLatitude(nextBLat);
                nextBearing.setLongitude(nextBLng);

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

                sendData = "#" + distanceCount + "@" + intDistance + "," + intDirection + "\n";
                final byte[] tx = sendData.getBytes();
                if (mConnected) {
                    characteristicTX.setValue(tx);
                    mBLEService.writeCharacteristic(characteristicTX);
                    mBLEService.setCharacteristicNotification(characteristicRX, true);
                }

                String passData = "Distance " + distanceCount + " = " + intDistance +
                        ", direction " + bearingCount + " = " + intDirection;
                Log.v(TAG, "data = " + sendData);
                Intent i = new Intent(getResources().getString(R.string.data_sent_intent));
                i.putExtra(getResources().getString(R.string.data_sent_key), passData);
                sendBroadcast(i);
                if (distance <= 20) {
                    distanceCount++;
                }
                if (distanceToCheckpointBearing <= 50) {
                    float inspectBearingDistance = checkpointBearing.distanceTo(nextBearing);
                    if (inspectBearingDistance <= 50.0) bearingCount += 2;
                    else bearingCount++;
                }
            } else {
                //Send notification to hardware
                sendData = "#-1@-1,-1\n";
                final byte[] tx = sendData.getBytes();
                if (mConnected) {
                    characteristicTX.setValue(tx);
                    mBLEService.writeCharacteristic(characteristicTX);
                    mBLEService.setCharacteristicNotification(characteristicRX, true);
                }
                //Update UI
                String complete = "Navigation has finished.";
                Intent i = new Intent(getResources().getString(R.string.data_sent_intent));
                i.putExtra(getResources().getString(R.string.data_sent_key), complete);
                sendBroadcast(i);
                stopService(new Intent(this, MyService.class));
            }
        }
    }

    private void savePreferences(String key, boolean value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putBoolean(key, value);
        editor.apply();
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