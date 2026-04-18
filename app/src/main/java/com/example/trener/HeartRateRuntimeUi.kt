package com.example.trener

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.trener.ble.heartrate.HeartRateBleSourceState
import com.example.trener.domain.heartrate.HeartRateConnectionStatus
import com.example.trener.domain.heartrate.HeartRateRuntimeState
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeartRateRuntimeSection(
    state: HeartRateRuntimeState,
    sourceState: HeartRateBleSourceState? = null,
    modifier: Modifier = Modifier,
    onReconnect: () -> Unit,
    onSelectTargetDevice: ((String) -> Unit)? = null,
    onEditThreshold: (() -> Unit)? = null
) {
    SectionCard(
        title = "",
        modifier = modifier.alpha(if (state.isStale) 0.58f else 1f)
    ) {
        var showDiagnosticsSheet by rememberSaveable { mutableStateOf(false) }
        var showDevicePicker by rememberSaveable { mutableStateOf(false) }
        val diagnosticsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        val devicePickerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        val currentBpmText = state.currentHeartRateBpm?.toString()
            ?: stringResource(R.string.heart_rate_no_data_value)

        if (showDiagnosticsSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDiagnosticsSheet = false },
                sheetState = diagnosticsSheetState
            ) {
                HeartRateDiagnosticsSheetContent(
                    state = state,
                    sourceState = sourceState,
                    onChooseDevice = if (onSelectTargetDevice != null) {
                        { showDiagnosticsSheet = false; showDevicePicker = true }
                    } else {
                        null
                    },
                    onClose = { showDiagnosticsSheet = false }
                )
            }
        }

        if (showDevicePicker && onSelectTargetDevice != null) {
            ModalBottomSheet(
                onDismissRequest = { showDevicePicker = false },
                sheetState = devicePickerSheetState
            ) {
                HeartRateDevicePickerSheetContent(
                    sourceState = sourceState,
                    onSelectTargetDevice = { address ->
                        onSelectTargetDevice(address)
                        showDevicePicker = false
                    },
                    onClose = { showDevicePicker = false }
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.heart_rate_section_title),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )

                Text(
                    text = currentBpmText,
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                )
            }

            IconButton(
                onClick = { showDiagnosticsSheet = true },
                modifier = Modifier.heightIn(min = 32.dp, max = 32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = stringResource(R.string.heart_rate_check_device_button)
                )
            }
        }
    }
}

@Composable
private fun HeartRateDiagnosticsSheetContent(
    state: HeartRateRuntimeState,
    sourceState: HeartRateBleSourceState?,
    onChooseDevice: (() -> Unit)?,
    onClose: () -> Unit
) {
    val blockingFindings = state.diagnostics.blockingFindings
    val warnings = state.diagnostics.warningFindings
    val currentTarget = sourceState?.targetDeviceAddress
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.heart_rate_diagnostics_title),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
        )
        Text(
            text = state.diagnostics.summary,
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
        )

        if (!currentTarget.isNullOrBlank()) {
            Text(
                text = stringResource(
                    R.string.heart_rate_current_target_label,
                    currentTarget
                ),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }

        if (blockingFindings.isNotEmpty()) {
            Text(
                text = stringResource(R.string.heart_rate_diagnostics_missing_title),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error
            )
            blockingFindings.forEach { finding ->
                DiagnosticFindingRow(
                    title = finding.title,
                    detail = finding.detail,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error
                )
            }
        }

        if (warnings.isNotEmpty()) {
            Text(
                text = stringResource(R.string.heart_rate_diagnostics_warning_title),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary
            )
            warnings.forEach { finding ->
                DiagnosticFindingRow(
                    title = finding.title,
                    detail = finding.detail,
                    contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (blockingFindings.isEmpty() && warnings.isEmpty()) {
            Text(
                text = stringResource(R.string.heart_rate_diagnostics_ready),
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = androidx.compose.material3.MaterialTheme.colorScheme.primary
            )
        }

        if (onChooseDevice != null) {
            Button(
                onClick = onChooseDevice,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.heart_rate_choose_device_button))
            }
        }

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.heart_rate_diagnostics_close_button))
        }
    }
}

