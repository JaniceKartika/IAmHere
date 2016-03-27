package com.jkm.android.iamhere;

import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.location.Location;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MapsActivity extends FragmentActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener, GoogleMap.OnMapClickListener {

    public static final String TAG = MapsActivity.class.getSimpleName();

    /*
     * Define a request code to send to Google Play services
     * This code is returned in Activity.onActivityResult
     */
    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    //private final static int earthRad = 6371000;
    double currentLat, currentLong, destinationLat, destinationLong;
    float distance, bearing;
    int intDistance, intBearing;
    String sendData;
    int count = 0;

    Location destination = new Location("Destination");
    Location startingPoint = new Location("Starting Point");
    LatLng currentLatLng, desLatLng;
    Marker startMarker, destinationMarker;

    BluetoothAdapter btAdapter;
    BluetoothDevice btDevice;
    BluetoothSocket btSocket;
    OutputStream btOutputStream;
    InputStream btInputStream;
    ListView myListView;
    ArrayAdapter<String> BTArrayAdapter;
    Set<BluetoothDevice> pairedDevices;
    Dialog dialog;

    Button btConnect, btReset;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        btConnect = (Button) findViewById(R.id.bt_connect);
        btReset = (Button) findViewById(R.id.bt_reset);

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBTState();

        setUpMapIfNeeded();

        Log.v("MyMap", "Im on onCreate");

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1000); // 1 second, in milliseconds

        mMap.setOnMapClickListener(this);

        destination.setLatitude(0);
        destination.setLongitude(0);

        btConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (btConnect.getText().equals("Connect")) {
                    if (!btAdapter.isEnabled()) {
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBtIntent, 1);
                    } else {
                        showBTDialog();
                    }
                } else if (btConnect.getText().equals("Disconnect")) {
                    btConnect.setText("Connect");
                    cancel();
                }
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v("MyMap", "Im on onResume");

        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        registerReceiver(receiver, filter);

        setUpMapIfNeeded();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v("MyMap", "Im on onPause");
        unregisterReceiver(receiver);
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v("MyMap", "Im on onDestroy");
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map)).getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        startMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
    }

    private void handleNewLocation(Location location) {
        float zoomLevel = (float) 16.0; //This goes up to 21
        //Log.d(TAG, location.toString());
        count++;

        currentLat = location.getLatitude();
        currentLong = location.getLongitude();

        startingPoint.setLatitude(currentLat);
        startingPoint.setLongitude(currentLong);

        currentLatLng = new LatLng(currentLat, currentLong);
        Log.v("MyMap", "starting = " + currentLatLng.toString());

        if (destination.getLatitude() != 0 && destination.getLongitude() != 0) {
            distance = startingPoint.distanceTo(destination);
            Log.v("MyMap", "distance = " + distance);

            bearing = getBearing(currentLatLng, desLatLng);
            Log.v("MyMap", "bearing = " + bearing);

            intDistance = (int) distance;
            intBearing = (int) bearing;
            sendData = "#" + intDistance + "," + intBearing + ",0" + "\n";
            if (btConnect.getText().equals("Disconnect"))
                write(sendData.getBytes());
            Log.v("MyMap", "data = " + sendData);
        }

        if (count > 1) {
            //mMap.addMarker(new MarkerOptions().position(new LatLng(currentLatitude, currentLongitude)).title("Current Location"));
            MarkerOptions options = new MarkerOptions().position(currentLatLng).title("I am here!");
            if (startMarker != null)
                startMarker.remove();
            startMarker = mMap.addMarker(options);
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, zoomLevel));
            //mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
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

    @Override
    public void onConnected(Bundle bundle) {
        Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        handleNewLocation(location);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
                 * Thrown if Google Play services canceled the original
                 * PendingIntent
                 */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        } else {
            /*
             * If no resolution is available, display a dialog to the
             * user with the error.
             */
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        handleNewLocation(location);
    }

    @Override
    public void onMapClick(LatLng latLng) {
        //myMap.addMarker(new MarkerOptions().position(point).title(point.toString()));

        //The code below demonstrate how to convert between LatLng and Location

        destinationLat = latLng.latitude;
        destinationLong = latLng.longitude;

        destination.setLatitude(destinationLat);
        destination.setLongitude(destinationLong);

        //Convert Location to LatLng
        desLatLng = new LatLng(destinationLat, destinationLong);
        Log.v("MyMap", "destination = " + desLatLng.toString());

        if (destination.getLatitude() != 0 && destination.getLongitude() != 0) {
            distance = startingPoint.distanceTo(destination);
            Log.v("MyMap", "distance = " + distance);

            bearing = getBearing(currentLatLng, desLatLng);
            Log.v("MyMap", "bearing = " + bearing);

            intDistance = (int) distance;
            intBearing = (int) bearing;
            sendData = "#" + intDistance + "," + intBearing + ",1" + "\n";
            if (btConnect.getText().equals("Disconnect"))
                write(sendData.getBytes());
            Log.v("MyMap", "data = " + sendData);
        }

        if (destinationMarker != null)
            destinationMarker.remove();

        MarkerOptions markerOptions = new MarkerOptions().position(desLatLng).title("Destination");
        destinationMarker = mMap.addMarker(markerOptions);
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
                btConnect.setText("Disconnect");
            } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
                btConnect.setText("Connect");
            }
        }
    };

    private void checkBTState() {
        if (btAdapter == null) {
            Toast.makeText(getApplicationContext(), "Fatal Error - No Bluetooth supported", Toast.LENGTH_LONG).show();
            finish();
        } else {
            if (btAdapter.isEnabled()) {
                Log.d("BTstatus", "Bluetooth ON");
            } else {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    private void write(byte[] bytes) {
        try {
            btOutputStream.write(bytes);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void cancel() {
        try {
            btOutputStream.close();
            btSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void connectClient() throws IOException {
        UUID stdUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
        btSocket = btDevice.createRfcommSocketToServiceRecord(stdUUID);
        btSocket.connect();
        btOutputStream = btSocket.getOutputStream();
        btInputStream = btSocket.getInputStream();
    }

    private void showBTDialog() {
        final AlertDialog.Builder popDialog = new AlertDialog.Builder(this);
        final LayoutInflater inflater = (LayoutInflater) this.getSystemService(LAYOUT_INFLATER_SERVICE);
        final View Viewlayout = inflater.inflate(R.layout.bluetooth_list, (ViewGroup) findViewById(R.id.bt_list));

        popDialog.setTitle("Paired Bluetooth device(s)");
        popDialog.setView(Viewlayout);

        // create the arrayAdapter that contains the BTDevices, and set it to a
        // ListView
        myListView = (ListView) Viewlayout.findViewById(R.id.BTList);
        BTArrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
        myListView.setAdapter(BTArrayAdapter);

        // get paired devices
        pairedDevices = btAdapter.getBondedDevices();

        // put it's one to the adapter
        for (BluetoothDevice device : pairedDevices)
            BTArrayAdapter.add(device.getName());

        // Button
        popDialog.setPositiveButton("OK",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });

        // Create pop up and show
        dialog = popDialog.create();

        myListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                dialog.dismiss();
                String name = parent.getItemAtPosition(position).toString();
                for (BluetoothDevice device : pairedDevices) {
                    if (device.getName().equals(name)) {
                        btDevice = device;
                        try {
                            connectClient();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }
        });
        dialog.show();
    }
}