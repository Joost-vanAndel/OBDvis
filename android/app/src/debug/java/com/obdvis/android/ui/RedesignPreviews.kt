package com.obdvis.android.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.obdvis.android.domain.PidRegistry
import com.obdvis.android.domain.health.*
import com.obdvis.android.ui.components.SessionHeader
import com.obdvis.android.ui.components.DashboardTabs
import com.obdvis.android.ui.components.SensorSidebar
import com.obdvis.android.ui.dtc.DtcScreen
import com.obdvis.android.ui.health.VehicleHealthScreen
import com.obdvis.android.ui.overview.OverviewScreen
import com.obdvis.android.ui.theme.OBDvisTheme

// Design fixtures only. Values reproduce the existing demo screenshots, not a live vehicle.
private val previewValues = mapOf("speed" to 38f, "rpm" to 1937f, "coolant" to 92f,
    "obd_voltage" to 12.2f, "oil_temp" to 98f, "throttle" to 27f)
private val previewFinding = DiagnosticFinding(
    "charging_voltage_low", "Possible charging issue",
    "Low while the engine is running — may indicate a charging system issue.",
    FindingSeverity.MEDIUM, FindingConfidence.MEDIUM,
    mapOf("Average system voltage" to "12.2 V", "Samples" to "26 over 30 s"),
)
private val previewRecord = FindingRecord(previewFinding, System.currentTimeMillis() - 120_000, System.currentTimeMillis(), 1,
    FindingSeverity.MEDIUM, FindingStatus.ACTIVE)
private val previewHealth = HealthState.Ready(DiagnosticSummary(
    timestamp = 0, operatingState = OperatingState.CRUISE,
    vehicleState = VehicleState.fromLatestValues(previewValues), findings = listOf(previewFinding),
    dataLimitations = listOf("Fuel trim data unavailable"),
))

@Preview(name = "Overview · 320", widthDp = 320, heightDp = 900)
@Preview(name = "Overview · 375", widthDp = 375, heightDp = 960)
@Preview(name = "Overview · 414", widthDp = 414, heightDp = 960)
@Preview(name = "Overview · 768", widthDp = 768, heightDp = 900)
@Preview(name = "Overview · large text", widthDp = 320, heightDp = 1000, fontScale = 1.5f)
@Preview(name = "Overview · landscape", widthDp = 768, heightDp = 375)
@Composable
fun OverviewRedesignPreview() {
    OBDvisTheme {
        Column {
            SessionHeader("Demo Mode", false, {}, {}, {})
            OverviewScreen(previewValues, emptyMap(), previewHealth,
                0.05f to 0.02f, PeakGForce(), {}, Modifier.weight(1f))
            DashboardTabs(ActiveTab.OVERVIEW, 1, 0, {})
        }
    }
}

@Preview(name = "Missing readings · 320", widthDp = 320, heightDp = 900)
@Composable
fun EmptyOverviewRedesignPreview() {
    OBDvisTheme {
        OverviewScreen(emptyMap(), emptyMap(), HealthState.Idle,
            0f to 0f, PeakGForce(), {})
    }
}

@Preview(name = "Health · 320", widthDp = 320, heightDp = 900)
@Preview(name = "Health · 375", widthDp = 375, heightDp = 900)
@Preview(name = "Health · 414", widthDp = 414, heightDp = 900)
@Preview(name = "Health · 768", widthDp = 768, heightDp = 900)
@Preview(name = "Health · large text", widthDp = 320, heightDp = 1100, fontScale = 1.5f)
@Composable
fun HealthRedesignPreview() {
    OBDvisTheme {
        VehicleHealthScreen(previewHealth, mapOf(previewFinding.id to previewRecord),
            emptyList(), true, {}, {}, {})
    }
}

@Preview(name = "DTCs · 320", widthDp = 320, heightDp = 700)
@Preview(name = "DTCs · large text", widthDp = 320, heightDp = 900, fontScale = 1.5f)
@Composable
fun DtcRedesignPreview() {
    OBDvisTheme { DtcScreen(DtcScreenState.Loaded(DtcReadResult(stored = listOf("P0171"))), {}, {}, {}) }
}

