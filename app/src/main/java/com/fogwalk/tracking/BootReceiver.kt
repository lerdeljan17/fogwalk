package com.fogwalk.tracking

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.fogwalk.data.Settings

/**
 * Restarts [LocationTrackingService] after a device reboot when the user has
 * opted into always-on tracking and location permission is still granted.
 *
 * Handles both [Intent.ACTION_BOOT_COMPLETED] and the direct-boot
 * [Intent.ACTION_LOCKED_BOOT_COMPLETED] so tracking can resume as early as the
 * platform allows.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            -> Unit
            else -> return
        }

        val settings = Settings(context)
        if (!settings.autoFollowEnabled) return
        if (!hasLocationPermission(context)) return

        LocationTrackingService.start(context)
    }

    private fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ) == PackageManager.PERMISSION_GRANTED
    }
}
