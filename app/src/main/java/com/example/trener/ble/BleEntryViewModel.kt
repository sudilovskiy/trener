package com.example.trener.ble

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.trener.R
import com.example.trener.formatBodyWeightKg
import com.example.trener.normalizeBodyWeightKg
import com.example.trener.logTag
import com.example.trener.data.local.BodyWeightHistoryRepository
import com.example.trener.data.local.TrenerDatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.collections.LinkedHashMap

enum class BleEntryStatus {
    Idle,
    PermissionRequired,
    BluetoothDisabled,
    Searching,
    Scanning,
    Connecting,
    Connected,
    Receiving,
    Saved,
    UnsupportedDevice,
    NoData,
    Error
}

enum class BleConnectionMode {
    Primary,
    DeviceSelection
}

data class BleDeviceUiModel(
    val address: String,
    val name: String,
    val rssi: Int? = null,
    val isLikelyScale: Boolean = false,
    val isPinned: Boolean = false
)

data class BleEntryUiState(
    val status: BleEntryStatus = BleEntryStatus.Idle,
    val statusMessage: String = "",
    val detailMessage: String? = null,
    val devices: List<BleDeviceUiModel> = emptyList(),
    val selectedDeviceAddress: String? = null,
    val measuredWeightKg: Double? = null,
    val primaryDeviceAddress: String? = null,
    val connectionMode: BleConnectionMode = BleConnectionMode.Primary
)

class BleEntryViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext = application.applicationContext
    private val bluetoothManager =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bodyWeightRepository = BodyWeightHistoryRepository(
        TrenerDatabaseProvider.getInstance(appContext)
    )
    private val primaryDeviceStore = BlePrimaryDeviceStore(appContext)
    private val deviceDiscoveryLock = Any()
    private val discoveredDevices = LinkedHashMap<String, DiscoveredBleDevice>()
    private val pinnedDeviceOrder = mutableListOf<String>()
    private val scaleDeviceOrder = mutableListOf<String>()
    private val genericDeviceOrder = mutableListOf<String>()
    private val discoveredTargets = LinkedHashMap<String, BluetoothGattCharacteristic>()

    private var scanActive = false
    private var activeGatt: BluetoothGatt? = null
    private var measurementTimeoutJob: Job? = null
    private var stabilizationJob: Job? = null
    private var deviceMaintenanceJob: Job? = null
    private var hasAnyMeasurementCandidate = false
    private var measurementSaved = false
    private var primaryDeviceAddress: String? = primaryDeviceStore.getPrimaryDeviceMac()

    private val _uiState = MutableStateFlow(BleEntryUiState())
    val uiState: StateFlow<BleEntryUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { current ->
            current.copy(primaryDeviceAddress = primaryDeviceAddress)
        }
    }

    fun showPermissionRequired() {
        updateStatus(
            status = BleEntryStatus.PermissionRequired,
            message = appContext.getString(R.string.weight_ble_permission_required)
        )
    }

    fun showPermissionDenied() {
        updateStatus(
            status = BleEntryStatus.PermissionRequired,
            message = appContext.getString(R.string.weight_ble_permission_denied)
        )
    }

    fun showBluetoothDisabled() {
        updateStatus(
            status = BleEntryStatus.BluetoothDisabled,
            message = appContext.getString(R.string.weight_ble_bluetooth_disabled)
        )
    }

    fun showScanIdle() {
        updateStatus(
            status = BleEntryStatus.Idle,
            message = appContext.getString(R.string.weight_ble_scan_button)
        )
    }

    fun startMainFlow() {
        val storedPrimary = primaryDeviceAddress ?: primaryDeviceStore.getPrimaryDeviceMac().also {
            primaryDeviceAddress = it
            _uiState.update { current ->
                current.copy(primaryDeviceAddress = primaryDeviceAddress)
            }
        }

        if (storedPrimary.isNullOrBlank()) {
            startDeviceSelectionFlow()
            return
        }

        stopScanInternal()
        shutdownGatt()
        clearMeasurementState()
        updateStatus(
            status = BleEntryStatus.Searching,
            message = appContext.getString(R.string.weight_ble_searching),
            selectedDeviceAddress = storedPrimary,
            primaryDeviceAddress = storedPrimary,
            connectionMode = BleConnectionMode.Primary
        )
        connectToDevice(storedPrimary)
    }

    fun startDeviceSelectionFlow() {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            updateStatus(
                status = BleEntryStatus.Error,
                message = appContext.getString(R.string.weight_ble_connection_failed)
            )
            return
        }

        if (!adapter.isEnabled) {
            showBluetoothDisabled()
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            updateStatus(
                status = BleEntryStatus.Error,
                message = appContext.getString(R.string.weight_ble_connection_failed)
            )
            return
        }

        stopScanInternal()
        shutdownGatt()
        clearMeasurementState()
        emitDevices()

        runCatching {
            scanner.startScan(null, defaultScanSettings(), scanCallback)
        }.onSuccess {
            scanActive = true
            startDeviceListMaintenance()
            updateStatus(
                status = BleEntryStatus.Scanning,
                message = appContext.getString(R.string.weight_ble_scanning),
                connectionMode = BleConnectionMode.DeviceSelection,
                primaryDeviceAddress = primaryDeviceAddress
            )
        }.onFailure {
            updateStatus(
                status = BleEntryStatus.Error,
                message = appContext.getString(R.string.weight_ble_connection_failed)
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun startScan() {
        startDeviceSelectionFlow()
    }

    fun stopScan() {
        stopScanInternal()
        if (_uiState.value.status == BleEntryStatus.Scanning) {
            updateStatus(
                status = BleEntryStatus.Idle,
                message = appContext.getString(R.string.weight_ble_scan_button)
            )
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(address: String) {
        val adapter = bluetoothAdapter
        if (adapter == null) {
            updateStatus(
                status = BleEntryStatus.Error,
                message = appContext.getString(R.string.weight_ble_connection_failed)
            )
            return
        }

        if (!adapter.isEnabled) {
            showBluetoothDisabled()
            return
        }

        val device = runCatching { adapter.getRemoteDevice(address) }.getOrNull()
        if (device == null) {
            updateStatus(
                status = BleEntryStatus.Error,
                message = appContext.getString(R.string.weight_ble_connection_failed)
            )
            return
        }

        stopScanInternal()
        shutdownGatt()
        clearMeasurementState()

        updateStatus(
            status = BleEntryStatus.Connecting,
            message = appContext.getString(R.string.weight_ble_connecting),
            selectedDeviceAddress = address,
            primaryDeviceAddress = primaryDeviceAddress,
            connectionMode = _uiState.value.connectionMode
        )

        activeGatt = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(
                    appContext,
                    false,
                    gattCallback,
                    BluetoothDevice.TRANSPORT_LE
                )
            } else {
                device.connectGatt(appContext, false, gattCallback)
            }
        } catch (_: SecurityException) {
            updateStatus(
                status = BleEntryStatus.Error,
                message = appContext.getString(R.string.weight_ble_connection_failed)
            )
            null
        }
    }

    fun close() {
        stopScanInternal()
        cancelMeasurementJobs()
        shutdownGatt()
    }

    override fun onCleared() {
        close()
        super.onCleared()
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal() {
        if (scanActive) {
            val scanner = bluetoothAdapter?.bluetoothLeScanner
            if (scanner != null) {
                runCatching {
                    scanner.stopScan(scanCallback)
                }
            }
        }
        scanActive = false
        stopDeviceListMaintenance()
    }

    @SuppressLint("MissingPermission")
    private fun shutdownGatt() {
        val gatt = activeGatt ?: return
        activeGatt = null
        runCatching { gatt.disconnect() }
        runCatching { gatt.close() }
    }

    private fun emitDevices() {
        val now = System.currentTimeMillis()
        val devices = mutableListOf<BleDeviceUiModel>()
        synchronized(deviceDiscoveryLock) {
            appendVisibleDevices(devices, pinnedDeviceOrder, now)
            appendVisibleDevices(devices, scaleDeviceOrder, now)
            appendVisibleDevices(devices, genericDeviceOrder, now)
        }
        _uiState.update { current ->
            current.copy(devices = devices)
        }
    }

    private fun appendVisibleDevices(
        devices: MutableList<BleDeviceUiModel>,
        orderedAddresses: List<String>,
        nowEpochMillis: Long
    ) {
        orderedAddresses.forEach { address ->
            val device = discoveredDevices[address] ?: return@forEach
            if (nowEpochMillis - device.lastSeenEpochMillis > DEVICE_STALE_TIMEOUT_MS) {
                return@forEach
            }
            devices.add(device.toUiModel())
        }
    }

    private fun defaultScanSettings(): ScanSettings {
        return ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
    }

    private fun updateStatus(
        status: BleEntryStatus,
        message: String,
        detailMessage: String? = null,
        selectedDeviceAddress: String? = null,
        measuredWeightKg: Double? = null,
        primaryDeviceAddress: String? = null,
        connectionMode: BleConnectionMode? = null
    ) {
        _uiState.update { current ->
            current.copy(
                status = status,
                statusMessage = message,
                detailMessage = detailMessage,
                selectedDeviceAddress = selectedDeviceAddress,
                measuredWeightKg = measuredWeightKg,
                primaryDeviceAddress = primaryDeviceAddress ?: current.primaryDeviceAddress,
                connectionMode = connectionMode ?: current.connectionMode
            )
        }
    }

    private fun updateDevice(result: ScanResult) {
        val device = result.device
        val address = device.address
        val name = device.safeDisplayName()
        val now = System.currentTimeMillis()
        var shouldEmit = false

        synchronized(deviceDiscoveryLock) {
            val existing = discoveredDevices[address]
            if (existing == null) {
                val isPinned = address.equals(PINNED_SCALE_MAC, ignoreCase = true)
                val isLikelyScale = address.startsWith(SCALE_MAC_PREFIX, ignoreCase = true)
                discoveredDevices[address] = DiscoveredBleDevice(
                    address = address,
                    name = name,
                    rssi = result.rssi,
                    lastSeenEpochMillis = now,
                    isLikelyScale = isLikelyScale,
                    isPinned = isPinned
                )
                when {
                    isPinned -> pinnedDeviceOrder.add(address)
                    isLikelyScale -> scaleDeviceOrder.add(address)
                    else -> genericDeviceOrder.add(address)
                }
                shouldEmit = true
            } else {
                val wasStale = now - existing.lastSeenEpochMillis > DEVICE_STALE_TIMEOUT_MS
                if (existing.rssi != result.rssi) {
                    existing.rssi = result.rssi
                    shouldEmit = true
                }
                if (existing.name != name && name != appContext.getString(R.string.weight_ble_unknown_device)) {
                    existing.name = name
                    shouldEmit = true
                }
                existing.lastSeenEpochMillis = now
                if (wasStale) {
                    shouldEmit = true
                }
            }
        }

        if (shouldEmit) {
            emitDevices()
        }
    }

    private fun BluetoothDevice.safeDisplayName(): String {
        return name?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.weight_ble_unknown_device)
    }

    private fun savePrimaryDevice(address: String) {
        primaryDeviceAddress = address
        primaryDeviceStore.savePrimaryDeviceMac(address)
        _uiState.update { current ->
            current.copy(primaryDeviceAddress = address)
        }
        Log.d(logTag, "Saved primary BLE device mac=$address")
    }

    private fun clearMeasurementState() {
        cancelMeasurementJobs()
        hasAnyMeasurementCandidate = false
        measurementSaved = false
        discoveredTargets.clear()
    }

    private fun startDeviceListMaintenance() {
        deviceMaintenanceJob?.cancel()
        deviceMaintenanceJob = viewModelScope.launch {
            while (isActive) {
                delay(DEVICE_LIST_REFRESH_INTERVAL_MS)
                emitDevices()
            }
        }
    }

    private fun stopDeviceListMaintenance() {
        deviceMaintenanceJob?.cancel()
        deviceMaintenanceJob = null
    }

    private fun cancelMeasurementJobs() {
        measurementTimeoutJob?.cancel()
        measurementTimeoutJob = null
        stabilizationJob?.cancel()
        stabilizationJob = null
    }

    private fun beginMeasurementTimeout() {
        measurementTimeoutJob?.cancel()
        measurementTimeoutJob = viewModelScope.launch {
            delay(MEASUREMENT_TIMEOUT_MS)
            if (measurementSaved) {
                return@launch
            }

            val status = if (hasAnyMeasurementCandidate) {
                BleEntryStatus.NoData
            } else {
                BleEntryStatus.NoData
            }
            updateStatus(
                status = status,
                message = if (hasAnyMeasurementCandidate) {
                    appContext.getString(R.string.weight_ble_unstable_reading)
                } else {
                    appContext.getString(R.string.weight_ble_no_data)
                },
                detailMessage = appContext.getString(R.string.weight_ble_no_data_detail),
                selectedDeviceAddress = _uiState.value.selectedDeviceAddress
            )
            shutdownGatt()
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleServicesDiscovered(gatt: BluetoothGatt, status: Int) {
        if (gatt != activeGatt) {
            return
        }

        if (status != BluetoothGatt.GATT_SUCCESS) {
            updateStatus(
                status = BleEntryStatus.Error,
                message = appContext.getString(R.string.weight_ble_connection_failed)
            )
            shutdownGatt()
            return
        }

        val targets = selectWeightTargets(gatt)
        if (targets.isEmpty()) {
            updateStatus(
                status = BleEntryStatus.UnsupportedDevice,
                message = appContext.getString(R.string.weight_ble_unsupported_device),
                detailMessage = appContext.getString(R.string.weight_ble_unsupported_device_detail)
            )
            shutdownGatt()
            return
        }

        discoveredTargets.clear()
        targets.forEach { target ->
            discoveredTargets[target.uuid.toString() + ":" + target.instanceId] = target
        }

        updateStatus(
            status = BleEntryStatus.Receiving,
            message = appContext.getString(R.string.weight_ble_receiving_status),
            detailMessage = null,
            selectedDeviceAddress = gatt.device.address
        )
        beginMeasurementTimeout()

        targets.forEach { target ->
            val supportsNotify = target.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
            val supportsIndicate = target.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0
            val supportsRead = target.properties and BluetoothGattCharacteristic.PROPERTY_READ != 0

            if (supportsNotify || supportsIndicate) {
                subscribeToCharacteristic(gatt, target, supportsIndicate)
            } else if (supportsRead) {
                runCatching {
                    gatt.readCharacteristic(target)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun subscribeToCharacteristic(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        useIndication: Boolean
    ) {
        runCatching {
            gatt.setCharacteristicNotification(characteristic, true)
        }

        val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_UUID) ?: return
        descriptor.value = if (useIndication) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        runCatching {
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun selectWeightTargets(gatt: BluetoothGatt): List<BluetoothGattCharacteristic> {
        data class ScoredCharacteristic(
            val characteristic: BluetoothGattCharacteristic,
            val score: Int
        )

        val scored = mutableListOf<ScoredCharacteristic>()
        val fallback = mutableListOf<ScoredCharacteristic>()

        gatt.services.forEach { service ->
            service.characteristics.forEach charLoop@{ characteristic ->
                val properties = characteristic.properties
                val supportsRead = properties and BluetoothGattCharacteristic.PROPERTY_READ != 0
                val supportsNotify = properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0
                val supportsIndicate = properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0

                if (!supportsRead && !supportsNotify && !supportsIndicate) {
                    return@charLoop
                }

                val score = when {
                    service.uuid == STANDARD_WEIGHT_SERVICE_UUID &&
                        characteristic.uuid == STANDARD_WEIGHT_MEASUREMENT_UUID -> 0

                    service.uuid == BODY_COMPOSITION_SERVICE_UUID &&
                        characteristic.uuid == BODY_COMPOSITION_MEASUREMENT_UUID -> 1

                    service.uuid == STANDARD_WEIGHT_SERVICE_UUID -> 2

                    characteristic.uuid == STANDARD_WEIGHT_MEASUREMENT_UUID -> 3

                    characteristic.uuid == BODY_COMPOSITION_MEASUREMENT_UUID -> 4

                    else -> 10
                }

                val scoredCharacteristic = ScoredCharacteristic(characteristic, score)
                if (score <= 4) {
                    scored.add(scoredCharacteristic)
                } else {
                    fallback.add(scoredCharacteristic)
                }
            }
        }

        return (scored.sortedBy { it.score }.map { it.characteristic } +
            fallback.sortedBy { it.score }.map { it.characteristic })
            .distinctBy { it.uuid to it.instanceId }
    }

    private fun handleWeightPayload(parsed: ParsedWeightMeasurement) {
        if (!parsed.weightKg.isLikelyScaleWeight()) {
            return
        }

        hasAnyMeasurementCandidate = true
        val candidateWeight = normalizeBodyWeightKg(parsed.weightKg)
        updateStatus(
            status = BleEntryStatus.Receiving,
            message = appContext.getString(R.string.weight_ble_receiving_status),
            detailMessage = null,
            measuredWeightKg = candidateWeight,
            selectedDeviceAddress = _uiState.value.selectedDeviceAddress
        )

        stabilizationJob?.cancel()
        stabilizationJob = viewModelScope.launch {
            delay(STABILIZE_WINDOW_MS)
            persistStableWeight(candidateWeight)
        }
    }

    private fun recordDebugPacket(
        payload: ByteArray,
        parsed: ParsedWeightMeasurement?
    ) {
        Log.d(
            logTag,
            buildString {
                append("BLE packet ")
                append("len=").append(payload.size)
                append(" hex=").append(payload.toHexString())
                append(" field=").append(parsed?.rawFieldHex ?: "null")
                append(" raw=").append(parsed?.rawValue?.toString() ?: "null")
                append(" computedWeight=")
                append(parsed?.weightKg?.let(::formatBodyWeightKg) ?: "null")
            }
        )
    }

    private suspend fun persistStableWeight(candidateWeight: Double) {
        if (measurementSaved) {
            return
        }

        runCatching {
            withContext(Dispatchers.IO) {
                bodyWeightRepository.saveWeightForToday(candidateWeight)
            }
        }.onSuccess {
            measurementSaved = true
            cancelMeasurementJobs()
            updateStatus(
                status = BleEntryStatus.Saved,
                message = appContext.getString(R.string.weight_ble_saved_status),
                detailMessage = null,
                measuredWeightKg = candidateWeight,
                selectedDeviceAddress = _uiState.value.selectedDeviceAddress
            )
            shutdownGatt()
        }.onFailure {
            updateStatus(
                status = BleEntryStatus.Error,
                message = appContext.getString(R.string.weight_ble_save_failed),
                detailMessage = it.message ?: appContext.getString(R.string.weight_ble_save_failed_detail),
                measuredWeightKg = candidateWeight,
                selectedDeviceAddress = _uiState.value.selectedDeviceAddress
            )
            shutdownGatt()
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun handlePayloadFromGatt(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray
    ) {
        if (gatt != activeGatt) {
            return
        }

        val parsed = BleWeightMeasurementParser.parse(payload)
        recordDebugPacket(payload, parsed)
        if (measurementSaved || parsed == null) {
            return
        }

        handleWeightPayload(parsed)
    }

    private fun handleDisconnect(gatt: BluetoothGatt, status: Int) {
        if (activeGatt != gatt) {
            return
        }

        activeGatt = null
        stopScanInternal()
        cancelMeasurementJobs()

        if (measurementSaved) {
            return
        }

        val message = when {
            status == BluetoothGatt.GATT_SUCCESS && hasAnyMeasurementCandidate ->
                appContext.getString(R.string.weight_ble_disconnect_mid_process)

            status == BluetoothGatt.GATT_SUCCESS ->
                appContext.getString(R.string.weight_ble_disconnect_message)

            else ->
                "${appContext.getString(R.string.weight_ble_connection_failed)} ($status)"
        }

        updateStatus(
            status = BleEntryStatus.Error,
            message = message,
            detailMessage = if (hasAnyMeasurementCandidate) {
                appContext.getString(R.string.weight_ble_disconnect_detail)
            } else {
                null
            }
        )
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            updateDevice(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { result ->
                updateDevice(result)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            scanActive = false
            updateStatus(
                status = BleEntryStatus.Error,
                message = "${appContext.getString(R.string.weight_ble_error)}: $errorCode"
            )
        }
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
                    activeGatt = gatt
                    savePrimaryDevice(gatt.device.address)
                    updateStatus(
                        status = BleEntryStatus.Connected,
                        message = appContext.getString(R.string.weight_ble_connected),
                        selectedDeviceAddress = gatt.device.address,
                        primaryDeviceAddress = gatt.device.address,
                        connectionMode = BleConnectionMode.Primary
                    )
                    runCatching { gatt.discoverServices() }
                }

                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    handleDisconnect(gatt, status)
                    gatt.close()
                }

                else -> {
                    if (activeGatt === gatt) {
                        activeGatt = null
                    }
                    gatt.close()
                    updateStatus(
                        status = BleEntryStatus.Error,
                        message = "${appContext.getString(R.string.weight_ble_connection_failed)} ($status)"
                    )
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
            handlePayloadFromGatt(gatt, characteristic, characteristic.value ?: return)
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handlePayloadFromGatt(gatt, characteristic, value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handlePayloadFromGatt(gatt, characteristic, characteristic.value ?: return)
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handlePayloadFromGatt(gatt, characteristic, value)
            }
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS && activeGatt == gatt) {
                updateStatus(
                    status = BleEntryStatus.Error,
                    message = appContext.getString(R.string.weight_ble_connection_failed),
                    detailMessage = appContext.getString(R.string.weight_ble_notification_failed)
                )
            }
        }
    }

    private fun DiscoveredBleDevice.toUiModel(): BleDeviceUiModel {
        return BleDeviceUiModel(
            address = address,
            name = name,
            rssi = rssi,
            isLikelyScale = isLikelyScale,
            isPinned = isPinned
        )
    }

    private data class DiscoveredBleDevice(
        val address: String,
        var name: String,
        var rssi: Int?,
        var lastSeenEpochMillis: Long,
        val isLikelyScale: Boolean,
        val isPinned: Boolean
    )

    private companion object {
        private val CLIENT_CONFIG_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        private val STANDARD_WEIGHT_SERVICE_UUID: UUID =
            UUID.fromString("0000181d-0000-1000-8000-00805f9b34fb")
        private val STANDARD_WEIGHT_MEASUREMENT_UUID: UUID =
            UUID.fromString("00002a9d-0000-1000-8000-00805f9b34fb")
        private val BODY_COMPOSITION_SERVICE_UUID: UUID =
            UUID.fromString("0000181b-0000-1000-8000-00805f9b34fb")
        private val BODY_COMPOSITION_MEASUREMENT_UUID: UUID =
            UUID.fromString("00002a9c-0000-1000-8000-00805f9b34fb")

        private const val MEASUREMENT_TIMEOUT_MS = 15_000L
        private const val STABILIZE_WINDOW_MS = 900L
        private const val DEVICE_LIST_REFRESH_INTERVAL_MS = 2_000L
        private const val DEVICE_STALE_TIMEOUT_MS = 12_000L
        private const val SCALE_MAC_PREFIX = "88:22:B2"
        private const val PINNED_SCALE_MAC = "88:22:B2:B1:E7:B9"
    }
}

private fun Float.isLikelyScaleWeight(): Boolean {
    return isFinite() && this in 1.0f..200.0f
}

private fun ByteArray.toHexString(): String {
    return joinToString(separator = " ") { byte ->
        "%02X".format(byte.toInt() and 0xFF)
    }
}
