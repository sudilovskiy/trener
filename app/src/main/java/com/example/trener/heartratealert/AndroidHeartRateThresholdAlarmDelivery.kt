package com.example.trener.heartratealert

import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log

class AndroidHeartRateThresholdAlarmDelivery(
    private val context: Context
) : HeartRateThresholdAlarmDelivery {
    override fun alert(heartRateBpm: Int, thresholdBpm: Int) {
        playSound(heartRateBpm = heartRateBpm, thresholdBpm = thresholdBpm)
        vibrate(heartRateBpm = heartRateBpm, thresholdBpm = thresholdBpm)
    }

    private fun playSound(heartRateBpm: Int, thresholdBpm: Int) {
        val alarmUri = RingtoneManager.getActualDefaultRingtoneUri(
            context,
            RingtoneManager.TYPE_ALARM
        ) ?: RingtoneManager.getActualDefaultRingtoneUri(
            context,
            RingtoneManager.TYPE_NOTIFICATION
        )

        if (alarmUri == null) {
            Log.w(
                TAG,
                "Skipping heart-rate alarm sound; no default alarm or notification ringtone is available " +
                    "(heartRateBpm=$heartRateBpm thresholdBpm=$thresholdBpm)"
            )
            return
        }

        val ringtone = RingtoneManager.getRingtone(context, alarmUri)
        if (ringtone == null) {
            Log.w(
                TAG,
                "Skipping heart-rate alarm sound; ringtone could not be loaded " +
                    "(heartRateBpm=$heartRateBpm thresholdBpm=$thresholdBpm)"
            )
            return
        }

        runCatching {
            ringtone.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }.onFailure {
            Log.w(TAG, "Unable to set alarm audio attributes on the ringtone", it)
        }

        runCatching {
            ringtone.play()
        }.onFailure {
            Log.w(
                TAG,
                "Failed to play heart-rate alarm sound (heartRateBpm=$heartRateBpm thresholdBpm=$thresholdBpm)",
                it
            )
        }
    }

    private fun vibrate(heartRateBpm: Int, thresholdBpm: Int) {
        val vibrator = context.getSystemService(Vibrator::class.java)
        if (vibrator == null || !vibrator.hasVibrator()) {
            Log.w(
                TAG,
                "Skipping heart-rate alarm vibration; no vibrator is available " +
                    "(heartRateBpm=$heartRateBpm thresholdBpm=$thresholdBpm)"
            )
            return
        }

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(
                        ALARM_VIBRATION_DURATION_MILLIS,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(ALARM_VIBRATION_DURATION_MILLIS)
            }
        }.onFailure {
            Log.w(
                TAG,
                "Failed to vibrate for heart-rate alarm (heartRateBpm=$heartRateBpm thresholdBpm=$thresholdBpm)",
                it
            )
        }
    }

    private companion object {
        private const val TAG = "HrThresholdAlarm"
        private const val ALARM_VIBRATION_DURATION_MILLIS = 700L
    }
}