@Composable
private fun HeartRateDevicePickerSheetContent(
    sourceState: HeartRateBleSourceState?,
    onSelectTargetDevice: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val discovery = remember(context) { HeartRateDeviceDiscoveryController(context.applicationContext) }
    val bondedDevices = remember(context, sourceState?.targetDeviceAddress) {
        context.heartRateBondedDevices()
    }

    LaunchedEffect(Unit) {
        discovery.startScan()
    }

    DisposableEffect(Unit) {
        onDispose {
            discovery.stopScan()
        }
    }

    val combinedDevices = remember(bondedDevices, discovery.discoveredDevices) {
        mergeHeartRateDeviceLists(bondedDevices, discovery.discoveredDevices)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.heart_rate_device_picker_title),
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.heart_rate_device_picker_hint),
            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
        )

        OutlinedButton(
            onClick = {
                discovery.startScan(forceRestart = true)
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !discovery.isScanning
        ) {
            Text(
                text = if (discovery.isScanning) {
                    stringResource(R.string.heart_rate_device_picker_scanning)
                } else {
                    stringResource(R.string.heart_rate_device_picker_scan)
                }
            )
        }

        discovery.errorMessage?.let { message ->
            Text(
                text = message,
                color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }

        if (combinedDevices.isEmpty()) {
            Text(
                text = stringResource(R.string.heart_rate_device_picker_empty),
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
            )
        } else {
            combinedDevices.forEach { device ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = device.name,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = device.address,
                            color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = if (device.isBonded) {
                                stringResource(R.string.heart_rate_device_picker_bonded)
                            } else {
                                stringResource(R.string.heart_rate_device_picker_nearby)
                            },
                            color = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                        )
                        if (device.rssi != null) {
                            Text(
                                text = "RSSI: ${device.rssi}",
                                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                            )
                        }
                        if (device.address == sourceState?.targetDeviceAddress) {
                            Text(
                                text = stringResource(R.string.heart_rate_device_picker_current),
                                color = androidx.compose.material3.MaterialTheme.colorScheme.tertiary,
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium
                            )
                        }
                        Button(
                            onClick = { onSelectTargetDevice(device.address) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = stringResource(R.string.heart_rate_device_picker_select))
                        }
                    }
                }
            }
        }

        Button(
            onClick = onClose,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(text = stringResource(R.string.heart_rate_diagnostics_close_button))
        }
    }
}

@Composable
private fun DiagnosticFindingRow(
    title: String,
    detail: String,
    contentColor: androidx.compose.ui.graphics.Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = contentColor,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium
        )
        Text(
            text = detail,
            color = contentColor,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
    }
}

private data class HeartRateDeviceUiModel(
    val address: String,
    val name: String,
    val rssi: Int?,
    val isBonded: Boolean
)

