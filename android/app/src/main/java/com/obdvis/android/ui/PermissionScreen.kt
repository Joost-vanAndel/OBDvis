package com.obdvis.android.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.obdvis.android.ui.theme.OBDvisTheme
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
        // API 23-30: only location is dangerous (needed for BT device discovery)
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

@Composable
fun PermissionScreen(onPermissionsGranted: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results.values.all { it }) onPermissionsGranted()
    }

    OBDvisTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(32.dp),
            ) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp),
                )
                Text(
                    "Bluetooth Permission Required",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "OBDvis needs Bluetooth access to connect to your ELM327 adapter.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SubText,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = { launcher.launch(requiredPermissions) }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}
