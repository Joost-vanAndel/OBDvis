package com.obdvis.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.obdvis.android.BuildConfig
import com.obdvis.android.ui.theme.Background
import com.obdvis.android.ui.theme.Border
import com.obdvis.android.ui.theme.OBDvisTheme
import com.obdvis.android.ui.theme.OnSurface
import com.obdvis.android.ui.theme.Primary
import com.obdvis.android.ui.theme.SubText
import com.obdvis.android.ui.theme.Surface as AppSurface

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    var showThirdPartyNotices by remember { mutableStateOf(false) }

    BackHandler(enabled = showThirdPartyNotices) {
        showThirdPartyNotices = false
    }

    if (showThirdPartyNotices) {
        ThirdPartyNoticesScreen(onBack = { showThirdPartyNotices = false })
        return
    }

    OBDvisTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background),
        ) {
            Surface(color = AppSurface, tonalElevation = 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = SubText)
                    }
                    Text("About", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column {
                    Text("OBDvis", style = MaterialTheme.typography.titleLarge, color = OnSurface)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Real-time OBD-II data visualization and diagnostics for ELM327 Bluetooth adapters.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SubText,
                    )
                }

                HorizontalDivider(color = Border)

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    AboutRow(label = "Version", value = BuildConfig.VERSION_NAME)
                    AboutRow(label = "Publisher", value = "ZES - Zevenaar Elektronica & Sensoren.")
                }

                HorizontalDivider(color = Border)

                Column {
                    Text("Source and licenses", style = MaterialTheme.typography.titleSmall, color = OnSurface)
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = { uriHandler.openUri(REPOSITORY_URL) },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                    ) {
                        Text("Joost-vanAndel/OBDvis", color = Primary)
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    TextButton(
                        onClick = { showThirdPartyNotices = true },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
                    ) {
                        Text("Open-source licenses", color = Primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = SubText)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
    }
}

private const val REPOSITORY_URL = "https://github.com/Joost-vanAndel/OBDvis"
