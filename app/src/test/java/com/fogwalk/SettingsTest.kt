package com.fogwalk

import androidx.test.core.app.ApplicationProvider
import com.fogwalk.data.Settings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SettingsTest {

    private lateinit var settings: Settings

    @Before
    fun setUp() {
        settings = Settings(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun autoFollow_defaultsToFalse() {
        assertFalse(settings.autoFollowEnabled)
    }

    @Test
    fun autoFollow_persistsValue() {
        settings.autoFollowEnabled = true
        assertTrue(settings.autoFollowEnabled)

        // A fresh instance reads back the persisted value.
        val reopened = Settings(ApplicationProvider.getApplicationContext())
        assertTrue(reopened.autoFollowEnabled)
    }

    @Test
    fun trackingActive_defaultsToFalse() {
        assertFalse(settings.trackingActive)
    }

    @Test
    fun trackingActive_canBeSetAndCleared() {
        settings.trackingActive = true
        assertTrue(settings.trackingActive)

        settings.trackingActive = false
        assertFalse(settings.trackingActive)
    }
}
