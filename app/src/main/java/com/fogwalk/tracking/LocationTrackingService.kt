package com.fogwalk.tracking

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.fogwalk.MainActivity
import com.fogwalk.R
import com.fogwalk.data.AppDatabase
import com.fogwalk.data.Settings
import com.fogwalk.data.VisitedRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that listens for location updates via the framework
 * [LocationManager] and persists points the user walks through. Running as a
 * foreground service lets tracking continue while the app is backgrounded or
 * the screen is off.
 */
class LocationTrackingService : Service(), LocationListener {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var repository: VisitedRepository
    private lateinit var locationManager: LocationManager
    private lateinit var settings: Settings

    override fun onCreate() {
        super.onCreate()
        repository = VisitedRepository(AppDatabase.get(this).visitedPointDao())
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        settings = Settings(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopTracking()
                return START_NOT_STICKY
            }
            else -> startTracking()
        }
        return START_STICKY
    }

    private fun startTracking() {
        createChannel()
        startForegroundCompat()

        if (!hasLocationPermission()) {
            stopTracking()
            return
        }

        settings.trackingActive = true

        try {
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (locationManager.isProviderEnabled(provider)) {
                    locationManager.requestLocationUpdates(
                        provider,
                        MIN_TIME_MS,
                        MIN_DISTANCE_M,
                        this,
                    )
                }
            }
        } catch (_: SecurityException) {
            stopTracking()
        }
    }

    private fun stopTracking() {
        settings.trackingActive = false
        try {
            locationManager.removeUpdates(this)
        } catch (_: SecurityException) {
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onLocationChanged(location: Location) {
        scope.launch {
            val stored = repository.recordIfMoved(location.latitude, location.longitude)
            if (stored != null) {
                val broadcast = Intent(ACTION_POINT_ADDED).apply {
                    setPackage(packageName)
                    putExtra(EXTRA_LAT, stored.latitude)
                    putExtra(EXTRA_LON, stored.longitude)
                }
                sendBroadcast(broadcast)
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onProviderEnabled(provider: String) {
    }

    @Deprecated("Deprecated in Java")
    override fun onProviderDisabled(provider: String) {
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        try {
            locationManager.removeUpdates(this)
        } catch (_: SecurityException) {
        }
        super.onDestroy()
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

    private fun startForegroundCompat() {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
            )
        } else {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, 0)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.notification_channel_description)
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val ACTION_START = "com.fogwalk.action.START"
        const val ACTION_STOP = "com.fogwalk.action.STOP"
        const val ACTION_POINT_ADDED = "com.fogwalk.action.POINT_ADDED"
        const val EXTRA_LAT = "extra_lat"
        const val EXTRA_LON = "extra_lon"

        private const val CHANNEL_ID = "fogwalk_tracking"
        private const val NOTIFICATION_ID = 1001
        private const val MIN_TIME_MS = 1_000L
        private const val MIN_DISTANCE_M = 3f

        fun start(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_START
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocationTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
