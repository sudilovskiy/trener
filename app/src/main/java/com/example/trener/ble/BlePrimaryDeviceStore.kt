package com.example.trener.ble

import android.content.Context

class BlePrimaryDeviceStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun getPrimaryDeviceMac(): String? {
        return preferences.getString(KEY_PRIMARY_DEVICE_MAC, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    fun savePrimaryDeviceMac(macAddress: String) {
        preferences.edit()
            .putString(KEY_PRIMARY_DEVICE_MAC, macAddress.trim())
            .apply()
    }

    private companion object {
        private const val PREFERENCES_NAME = "ble_primary_device_store"
        private const val KEY_PRIMARY_DEVICE_MAC = "primary_device_mac"
    }
}
