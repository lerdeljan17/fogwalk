package com.fogwalk

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.fogwalk.data.AppDatabase
import com.fogwalk.data.VisitedRepository
import com.fogwalk.databinding.ActivityMainBinding
import com.fogwalk.map.FogOverlay
import com.fogwalk.tracking.LocationTrackingService
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var map: MapView
    private lateinit var fogOverlay: FogOverlay
    private lateinit var locationOverlay: MyLocationNewOverlay
    private lateinit var repository: VisitedRepository

    private var isTracking = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            enableMyLocation()
            beginTracking()
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_LONG).show()
        }
    }

    private val pointAddedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val lat = intent?.getDoubleExtra(LocationTrackingService.EXTRA_LAT, Double.NaN) ?: return
            val lon = intent.getDoubleExtra(LocationTrackingService.EXTRA_LON, Double.NaN)
            if (!lat.isNaN() && !lon.isNaN()) {
                map.controller.animateTo(GeoPoint(lat, lon))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        repository = VisitedRepository(AppDatabase.get(this).visitedPointDao())

        setupMap()
        observeVisitedPoints()

        binding.trackButton.setOnClickListener { toggleTracking() }
        binding.recenterButton.setOnClickListener { recenter() }
    }

    private fun setupMap() {
        map = binding.map
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(DEFAULT_ZOOM)
        map.controller.setCenter(GeoPoint(DEFAULT_LAT, DEFAULT_LON))

        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        map.overlays.add(locationOverlay)

        fogOverlay = FogOverlay(map)
        map.overlays.add(fogOverlay)

        if (hasLocationPermission()) {
            enableMyLocation()
        }
    }

    private fun enableMyLocation() {
        locationOverlay.enableMyLocation()
        locationOverlay.runOnFirstFix {
            val location = locationOverlay.myLocation ?: return@runOnFirstFix
            runOnUiThread {
                map.controller.setZoom(MY_LOCATION_ZOOM)
                map.controller.animateTo(location)
            }
        }
    }

    private fun observeVisitedPoints() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repository.observeAll().collect { points ->
                    fogOverlay.setPoints(points.map { GeoPoint(it.latitude, it.longitude) })
                    binding.statusText.text = getString(R.string.stat_revealed, points.size)
                }
            }
        }
    }

    private fun toggleTracking() {
        if (isTracking) {
            stopTracking()
        } else {
            if (hasLocationPermission()) {
                beginTracking()
            } else {
                requestPermissions()
            }
        }
    }

    private fun beginTracking() {
        LocationTrackingService.start(this)
        isTracking = true
        binding.trackButton.setText(R.string.action_stop)
        binding.trackButton.setIconResource(android.R.drawable.ic_media_pause)
    }

    private fun stopTracking() {
        LocationTrackingService.stop(this)
        isTracking = false
        binding.trackButton.setText(R.string.action_start)
        binding.trackButton.setIconResource(android.R.drawable.ic_media_play)
    }

    private fun recenter() {
        val location = locationOverlay.myLocation
        if (location != null) {
            map.controller.setZoom(MY_LOCATION_ZOOM)
            map.controller.animateTo(location)
        } else if (hasLocationPermission()) {
            enableMyLocation()
        } else {
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        permissionLauncher.launch(permissions.toTypedArray())
    }

    private fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        val filter = IntentFilter(LocationTrackingService.ACTION_POINT_ADDED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pointAddedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pointAddedReceiver, filter)
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        runCatching { unregisterReceiver(pointAddedReceiver) }
    }

    companion object {
        private const val DEFAULT_ZOOM = 5.0
        private const val MY_LOCATION_ZOOM = 18.0
        private const val DEFAULT_LAT = 20.0
        private const val DEFAULT_LON = 0.0
    }
}
