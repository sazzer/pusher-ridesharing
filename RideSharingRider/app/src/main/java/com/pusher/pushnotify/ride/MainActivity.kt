package com.pusher.pushnotify.ride

import android.content.pm.PackageManager
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.widget.Button
import android.widget.Toast
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.firebase.messaging.RemoteMessage
import com.loopj.android.http.AsyncHttpClient
import com.loopj.android.http.JsonHttpResponseHandler
import com.loopj.android.http.TextHttpResponseHandler
import com.pusher.pushnotifications.PushNotificationReceivedListener
import com.pusher.pushnotifications.PushNotifications
import cz.msebera.android.httpclient.Header
import cz.msebera.android.httpclient.entity.StringEntity
import org.json.JSONObject


class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private val REQUEST_LOCATION_PERMISSIONS = 1001

    private lateinit var mMap: GoogleMap

    private var driverMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        PushNotifications.start(applicationContext, "PUSHER INSTANCE ID");

        checkLocationPermissions()
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
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap
        mMap.isMyLocationEnabled = true
        mMap.isTrafficEnabled = true

        mMap.setOnMyLocationChangeListener {
            findViewById<Button>(R.id.request_ride).isEnabled = true
        }
    }

    fun requestRide(view: View) {
        val location = mMap.myLocation

        val request = JSONObject(mapOf(
                "latitude" to location.latitude,
                "longitude" to location.longitude
        ))
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(location.latitude, location.longitude), 15.0f))

        val client = AsyncHttpClient()
        client.post(applicationContext, "http://10.0.2.2:8080/request-ride", StringEntity(request.toString()),
                "application/json", object : TextHttpResponseHandler() {

            override fun onSuccess(statusCode: Int, headers: Array<out Header>, responseString: String) {
                Log.i("MainActivity", "Requested ride. Job ID = $responseString")
                PushNotifications.subscribe("rider_$responseString")
                runOnUiThread {
                    displayMessage("Your ride has been requested")
                    findViewById<Button>(R.id.request_ride).visibility = INVISIBLE
                }
            }

            override fun onFailure(statusCode: Int, headers: Array<out Header>, responseString: String, throwable: Throwable) {
                runOnUiThread {
                    displayMessage("An error occurred requesting your ride")
                }
            }
        });
    }

    private fun displayMessage(message: String) {
        Toast.makeText(
                this,
                message,
                Toast.LENGTH_SHORT).show();
    }

    private fun updateDriverLocation(latitude: Double, longitude: Double) {
        val location = LatLng(latitude, longitude)

        if (driverMarker == null) {
            driverMarker = mMap.addMarker(MarkerOptions()
                    .title("Driver Location")
                    .position(location)
            )
        } else {
            driverMarker?.position = location
        }

        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 17.0f))
    }

    override fun onResume() {
        super.onResume()

        PushNotifications.setOnMessageReceivedListenerForVisibleActivity(this, object : PushNotificationReceivedListener {
            override fun onMessageReceived(remoteMessage: RemoteMessage) {
                Log.i("ReceiveMessage", "Received Notification: ${remoteMessage.data}")
                val action = remoteMessage.data["action"]

                runOnUiThread {
                    updateDriverLocation(remoteMessage.data["latitude"]!!.toDouble(), remoteMessage.data["longitude"]!!.toDouble())

                    if (action == "ACCEPT_JOB") {
                        displayMessage("Your ride request has been accepted. Your driver is on their way.")
                    } else if (action == "PICKUP") {
                        displayMessage("Your driver has arrived and is waiting for you.")
                    } else if (action == "DROPOFF") {
                        displayMessage("You are at your destination")
                        findViewById<Button>(R.id.request_ride).visibility = VISIBLE
                    }
                }
            }
        })
    }
}
