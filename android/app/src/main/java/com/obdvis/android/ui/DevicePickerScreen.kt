package com.obdvis.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.obdvis.android.domain.model.BluetoothDeviceInfo
import com.obdvis.android.domain.model.ConnectionState
import com.obdvis.android.ui.theme.Border
import com.obdvis.android.ui.theme.OBDvisTheme
import com.obdvis.android.ui.theme.SubText

@Composable
fun DevicePickerScreen(
    viewModel: MainViewModel,
    historyCount: Int,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val devices by viewModel.pairedDevices.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadPairedDevices(context)
    }

    OBDvisTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(bottom = 80.dp)) {
                // Header
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.Bluetooth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            "OBDvis",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onOpenHistory) {
                            Icon(
                                Icons.Default.History,
                                contentDescription = null,
                                modifier = Modifier.size(17.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(if (historyCount > 0) "History ($historyCount)" else "History")
                        }
                    }
                }

                HorizontalDivider(color = Border)

                Column(modifier = Modifier.padding(24.dp)) {
                    Text(
                        "Select ELM327 Device",
                        style = MaterialTheme.typography.titleSmall,
                        color = SubText,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Showing Bluetooth devices already paired to this phone. " +
                            "Pair your ELM327 adapter in Android Settings first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SubText,
                    )
                }

                if (devices.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.BluetoothSearching,
                                contentDescription = null,
                                tint = SubText,
                                modifier = Modifier.size(40.dp),
                            )
                            Text("No paired devices found", color = SubText)
                        }
                    }
                } else {
                    LazyColumn {
                        items(devices) { device ->
                            DeviceRow(
                                info = device,
                                onClick = { viewModel.connect(device) },
                            )
                            HorizontalDivider(color = Border, thickness = 0.5.dp)
                        }
                    }
                }
            }

            // Demo mode footer
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                Column {
                    HorizontalDivider(color = Border)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "No device? Try demo mode",
                            style = MaterialTheme.typography.bodySmall,
                            color = SubText,
                        )
                        OutlinedButton(
                            onClick = { viewModel.startDemo() },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Demo", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            // Connecting overlay
            if (connectionState is ConnectionState.Connecting ||
                connectionState is ConnectionState.Initializing
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Column(
                            modifier = Modifier.padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Text(
                                if (connectionState is ConnectionState.Initializing)
                                    "Initializing ELM327…"
                                else
                                    "Connecting…"
                            )
                        }
                    }
                }
            }

            // Error snackbar area
            if (connectionState is ConnectionState.Error) {
                val msg = (connectionState as ConnectionState.Error).message
                Snackbar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp),
                    action = {
                        TextButton(onClick = { viewModel.disconnect() }) { Text("Dismiss") }
                    },
                ) {
                    Text(msg)
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(info: BluetoothDeviceInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            Icons.Default.Bluetooth,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(
                info.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                info.address,
                style = MaterialTheme.typography.bodySmall,
                color = SubText,
            )
        }
    }
}
