/* Hallmark · modern-minimal · grouped list · design-system: design.md */
package com.obdvis.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.obdvis.android.domain.model.ConnectionState
import com.obdvis.android.ui.components.AppHeader
import com.obdvis.android.ui.components.SectionHeading
import com.obdvis.android.ui.theme.*
import com.obdvis.android.domain.model.BluetoothDeviceInfo

@Composable
fun DevicePickerScreen(viewModel: MainViewModel, historyCount: Int, onOpenHistory: () -> Unit) {
    val context = LocalContext.current
    val devices by viewModel.pairedDevices.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    LaunchedEffect(Unit) { viewModel.loadPairedDevices(context) }

    DevicePickerContent(devices, connectionState, historyCount, onOpenHistory,
        viewModel::connect, viewModel::startDemo, viewModel::disconnect)
}

@Composable
internal fun DevicePickerContent(
    devices: List<BluetoothDeviceInfo>,
    connectionState: ConnectionState,
    historyCount: Int,
    onOpenHistory: () -> Unit,
    onConnect: (BluetoothDeviceInfo) -> Unit,
    onDemo: () -> Unit,
    onDismissError: () -> Unit,
) {
    val connecting = connectionState is ConnectionState.Connecting || connectionState is ConnectionState.Initializing
    OBDvisTheme {
        Scaffold(
            containerColor = Background,
            topBar = {
                AppHeader("OBDvis") {
                    IconButton(onClick = onOpenHistory, enabled = !connecting) {
                        Icon(Icons.Default.History, if (historyCount > 0) "History, $historyCount drives" else "History")
                    }
                }
            },
            bottomBar = {
                Surface(color = Surface) {
                    Column {
                        HorizontalDivider(color = Border)
                        Row(Modifier.fillMaxWidth().padding(AppSpace.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpace.md)) {
                            Text("No adapter? Explore demo mode.", style = MaterialTheme.typography.bodySmall,
                                color = SubText, modifier = Modifier.weight(1f))
                            OutlinedButton(shape = MaterialTheme.shapes.small, onClick = onDemo, enabled = !connecting) {
                                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(AppSpace.xs))
                                Text("Demo", maxLines = 1)
                            }
                        }
                    }
                }
            },
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(AppSpace.md),
                verticalArrangement = Arrangement.spacedBy(AppSpace.md)) {
                item {
                    Column(Modifier.padding(vertical = AppSpace.md), verticalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
                        Text("Connect your vehicle", style = MaterialTheme.typography.headlineMedium)
                        Text("Select your paired ELM327 Bluetooth adapter.", style = MaterialTheme.typography.bodyLarge, color = SubText)
                        Text("Pair the adapter in Android Settings first. It will appear in the list below.",
                            style = MaterialTheme.typography.bodySmall, color = DimText)
                    }
                }
                if (connectionState is ConnectionState.Error) {
                    item {
                        Surface(color = DangerContainer, shape = MaterialTheme.shapes.medium) {
                            Column(Modifier.fillMaxWidth().padding(AppSpace.md)) {
                                Text("Connection failed", style = MaterialTheme.typography.titleSmall, color = Danger)
                                Text((connectionState as ConnectionState.Error).message, color = OnSurface,
                                    style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = onDismissError) { Text("Dismiss") }
                            }
                        }
                    }
                }
                item { SectionHeading("Paired devices") }
                if (devices.isEmpty()) {
                    item {
                        Text("No paired devices found", style = MaterialTheme.typography.bodyMedium, color = SubText,
                            modifier = Modifier.padding(vertical = AppSpace.lg))
                    }
                }
                items(devices, key = { it.address }) { device ->
                    DeviceRow(device, enabled = !connecting, onClick = { onConnect(device) })
                    HorizontalDivider(color = Border)
                }
            }
        }
        if (connecting) {
            Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
                Surface(shape = MaterialTheme.shapes.medium, color = Surface) {
                    Column(Modifier.padding(AppSpace.lg), verticalArrangement = Arrangement.spacedBy(AppSpace.md)) {
                        CircularProgressIndicator(Modifier.size(32.dp))
                        Text(if (connectionState is ConnectionState.Initializing) "Initializing ELM327…" else "Connecting…",
                            style = MaterialTheme.typography.titleSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(info: BluetoothDeviceInfo, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 80.dp).clickable(enabled = enabled, onClick = onClick)
        .padding(vertical = AppSpace.md), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpace.md)) {
        Icon(Icons.Default.Bluetooth, null, tint = Primary, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpace.xxs)) {
            Text(info.name, style = MaterialTheme.typography.titleSmall, color = OnSurface)
            Text(info.address, style = MaterialTheme.typography.bodySmall, color = SubText)
        }
    }
}
