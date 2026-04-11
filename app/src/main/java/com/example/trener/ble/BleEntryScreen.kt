package com.example.trener.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trener.BuildConfig
import com.example.trener.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BleEntryScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val viewModel: BleEntryViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val requiredPermissions = remember { requiredBlePermissions() }
    var requestedInitialAction by remember { mutableStateOf(false) }
    var pendingBleAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val bluetoothEnableLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val adapter = bluetoothAdapter(appContext)
        if (adapter?.isEnabled == true || result.resultCode == android.app.Activity.RESULT_OK) {
            val action = pendingBleAction
            pendingBleAction = null
            action?.invoke()
        } else {
            pendingBleAction = null
            viewModel.showBluetoothDisabled()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        if (grantResults.values.all { it }) {
            val adapter = bluetoothAdapter(appContext)
            if (adapter?.isEnabled == true) {
                val action = pendingBleAction
                pendingBleAction = null
                action?.invoke()
            } else {
                viewModel.showBluetoothDisabled()
                bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            }
        } else {
            pendingBleAction = null
            viewModel.showPermissionDenied()
        }
    }

    fun ensureBleAccessAndThen(onReady: () -> Unit) {
        val hasPermissions = requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        }

        if (!hasPermissions) {
            pendingBleAction = onReady
            viewModel.showPermissionRequired()
            permissionLauncher.launch(requiredPermissions)
            return
        }

        val adapter = bluetoothAdapter(appContext)
        if (adapter?.isEnabled != true) {
            pendingBleAction = onReady
            viewModel.showBluetoothDisabled()
            bluetoothEnableLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        pendingBleAction = null
        onReady()
    }

    LaunchedEffect(Unit) {
        if (!requestedInitialAction) {
            requestedInitialAction = true
            ensureBleAccessAndThen {
                viewModel.startMainFlow()
            }
        }
    }

    DisposableEffect(viewModel) {
        onDispose {
            viewModel.close()
        }
    }

    BackHandler(enabled = true) {
        onBack()
    }

    val isSelectionMode = uiState.connectionMode == BleConnectionMode.DeviceSelection
    val hasPrimaryDevice = !uiState.primaryDeviceAddress.isNullOrBlank()
    val devicesVisible = isSelectionMode
    val emptyStateText = when (uiState.status) {
        BleEntryStatus.PermissionRequired,
        BleEntryStatus.BluetoothDisabled,
        BleEntryStatus.UnsupportedDevice,
        BleEntryStatus.NoData,
        BleEntryStatus.Error -> uiState.statusMessage

        else -> stringResource(R.string.weight_ble_no_devices)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.weight_ble_screen_title)) },
                navigationIcon = {
                    TextButton(
                        onClick = onBack,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Text(text = stringResource(R.string.weight_ble_back))
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            item {
                BleStatusCard(uiState = uiState)
            }

            item {
                BleActionRow(
                    isSelectionMode = isSelectionMode,
                    hasPrimaryDevice = hasPrimaryDevice,
                    canInteract = uiState.status != BleEntryStatus.Connecting &&
                        uiState.status != BleEntryStatus.Scanning &&
                        uiState.status != BleEntryStatus.Searching,
                    onReconnect = {
                        ensureBleAccessAndThen {
                            viewModel.startMainFlow()
                        }
                    },
                    onChangeDevice = {
                        ensureBleAccessAndThen {
                            viewModel.startDeviceSelectionFlow()
                        }
                    }
                )
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = when {
                            uiState.status == BleEntryStatus.PermissionRequired -> {
                                stringResource(R.string.weight_ble_permission_required)
                            }

                            uiState.status == BleEntryStatus.BluetoothDisabled -> {
                                stringResource(R.string.weight_ble_bluetooth_disabled)
                            }

                            uiState.status == BleEntryStatus.Connected -> {
                                stringResource(R.string.weight_ble_connected)
                            }

                            else -> uiState.statusMessage
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    uiState.detailMessage?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    uiState.primaryDeviceAddress?.let { address ->
                        Text(
                            text = stringResource(R.string.weight_ble_primary_device_label, address),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (isSelectionMode) {
                item {
                    Text(
                        text = stringResource(R.string.weight_ble_scan_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (devicesVisible) {
                if (uiState.devices.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text(
                                text = emptyStateText,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(uiState.devices, key = { it.address }) { device ->
                        BleDeviceCard(
                            device = device,
                            enabled = uiState.status != BleEntryStatus.Connecting,
                            onConnect = {
                                viewModel.connectToDevice(device.address)
                            }
                        )
                    }
                }
            }

            if (BuildConfig.DEBUG) {
                item {
                    BleDebugCard(
                        packets = uiState.debugPackets
                    )
                }
            }
        }
    }
}

@Composable
private fun BleActionRow(
    isSelectionMode: Boolean,
    hasPrimaryDevice: Boolean,
    canInteract: Boolean,
    onReconnect: () -> Unit,
    onChangeDevice: () -> Unit
) {
    if (isSelectionMode) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onReconnect,
                enabled = canInteract,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = if (hasPrimaryDevice) {
                        stringResource(R.string.weight_ble_use_saved_device)
                    } else {
                        stringResource(R.string.weight_ble_retry_button)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            OutlinedButton(
                onClick = onChangeDevice,
                enabled = canInteract,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.weight_ble_rescan_button),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Button(
            onClick = onReconnect,
            enabled = canInteract,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                text = stringResource(R.string.weight_ble_retry_button),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        OutlinedButton(
            onClick = onChangeDevice,
            enabled = canInteract,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            Text(
                text = stringResource(R.string.weight_ble_change_device_button),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BleStatusCard(uiState: BleEntryUiState) {
    val containerColor = when (uiState.status) {
        BleEntryStatus.Searching -> MaterialTheme.colorScheme.tertiaryContainer
        BleEntryStatus.Connected -> MaterialTheme.colorScheme.primaryContainer
        BleEntryStatus.Connecting -> MaterialTheme.colorScheme.secondaryContainer
        BleEntryStatus.Scanning -> MaterialTheme.colorScheme.tertiaryContainer
        BleEntryStatus.Receiving -> MaterialTheme.colorScheme.primaryContainer
        BleEntryStatus.Saved -> MaterialTheme.colorScheme.primaryContainer
        BleEntryStatus.PermissionRequired,
        BleEntryStatus.BluetoothDisabled,
        BleEntryStatus.UnsupportedDevice,
        BleEntryStatus.NoData,
        BleEntryStatus.Error -> MaterialTheme.colorScheme.errorContainer

        BleEntryStatus.Idle -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (uiState.status) {
        BleEntryStatus.Searching -> MaterialTheme.colorScheme.onTertiaryContainer
        BleEntryStatus.Connected -> MaterialTheme.colorScheme.onPrimaryContainer
        BleEntryStatus.Connecting -> MaterialTheme.colorScheme.onSecondaryContainer
        BleEntryStatus.Scanning -> MaterialTheme.colorScheme.onTertiaryContainer
        BleEntryStatus.Receiving -> MaterialTheme.colorScheme.onPrimaryContainer
        BleEntryStatus.Saved -> MaterialTheme.colorScheme.onPrimaryContainer
        BleEntryStatus.PermissionRequired,
        BleEntryStatus.BluetoothDisabled,
        BleEntryStatus.UnsupportedDevice,
        BleEntryStatus.NoData,
        BleEntryStatus.Error -> MaterialTheme.colorScheme.onErrorContainer

        BleEntryStatus.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = statusLabel(uiState.status),
                style = MaterialTheme.typography.titleMedium,
                color = contentColor
            )
            Text(
                text = uiState.statusMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor
            )
            uiState.measuredWeightKg?.let { weightKg ->
                Text(
                    text = "${BleWeightMeasurementParser.formatWeight(weightKg)} kg",
                    style = MaterialTheme.typography.headlineSmall,
                    color = contentColor
                )
            }
            uiState.selectedDeviceAddress?.let { address ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = address,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor
                )
            }
        }
    }
}

@Composable
private fun BleDeviceCard(
    device: BleDeviceUiModel,
    enabled: Boolean,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (device.isLikelyScale) {
                    Text(
                        text = stringResource(R.string.weight_ble_scale_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                device.rssi?.let { value ->
                    Text(
                        text = "RSSI: $value",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Button(
                onClick = onConnect,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = stringResource(R.string.weight_ble_connect_device),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BleDebugCard(
    packets: List<BleDebugPacketUiModel>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.weight_ble_debug_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.weight_ble_debug_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (packets.isEmpty()) {
                Text(
                    text = stringResource(R.string.weight_ble_debug_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                val timeFormatter = rememberTimeFormatter()
                packets.takeLast(12).forEach { packet ->
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = timeFormatter.format(Date(packet.timestampEpochMillis)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "len=${packet.packetLength} weight=${
                                packet.computedWeightKg?.let(BleWeightMeasurementParser::formatWeight)
                                    ?: "null"
                            }",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "field=${packet.weightFieldHex ?: "null"} raw=${
                                packet.decodedRawValue?.toString() ?: "null"
                            } ${packet.decoderSummary ?: ""}".trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = packet.hexPayload,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberTimeFormatter(): SimpleDateFormat {
    return remember {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }
}

@Composable
private fun statusLabel(status: BleEntryStatus): String {
    return when (status) {
        BleEntryStatus.Idle -> stringResource(R.string.weight_ble_idle_status)
        BleEntryStatus.PermissionRequired -> stringResource(R.string.weight_ble_permissions_status)
        BleEntryStatus.BluetoothDisabled -> stringResource(R.string.weight_ble_bluetooth_off_status)
        BleEntryStatus.Searching -> stringResource(R.string.weight_ble_searching_status)
        BleEntryStatus.Scanning -> stringResource(R.string.weight_ble_scanning)
        BleEntryStatus.Connecting -> stringResource(R.string.weight_ble_connecting)
        BleEntryStatus.Connected -> stringResource(R.string.weight_ble_connected)
        BleEntryStatus.Receiving -> stringResource(R.string.weight_ble_receiving_status)
        BleEntryStatus.Saved -> stringResource(R.string.weight_ble_saved_status)
        BleEntryStatus.UnsupportedDevice -> stringResource(R.string.weight_ble_unsupported_status)
        BleEntryStatus.NoData -> stringResource(R.string.weight_ble_no_data_status)
        BleEntryStatus.Error -> stringResource(R.string.weight_ble_error)
    }
}

private fun requiredBlePermissions(): Array<String> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private fun bluetoothAdapter(context: Context): BluetoothAdapter? {
    val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    return manager.adapter
}