private class HeartRateDeviceDiscoveryController(
    context: Context
) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val adapter: BluetoothAdapter? = manager?.adapter
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    var discoveredDevices by mutableStateOf<List<HeartRateDeviceUiModel>>(emptyList())
        private set
    var isScanning by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var scanCycleJob: Job? = null

    @SuppressLint("MissingPermission")
    fun startScan(forceRestart: Boolean = false) {
        val bluetoothAdapter = adapter ?: run {
            errorMessage = appContext.getString(R.string.heart_rate_device_picker_no_bluetooth)
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            errorMessage = appContext.getString(R.string.heart_rate_device_picker_bluetooth_off)
            return
        }

        val scanner = bluetoothAdapter.bluetoothLeScanner ?: run {
            errorMessage = appContext.getString(R.string.heart_rate_device_picker_scan_unavailable)
            return
        }

        if (isScanning && !forceRestart) {
            return
        }

        if (forceRestart) {
            stopScan()
        }

        discoveredDevices = emptyList()
        errorMessage = null
        isScanning = true

        scanCycleJob?.cancel()
        scanCycleJob = scope.launch {
            while (isActive) {
                val started = startSingleScan(scanner)
                if (!started) {
                    return@launch
                }
                delay(SCAN_ACTIVE_WINDOW_MILLIS)
                stopSingleScan(scanner)
                delay(SCAN_PAUSE_WINDOW_MILLIS)
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanCycleJob?.cancel()
        scanCycleJob = null
        stopSingleScan(adapter?.bluetoothLeScanner)
        isScanning = false
    }

    @SuppressLint("MissingPermission")
    private fun startSingleScan(
        scanner: android.bluetooth.le.BluetoothLeScanner
    ): Boolean {
        return runCatching {
            scanner.startScan(
                null,
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                scanCallback
            )
        }.onSuccess {
            isScanning = true
        }.onFailure { throwable ->
            errorMessage = throwable.message
                ?: appContext.getString(R.string.heart_rate_device_picker_scan_failed)
            isScanning = false
        }.isSuccess
    }

    @SuppressLint("MissingPermission")
    private fun stopSingleScan(scanner: android.bluetooth.le.BluetoothLeScanner?) {
        if (scanner != null) {
            runCatching {
                scanner.stopScan(scanCallback)
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            updateDevice(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::updateDevice)
        }

        override fun onScanFailed(errorCode: Int) {
            scanCycleJob?.cancel()
            scanCycleJob = null
            isScanning = false
            errorMessage = appContext.getString(
                R.string.heart_rate_device_picker_scan_failed_with_code,
                errorCode
            )
        }
    }

    private fun updateDevice(result: ScanResult) {
        val device = result.device
        val address = device.address
        val name = runCatching { device.name }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: appContext.getString(R.string.heart_rate_device_picker_unknown_device)
        val isBonded = adapter?.bondedDevices?.any { it.address == address } == true
        val nextDevice = HeartRateDeviceUiModel(
            address = address,
            name = name,
            rssi = result.rssi,
            isBonded = isBonded
        )
        discoveredDevices = (discoveredDevices.filterNot { it.address == address } + nextDevice)
            .sortedWith(
                compareByDescending<HeartRateDeviceUiModel> { it.isBonded }
                    .thenByDescending { it.rssi ?: Int.MIN_VALUE }
                    .thenBy { it.name.lowercase() }
            )
    }

    private companion object {
        private const val SCAN_ACTIVE_WINDOW_MILLIS = 2500L
        private const val SCAN_PAUSE_WINDOW_MILLIS = 1200L
    }
}

private fun Context.heartRateBondedDevices(): List<HeartRateDeviceUiModel> {
    val manager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = manager?.adapter ?: return emptyList()

    val bondedDevices = try {
        adapter.bondedDevices.orEmpty()
    } catch (_: SecurityException) {
        emptySet()
    }

    return bondedDevices
        .map { device ->
            val deviceName = runCatching { device.name }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: device.address
            HeartRateDeviceUiModel(
                address = device.address,
                name = deviceName,
                rssi = null,
                isBonded = true
            )
        }
        .sortedWith(
            compareBy { it.name.lowercase() }
        )
}

private fun mergeHeartRateDeviceLists(
    bonded: List<HeartRateDeviceUiModel>,
    nearby: List<HeartRateDeviceUiModel>
): List<HeartRateDeviceUiModel> {
    val mergedByAddress = linkedMapOf<String, HeartRateDeviceUiModel>()

    bonded.forEach { device ->
        mergedByAddress[device.address] = device
    }

    nearby.forEach { device ->
        val existing = mergedByAddress[device.address]
        mergedByAddress[device.address] = if (existing == null) {
            device
        } else {
            existing.copy(
                name = device.name.ifBlank { existing.name },
                rssi = device.rssi ?: existing.rssi,
                isBonded = existing.isBonded || device.isBonded
            )
        }
    }

    return mergedByAddress.values.sortedWith(
        compareByDescending<HeartRateDeviceUiModel> { it.isBonded }
            .thenByDescending { it.rssi ?: Int.MIN_VALUE }
            .thenBy { it.name.lowercase() }
    )
}

@Composable
fun HeartRateThresholdDialog(
    currentThresholdBpm: Int?,
    onDismiss: () -> Unit,
    onSave: (Int?) -> Unit
) {
    val invalidThresholdMessage = stringResource(R.string.heart_rate_threshold_invalid)
    var thresholdInput by rememberSaveable(currentThresholdBpm) {
        mutableStateOf(currentThresholdBpm?.toString().orEmpty())
    }
    var validationError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(currentThresholdBpm) {
        thresholdInput = currentThresholdBpm?.toString().orEmpty()
        validationError = null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.heart_rate_threshold_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.heart_rate_threshold_dialog_message),
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = thresholdInput,
                    onValueChange = { updatedValue ->
                        thresholdInput = updatedValue.filter(Char::isDigit)
                        validationError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text(text = stringResource(R.string.heart_rate_threshold_input_label)) },
                    placeholder = { Text(text = stringResource(R.string.heart_rate_threshold_input_placeholder)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                validationError?.let { error ->
                    Text(
                        text = error,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.error,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val threshold = thresholdInput.trim().takeIf(String::isNotBlank)
                        ?.toIntOrNull()
                    when {
                        thresholdInput.isBlank() -> onSave(null)
                        threshold == null || threshold <= 0 -> {
                            validationError = invalidThresholdMessage
                            return@TextButton
                        }
                        else -> onSave(threshold)
                    }
                }
            ) {
                Text(text = stringResource(R.string.heart_rate_threshold_save_button))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) {
                    Text(text = stringResource(R.string.heart_rate_threshold_cancel_button))
                }
                OutlinedButton(
                    onClick = { onSave(null) },
                    modifier = Modifier.heightIn(min = 40.dp)
                ) {
                    Text(text = stringResource(R.string.heart_rate_threshold_disable_button))
                }
            }
        }
    )
}

@Composable
private fun heartRateConnectionLabel(status: HeartRateConnectionStatus): String {
    return when (status) {
        HeartRateConnectionStatus.Connected -> stringResource(R.string.heart_rate_status_connected)
        HeartRateConnectionStatus.Reconnecting -> stringResource(R.string.heart_rate_status_reconnecting)
        HeartRateConnectionStatus.NoData -> stringResource(R.string.heart_rate_status_no_data)
    }
}
