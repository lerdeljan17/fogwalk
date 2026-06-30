package com.fogwalk

import com.fogwalk.geo.GeoUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FogRevealTest {

    private val centers = listOf(
        100.0 to 100.0,
        300.0 to 400.0,
    )

    @Test
    fun pixelDistance_isEuclidean() {
        assertEquals(5.0, GeoUtils.pixelDistance(0.0, 0.0, 3.0, 4.0), 0.0001)
    }

    @Test
    fun isRevealed_pixelAtCenter_isRevealed() {
        assertTrue(GeoUtils.isRevealed(100.0, 100.0, centers, 50.0))
    }

    @Test
    fun isRevealed_pixelInsideRadius_isRevealed() {
        // 30px away from the first center, radius 50 -> inside.
        assertTrue(GeoUtils.isRevealed(130.0, 100.0, centers, 50.0))
    }

    @Test
    fun isRevealed_pixelJustOutsideRadius_isNotRevealed() {
        // ~60px away from the nearest center, radius 50 -> outside.
        assertFalse(GeoUtils.isRevealed(160.0, 100.0, centers, 50.0))
    }

    @Test
    fun isRevealed_withSecondCenter_isRevealed() {
        assertTrue(GeoUtils.isRevealed(310.0, 410.0, centers, 50.0))
    }

    @Test
    fun isRevealed_withNoCenters_isNotRevealed() {
        assertFalse(GeoUtils.isRevealed(100.0, 100.0, emptyList(), 50.0))
    }

    @Test
    fun isRevealed_withZeroRadius_isNotRevealed() {
        assertFalse(GeoUtils.isRevealed(100.0, 100.0, centers, 0.0))
    }

    @Test
    fun isWithinConnectDistance_closePoints_areConnected() {
        // ~11 m apart, well under a 40 m max gap -> bridge the segment.
        assertTrue(
            GeoUtils.isWithinConnectDistance(48.2082, 16.3738, 48.20830, 16.3738, 40.0),
        )
    }

    @Test
    fun isWithinConnectDistance_distantPoints_areNotConnected() {
        // ~150 m apart, above a 40 m max gap -> treat as a gap.
        assertFalse(
            GeoUtils.isWithinConnectDistance(48.2082, 16.3738, 48.2095, 16.3738, 40.0),
        )
    }
}