@Preview(name = "Live sensors · 320", widthDp = 320, heightDp = 700)
@Composable
fun SensorsRedesignPreview() {
    OBDvisTheme {
        SensorSidebar(PidRegistry.live, PidRegistry.live.take(4).toSet(), previewValues,
            {}, {}, {}, false, {}, {}, false, Modifier.fillMaxSize())
    }
}

// Preview-only owners satisfy launchers and BackHandler without launching external activities.
// Screenshot renderers may create the preview outside Android's main thread.
@android.annotation.SuppressLint("VisibleForTests")
private class PreviewOwners : androidx.activity.result.ActivityResultRegistryOwner,
    androidx.activity.OnBackPressedDispatcherOwner {
    override val lifecycle = androidx.lifecycle.LifecycleRegistry.createUnsafe(this).apply {
        currentState = androidx.lifecycle.Lifecycle.State.RESUMED
    }
    override val onBackPressedDispatcher = androidx.activity.OnBackPressedDispatcher()
    override val activityResultRegistry = object : androidx.activity.result.ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int,
            contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I,
            options: androidx.core.app.ActivityOptionsCompat?) = Unit
    }
}

@Composable
private fun PreviewHost(content: @Composable () -> Unit) {
    val owners = androidx.compose.runtime.remember { PreviewOwners() }
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.activity.compose.LocalActivityResultRegistryOwner provides owners,
        androidx.activity.compose.LocalOnBackPressedDispatcherOwner provides owners,
    ) { content() }
}

@Preview(name = "Permissions · 320", widthDp = 320, heightDp = 700)
@Composable
fun PermissionRedesignPreview() = PreviewHost { PermissionScreen {} }

@Preview(name = "Settings · 320", widthDp = 320, heightDp = 900)
@Composable
fun SettingsRedesignPreview() = PreviewHost {
    val model = androidx.compose.runtime.remember { MainViewModel() }
    SettingsScreen(model, {})
}

@Preview(name = "Summary · 320", widthDp = 320, heightDp = 1000)
@Composable
fun SummaryRedesignPreview() = PreviewHost {
    PostDriveScreen(PostDriveData(
        sessionStartMs = 0, sessionEndMs = 120_000, findingRecords = emptyList(), totalHealthChecks = 4,
        operatingStateCounts = mapOf(OperatingState.CRUISE to 4), peakRpm = 1937f,
        peakSpeedKph = 38f, peakCoolantTempC = 92f,
    ), "time,rpm\n0,1937", {}, {})
}

@Preview(name = "History empty · 320", widthDp = 320, heightDp = 700)
@Composable
fun HistoryRedesignPreview() = PreviewHost { DriveHistoryScreen(emptyList(), {}, {}, {}) }

@Preview(name = "About · 320", widthDp = 320, heightDp = 700)
@Composable
fun AboutRedesignPreview() = PreviewHost { AboutScreen {} }

@Preview(name = "Fuel trim · 320", widthDp = 320, heightDp = 900)
@Composable
fun FuelTrimRedesignPreview() = PreviewHost {
    OBDvisTheme {
        com.obdvis.android.ui.health.FuelTrimDivePanel(emptyMap(), emptyMap(), HealthState.Idle, {})
    }
}

@Preview(name = "Connection empty · 320", widthDp = 320, heightDp = 700)
@Composable
fun ConnectionRedesignPreview() {
    DevicePickerContent(emptyList(), com.obdvis.android.domain.model.ConnectionState.Disconnected,
        0, {}, {}, {}, {})
}

@Preview(name = "Connection error · 320", widthDp = 320, heightDp = 700)
@Composable
fun ConnectionErrorRedesignPreview() {
    DevicePickerContent(emptyList(), com.obdvis.android.domain.model.ConnectionState.Error("Could not connect to the adapter."),
        0, {}, {}, {}, {})
}
