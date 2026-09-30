package com.obdvis.android.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.obdvis.android.ui.theme.*
import com.obdvis.android.ui.theme.SubText

/**
 * Only the DANGEROUS permissions that require a runtime prompt.
 * BLUETOOTH and BLUETOOTH_ADMIN are normal (install-time) permissions — they must
 * be in the manifest but must NOT be in this list, because on some devices the
 * system returns DENIED for normal permissions passed to requestPermissions(),
 * causing our "all granted" check to fail even though Bluetooth is usable.
 */
val requiredPermissions: Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        // API 23-30: location is dangerous (needed for BT device discovery)
        arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

@Composable
fun PermissionScreen(onPermissionsGranted: () -> Unit) {
    var permissionDenied by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) onPermissionsGranted() else permissionDenied = true
    }

    OBDvisTheme {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpace.lg),
            verticalArrangement = Arrangement.spacedBy(AppSpace.lg)) {
            Text("OBDvis", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(AppSpace.xl))
            Icon(Icons.Default.Bluetooth, null, tint = Primary, modifier = Modifier.size(40.dp))
            Text("Connect to your adapter", style = MaterialTheme.typography.headlineMedium)
            Text("OBDvis needs Bluetooth access to connect to your ELM327 adapter.",
                style = MaterialTheme.typography.bodyLarge, color = SubText)
            if (permissionDenied) {
                Text("Permission was not granted. Try again, or enable permissions for OBDvis in Android Settings.",
                    style = MaterialTheme.typography.bodyMedium, color = Warning)
            }
            Button(shape = MaterialTheme.shapes.small, onClick = { launcher.launch(requiredPermissions) },
                modifier = Modifier.fillMaxWidth().heightIn(min = AppSpace.touch)) {
                Text("Grant permissions", maxLines = 1)
            }
        }
    }
}
