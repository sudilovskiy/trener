package com.example.trener

import android.app.Application
import com.example.trener.ble.heartrate.HeartRateBleService
import com.example.trener.data.local.HeartRateSettingsStore
import com.example.trener.data.local.TrenerDatabaseProvider
import com.example.trener.data.local.WorkoutHeartRateRepository
import com.example.trener.heartratealert.AlertingHeartRateThresholdAlertSink
import com.example.trener.heartratealert.AndroidHeartRateThresholdAlarmDelivery
import com.example.trener.domain.heartrate.HeartRateRuntimeCoordinator
import com.example.trener.domain.heartrate.WorkoutHeartRateRepositoryAppender

class TrenerApplication : Application() {
    val heartRateSettingsStore: HeartRateSettingsStore by lazy {
        HeartRateSettingsStore(this)
    }

    val heartRateRuntimeCoordinator: HeartRateRuntimeCoordinator by lazy {
        HeartRateRuntimeCoordinator(
            sampleAppender = WorkoutHeartRateRepositoryAppender(
                WorkoutHeartRateRepository(
                    TrenerDatabaseProvider.getInstance(this)
                )
            ),
            thresholdAlertSink = AlertingHeartRateThresholdAlertSink(
                AndroidHeartRateThresholdAlarmDelivery(this)
            )
        )
    }

    val heartRateBleService: HeartRateBleService by lazy {
        HeartRateBleService(
            context = this,
            coordinator = heartRateRuntimeCoordinator
        )
    }

    override fun onCreate() {
        super.onCreate()
        heartRateRuntimeCoordinator.configureThreshold(
            heartRateSettingsStore.getGlobalHeartRateThresholdBpm()
        )
        heartRateBleService.start()
    }
}
