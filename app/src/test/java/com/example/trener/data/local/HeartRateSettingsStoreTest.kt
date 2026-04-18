package com.example.trener.data.local

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNull
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class HeartRateSettingsStoreTest {
    private val context: android.content.Context
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun clearPreferences() {
        context.getSharedPreferences(PREFERENCES_NAME, android.content.Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun persistsThresholdAcrossStoreInstances() {
        val firstStore = HeartRateSettingsStore(context)
        firstStore.setGlobalHeartRateThresholdBpm(123)

        val reloadedStore = HeartRateSettingsStore(context)

        assertEquals(123, reloadedStore.getGlobalHeartRateThresholdBpm())
    }

    @Test
    fun supportsDisablingThreshold() {
        val store = HeartRateSettingsStore(context)
        store.setGlobalHeartRateThresholdBpm(140)
        store.setGlobalHeartRateThresholdBpm(null)

        assertNull(store.getGlobalHeartRateThresholdBpm())
    }

    private companion object {
        private const val PREFERENCES_NAME = "heart_rate_settings"
    }
}
