package com.example.trener.data.local

import android.content.Context

class HeartRateSettingsStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun getGlobalHeartRateThresholdBpm(): Int? {
        if (!preferences.contains(KEY_GLOBAL_THRESHOLD_BPM)) {
            return null
        }

        val threshold = preferences.getInt(KEY_GLOBAL_THRESHOLD_BPM, INVALID_THRESHOLD_BPM)
        return threshold.takeIf { it > 0 }
    }

    fun setGlobalHeartRateThresholdBpm(thresholdBpm: Int?) {
        preferences.edit()
            .apply {
                if (thresholdBpm == null) {
                    remove(KEY_GLOBAL_THRESHOLD_BPM)
                } else {
                    putInt(KEY_GLOBAL_THRESHOLD_BPM, thresholdBpm)
                }
            }
            .apply()
    }

    private companion object {
        private const val PREFERENCES_NAME = "heart_rate_settings"
        private const val KEY_GLOBAL_THRESHOLD_BPM = "global_heart_rate_threshold_bpm"
        private const val INVALID_THRESHOLD_BPM = -1
    }
}
