package com.fogwalk

import android.app.Application
import org.osmdroid.config.Configuration

class FogWalkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // osmdroid needs a user agent (OSM tile policy) and a writable cache
        // directory. Configure it before any MapView is created.
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = filesDir
            osmdroidTileCache = cacheDir
        }
    }
}
