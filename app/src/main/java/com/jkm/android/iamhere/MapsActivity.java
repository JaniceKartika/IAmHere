package com.jkm.android.iamhere;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.location.Location;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesNotAvailableException;
import com.google.android.gms.common.GooglePlayServicesRepairableException;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
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
import com.google.android.gms.maps.Projection;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

public class MapsActivity extends AppCompatActivity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        /*LocationListener,*/
        GoogleMap.OnMapClickListener,
        OnMapReadyCallback {

    public static final String TAG = MapsActivity.class.getSimpleName();

    private final static int CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;
    private final static int PLACE_AUTOCOMPLETE_REQUEST_CODE = 1;
    protected static final int REQUEST_CHECK_SETTINGS = 1000;

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.

    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;

    //double currentLat, currentLong, destinationLat, destinationLong;
    int count = 0;
    boolean hasPop = false;
    String EncodedPolyline;

    Location startLocation = new Location("Start");
    Location destinationLocation = new Location("Destination");
    LatLng startLatLng, destinationLatLng;
    Marker startMarker, destinationMarker;
    ArrayList<Marker> checkpointMarker = new ArrayList<>();
    ArrayList<String> checkpointLat = new ArrayList<>();
    ArrayList<String> checkpointLng = new ArrayList<>();
    float zoomLevel = (float) 16.0; //This goes up to 21
    Polyline PolylineRoute;

    TextView tvDebug;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        tvDebug = (TextView) findViewById(R.id.textview_debug);

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

        mLocationRequest = new LocationRequest();
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(500);

        destinationLocation.setLatitude(0);
        destinationLocation.setLongitude(0);

        if (isMyServiceRunning(MyService.class)) {
            hasPop = loadSavedPreferencesBoolean(getResources().getString(R.string.settings_has_pop));
            checkpointLat = loadSavedPreferencesStringArrayList(getResources().getString(R.string.checkpoint_lat));
            checkpointLng = loadSavedPreferencesStringArrayList(getResources().getString(R.string.checkpoint_lng));
            EncodedPolyline = loadSavedPreferencesString(getResources().getString(R.string.string_polyline));
        } else {
            hasPop = false;
            savePreferences(getResources().getString(R.string.settings_has_pop), hasPop);
            checkpointLat = loadSavedPreferencesStringArrayList(getResources().getString(R.string.just_null));
            checkpointLng = loadSavedPreferencesStringArrayList(getResources().getString(R.string.just_null));
            EncodedPolyline = null;
            savePreferences(getResources().getString(R.string.string_polyline), EncodedPolyline);
        }
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
        IntentFilter filter = new IntentFilter(getResources().getString(R.string.data_sent_intent));
        filter.addAction(getResources().getString(R.string.location_change_intent));
        registerReceiver(UpdateUI, filter);
        if (!isMyServiceRunning(MyService.class))
            startService(new Intent(this, MyService.class));
        //stopService(new Intent(this, MyService.class));
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.v(TAG, "Im on onPause");
        if (mGoogleApiClient.isConnected()) {
            //LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
            mGoogleApiClient.disconnect();
        }
        unregisterReceiver(UpdateUI);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "Im on onDestroy");
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private void updateUIOnMap(ArrayList<String> checkpointLat, ArrayList<String> checkpointLng, String EncodedOverviewPolyline) {
        if (EncodedOverviewPolyline != null) {
            ArrayList<LatLng> DecodedPoly = decodePoly(EncodedOverviewPolyline);
            PolylineOptions route = new PolylineOptions();
            for (int i = 0; i < DecodedPoly.size(); i++) {
                route.add(new LatLng(DecodedPoly.get(i).latitude, DecodedPoly.get(i).longitude));
            }
            route.color(R.color.colorPrimaryDark).width(7);
            PolylineRoute = mMap.addPolyline(route);
        }
        for (int i = 0; i < checkpointLat.size(); i++) {
            double lat = Double.valueOf(checkpointLat.get(i));
            double lng = Double.valueOf(checkpointLng.get(i));
            LatLng checkpointLatLng = new LatLng(lat, lng);
            if (checkpointLatLng.latitude != 0 && checkpointLatLng.longitude != 0) {
                if (i == checkpointLat.size() - 1) {
                    MarkerOptions markerOptions = new MarkerOptions()
                            .position(checkpointLatLng)
                            .title("Destination")
                            .draggable(true)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.destination_icon));
                    destinationMarker = mMap.addMarker(markerOptions);
                } else {
                    MarkerOptions markerOptions = new MarkerOptions()
                            .position(checkpointLatLng)
                            .icon(BitmapDescriptorFactory.fromResource(R.drawable.checkpoint_icon));
                    checkpointMarker.add(mMap.addMarker(markerOptions));
                }
            }
        }
    }

    private BroadcastReceiver UpdateUI = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (getResources().getString(R.string.data_sent_intent).equals(action)) {
                tvDebug.setText(intent.getExtras().getString(getResources().getString(R.string.data_sent_key)));
            } else if (getResources().getString(R.string.location_change_intent).equals(action)) {
                handleNewLocation((Location) intent.getParcelableExtra(getResources().getString(R.string.location_change_key)));
            }
        }
    };

    public void SettingsRequest() {
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(mLocationRequest);
        builder.setAlwaysShow(true);
        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(LocationSettingsResult result) {
                final Status status = result.getStatus();
                //Log.i(TAG, "status code: " + status.getStatusCode() + ", status message: " + status.getStatusMessage());
                //final LocationSettingsStates state = result.getLocationSettingsStates();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        try {
                            if (!hasPop) {
                                status.startResolutionForResult(MapsActivity.this, REQUEST_CHECK_SETTINGS);
                                hasPop = true;
                                savePreferences(getResources().getString(R.string.settings_has_pop), hasPop);
                            }
                        } catch (IntentSender.SendIntentException e) {
                            e.printStackTrace();
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        break;
                }
            }
        });
    }

    private void setUpMapIfNeeded() {
        if (mMap == null) {
            SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
            mapFragment.getMapAsync(this);
            if (mMap != null) {
                startMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        Log.i(TAG, "Map is ready.");
        mMap = googleMap;
        setUpMap();
        mMap.setOnMapClickListener(this);
        cleanUpMap();
        updateUIOnMap(checkpointLat, checkpointLng, EncodedPolyline);
    }

    private void setUpMap() {
        mMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            mMap.setMyLocationEnabled(true);
        //mMap.setTrafficEnabled(true);
        mMap.getUiSettings().setZoomControlsEnabled(true);
    }

    private void handleNewLocation(Location location) {
        count++;
        startLocation.setLatitude(location.getLatitude());
        startLocation.setLongitude(location.getLongitude());
        startLatLng = new LatLng(location.getLatitude(), location.getLongitude());
        Log.v(TAG, "starting = " + startLatLng.toString());

        MarkerOptions options = new MarkerOptions()
                .position(startLatLng)
                .title("I am here!")
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.start_icon));
        if (startMarker == null)
            startMarker = mMap.addMarker(options);
        animateMarker(startMarker, startLatLng, false);
        if (count == 1)
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startLatLng, zoomLevel));
    }

    public void animateMarker(final Marker marker, final LatLng toPosition, final boolean hideMarker) {
        final Handler handler = new Handler();
        final long start = SystemClock.uptimeMillis();
        Projection proj = mMap.getProjection();
        Point startPoint = proj.toScreenLocation(marker.getPosition());
        final LatLng startLatLng = proj.fromScreenLocation(startPoint);
        final long duration = 500;
        final Interpolator interpolator = new LinearInterpolator();

        handler.post(new Runnable() {
            @Override
            public void run() {
                long elapsed = SystemClock.uptimeMillis() - start;
                float t = interpolator.getInterpolation((float) elapsed / duration);
                double lat = t * toPosition.latitude + (1 - t) * startLatLng.latitude;
                double lng = t * toPosition.longitude + (1 - t) * startLatLng.longitude;
                marker.setPosition(new LatLng(lat, lng));
                if (t < 1.0) {
                    handler.postDelayed(this, 16);
                } else {
                    if (hideMarker)
                        marker.setVisible(false);
                    else
                        marker.setVisible(true);
                }
            }
        });
    }

    @Override
    public void onConnected(Bundle bundle) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            Location location = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            //LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
            handleNewLocation(location);
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
        if (connectionResult.hasResolution()) {
            try {
                connectionResult.startResolutionForResult(this, CONNECTION_FAILURE_RESOLUTION_REQUEST);
            } catch (IntentSender.SendIntentException e) {
                e.printStackTrace();
            }
        } else {
            Log.i(TAG, "Location services connection failed with code " + connectionResult.getErrorCode());
        }
    }

    /*@Override
    public void onLocationChanged(Location location) {
        handleNewLocation(location);
    }*/

    @Override
    public void onMapClick(LatLng latLng) {
        //myMap.addMarker(new MarkerOptions().position(point).title(point.toString()));

        destinationLocation.setLatitude(latLng.latitude);
        destinationLocation.setLongitude(latLng.longitude);

        //Convert Location to LatLng
        destinationLatLng = new LatLng(latLng.latitude, latLng.longitude);
        Log.v(TAG, "destination = " + destinationLatLng.toString());

        if (destinationMarker != null)
            destinationMarker.remove();

        MarkerOptions markerOptions = new MarkerOptions()
                .position(destinationLatLng)
                .title("Destination")
                .draggable(true)
                .icon(BitmapDescriptorFactory.fromResource(R.drawable.destination_icon));
        destinationMarker = mMap.addMarker(markerOptions);
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

    private void cleanUpMap() {
        if (destinationMarker != null)
            destinationMarker.remove();
        if (PolylineRoute != null)
            PolylineRoute.remove();
        if (checkpointMarker.size() > 0) {
            int checkpointCount = checkpointMarker.size();
            for (int i = 0; i < checkpointCount; i++) {
                Marker marker = checkpointMarker.get(i);
                marker.remove();
            }
            checkpointMarker.clear();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == PLACE_AUTOCOMPLETE_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Place place = PlaceAutocomplete.getPlace(this, data);
                Log.i(TAG, "Place: " + place.getName() + ", LatLng = " + place.getLatLng());

                new GetAsync().execute(
                        String.valueOf(startLatLng.latitude),
                        String.valueOf(startLatLng.longitude),
                        String.valueOf(place.getLatLng().latitude),
                        String.valueOf(place.getLatLng().longitude));

                cleanUpMap();
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
                Log.i(TAG, "Settings answer: OK");
                hasPop = false;
                savePreferences(getResources().getString(R.string.settings_has_pop), hasPop);
            } else if (resultCode == Activity.RESULT_CANCELED) {
                Log.i(TAG, "Settings answer: CANCEL");
            }
        }
    }

    private ArrayList<LatLng> decodePoly(String encoded) {
        Log.i(TAG, "String received: " + encoded);
        ArrayList<LatLng> poly = new ArrayList<>();
        int index = 0, len = encoded.length();
        int lat = 0, lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0 ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            LatLng p = new LatLng((((double) lat / 1E5)), (((double) lng / 1E5)));
            poly.add(p);
        }
        return poly;
    }

    public class GetAsync extends AsyncTask<String, String, JSONObject> {
        JSONParser jsonParser = new JSONParser();
        private static final String BASE_URL = "https://maps.googleapis.com/maps/api/directions/json";
        private static final String ORIGIN_PARAM = "origin";
        private static final String DESTINATION_PARAM = "destination";
        private static final String AVOID_PARAM = "avoid";
        private static final String ROUTES_PARAM = "routes";
        private static final String OVERVIEW_POLYLINE_PARAM = "overview_polyline";
        private static final String POINTS_PARAM = "points";
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
            checkpointLat.clear();
            checkpointLng.clear();
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
                    JSONObject OverviewPolylineObject = RouteDataObject.getJSONObject(OVERVIEW_POLYLINE_PARAM);
                    String EncodedOverviewPolyline = OverviewPolylineObject.getString(POINTS_PARAM);
                    savePreferences(getResources().getString(R.string.string_polyline), EncodedOverviewPolyline);
                    //Log.i(TAG, "Encoded Polyline = " + EncodedOverviewPolyline);
                    final ArrayList<LatLng> DecodedPoly = decodePoly(EncodedOverviewPolyline);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            PolylineOptions route = new PolylineOptions();
                            for (int i = 0; i < DecodedPoly.size(); i++) {
                                route.add(new LatLng(DecodedPoly.get(i).latitude, DecodedPoly.get(i).longitude));
                            }
                            route.color(R.color.colorPrimaryDark).width(7);
                            PolylineRoute = mMap.addPolyline(route);
                        }
                    });

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

                        final LatLng stepLatLng = new LatLng(DestinationStepLat, DestinationStepLng);
                        //LatLng checkpoint = new LatLng(DestinationStepLat, DestinationStepLng);

                        checkpointLat.add(String.valueOf(DestinationStepLat));
                        checkpointLng.add(String.valueOf(DestinationStepLng));

                        Log.i(TAG, "Start Lat:" + i + " " + StartStepLat);
                        Log.i(TAG, "Start Lng:" + i + " " + StartStepLng);
                        Log.i(TAG, "End Lat:" + i + " " + DestinationStepLat);
                        Log.i(TAG, "End Lng:" + i + " " + DestinationStepLng);

                        if (i < StepsDataArray.length() - 1) {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    MarkerOptions markerOptions = new MarkerOptions().position(stepLatLng).
                                            icon(BitmapDescriptorFactory.fromResource(R.drawable.checkpoint_icon));
                                    checkpointMarker.add(mMap.addMarker(markerOptions));
                                }
                            });
                        }
                    }
                    savePreferences(getResources().getString(R.string.checkpoint_lat), checkpointLat);
                    savePreferences(getResources().getString(R.string.checkpoint_lng), checkpointLng);
                    startService(new Intent(getApplicationContext(), MyService.class));
                } else {
                    Log.i(TAG, "json null");
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        protected void onPostExecute(JSONObject json) {
            Log.i(TAG, "checkpointLat = " + checkpointLat.toString());
            Log.i(TAG, "checkpointLng = " + checkpointLng.toString());
        }
    }

    private void savePreferences(String key, ArrayList<String> value) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        String set = value.toString();
        //Log.i(TAG, "set = " + set);
        editor.putString(key, set);
        editor.apply();
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

    private boolean loadSavedPreferencesBoolean(String key) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getBoolean(key, false);
    }

    private String loadSavedPreferencesString(String key) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        return sharedPreferences.getString(key, null);
    }

    private ArrayList<String> loadSavedPreferencesStringArrayList(String key) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        String set = sharedPreferences.getString(key, "0");
        return convertToArrayList(set);
    }

    private ArrayList<String> convertToArrayList(String dataString) {
        String temp1 = dataString.replace("[", "");
        String temp2 = temp1.replace("]", "");
        String[] dataArray = temp2.split(",");
        return new ArrayList<>(Arrays.asList(dataArray));
    }
}