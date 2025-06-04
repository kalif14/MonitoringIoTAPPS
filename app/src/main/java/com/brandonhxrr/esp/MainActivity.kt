// ... (imports unchanged)
package com.brandonhxrr.esp

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.material.textfield.TextInputEditText
import okhttp3.*
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    // UI Elements
    private lateinit var tvLastUpdate: TextView
    private lateinit var tvUserInfo: TextView
    private lateinit var tvDepth: TextView
    private lateinit var tvVolume: TextView
    private lateinit var tvPercentage: TextView
    private lateinit var tvConnectionStatus: TextView
    private lateinit var txtApiUrl: TextInputEditText
    private lateinit var btnConnect: Button
    private lateinit var tvLocation: TextView
    private lateinit var mapView: MapView
    private lateinit var btnMyLocation: Button
    private lateinit var btnMySensor: Button
    private lateinit var recentUrlsLayout: LinearLayout

    // GPS
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private var userMarker: Marker? = null
    private var lastKnownLocation: Location? = null

    // ESP32 marker and last known ESP32 position
    private var espMarker: Marker? = null
    private var lastEspLat: Double? = null
    private var lastEspLon: Double? = null

    private val channelId = "2958867"
    private val thingSpeakUrl = "https://api.thingspeak.com/channels/$channelId/feeds.json?results=1"

    // Recent URLs shared prefs
    private val RECENT_URLS_PREF = "recent_urls"
    private val RECENT_URLS_KEY = "recent_urls_list"
    private val maxRecentUrls = 3

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // osmdroid config must be loaded before setContentView
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.activity_main)

        // Initialize views
        initializeViews()
        setupClickListeners()

        // Map setup
        mapView.setTileSource(TileSourceFactory.MAPNIK)
        mapView.setBuiltInZoomControls(true)
        mapView.setMultiTouchControls(true)

        // GPS
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        setupLocationUpdates()

        updateTimestamp()
        tvUserInfo.text = "User: kalif14"
    }

    private fun initializeViews() {
        try {
            tvLastUpdate = findViewById(R.id.tvLastUpdate)
            tvUserInfo = findViewById(R.id.tvUserInfo)
            tvDepth = findViewById(R.id.tvTemperature)
            tvVolume = findViewById(R.id.tvVoltage)
            tvPercentage = findViewById(R.id.tvRPM)
            tvConnectionStatus = findViewById(R.id.tvCapacitance)
            txtApiUrl = findViewById(R.id.txt_ip)
            btnConnect = findViewById(R.id.btn_connect)
            tvLocation = findViewById(R.id.tvLocation)
            mapView = findViewById(R.id.map)
            btnMyLocation = findViewById(R.id.btnMyLocation)
            btnMySensor = findViewById(R.id.btnMySensor)
            recentUrlsLayout = findViewById(R.id.layoutRecentUrls)

            tvDepth.text = "0.0 cm"
            tvVolume.text = "0.0 L"
            tvPercentage.text = "0.0%"
            tvConnectionStatus.text = "Disconnected"
            tvLocation.text = "Location: -"

            displayRecentUrls()
        } catch (e: Exception) {
            showToast("Error initializing views: ${e.message}")
        }
    }

    private fun isValidUrl(url: String): Boolean {
        return try {
            val uri = android.net.Uri.parse(url)
            (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    private fun setupClickListeners() {
        btnConnect.setOnClickListener {
            val apiUrl = txtApiUrl.text.toString().trim().ifEmpty { thingSpeakUrl }
            if (!isValidUrl(apiUrl)) {
                showConnectionFailedDialog()
                return@setOnClickListener
            }
            fetchThingSpeakData(apiUrl)
        }

        btnMyLocation.setOnClickListener {
            lastKnownLocation?.let { location ->
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                mapView.controller.setZoom(20.0)
                mapView.controller.animateTo(geoPoint)
            } ?: showToast("Location not available yet.")
        }

        btnMySensor.setOnClickListener {
            if (lastEspLat != null && lastEspLon != null) {
                val geoPoint = GeoPoint(lastEspLat!!, lastEspLon!!)
                mapView.controller.setZoom(20.0)
                mapView.controller.animateTo(geoPoint)
            } else {
                showToast("Sensor location not available yet.")
            }
        }
    }

    // ------ Recent URLs logic ------
    private fun getRecentUrls(): MutableList<String> {
        val prefs = getSharedPreferences(RECENT_URLS_PREF, MODE_PRIVATE)
        val urls = prefs.getStringSet(RECENT_URLS_KEY, null)
        return urls?.toMutableList() ?: mutableListOf()
    }

    private fun saveRecentUrl(url: String) {
        val prefs = getSharedPreferences(RECENT_URLS_PREF, MODE_PRIVATE)
        val urls = getRecentUrls()
        urls.remove(url) // Remove if already exists
        urls.add(0, url) // Add to top
        if (urls.size > maxRecentUrls) {
            urls.removeAt(urls.size - 1)
        }
        prefs.edit().putStringSet(RECENT_URLS_KEY, urls.toSet()).apply()
        displayRecentUrls()
    }

    private fun displayRecentUrls() {
        recentUrlsLayout.removeAllViews()
        val urls = getRecentUrls()
        if (urls.isEmpty()) {
            recentUrlsLayout.visibility = View.GONE
            return
        }
        recentUrlsLayout.visibility = View.VISIBLE
        for (url in urls) {
            val tv = TextView(this)
            tv.text = url
            tv.textSize = 14f
            tv.setTextColor(android.graphics.Color.parseColor("#3700B3")) // Use purple_700 hex directly
            tv.setPadding(0, 8, 0, 8)
            tv.setOnClickListener {
                txtApiUrl.setText(url)
                txtApiUrl.setSelection(url.length)
            }
            tv.paint.isUnderlineText = true
            tv.isClickable = true
            recentUrlsLayout.addView(tv)
        }
    }
    // ------ End Recent URLs logic ------

    // ===================== GPS and MAP ===========================
    private fun setupLocationUpdates() {
        locationRequest = LocationRequest.create().apply {
            interval = 3000
            fastestInterval = 1500
            smallestDisplacement = 1f
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location: Location? = locationResult.lastLocation
                if (location != null) {
                    runOnUiThread {
                        tvLocation.text = "Location: %.5f, %.5f".format(location.latitude, location.longitude)
                        lastKnownLocation = location
                        val geoPoint = GeoPoint(location.latitude, location.longitude)
                        showUserMarker(geoPoint)
                    }
                }
            }
        }
        requestLocationPermission()
    }

    private fun showUserMarker(geoPoint: GeoPoint) {
        if (userMarker == null) {
            userMarker = Marker(mapView)
            userMarker?.title = "You are here"
            userMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            mapView.overlays.add(userMarker)
        }
        userMarker?.position = geoPoint
        mapView.invalidate()
    }

    private fun showEsp32Marker(lat: Double, lon: Double) {
        lastEspLat = lat
        lastEspLon = lon
        val geoPoint = GeoPoint(lat, lon)
        if (espMarker == null) {
            espMarker = Marker(mapView)
            espMarker?.title = "ESP32 Device"
            espMarker?.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            mapView.overlays.add(espMarker)
        }
        espMarker?.position = geoPoint
        mapView.invalidate()
    }

    private fun requestLocationPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
        } else {
            startLocationUpdates()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        } else {
            tvLocation.text = "Location: Permission Denied"
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        }
    }

    override fun onPause() {
        super.onPause()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        mapView.onPause()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }
    // ================== END GPS and MAP ===========================

    private fun fetchThingSpeakData(url: String) {
        updateConnectionStatus("Connecting...")
        val client = OkHttpClient()
        val request = Request.Builder().url(url).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    updateConnectionStatus("Error")
                    showConnectionFailedDialog()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val body = it.body?.string() ?: ""
                    val isJson = it.header("Content-Type")?.contains("application/json") == true
                    if (!it.isSuccessful || body.isEmpty() || !isJson || !body.contains("feeds")) {
                        runOnUiThread {
                            updateConnectionStatus("Error")
                            showConnectionFailedDialog()
                        }
                        return
                    }
                    try {
                        parseAndDisplayThingSpeak(body)
                    } catch (e: Exception) {
                        runOnUiThread {
                            updateConnectionStatus("Error")
                            showConnectionFailedDialog()
                        }
                    }
                }
            }
        })
    }

    private fun showConnectionFailedDialog() {
        runOnUiThread {
            AlertDialog.Builder(this)
                .setTitle("Connection Failed")
                .setMessage("Failed to connect to ThingSpeak. Please check your channel URL or network connection.")
                .setPositiveButton("OK", null)
                .show()
        }
    }

    private fun parseAndDisplayThingSpeak(json: String) {
        try {
            val jsonObject = JSONObject(json)
            val feeds = jsonObject.getJSONArray("feeds")
            val channel = jsonObject.getJSONObject("channel")

            // ESP32 location from channel metadata
            val espLat = channel.optString("latitude", null)?.toDoubleOrNull()
            val espLon = channel.optString("longitude", null)?.toDoubleOrNull()
            if (espLat != null && espLon != null) {
                runOnUiThread { showEsp32Marker(espLat, espLon) }
            }

            if (feeds.length() > 0) {
                val latestFeed = feeds.getJSONObject(0)
                val depth = latestFeed.optString("field1", "0.0").toFloatOrNull() ?: 0.0f
                val volume = latestFeed.optString("field2", "0.0").toFloatOrNull() ?: 0.0f
                val percentage = latestFeed.optString("field3", "0.0").toFloatOrNull() ?: 0.0f

                runOnUiThread {
                    updateValues(depth, volume, percentage)
                    updateConnectionStatus("Connected")
                    updateTimestamp()
                    // Save as recent url on success
                    val urlText = txtApiUrl.text.toString().trim().ifEmpty { thingSpeakUrl }
                    saveRecentUrl(urlText)
                }
            } else {
                runOnUiThread {
                    updateConnectionStatus("No Data")
                    showToast("No feeds found in ThingSpeak channel.")
                }
            }
        } catch (e: Exception) {
            runOnUiThread {
                updateConnectionStatus("Error")
                showConnectionFailedDialog()
            }
        }
    }

    private fun updateValues(depth: Float, volume: Float, percentage: Float) {
        tvDepth.text = String.format("%.1f cm", depth)
        tvVolume.text = String.format("%.1f L", volume)
        tvPercentage.text = String.format("%.1f%%", percentage)
    }

    private fun updateConnectionStatus(status: String) {
        runOnUiThread {
            tvConnectionStatus.text = status
        }
    }

    private fun updateTimestamp() {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(Date())
        tvLastUpdate.text = "Last Update: $timestamp UTC"
    }

    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}