package com.example.trener.ble.heartrate

import android.content.Context

class HeartRateBleTargetStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getTargetDeviceMac(): String? {
        return preferences.getString(KEY_TARGET_DEVICE_MAC, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun saveTargetDeviceMac(macAddress: String) {
        preferences.edit()
            .putString(KEY_TARGET_DEVICE_MAC, macAddress.trim())
            .apply()
    }

    fun clearTargetDeviceMac() {
        preferences.edit()
            .remove(KEY_TARGET_DEVICE_MAC)
            .apply()
    }

    private companion object {
        private const val PREFERENCES_NAME = "heart_rate_ble_target_store"
        private const val KEY_TARGET_DEVICE_MAC = "target_device_mac"
    }
}
