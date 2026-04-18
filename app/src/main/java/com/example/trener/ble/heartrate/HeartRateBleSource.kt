package com.example.trener.ble.heartrate

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.trener.R
import com.example.trener.logTag
import com.example.trener.domain.heartrate.HeartRateDiagnosticFinding
import com.example.trener.domain.heartrate.HeartRateDiagnosticSeverity
import com.example.trener.domain.heartrate.HeartRateDiagnosticsState
import com.example.trener.domain.heartrate.HeartRateConnectionStatus
import com.example.trener.domain.heartrate.HeartRateRuntimeCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class HeartRateBleSource(
    context: Context,
    private val coordinator: HeartRateRuntimeCoordinator,
    private val targetStore: HeartRateBleTargetStore,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {
    private val appContext = context.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val connectionLock = Any()
    private val _state = MutableStateFlow(HeartRateBleSourceState())
    val state: StateFlow<HeartRateBleSourceState> = _state.asStateFlow()

    @Volatile
    private var running = false

    @Volatile
    private var activeGatt: BluetoothGatt? = null

    @Volatile
    private var supportsContinuousNotifications = false

    @Volatile
    private var supportsPolling = false

    private var reconnectJob: Job? = null
    private var pollJob: Job? = null
    private var firstSampleWatchdogJob: Job? = null
    private var reconnectAttempt = 0
    private var currentTargetAddress: String? = targetStore.getTargetDeviceMac()
    private var autoReconnectEnabled = false
    private var everReachedUsableHeartRateChannel = false
    private var waitingForFirstHeartRateSample = false

    fun start() {
        if (running) {
            return
        }

        running = true
        autoReconnectEnabled = true
        publishDiagnostics()
        coordinator.markNoData()
        updateState(
            connectionStatus = HeartRateConnectionStatus.NoData,
            targetDeviceAddress = currentTargetAddress,
            retryDelayMillis = null,
            lastErrorMessage = null
        )
        scope.launch {
            connectToResolvedTarget()
        }
    }

    fun stop() {
        running = false
        autoReconnectEnabled = false
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null
        cancelPolling()
        cancelFirstSampleWatchdog()
        closeGatt()
        everReachedUsableHeartRateChannel = false
        waitingForFirstHeartRateSample = false
        coordinator.markNoData()
        updateState(
            connectionStatus = HeartRateConnectionStatus.NoData,
            retryDelayMillis = null
        )
    }

    fun reconnectNow() {
        autoReconnectEnabled = true
        if (!running) {
            running = true
        }

        publishDiagnostics()
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        scope.launch {
            connectToResolvedTarget()
        }
    }

    fun selectTargetDevice(macAddress: String) {
        val normalizedAddress = macAddress.trim()
        if (normalizedAddress.isEmpty()) {
            return
        }

        currentTargetAddress = normalizedAddress
        targetStore.saveTargetDeviceMac(normalizedAddress)
        publishDiagnostics(connectionNote = "Selected device $normalizedAddress.")
        updateState(
            targetDeviceAddress = normalizedAddress,
            lastErrorMessage = null,
            retryDelayMillis = null
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun connectToResolvedTarget() {
        if (!running) {
            return
        }

        publishDiagnostics()
        val adapter = bluetoothAdapter ?: run {
            markNoData("Bluetooth adapter unavailable.")
            return
        }

        if (!adapter.isEnabled) {
            markNoData("Bluetooth is disabled.")
            return
        }

        val targetDevice = resolveTargetDevice(adapter) ?: run {
            markNoData("No paired heart-rate target was found.")
            return
        }

        currentTargetAddress = targetDevice.address
        everReachedUsableHeartRateChannel = false
        updateState(
            connectionStatus = HeartRateConnectionStatus.Reconnecting,
            targetDeviceAddress = targetDevice.address,
            retryDelayMillis = null,
            lastErrorMessage = null
        )
        coordinator.markReconnecting()
        closeGatt()

        val gatt = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                targetDevice.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } else {
                targetDevice.connectGatt(appContext, false, gattCallback)
            }
        } catch (securityException: SecurityException) {
            Log.w(logTag, "Heart-rate GATT connect denied by permissions.", securityException)
            markNoData("Missing Bluetooth permission.")
            return
        } catch (throwable: Throwable) {
            Log.e(logTag, "Heart-rate GATT connect failed.", throwable)
            scheduleReconnect("Connection attempt failed.")
            return
        }

        synchronized(connectionLock) {
            activeGatt = gatt
            supportsContinuousNotifications = false
            supportsPolling = false
        }
    }

    @SuppressLint("MissingPermission")
    private fun resolveTargetDevice(adapter: BluetoothAdapter): BluetoothDevice? {
        val bondedDevices = try {
            adapter.bondedDevices.orEmpty()
        } catch (securityException: SecurityException) {
            Log.w(logTag, "Unable to inspect bonded Bluetooth devices.", securityException)
            return null
        }

        val savedAddress = currentTargetAddress ?: targetStore.getTargetDeviceMac()
        if (!savedAddress.isNullOrBlank()) {
            bondedDevices.firstOrNull { it.address.equals(savedAddress, ignoreCase = true) }?.let {
                return it
            }

            runCatching { adapter.getRemoteDevice(savedAddress) }
                .getOrNull()
                ?.let { return it }
        }

        return bondedDevices.firstOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun closeGatt() {
        val gatt = synchronized(connectionLock) {
            val current = activeGatt
            activeGatt = null
            current
        } ?: return

        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
    }

    private fun cancelPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun cancelFirstSampleWatchdog() {
        firstSampleWatchdogJob?.cancel()
        firstSampleWatchdogJob = null
    }

    private fun markNoData(message: String) {
        Log.d(logTag, "Heart-rate source idle: $message")
        autoReconnectEnabled = false
        reconnectAttempt = 0
        reconnectJob?.cancel()
        reconnectJob = null
        cancelPolling()
        cancelFirstSampleWatchdog()
        closeGatt()
        coordinator.markNoData()
        publishDiagnostics(connectionNote = message)
        updateState(
            connectionStatus = HeartRateConnectionStatus.NoData,
            retryDelayMillis = null,
            lastErrorMessage = message
        )
    }

    private fun scheduleReconnect(message: String) {
        if (!running || !autoReconnectEnabled) {
            markNoData(message)
            return
        }

        val delayMillis = HeartRateReconnectBackoff.delayForAttempt(reconnectAttempt++)
        updateState(
            connectionStatus = HeartRateConnectionStatus.Reconnecting,
            retryDelayMillis = delayMillis,
            lastErrorMessage = message
        )
        coordinator.markReconnecting()
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            delay(delayMillis)
            connectToResolvedTarget()
        }
    }

    private fun updateState(
        connectionStatus: HeartRateConnectionStatus? = null,
        targetDeviceAddress: String? = null,
        currentHeartRateBpm: Int? = null,
        lastSampleEpochMillis: Long? = null,
        retryDelayMillis: Long? = null,
        lastErrorMessage: String? = null
    ) {
        _state.update { current ->
            current.copy(
                connectionStatus = connectionStatus ?: current.connectionStatus,
                targetDeviceAddress = targetDeviceAddress ?: current.targetDeviceAddress,
                currentHeartRateBpm = currentHeartRateBpm ?: current.currentHeartRateBpm,
                lastSampleEpochMillis = lastSampleEpochMillis ?: current.lastSampleEpochMillis,
                retryDelayMillis = retryDelayMillis,
                lastErrorMessage = lastErrorMessage
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToHeartRateCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_UUID)
            ?: return false

        runCatching {
            gatt.setCharacteristicNotification(characteristic, true)
        }

        descriptor.value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }

        return runCatching {
            gatt.writeDescriptor(descriptor)
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    private fun startPollingHeartRate(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive && activeGatt === gatt) {
                val readStarted = runCatching { gatt.readCharacteristic(characteristic) }
                    .getOrDefault(false)
                if (!readStarted) {
                    scheduleReconnect("Heart-rate read could not be started.")
                    return@launch
                }
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (gatt !== activeGatt) {
            return
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            scheduleReconnect("Heart-rate service discovery failed with status $status.")
            return
        }

        val heartRateCharacteristic = gatt.services
            .firstOrNull { it.uuid == HEART_RATE_SERVICE_UUID }
            ?.characteristics
            ?.firstOrNull { characteristic ->
            characteristic.uuid == HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID &&
                (
                    (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                        (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0 ||
                        (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ) != 0
                    )
        }

        if (heartRateCharacteristic == null) {
            publishDiagnostics(
                connectionNote = "No standard heart-rate service or characteristic was found."
            )
            updateState(
                connectionStatus = HeartRateConnectionStatus.NoData,
                lastErrorMessage = "No usable standard heart-rate characteristic was found."
            )
            coordinator.markNoData()
            autoReconnectEnabled = false
            closeGatt()
            return
        }

        currentTargetAddress = gatt.device.address
        targetStore.saveTargetDeviceMac(gatt.device.address)
        reconnectAttempt = 0
        everReachedUsableHeartRateChannel = true
        startFirstSampleWatchdog(gatt)
        publishDiagnostics(
            connectionNote = "Standard heart-rate service detected and ready."
        )
        updateState(
            connectionStatus = HeartRateConnectionStatus.Connected,
            targetDeviceAddress = gatt.device.address,
            retryDelayMillis = null,
            lastErrorMessage = null
        )
        coordinator.markConnected()

        val supportsNotifications =
            heartRateCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0 ||
                heartRateCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
        val supportsReads =
            heartRateCharacteristic.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0

        synchronized(connectionLock) {
            supportsContinuousNotifications = supportsNotifications
            supportsPolling = supportsReads && !supportsNotifications
        }

        if (supportsNotifications) {
            val subscribed = subscribeToHeartRateCharacteristic(gatt, heartRateCharacteristic)
            if (!subscribed) {
                if (supportsReads) {
                    startPollingHeartRate(gatt, heartRateCharacteristic)
                    return
                }
                scheduleReconnect("Unable to enable heart-rate notifications.")
                return
            }
            if (supportsReads) {
                runCatching { gatt.readCharacteristic(heartRateCharacteristic) }
            }
        } else if (supportsReads) {
            startPollingHeartRate(gatt, heartRateCharacteristic)
        } else {
            updateState(
                connectionStatus = HeartRateConnectionStatus.NoData,
                lastErrorMessage = "Heart-rate characteristic exposes no usable transport."
            )
            coordinator.markNoData()
            autoReconnectEnabled = false
            closeGatt()
        }
    }

    private fun handleHeartRateMeasurement(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray
    ) {
        if (gatt !== activeGatt || characteristic.uuid != HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID) {
            return
        }

        everReachedUsableHeartRateChannel = true
        val parsedMeasurement = HeartRateMeasurementParser.parse(payload) ?: return
        reconnectAttempt = 0
        val timestampEpochMillis = System.currentTimeMillis()
        waitingForFirstHeartRateSample = false
        cancelFirstSampleWatchdog()
        publishDiagnostics(
            connectionNote = "Heart-rate data is flowing from the band."
        )

        updateState(
            connectionStatus = HeartRateConnectionStatus.Connected,
            targetDeviceAddress = gatt.device.address,
            currentHeartRateBpm = parsedMeasurement.heartRateBpm,
            lastSampleEpochMillis = timestampEpochMillis,
            retryDelayMillis = null,
            lastErrorMessage = null
        )

        scope.launch {
            coordinator.captureHeartRateSample(
                heartRateBpm = parsedMeasurement.heartRateBpm,
                timestampEpochMillis = timestampEpochMillis
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDisconnect(gatt: BluetoothGatt, status: Int) {
        if (gatt !== activeGatt) {
            return
        }

        closeGatt()
        cancelPolling()
        cancelFirstSampleWatchdog()
        waitingForFirstHeartRateSample = false

        if (!running) {
            coordinator.markNoData()
            updateState(
                connectionStatus = HeartRateConnectionStatus.NoData,
                retryDelayMillis = null
            )
            return
        }

        if (!autoReconnectEnabled) {
            markNoData("Disconnected before the heart-rate channel became usable.")
            return
        }

        if (status == BluetoothGatt.GATT_SUCCESS &&
            !everReachedUsableHeartRateChannel &&
            !supportsContinuousNotifications &&
            !supportsPolling
        ) {
            markNoData("Disconnected before the heart-rate channel became usable.")
            return
        }

        scheduleReconnect("Heart-rate connection lost with status $status.")
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(
            gatt: BluetoothGatt,
            status: Int,
            newState: Int
        ) {
            when {
                status == BluetoothGatt.GATT_SUCCESS &&
                    newState == BluetoothProfile.STATE_CONNECTED -> {
                    reconnectAttempt = 0
                    updateState(
                        connectionStatus = HeartRateConnectionStatus.Connected,
                        targetDeviceAddress = gatt.device.address,
                        retryDelayMillis = null,
                        lastErrorMessage = null
                    )
                    coordinator.markConnected()
                    runCatching { gatt.discoverServices() }
                        .onFailure { throwable ->
                            Log.e(logTag, "Heart-rate service discovery request failed.", throwable)
                            scheduleReconnect("Unable to start heart-rate service discovery.")
                        }
                }

                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    handleDisconnect(gatt, status)
                    gatt.close()
                }

                else -> {
                    if (gatt === activeGatt) {
                        closeGatt()
                    }
                    scheduleReconnect("Heart-rate connection failed with status $status.")
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            handleServicesDiscovered(gatt, status)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            handleHeartRateMeasurement(gatt, characteristic, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleHeartRateMeasurement(gatt, characteristic, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleHeartRateMeasurement(gatt, characteristic, characteristic.value ?: return)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleHeartRateMeasurement(gatt, characteristic, value)
            }
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (gatt !== activeGatt ||
                descriptor.characteristic.uuid != HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID
            ) {
                return
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (supportsPolling) {
                    startPollingHeartRate(gatt, descriptor.characteristic)
                    return
                }
                scheduleReconnect("Heart-rate notification subscription failed with status $status.")
            }
        }
    }

    private fun BluetoothDevice.safeDisplayName(): String {
        return runCatching { name }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: address
    }

    @SuppressLint("MissingPermission")
    private fun startFirstSampleWatchdog(gatt: BluetoothGatt) {
        cancelFirstSampleWatchdog()
        waitingForFirstHeartRateSample = true
        firstSampleWatchdogJob = scope.launch {
            delay(FIRST_SAMPLE_TIMEOUT_MILLIS)
            if (!running || activeGatt !== gatt || !waitingForFirstHeartRateSample) {
                return@launch
            }
            publishDiagnostics(
                connectionNote = "Connected, but no heart-rate samples were received yet.",
                firstSampleWarning = true
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun publishDiagnostics(
        connectionNote: String? = null,
        firstSampleWarning: Boolean = false
    ) {
        coordinator.publishDiagnostics(
            buildDiagnosticsState(
                connectionNote = connectionNote,
                firstSampleWarning = firstSampleWarning
            )
        )
    }

    @SuppressLint("MissingPermission")
    private fun buildDiagnosticsState(
        connectionNote: String? = null,
        firstSampleWarning: Boolean = false
    ): HeartRateDiagnosticsState {
        val findings = mutableListOf<HeartRateDiagnosticFinding>()
        val adapter = bluetoothAdapter

        if (adapter == null) {
            findings.add(
                HeartRateDiagnosticFinding(
                    title = appContext.getString(R.string.heart_rate_diag_no_adapter_title),
                    detail = appContext.getString(R.string.heart_rate_diag_no_adapter_detail),
                    severity = HeartRateDiagnosticSeverity.Blocker
                )
            )
        } else {
            findings.add(
                HeartRateDiagnosticFinding(
                    title = appContext.getString(R.string.heart_rate_diag_adapter_present_title),
                    detail = appContext.getString(R.string.heart_rate_diag_adapter_present_detail),
                    severity = HeartRateDiagnosticSeverity.Pass
                )
            )

            if (!adapter.isEnabled) {
                findings.add(
                    HeartRateDiagnosticFinding(
                        title = appContext.getString(R.string.heart_rate_diag_bluetooth_off_title),
                        detail = appContext.getString(R.string.heart_rate_diag_bluetooth_off_detail),
                        severity = HeartRateDiagnosticSeverity.Blocker
                    )
                )
            } else {
                findings.add(
                    HeartRateDiagnosticFinding(
                        title = appContext.getString(R.string.heart_rate_diag_bluetooth_on_title),
                        detail = appContext.getString(R.string.heart_rate_diag_bluetooth_on_detail),
                        severity = HeartRateDiagnosticSeverity.Pass
                    )
                )
            }

            val requiredPermissions = requiredPermissions()
            val missingPermissions = requiredPermissions.filterNot { permission ->
                ContextCompat.checkSelfPermission(
                    appContext,
                    permission
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
            if (missingPermissions.isNotEmpty()) {
                findings.add(
                    HeartRateDiagnosticFinding(
                        title = appContext.getString(R.string.heart_rate_diag_permissions_title),
                        detail = missingPermissions.joinToString { permission ->
                            permission.substringAfterLast('.')
                        },
                        severity = HeartRateDiagnosticSeverity.Blocker
                    )
                )
            } else {
                findings.add(
                    HeartRateDiagnosticFinding(
                        title = appContext.getString(R.string.heart_rate_diag_permissions_ok_title),
                        detail = appContext.getString(R.string.heart_rate_diag_permissions_ok_detail),
                        severity = HeartRateDiagnosticSeverity.Pass
                    )
                )
            }

            val bondedDevices = try {
                adapter.bondedDevices.orEmpty()
            } catch (securityException: SecurityException) {
                emptySet()
            }

            if (bondedDevices.isEmpty()) {
                findings.add(
                    HeartRateDiagnosticFinding(
                        title = appContext.getString(R.string.heart_rate_diag_no_bonded_title),
                        detail = appContext.getString(R.string.heart_rate_diag_no_bonded_detail),
                        severity = HeartRateDiagnosticSeverity.Blocker
                    )
                )
            } else {
                findings.add(
                    HeartRateDiagnosticFinding(
                        title = appContext.getString(R.string.heart_rate_diag_bonded_present_title),
                        detail = appContext.getString(
                            R.string.heart_rate_diag_bonded_present_detail,
                            bondedDevices.size
                        ),
                        severity = HeartRateDiagnosticSeverity.Pass
                    )
                )
            }

            val savedAddress = currentTargetAddress ?: targetStore.getTargetDeviceMac()
            val targetDevice = resolveTargetDevice(adapter)
            if (savedAddress.isNullOrBlank()) {
                findings.add(
                    HeartRateDiagnosticFinding(
                        title = appContext.getString(R.string.heart_rate_diag_no_saved_target_title),
                        detail = appContext.getString(R.string.heart_rate_diag_no_saved_target_detail),
                        severity = HeartRateDiagnosticSeverity.Warning
                    )
                )
            } else if (targetDevice == null) {
                findings.add(
                    HeartRateDiagnosticFinding(
                        title = appContext.getString(R.string.heart_rate_diag_target_missing_title),
                        detail = appContext.getString(
                            R.string.heart_rate_diag_target_missing_detail,
                            savedAddress
                        ),
                        severity = HeartRateDiagnosticSeverity.Blocker
                    )
                )
            } else {
                findings.add(
                    HeartRateDiagnosticFinding(
                        title = appContext.getString(R.string.heart_rate_diag_target_found_title),
                        detail = appContext.getString(
                            R.string.heart_rate_diag_target_found_detail,
                            targetDevice.safeDisplayName()
                        ),
                        severity = HeartRateDiagnosticSeverity.Pass
                    )
                )

            }
        }

        connectionNote?.let {
            findings.add(
                HeartRateDiagnosticFinding(
                    title = appContext.getString(R.string.heart_rate_diag_connection_note_title),
                    detail = it,
                    severity = HeartRateDiagnosticSeverity.Pass
                )
            )
        }

        if (firstSampleWarning) {
            findings.add(
                HeartRateDiagnosticFinding(
                    title = appContext.getString(R.string.heart_rate_diag_no_sample_title),
                    detail = appContext.getString(R.string.heart_rate_diag_no_sample_detail),
                    severity = HeartRateDiagnosticSeverity.Warning
                )
            )
        }

        val blockingFindings = findings.filter { it.severity == HeartRateDiagnosticSeverity.Blocker }
        val summary = when {
            adapter == null -> appContext.getString(R.string.heart_rate_diag_summary_no_adapter)
            blockingFindings.isNotEmpty() -> appContext.getString(
                R.string.heart_rate_diag_summary_blocked,
                blockingFindings.size
            )
            findings.any { it.severity == HeartRateDiagnosticSeverity.Warning } ->
                appContext.getString(R.string.heart_rate_diag_summary_warnings)
            else -> appContext.getString(R.string.heart_rate_diag_summary_ready)
        }

        return HeartRateDiagnosticsState(
            lastCheckedEpochMillis = System.currentTimeMillis(),
            summary = summary,
            findings = findings
        )
    }

    private fun requiredPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private companion object {
        private val HEART_RATE_SERVICE_UUID: UUID =
            UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HEART_RATE_MEASUREMENT_CHARACTERISTIC_UUID: UUID =
            UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CLIENT_CHARACTERISTIC_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private const val POLL_INTERVAL_MILLIS = 5_000L
        private const val FIRST_SAMPLE_TIMEOUT_MILLIS = 20_000L
    }
}
