package com.fogwalk

import com.fogwalk.geo.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoUtilsTest {

    @Test
    fun haversine_samePoint_isZero() {
        val d = GeoUtils.haversineMeters(48.2082, 16.3738, 48.2082, 16.3738)
        assertEquals(0.0, d, 0.0001)
    }

    @Test
    fun haversine_oneDegreeLongitudeAtEquator_isAboutOneEleventhOfThousandKm() {
        // One degree of longitude at the equator is ~111.19 km.
        val d = GeoUtils.haversineMeters(0.0, 0.0, 0.0, 1.0)
        assertEquals(111_195.0, d, 50.0)
    }

    @Test
    fun haversine_knownCityDistance_isApproximatelyCorrect() {
        // Vienna -> Bratislava is roughly 55 km.
        val vienna = doubleArrayOf(48.2082, 16.3738)
        val bratislava = doubleArrayOf(48.1486, 17.1077)
        val d = GeoUtils.haversineMeters(vienna[0], vienna[1], bratislava[0], bratislava[1])
        assertEquals(55_000.0, d, 3_000.0)
    }

    @Test
    fun metersPerPixel_atEquatorZoomZero_matchesStandardConstant() {
        val mpp = GeoUtils.metersPerPixel(0.0, 0.0)
        assertEquals(156_543.03, mpp, 1.0)
    }

    @Test
    fun metersPerPixel_decreasesAsZoomIncreases() {
        val low = GeoUtils.metersPerPixel(0.0, 5.0)
        val high = GeoUtils.metersPerPixel(0.0, 15.0)
        assertTrue("Higher zoom should mean fewer meters per pixel", high < low)
        // Each zoom level halves resolution: 10 levels => factor of 1024.
        assertEquals(low / 1024.0, high, high * 0.0001)
    }

    @Test
    fun metersPerPixel_shrinksTowardThePoles() {
        val equator = GeoUtils.metersPerPixel(0.0, 12.0)
        val north = GeoUtils.metersPerPixel(60.0, 12.0)
        assertTrue("Resolution near the pole is finer than at the equator", north < equator)
    }

    @Test
    fun metersToPixels_isInverseOfMetersPerPixel() {
        val zoom = 18.0
        val lat = 50.0
        val mpp = GeoUtils.metersPerPixel(lat, zoom)
        val pixels = GeoUtils.metersToPixels(mpp * 10.0, lat, zoom)
        assertEquals(10.0, pixels, 0.0001)
    }

    @Test
    fun shouldRecord_whenNoPreviousPoint_isTrue() {
        assertTrue(GeoUtils.shouldRecord(null, null, 10.0, 10.0, 15.0))
    }

    @Test
    fun shouldRecord_whenBarelyMoved_isFalse() {
        // ~1 meter apart, below the 15 m threshold.
        val moved = GeoUtils.shouldRecord(48.2082, 16.3738, 48.20821, 16.3738, 15.0)
        assertFalse(moved)
    }

    @Test
    fun shouldRecord_whenMovedFarEnough_isTrue() {
        // ~150 meters apart.
        val moved = GeoUtils.shouldRecord(48.2082, 16.3738, 48.2095, 16.3738, 15.0)
        assertTrue(moved)
    }
}
