package com.obdvis.android.ui

import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import android.view.WindowManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.obdvis.android.domain.PidRegistry
import com.obdvis.android.domain.health.DtcScreenState
import com.obdvis.android.domain.health.FindingSeverity
import com.obdvis.android.domain.health.FindingStatus
import com.obdvis.android.domain.model.ConnectionState
import com.obdvis.android.ui.components.LiveLineChart
import com.obdvis.android.ui.components.SensorSidebar
import com.obdvis.android.ui.dtc.DtcScreen
import com.obdvis.android.ui.health.FuelTrimDivePanel
import com.obdvis.android.ui.health.VehicleHealthScreen
import com.obdvis.android.ui.overview.OverviewScreen
import com.obdvis.android.ui.theme.*

@Composable
fun DashboardScreen(viewModel: MainViewModel) {
    val connectionState by viewModel.connectionState.collectAsState()
    val enabledPids by viewModel.enabledPids.collectAsState()
    val latestValues by viewModel.latestValues.collectAsState()
    val chartData by viewModel.chartData.collectAsState()
    val normalizeChart by viewModel.normalizeChart.collectAsState()
    val healthState by viewModel.healthState.collectAsState()
    val activeFindings by viewModel.activeFindings.collectAsState()
    val findingEventLog by viewModel.findingEventLog.collectAsState()
    val healthAutoUpdateEnabled by viewModel.healthAutoUpdateEnabled.collectAsState()
    val dtcScreenState by viewModel.dtcScreenState.collectAsState()
    val gForce by viewModel.gForce.collectAsState()
    val peakGForce by viewModel.peakGForce.collectAsState()
    val fuelTrimFocused by viewModel.fuelTrimFocused.collectAsState()
    val driveEndDetected by viewModel.driveEndDetected.collectAsState()
    val activeFindingCount = activeFindings.count {
        it.value.status == FindingStatus.ACTIVE && it.value.finding.severity != FindingSeverity.INFO
    }
    val dtcCount = (dtcScreenState as? DtcScreenState.Loaded)?.result?.totalCount ?: 0
    var showOverviewTab by remember { mutableStateOf(true) }
    var showHealthTab by remember { mutableStateOf(false) }
    var showDtcTab by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    val showLiveTab = !showOverviewTab && !showHealthTab && !showDtcTab

    val onNonOverviewTab = !showOverviewTab && !showSettings && !showAbout
    BackHandler(enabled = showSettings) { showSettings = false }
    BackHandler(enabled = showAbout) { showAbout = false }
    BackHandler(enabled = onNonOverviewTab) {
        showOverviewTab = true; showHealthTab = false; showDtcTab = false
        viewModel.setActiveTab(ActiveTab.OVERVIEW)
    }

    fun navigateToHealth() {
        showHealthTab = true; showOverviewTab = false; showDtcTab = false
        viewModel.setActiveTab(ActiveTab.HEALTH)
    }
    fun navigateToDtcs() {
        showDtcTab = true; showOverviewTab = false; showHealthTab = false
        viewModel.setActiveTab(ActiveTab.DTC)
        viewModel.readDtcs()
    }
    val isPortrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
    val context = LocalContext.current
    val view = LocalView.current
    DisposableEffect(Unit) {
        val window = (view.context as android.app.Activity).window
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }
    val exportCsv = rememberCsvExportAction(viewModel::buildCsvContent)

    if (showSettings) {
        SettingsScreen(viewModel = viewModel, onBack = { showSettings = false })
        return
    }

    if (showAbout) {
        AboutScreen(onBack = { showAbout = false })
        return
    }

    OBDvisTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background),
        ) {
            // ── Status bar ────────────────────────────────────────────────────
            Surface(color = Surface, tonalElevation = 0.dp) {
                Column {
                    // Row 1: title + connection info + action icons
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("OBDvis", style = MaterialTheme.typography.titleMedium)

                        Icon(
                            Icons.Default.FiberManualRecord,
                            contentDescription = null,
                            tint = Color(0xFF4CD97B),
                            modifier = Modifier.size(10.dp),
                        )

                        val deviceName = (connectionState as? ConnectionState.Connected)?.deviceName ?: ""
                        Text(
                            deviceName,
                            style = MaterialTheme.typography.bodySmall,
                            color = SubText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            softWrap = false,
                            modifier = Modifier.weight(1f),
                        )

                        IconButton(onClick = { showAbout = true }) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = "About",
                                tint = SubText,
                            )
                        }

                        IconButton(onClick = { showSettings = true }) {
                            Icon(
                                Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = SubText,
                            )
                        }

                        IconButton(onClick = viewModel::disconnect) {
                            Icon(
                                Icons.Default.StopCircle,
                                contentDescription = if (driveEndDetected) "End drive" else "Disconnect",
                                tint = if (driveEndDetected) Color(0xFFFFB74D) else SubText,
                            )
                        }
                    }

                    // Row 2: tab chips + context-sensitive chips, horizontally scrollable
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FilterChip(
                            selected = showOverviewTab,
                            onClick = {
                                showOverviewTab = true; showHealthTab = false; showDtcTab = false
                                viewModel.setActiveTab(ActiveTab.OVERVIEW)
                            },
                            label = { Text("Overview", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp),
                        )

                        FilterChip(
                            selected = !showOverviewTab && !showHealthTab && !showDtcTab,
                            onClick = {
                                showOverviewTab = false; showHealthTab = false; showDtcTab = false
                                viewModel.setActiveTab(ActiveTab.LIVE)
                            },
                            label = { Text("Live", style = MaterialTheme.typography.labelSmall) },
                            modifier = Modifier.height(28.dp),
                        )

                        BadgedBox(badge = {
                            if (activeFindingCount > 0) Badge { Text("$activeFindingCount") }
                        }) {
                            FilterChip(
                                selected = showHealthTab,
                                onClick = { navigateToHealth() },
                                label = { Text("Health", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp),
                            )
                        }

                        BadgedBox(badge = {
                            if (dtcCount > 0) Badge { Text("$dtcCount") }
                        }) {
                            FilterChip(
                                selected = showDtcTab,
                                onClick = { navigateToDtcs() },
                                label = { Text("DTCs", style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.height(28.dp),
                            )
                        }

                    }
                }
            }

            HorizontalDivider(color = Border, thickness = 0.5.dp)

            // ── Main content — adaptive layout ────────────────────────────────
            if (isPortrait) {
                // Portrait: chart on top, sensor list below
                if (showOverviewTab) {
                    OverviewScreen(
                        latestValues       = latestValues,
                        chartData          = chartData,
                        healthState        = healthState,
                        activeFindings     = activeFindings,
                        dtcScreenState     = dtcScreenState,
                        gForce             = gForce,
                        peakGForce         = peakGForce,
                        onResetPeaks       = viewModel::resetPeakGForce,
                        onNavigateToHealth = { navigateToHealth() },
                        onNavigateToDtcs   = { navigateToDtcs() },
                        modifier           = Modifier.fillMaxWidth().weight(1f),
                    )
                } else if (showDtcTab) {
                    DtcScreen(
                        state    = dtcScreenState,
                        onRead   = viewModel::readDtcs,
                        onClear  = viewModel::clearDtcs,
                        onSearchDtc = context::searchForDtc,
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                } else if (showHealthTab) {
                    if (fuelTrimFocused) {
                        FuelTrimDivePanel(
                            chartData = chartData,
                            latestValues = latestValues,
                            healthState = healthState,
                            onClose = { viewModel.setFuelTrimFocused(false) },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    } else {
                        VehicleHealthScreen(
                            healthState = healthState,
                            activeFindings = activeFindings,
                            findingEventLog = findingEventLog,
                            autoUpdateEnabled = healthAutoUpdateEnabled,
                            onToggleAutoUpdate = viewModel::toggleHealthAutoUpdate,
                            onOpenFuelTrimDive = { viewModel.setFuelTrimFocused(true) },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                } else {
                    LiveLineChart(
                        chartData = chartData,
                        enabledPids = enabledPids,
                        normalize = normalizeChart,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(8.dp),
                    )
                }

                if (showLiveTab) {
                    HorizontalDivider(color = Border, thickness = 0.5.dp)

                    SensorSidebar(
                        pids = PidRegistry.live,
                        enabledPids = enabledPids,
                        latestValues = latestValues,
                        onToggle = viewModel::togglePid,
                        onEnableAll = viewModel::enableAllPids,
                        onDisableAll = viewModel::disableAllPids,
                        normalizeChart = normalizeChart,
                        onToggleNormalize = viewModel::toggleNormalizeChart,
                        onExportCsv = { exportCsv("obd_session.csv") },
                        canExportCsv = chartData.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                    )
                }
            } else {
                // Landscape: sidebar left, chart right
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (showLiveTab) {
                        SensorSidebar(
                            pids = PidRegistry.live,
                            enabledPids = enabledPids,
                            latestValues = latestValues,
                            onToggle = viewModel::togglePid,
                            onEnableAll = viewModel::enableAllPids,
                            onDisableAll = viewModel::disableAllPids,
                            normalizeChart = normalizeChart,
                            onToggleNormalize = viewModel::toggleNormalizeChart,
                            onExportCsv = { exportCsv("obd_session.csv") },
                            canExportCsv = chartData.isNotEmpty(),
                            modifier = Modifier
                                .width(200.dp)
                                .fillMaxHeight(),
                        )

                        VerticalDivider(color = Border, thickness = 0.5.dp)
                    }

                    if (showOverviewTab) {
                        OverviewScreen(
                            latestValues       = latestValues,
                            chartData          = chartData,
                            healthState        = healthState,
                            activeFindings     = activeFindings,
                            dtcScreenState     = dtcScreenState,
                            gForce             = gForce,
                            peakGForce         = peakGForce,
                            onResetPeaks       = viewModel::resetPeakGForce,
                            onNavigateToHealth = { navigateToHealth() },
                            onNavigateToDtcs   = { navigateToDtcs() },
                            modifier           = Modifier.weight(1f).fillMaxHeight(),
                        )
                    } else if (showDtcTab) {
                        DtcScreen(
                            state    = dtcScreenState,
                            onRead   = viewModel::readDtcs,
                            onClear  = viewModel::clearDtcs,
                            onSearchDtc = context::searchForDtc,
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    } else if (showHealthTab) {
                        if (fuelTrimFocused) {
                            FuelTrimDivePanel(
                                chartData = chartData,
                                latestValues = latestValues,
                                healthState = healthState,
                                onClose = { viewModel.setFuelTrimFocused(false) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        } else {
                            VehicleHealthScreen(
                                healthState = healthState,
                                activeFindings = activeFindings,
                                findingEventLog = findingEventLog,
                                autoUpdateEnabled = healthAutoUpdateEnabled,
                                onToggleAutoUpdate = viewModel::toggleHealthAutoUpdate,
                                onOpenFuelTrimDive = { viewModel.setFuelTrimFocused(true) },
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                            )
                        }
                    } else {
                        LiveLineChart(
                            chartData = chartData,
                            enabledPids = enabledPids,
                            normalize = normalizeChart,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(8.dp),
                        )
                    }
                }
            }
        }
    }
}
