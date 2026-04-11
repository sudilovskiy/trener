package com.example.trener

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object DatabaseRefreshCoordinator {
    private val refreshGenerationCounter = AtomicInteger(0)
    private val _refreshGeneration = MutableStateFlow(refreshGenerationCounter.get())

    val refreshGeneration: StateFlow<Int> = _refreshGeneration.asStateFlow()

    fun requestRefresh() {
        _refreshGeneration.value = refreshGenerationCounter.incrementAndGet()
    }
}
