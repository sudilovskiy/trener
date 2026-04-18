package com.example.trener.ble.heartrate

import android.content.Context
import com.example.trener.domain.heartrate.HeartRateRuntimeCoordinator
import kotlinx.coroutines.flow.StateFlow

class HeartRateBleService(
    context: Context,
    private val coordinator: HeartRateRuntimeCoordinator
) {
    private val targetStore = HeartRateBleTargetStore(context)
    private val source = HeartRateBleSource(
        context = context,
        coordinator = coordinator,
        targetStore = targetStore
    )

    val state: StateFlow<HeartRateBleSourceState> = source.state

    fun start() {
        source.start()
    }

    fun stop() {
        source.stop()
    }

    fun reconnectNow() {
        source.reconnectNow()
    }

    fun selectTargetDevice(macAddress: String) {
        source.selectTargetDevice(macAddress)
        source.reconnectNow()
    }
}
