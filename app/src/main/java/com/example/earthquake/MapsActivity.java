package com.example.earthquake;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.os.Build;
import android.os.Bundle;
import android.os.CpuUsageInfo;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

import model.EarthQuake;
import ui.CustomInfoWindow;
import util.Constants;

public class MapsActivity extends AppCompatActivity implements
        GoogleMap.OnInfoWindowClickListener, GoogleMap.OnMarkerClickListener,
        OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener, LocationListener {

    private GoogleMap mMap;
    private static final int ALL_PERMISSION_RESULT = 1111 ;
    private GoogleApiClient client;
    private ArrayList<String> permissionToRequest;
    private ArrayList<String> permissions = new ArrayList<>();
    private ArrayList<String> permissionRejected = new ArrayList<>();
    private FusedLocationProviderClient fusedLocationProviderClient;
    private LocationRequest locationRequest;
    public static final long UPDATE_INTERVAL = 5000;
    public static final long FASTEST_INTERVAL = 5000;
    private RequestQueue queue;
    private AlertDialog.Builder dialogBuilder;
    private AlertDialog dialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        queue = Volley.newRequestQueue(this);

        getEarthQuakes();

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(MapsActivity.this);

        //lets add permissions we need to req loc of users
        permissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);

        permissionToRequest = permissionsToRequest(permissions);

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(permissionToRequest.size() > 0){
                requestPermissions(permissionToRequest.toArray(
                        new String[permissionToRequest.size()]),
                        ALL_PERMISSION_RESULT
                );
            }
        }


        client = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addOnConnectionFailedListener(this)
                .addConnectionCallbacks(this)
                .build();
    }

    private void getEarthQuakes() {

        final EarthQuake earthQuake = new EarthQuake();
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET,
                Constants.URL, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {

                try {
                    JSONArray features = response.getJSONArray("features");
                    Log.d("Features", "onResponse: " + features.length());
                    for(int i = 0; i<Constants.LIMIT; i++){
                        JSONObject properties = features.getJSONObject(i).getJSONObject("properties");
                        //Get Geometry
                        JSONObject geometry = features.getJSONObject(i).getJSONObject("geometry");

                        //Get coordinates array
                        JSONArray coordinates = geometry.getJSONArray("coordinates");

                        double lon = coordinates.getDouble(0);
                        double lat = coordinates.getDouble(1);

                        //Log.d("places", "onResponse: "+lon+", "+lat);
                        earthQuake.setPlace(properties.getString("place"));
                        earthQuake.setType(properties.getString("type"));
                        earthQuake.setTime(properties.getLong("time"));
                        earthQuake.setLat(lat);
                        earthQuake.setLon(lon);
                        earthQuake.setMagnitude((properties.getLong("mag")));
                        earthQuake.setDetailLink(properties.getString("detail"));

                        //Log.d("places", "onResponse: 1 ");

                        java.text.DateFormat dateFormat = java.text.DateFormat.getDateInstance();
                        String formattedDate = dateFormat.format(new Date(properties.getLong("time")).getTime());

                        //Log.d("places", "onResponse: 2");

                        MarkerOptions markerOptions = new MarkerOptions();
                        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE));
                        markerOptions.title(earthQuake.getPlace());
                        markerOptions.position(new LatLng(lat, lon));
                        markerOptions.snippet("Magnitude: "+ earthQuake.getMagnitude()+"\n"+
                                "Date: "+ formattedDate);

                        //Add Circle to markers that have mag>2
                        if(earthQuake.getMagnitude() >=2.0){
                            CircleOptions circleOptions = new CircleOptions();
                            circleOptions.center(new LatLng(earthQuake.getLat(),earthQuake.getLon()));
                            circleOptions.radius(30000);
                            circleOptions.strokeWidth(3.6f);
                            circleOptions.fillColor(Color.RED);
                            markerOptions.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED));

                            mMap.addCircle(circleOptions);
                        }

                        //Log.d("places", "onResponse: 3");

                        Marker marker = mMap.addMarker(markerOptions);
                        marker.setTag(earthQuake.getDetailLink());
                        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lon), 4));

                        //Log.d("places", "onResponse: 4");
                    }
                } catch (JSONException e) {
                    //Log.d("places", "onResponse: Exception has occured." + Arrays.toString(e.getStackTrace()));
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });
        queue.add(jsonObjectRequest);
    }

    private ArrayList<String> permissionsToRequest(ArrayList<String> wantedPermissions) {
        ArrayList<String> result = new ArrayList<>();
        for(String perm : wantedPermissions){
            if(!hasPermission(perm)){
                result.add(perm);
            }
        }
        return result;
    }

    private boolean hasPermission(String perm) {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            return checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setOnInfoWindowClickListener(this);
        mMap.setOnMarkerClickListener(this);
        mMap.setInfoWindowAdapter(new CustomInfoWindow(getApplicationContext()));

        // Add a marker in Sydney and move the camera
//        LatLng sydney = new LatLng(-34, 151);
//        mMap.addMarker(new MarkerOptions().position(sydney).title("Marker in Sydney"));
//        mMap.moveCamera(CameraUpdateFactory.newLatLng(sydney));
    }

    @Override
    public void onLocationChanged(Location location) {

    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if(ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                && (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION))
                != PackageManager.PERMISSION_GRANTED){
            return;
        }
        startLocationUpdates();
    }

    private void startLocationUpdates() {

        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) !=
                PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(MapsActivity.this, "You need to enable permission to display location", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull final String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch(requestCode){
            case ALL_PERMISSION_RESULT:
                for(String perm: permissionToRequest){
                    if(!hasPermission(perm)){
                        permissionRejected.add(perm);

                    }
                }
                if(permissionRejected.size()>0){
                    if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (shouldShowRequestPermissionRationale(permissionRejected.get(0))) {
                            new AlertDialog.Builder(MapsActivity.this)
                                    .setMessage("These permissions are mandatory " +
                                            "to get location")
                                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                                requestPermissions(permissionRejected.toArray(
                                                        new String[permissionRejected.size()]),
                                                        ALL_PERMISSION_RESULT);
                                            }
                                        }
                                    }).setNegativeButton("Cancel", null)
                                    .create()
                                    .show();
                        }
                    }
                }else{
                    if(client!=null){
                        client.connect();
                    }
                }
                break;
        }
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onInfoWindowClick(Marker marker) {
//        Toast.makeText(getApplicationContext(), marker.getTitle().toString(), Toast.LENGTH_LONG)
//                .show();

        getQuakeDetails(marker.getTag().toString());
    }

    private void getQuakeDetails(String url) {
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                String detailsUrl = "";
                try {
                    JSONObject properties = response.getJSONObject("properties");
                    JSONObject products = properties.getJSONObject("products");
                    JSONArray geoserve = products.getJSONArray("geoserve");
                    for(int i =0; i<geoserve.length(); i++){
                        JSONObject geoserveObj = geoserve.getJSONObject(i);
                        JSONObject contentObj = geoserveObj.getJSONObject("contents");
                        JSONObject geoJsonObj = contentObj.getJSONObject("geoserve.json");

                        detailsUrl = geoJsonObj.getString("url");
                    }

                    //Log.d("Tag", "onResponse: "+ detailsUrl);
                    getMoreDetails(detailsUrl);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });

        queue.add(jsonObjectRequest);
    }

    public void getMoreDetails(String url){
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET,
                url, new Response.Listener<JSONObject>() {
            @Override
            public void onResponse(JSONObject response) {
                dialogBuilder = new AlertDialog.Builder(MapsActivity.this);
                View view = getLayoutInflater().inflate(R.layout.popup, null);
                Button dismissButton = (Button) view.findViewById(R.id.dismissPop);
                Button dismissButtonTop = (Button) view.findViewById(R.id.dismissPopTop);
                TextView popList = (TextView) view.findViewById(R.id.popList);
                WebView htmlPop = (WebView) view.findViewById(R.id.htmlWebview);

                StringBuilder stringBuilder = new StringBuilder();

                try {
                    JSONArray cities = response.getJSONArray("cities");
                    Log.d("citylog", "onResponse: 1");
                    for(int i =0; i<cities.length(); i++){
                        JSONObject citiesObj = cities.getJSONObject(i);
                        stringBuilder.append("City: "+citiesObj.getString("name")+"\n"
                        +"Distance: "+ citiesObj.getString("distance")+"\n"
                        +"Population: "+citiesObj.getString("population"));

                        stringBuilder.append("\n\n");
                    }
                    popList.setText(stringBuilder);

                    dialogBuilder.setView(view);
                    dialog = dialogBuilder.create();
                    dialog.show();
                } catch (JSONException e) {
                    Log.d("Exception", "onResponse: " + e.getStackTrace().toString());
                    e.printStackTrace();
                }


            }
        }, new Response.ErrorListener() {
            @Override
            public void onErrorResponse(VolleyError error) {

            }
        });
        queue.add(jsonObjectRequest);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        return false;
    }
}
