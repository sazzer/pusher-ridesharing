package com.pusher.pushnotify.ride

import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.Toast
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.messaging.RemoteMessage
import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.TextHttpResponseHandler
import com.pusher.pushnotifications.PushNotificationReceivedListener
import com.pusher.pushnotifications.PushNotifications
import cz.msebera.android.httpclient.Header
import cz.msebera.android.httpclient.entity.StringEntity
import org.json.JSONObject

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private val REQUEST_LOCATION_PERMISSIONS = 1001

    private lateinit var mMap: GoogleMap

    private var currentJob: String? = null

    private val markers = mutableMapOf<String, Marker>()

    private var currentJobMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PushNotifications.start(getApplicationContext(), "PUSHER INSTANCE ID");
        PushNotifications.subscribe("driver_broadcast")

        checkLocationPermissions()
    }

    override fun onResume() {
        super.onResume()

        PushNotifications.setOnMessageReceivedListenerForVisibleActivity(this, object : PushNotificationReceivedListener {
            override fun onMessageReceived(remoteMessage: RemoteMessage) {
                val action = remoteMessage.data["action"]

                runOnUiThread {
                    if (action == "NEW_JOB") {
                        val jobId = remoteMessage.data["job"]!!
                        val location = LatLng(remoteMessage.data["latitude"]!!.toDouble(), remoteMessage.data["longitude"]!!.toDouble())

                        val marker = mMap.addMarker(MarkerOptions()
                                .position(location)
                                .title("New job"))
                        marker.tag = jobId
                        markers[jobId] = marker

                        displayMessage("A new job is available")
                    } else if (action == "ACCEPTED_JOB") {
                        val jobId = remoteMessage.data["job"]!!
                        val location = LatLng(remoteMessage.data["latitude"]!!.toDouble(), remoteMessage.data["longitude"]!!.toDouble())

                        markers[jobId]?.remove()
                        markers.remove(jobId)
                    }
                }
            }
        })
    }


    private fun checkLocationPermissions() {
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_LOCATION_PERMISSIONS)
            return
        }
        setupMap()
    }

    override fun onRequestPermissionsResult(requestCode: Int,
                                            permissions: Array<out String>,
                                            grantResults: IntArray) {
        if (requestCode == REQUEST_LOCATION_PERMISSIONS) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupMap()
            } else {
                Toast.makeText(this, "Location Permission Denied", Toast.LENGTH_SHORT)
                        .show();
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }


    private fun setupMap() {
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val locationManager = applicationContext.getSystemService(LocationManager::class.java)
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100, 0.0f, object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (currentJob != null) {
                    val request = JSONObject(mapOf(
                            "latitude" to location.latitude,
                            "longitude" to location.longitude
                    ))

                    val client = AsyncHttpClient()
                    client.post(applicationContext, "http://10.0.2.2:8080/update-location/$currentJob", StringEntity(request.toString()),
                            "application/json", object : TextHttpResponseHandler() {

                        override fun onSuccess(statusCode: Int, headers: Array<out Header>, responseString: String?) {
                        }

                        override fun onFailure(statusCode: Int, headers: Array<out Header>, responseString: String, throwable: Throwable) {
                        }
                    });

                }
            }

            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {

            }

            override fun onProviderEnabled(provider: String?) {

            }

            override fun onProviderDisabled(provider: String?) {

            }
        }, null)

    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.isMyLocationEnabled = true
        mMap.isTrafficEnabled = true

        mMap.setOnMarkerClickListener { marker ->
            if (currentJob != null) {
                runOnUiThread {
                    displayMessage("You are already on a job!")
                }
            } else {

                val jobId = marker.tag

                val location = mMap.myLocation

                val request = JSONObject(mapOf(
                        "latitude" to location.latitude,
                        "longitude" to location.longitude
                ))

                val client = AsyncHttpClient()
                client.post(applicationContext, "http://10.0.2.2:8080/accept-job/$jobId", StringEntity(request.toString()),
                        "application/json", object : TextHttpResponseHandler() {

                    override fun onSuccess(statusCode: Int, headers: Array<out Header>, responseString: String?) {
                        runOnUiThread {
                            displayMessage("You have accepted this job")
                            currentJob = jobId as String

                            findViewById<Button>(R.id.dropoff_ride).visibility = INVISIBLE
                            findViewById<Button>(R.id.pickup_ride).visibility = VISIBLE

                            val selectedJobMarker = markers[jobId]!!
                            val marker = mMap.addMarker(MarkerOptions()
                                    .position(selectedJobMarker.position)
                                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                                    .title("Current job"))
                            marker.tag = jobId

                            currentJobMarker = marker

                        }
                    }

                    override fun onFailure(statusCode: Int, headers: Array<out Header>, responseString: String, throwable: Throwable) {
                        runOnUiThread {
                            displayMessage("An error occurred accepting this job")
                        }
                    }
                });
            }

            true
        }
    }

    private fun displayMessage(message: String) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT).show();
    }


    fun pickupRide(view: View) {
        val location = mMap.myLocation

        val request = JSONObject(mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude
        ))

        val client = AsyncHttpClient()
        client.post(applicationContext, "http://10.0.2.2:8080/pickup/$currentJob", StringEntity(request.toString()),
                "application/json", object : TextHttpResponseHandler() {

            override fun onSuccess(statusCode: Int, headers: Array<out Header>, responseString: String?) {
                runOnUiThread {
                    findViewById<Button>(R.id.dropoff_ride).visibility = VISIBLE
                    findViewById<Button>(R.id.pickup_ride).visibility = INVISIBLE
                    currentJobMarker?.remove()
                    currentJobMarker = null
                }
            }

            override fun onFailure(statusCode: Int, headers: Array<out Header>, responseString: String, throwable: Throwable) {
                runOnUiThread {
                    displayMessage("An error occurred picking up your ride")
                }
            }
        });
    }

    fun dropoffRide(view: View) {
        val location = mMap.myLocation

        val request = JSONObject(mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude
        ))

        val client = AsyncHttpClient()
        client.post(applicationContext, "http://10.0.2.2:8080/dropoff/$currentJob", StringEntity(request.toString()),
                "application/json", object : TextHttpResponseHandler() {

            override fun onSuccess(statusCode: Int, headers: Array<out Header>, responseString: String?) {
                runOnUiThread {
                    findViewById<Button>(R.id.dropoff_ride).visibility = INVISIBLE
                    findViewById<Button>(R.id.pickup_ride).visibility = INVISIBLE
                    currentJob = null
                }
            }

            override fun onFailure(statusCode: Int, headers: Array<out Header>, responseString: String, throwable: Throwable) {
                runOnUiThread {
                    displayMessage("An error occurred dropping off your ride")
                }
            }
        });
    }
}
