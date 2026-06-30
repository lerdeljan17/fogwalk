package com.fogwalk.geo

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure geographic / projection helpers.
 *
 * Everything here is deliberately free of Android dependencies so it can be
 * unit tested on a normal JVM without Robolectric or an emulator.
 */
object GeoUtils {

    /** Mean Earth radius in meters (WGS84 sphere approximation). */
    const val EARTH_RADIUS_M: Double = 6_371_000.0

    /**
     * Number of ground meters represented by a single screen pixel at zoom 0
     * on the equator for a 256px Web Mercator tile. Standard OSM/Google value.
     */
    private const val EQUATOR_METERS_PER_PIXEL_Z0: Double = 156_543.03392804097

    /**
     * Great-circle distance between two lat/lng points in meters using the
     * haversine formula.
     */
    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)

        val a = sin(dLat / 2).pow(2) +
            cos(rLat1) * cos(rLat2) * sin(dLon / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return EARTH_RADIUS_M * c
    }

    /**
     * Ground resolution (meters per pixel) at a given latitude and Web Mercator
     * zoom level. Resolution shrinks as you zoom in and as you move toward the
     * poles.
     */
    fun metersPerPixel(latitude: Double, zoom: Double): Double {
        val latRad = Math.toRadians(latitude)
        return EQUATOR_METERS_PER_PIXEL_Z0 * cos(latRad) / 2.0.pow(zoom)
    }

    /**
     * Convert a reveal radius expressed in meters to a radius in screen pixels
     * at the current latitude and zoom.
     */
    fun metersToPixels(meters: Double, latitude: Double, zoom: Double): Double {
        val mpp = metersPerPixel(latitude, zoom)
        if (mpp <= 0.0) return 0.0
        return meters / mpp
    }

    /**
     * Decide whether a freshly received location is far enough from the last
     * recorded one to be worth persisting. Returns true when there is no
     * previous point, or when the new point is more than [minDistanceMeters]
     * away. This keeps the database compact when standing still or jittering.
     */
    fun shouldRecord(
        lastLat: Double?,
        lastLon: Double?,
        newLat: Double,
        newLon: Double,
        minDistanceMeters: Double,
    ): Boolean {
        if (lastLat == null || lastLon == null) return true
        return haversineMeters(lastLat, lastLon, newLat, newLon) >= minDistanceMeters
    }

    /**
     * Decide whether two consecutive visited points are close enough to be
     * bridged with a continuous trail segment. Points farther apart than
     * [maxGapMeters] are considered a gap (GPS dropout, or the app reopened
     * elsewhere) and should be left unconnected.
     */
    fun isWithinConnectDistance(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double,
        maxGapMeters: Double,
    ): Boolean {
        return haversineMeters(lat1, lon1, lat2, lon2) <= maxGapMeters
    }

    /** Euclidean distance between two screen pixels. */
    fun pixelDistance(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        return hypot(x2 - x1, y2 - y1)
    }

    /**
     * Returns true if the pixel ([px], [py]) falls inside the reveal radius of
     * any of the [centers]. This is the core test that decides whether a given
     * spot on screen should be cleared of fog.
     */
    fun isRevealed(
        px: Double,
        py: Double,
        centers: List<Pair<Double, Double>>,
        radiusPx: Double,
    ): Boolean {
        if (radiusPx <= 0.0) return false
        for ((cx, cy) in centers) {
            if (pixelDistance(px, py, cx, cy) <= radiusPx) return true
        }
        return false
    }
}
