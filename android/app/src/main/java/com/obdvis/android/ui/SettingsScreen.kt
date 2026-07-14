package com.obdvis.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.obdvis.android.domain.health.FindingSeverity
import com.obdvis.android.ui.theme.*

@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val bgCheckIntervalMs by viewModel.bgCheckIntervalMs.collectAsState()
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val notificationMinSeverity by viewModel.notificationMinSeverity.collectAsState()
    val context = LocalContext.current

    // Local text state for the interval field; sync from ViewModel when it changes externally
    var intervalText by remember(bgCheckIntervalMs) {
        mutableStateOf((bgCheckIntervalMs / 1000L).toString())
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) viewModel.updateNotificationsEnabled(true)
    }

    fun requestNotificationEnable() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (granted) viewModel.updateNotificationsEnabled(true)
            else notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.updateNotificationsEnabled(true)
        }
    }

    OBDvisTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background),
        ) {
            Surface(color = Surface, tonalElevation = 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SubText)
                    }
                    Text("Settings", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SettingsSection(title = "Background Health Checks") {
                    Text(
                        "How often to check vehicle health while the app is in the background",
                        style = MaterialTheme.typography.bodySmall,
                        color = SubText,
                    )
                    Spacer(Modifier.height(12.dp))

                    val seconds = intervalText.toLongOrNull()
                    val isError = seconds == null || seconds < 1

                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { raw ->
                            val filtered = raw.filter { it.isDigit() }
                            intervalText = filtered
                            val s = filtered.toLongOrNull()
                            if (s != null && s >= 1) viewModel.updateBgCheckInterval(s * 1000L)
                        },
                        label = { Text("Interval (seconds)", color = SubText) },
                        isError = isError,
                        supportingText = if (isError) {
                            { Text("Enter a number greater than 0", color = MaterialTheme.colorScheme.error) }
                        } else null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface,
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Border,
                            cursorColor = Primary,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                HorizontalDivider(color = Border)

                SettingsSection(title = "Notifications") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Notify on findings", style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            Text(
                                "Send a notification when background checks detect issues",
                                style = MaterialTheme.typography.bodySmall,
                                color = SubText,
                            )
                        }
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { enabled ->
                                if (enabled) requestNotificationEnable()
                                else viewModel.updateNotificationsEnabled(false)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Primary,
                                checkedTrackColor = Primary.copy(alpha = 0.4f),
                            ),
                        )
                    }

                    if (notificationsEnabled) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Minimum severity",
                            style = MaterialTheme.typography.bodySmall,
                            color = SubText,
                        )
                        Spacer(Modifier.height(4.dp))
                        severityOptions.forEach { (label, severity) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                RadioButton(
                                    selected = notificationMinSeverity == severity,
                                    onClick = { viewModel.updateNotificationMinSeverity(severity) },
                                    colors = RadioButtonDefaults.colors(selectedColor = Primary),
                                )
                                Text(
                                    label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurface,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = Primary)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

private val severityOptions = listOf(
    "Any finding (Info and above)" to FindingSeverity.INFO,
    "Low severity and above"       to FindingSeverity.LOW,
    "Medium severity and above"    to FindingSeverity.MEDIUM,
    "High severity only"           to FindingSeverity.HIGH,
)
