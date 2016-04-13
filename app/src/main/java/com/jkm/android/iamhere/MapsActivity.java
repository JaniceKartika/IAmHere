package com.jkm.android.iamhere;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.location.places.Place;
import com.google.android.gms.location.places.Places;
import com.google.android.gms.location.places.ui.PlaceAutocomplete;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;

public class MapsActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener,
        GoogleMap.OnMapClickListener,
        OnMapReadyCallback {

    public static final String TAG = MapsActivity.class.getSimpleName();

    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private final static int PLACE_AUTOCOMPLETE_REQUEST_CODE = 1;
    protected static final int REQUEST_CHECK_SETTINGS = 1000;

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    double currentLat, currentLong, destinationLat, destinationLong;
    int count = 0;

    Location destination = new Location("Destination");
    Location startingPoint = new Location("Starting Point");
    LatLng currentLatLng, desLatLng;
    Marker startMarker, destinationMarker;
    float zoomLevel = (float) 16.0; //This goes up to 21

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        setUpMapIfNeeded();

        Log.v(TAG, "Im on onCreate");

        mGoogleApiClient = new GoogleApiClient
                .Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API)
                .build();

        // Create the LocationRequest object
        mLocationRequest = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(10 * 1000)        // 10 seconds, in milliseconds
                .setFastestInterval(1000); // 1 second, in milliseconds

        destination.setLatitude(0);
        destination.setLongitude(0);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.v(TAG, "Im on onStart");
        SettingsRequest();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.v(TAG, "Im on onResume");
        setUpMapIfNeeded();
        mGoogleApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "Im on onPause");
        if (mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "Im on onDestroy");
    }

    public void SettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);
        builder.setAlwaysShow(true);

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                //final LocationSettingsStates state = result.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can initialize location
                        // requests here.
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied. But could be fixed by showing the user
                        // a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(MapsActivity.this, REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way to fix the
                        // settings so we won't show the dialog.
                        break;
                }
            }
        });
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p>
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
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                startMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        setUpMap();
        mMap.setOnMapClickListener(this);
    }

    private void setUpMap() {
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            mMap.setMyLocationEnabled(true);
        mMap.setTrafficEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
    }

    private void handleNewLocation(Location location) {
        //Log.d(TAG, location.toString());
        count++;

        currentLat = location.getLatitude();
        currentLong = location.getLongitude();

        startingPoint.setLatitude(currentLat);
        startingPoint.setLongitude(currentLong);

        currentLatLng = new LatLng(currentLat, currentLong);
        Log.v(TAG, "starting = " + currentLatLng.toString());

        //TODO: send data to hardware

        //mMap.addMarker(new MarkerOptions().position(new LatLng(currentLatitude, currentLongitude)).title("Current Location"));
        MarkerOptions options = new MarkerOptions()
                .position(currentLatLng)
                .title("I am here!")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.start_icon));
        if (startMarker != null)
            startMarker.remove();
        startMarker = mMap.addMarker(options);
        if (count == 1) {
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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            handleNewLocation(location);
        }
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

        destinationLat = latLng.latitude;
        destinationLong = latLng.longitude;

        destination.setLatitude(destinationLat);
        destination.setLongitude(destinationLong);

        //Convert Location to LatLng
        desLatLng = new LatLng(destinationLat, destinationLong);
        Log.v(TAG, "destination = " + desLatLng.toString());

        //TODO: send data to hardware

        if (destinationMarker != null)
            destinationMarker.remove();

        MarkerOptions markerOptions = new MarkerOptions()
                .position(desLatLng)
                .title("Destination")
                .draggable(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.destination_icon));
        destinationMarker = mMap.addMarker(markerOptions);
    }

    private void SendDataToHardware(int SendCode) {
        String sendData;
        float distance, bearing;
        int intDistance, intBearing;
        if (destination.getLatitude() != 0 && destination.getLongitude() != 0) {
            distance = startingPoint.distanceTo(destination);
            Log.v(TAG, "distance = " + distance);

            bearing = getBearing(currentLatLng, desLatLng);
            Log.v(TAG, "bearing = " + bearing);

            intDistance = (int) distance;
            intBearing = (int) bearing;
            sendData = "#" + intDistance + "," + intBearing + "," + SendCode + "\n";
            Log.v(TAG, "data = " + sendData);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.search) {
            callPlaceAutocompleteActivityIntent();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void callPlaceAutocompleteActivityIntent() {
        try {
            Intent intent = new PlaceAutocomplete.IntentBuilder(PlaceAutocomplete.MODE_OVERLAY).build(this);
            startActivityForResult(intent, PLACE_AUTOCOMPLETE_REQUEST_CODE);
        } catch (GooglePlayServicesRepairableException | GooglePlayServicesNotAvailableException e) {
            Log.e(TAG, "Error = " + e);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);
                Log.i(TAG, "Place: " + place.getName() + ", LatLng = " + place.getLatLng());
                destinationLat = place.getLatLng().latitude;
                destinationLong = place.getLatLng().longitude;

                new GetAsync().execute(
                        String.valueOf(currentLat),
                        String.valueOf(currentLong),
                        String.valueOf(destinationLat),
                        String.valueOf(destinationLong));

                //TODO: send data to hardware

                if (destinationMarker != null)
                    destinationMarker.remove();
                MarkerOptions markerOptions = new MarkerOptions()
                        .position(place.getLatLng())
                        .title("Destination")
                        .draggable(true)
                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.destination_icon));
                destinationMarker = mMap.addMarker(markerOptions);
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(place.getLatLng(), zoomLevel));
            } else if (resultCode == PlaceAutocomplete.RESULT_ERROR) {
                Status status = PlaceAutocomplete.getStatus(this, data);
                Log.i(TAG, "status = " + status.getStatusMessage());
            } else if (resultCode == RESULT_CANCELED) {
                Log.i(TAG, "status = canceled");
            }
        }

        if (requestCode == REQUEST_CHECK_SETTINGS) {
            if (resultCode == Activity.RESULT_OK) {
                Log.i(TAG, "Answer: OK");
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Log.i(TAG, "Answer: CANCEL");
            }
        }
    }

    public class GetAsync extends AsyncTask<String, String, JSONObject> {
        JSONParser jsonParser = new JSONParser();
        private static final String BASE_URL = "https://maps.googleapis.com/maps/api/directions/json";
        private static final String ORIGIN_PARAM = "origin";
        private static final String DESTINATION_PARAM = "destination";
        private static final String AVOID_PARAM = "avoid";
        private static final String ROUTES_PARAM = "routes";
        private static final String LEGS_PARAM = "legs";
        private static final String STEPS_PARAM = "steps";
        private static final String END_LOCATION_PARAM = "end_location";
        private static final String START_LOCATION_PARAM = "start_location";
        private static final String LATITUDE_PARAM = "lat";
        private static final String LONGITUDE_PARAM = "lng";
        private static final String API_KEY_PARAM = "key";

        double StartStepLat, StartStepLng, DestinationStepLat, DestinationStepLng;

        @Override
        protected void onPreExecute() {

        }

        @Override
        protected JSONObject doInBackground(String... args) {
            final String MyAPIKey = "AIzaSyBe6TqDxaDb3Bva99q1fiyG1d5ZKwYLnmY";
            String[] restriction = {"tolls", "highways", "ferries"};

            if (args.length == 0) {
                return null;
            }

            try {
                HashMap<String, String> params = new HashMap<>();
                params.put(ORIGIN_PARAM, args[0] + "," + args[1]);
                params.put(DESTINATION_PARAM, args[2] + "," + args[3]);
                params.put(AVOID_PARAM, restriction[0] + "|" + restriction[2]);
                params.put(API_KEY_PARAM, MyAPIKey);

                JSONObject json = jsonParser.makeHttpRequest(BASE_URL, "GET", params);
                if (json != null) {
                    //Log.i(TAG, json.toString());
                    JSONArray RouteDataArray = json.getJSONArray(ROUTES_PARAM);
                    JSONObject RouteDataObject = RouteDataArray.getJSONObject(0);
                    JSONArray LegsDataArray = RouteDataObject.getJSONArray(LEGS_PARAM);
                    JSONObject LegsDataObject = LegsDataArray.getJSONObject(0);
                    JSONArray StepsDataArray = LegsDataObject.getJSONArray(STEPS_PARAM);
                    for (int i = 0; i < StepsDataArray.length(); i++) {
                        JSONObject StepsDataObject = StepsDataArray.getJSONObject(i);
                        JSONObject startLatLng = StepsDataObject.getJSONObject(START_LOCATION_PARAM);
                        JSONObject endLatLng = StepsDataObject.getJSONObject(END_LOCATION_PARAM);

                        StartStepLat = startLatLng.getDouble(LATITUDE_PARAM);
                        StartStepLng = startLatLng.getDouble(LONGITUDE_PARAM);
                        DestinationStepLat = endLatLng.getDouble(LATITUDE_PARAM);
                        DestinationStepLng = endLatLng.getDouble(LONGITUDE_PARAM);

                        final LatLng stepLatLng = new LatLng(StartStepLat, StartStepLng);
                        final LatLng stepLatLngEnd = new LatLng(DestinationStepLat, DestinationStepLng);

                        Log.i(TAG, "Start Lat:" + i + " " + StartStepLat);
                        Log.i(TAG, "Start Lng:" + i + " " + StartStepLng);
                        Log.i(TAG, "End Lat:" + i + " " + DestinationStepLat);
                        Log.i(TAG, "End Lng:" + i + " " + DestinationStepLng);

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                MarkerOptions markerOptions = new MarkerOptions().position(stepLatLng);
                                MarkerOptions markerEnd = new MarkerOptions().position(stepLatLngEnd)
                                        .icon(BitmapDescriptorFactory.fromResource(R.drawable.destination_icon));
                                mMap.addMarker(markerOptions);
                                mMap.addMarker(markerEnd);
                            }
                        });
                    }
                } else {
                    Log.i(TAG, "json null");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute(JSONObject json) {

        }
    }
}