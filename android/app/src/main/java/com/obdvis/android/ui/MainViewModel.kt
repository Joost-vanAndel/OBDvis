package com.obdvis.android.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import com.obdvis.android.domain.model.BluetoothDeviceInfo
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.mikephil.charting.data.Entry
import com.obdvis.android.data.bluetooth.BluetoothSocketStream
import com.obdvis.android.domain.DemoSession
import com.obdvis.android.domain.OBDSession
import com.obdvis.android.domain.PidRegistry
import com.obdvis.android.domain.Session
import com.obdvis.android.domain.model.ConnectionState
import com.obdvis.android.domain.model.PidDefinition
import com.obdvis.android.domain.model.SensorSample
import com.obdvis.android.AppSettings
import com.obdvis.android.NotificationHelper
import com.obdvis.android.OBDVisApp
import com.obdvis.android.domain.health.DiagnosticSummary
import com.obdvis.android.domain.health.DiagnosticsInterpreter
import com.obdvis.android.domain.health.DtcReadResult
import com.obdvis.android.domain.health.DtcScreenState
import com.obdvis.android.domain.health.FindingEvent
import com.obdvis.android.domain.health.FindingRecord
import com.obdvis.android.domain.health.FindingSeverity
import com.obdvis.android.domain.health.FindingStateManager
import com.obdvis.android.domain.health.FindingStatus
import com.obdvis.android.domain.health.HealthState
import com.obdvis.android.domain.health.OperatingState
import com.obdvis.android.domain.health.PeakGForce
import com.obdvis.android.domain.health.PostDriveData
import com.obdvis.android.domain.health.VehicleState
import com.obdvis.android.domain.polling.PidPriorityGroups
import com.obdvis.android.domain.store.SampleStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.UUID

enum class ActiveTab { OVERVIEW, LIVE, HEALTH, DTC }

private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
private const val MAX_ENTRIES = 600        // ~60 s of data at ~10 Hz
private const val ENGINE_RUNNING_RPM = 200f
private const val ENGINE_OFF_RPM = 50f
private const val ENGINE_OFF_DELAY_MS = 5_000L
private const val VEHICLE_STOPPED_KPH = 5f
private const val MIN_SNAPSHOTS_FOR_SUMMARY = 3
private const val MAX_VEHICLE_STATE_HISTORY = 300
private const val CONTINUOUS_HEALTH_CHECK_INTERVAL_MS = 1_000L
private const val PERIODIC_FOREGROUND_HEALTH_CHECK_INTERVAL_MS = 30_000L
private val FUEL_TRIM_PIDS = listOf("stft", "ltft", "stft2", "ltft2")
    .mapNotNull { PidRegistry.byId[it] }

class MainViewModel(
    private val appSettings: AppSettings? = null,
    private val notificationHelper: NotificationHelper? = null,
    application: Application? = null,
) : ViewModel() {

    private val sharedAutoState = (application as? OBDVisApp)?.sharedAutoState

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDeviceInfo>> = _pairedDevices.asStateFlow()

    private val _enabledPids = MutableStateFlow(PidRegistry.live.toSet())
    val enabledPids: StateFlow<Set<PidDefinition>> = _enabledPids.asStateFlow()

    private val _activeTab = MutableStateFlow(ActiveTab.OVERVIEW)
    val activeTab: StateFlow<ActiveTab> = _activeTab.asStateFlow()

    private val _appForegrounded = MutableStateFlow(true)

    private val _bgCheckIntervalMs = MutableStateFlow(
        appSettings?.backgroundCheckIntervalMs ?: AppSettings.DEFAULT_BG_INTERVAL_MS
    )
    val bgCheckIntervalMs: StateFlow<Long> = _bgCheckIntervalMs.asStateFlow()

    private val _notificationsEnabled = MutableStateFlow(appSettings?.notificationsEnabled ?: false)
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _notificationMinSeverity = MutableStateFlow(
        appSettings?.notificationMinSeverity ?: FindingSeverity.MEDIUM
    )
    val notificationMinSeverity: StateFlow<FindingSeverity> = _notificationMinSeverity.asStateFlow()

    private val notifiedFindingIds = mutableSetOf<String>()

    fun setForegrounded(foreground: Boolean) {
        _appForegrounded.value = foreground
        val connected = _connectionState.value
        if (!foreground && connected is ConnectionState.Connected) {
            notificationHelper?.showStandingNotification(connected.deviceName)
        } else {
            notificationHelper?.cancelStandingNotification()
        }
    }

    fun updateBgCheckInterval(ms: Long) {
        appSettings?.backgroundCheckIntervalMs = ms
        _bgCheckIntervalMs.value = ms
        if (activeSession != null) startBackgroundChecks()
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        appSettings?.notificationsEnabled = enabled
        _notificationsEnabled.value = enabled
    }

    fun updateNotificationMinSeverity(severity: FindingSeverity) {
        appSettings?.notificationMinSeverity = severity
        _notificationMinSeverity.value = severity
    }

    private val _fuelTrimFocused = MutableStateFlow(false)
    val fuelTrimFocused: StateFlow<Boolean> = _fuelTrimFocused.asStateFlow()

    fun setFuelTrimFocused(focused: Boolean) { _fuelTrimFocused.value = focused }

    fun setActiveTab(tab: ActiveTab) {
        _activeTab.value = tab
        if (tab != ActiveTab.HEALTH) {
            _fuelTrimFocused.value = false
            _healthAutoUpdateEnabled.value = false
        } else {
            _healthAutoUpdateEnabled.value = true
            if (activeSession != null) startBackgroundChecks(skipSettle = true)
        }
    }

    private fun pidsForActiveTab(): List<PidDefinition> = when {
        _fuelTrimFocused.value -> FUEL_TRIM_PIDS
        _activeTab.value == ActiveTab.LIVE && _appForegrounded.value -> _enabledPids.value.toList()
        else -> PidRegistry.health
    }

    private fun currentWeightFn(): (String) -> Float = when {
        _appForegrounded.value && _activeTab.value == ActiveTab.OVERVIEW ->
            PidPriorityGroups::overviewWeightOf
        else ->
            PidPriorityGroups::diagnosticWeightOf
    }

    private val _latestValues = MutableStateFlow<Map<String, Float>>(emptyMap())
    val latestValues: StateFlow<Map<String, Float>> = _latestValues.asStateFlow()

    private val _chartData = MutableStateFlow<Map<String, List<Entry>>>(emptyMap())
    val chartData: StateFlow<Map<String, List<Entry>>> = _chartData.asStateFlow()

    private val sampleStore = SampleStore()

    private var sessionJob: Job? = null
    private var bgHealthJob: Job? = null
    private var engineOffJob: Job? = null
    private var stream: BluetoothSocketStream? = null
    @Volatile private var activeSession: Session? = null
    @Volatile private var engineWasRunning = false
    private var sessionStartMs = 0L

    @SuppressLint("MissingPermission")
    fun loadPairedDevices(context: Context) {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager)
            .adapter ?: return
        _pairedDevices.value = adapter.bondedDevices.map { device ->
            BluetoothDeviceInfo(
                name = device.name ?: device.address,
                address = device.address,
                device = device,
            )
        }
    }

    private fun startBackgroundChecks(skipSettle: Boolean = false) {
        bgHealthJob?.cancel()
        bgHealthJob = viewModelScope.launch {
            var lastDtcMs = 0L
            if (!skipSettle) delay(10_000L)
            while (isActive) {
                val inForeground = _appForegrounded.value
                val onOverview = _activeTab.value == ActiveTab.OVERVIEW
                val onHealthTab = _activeTab.value == ActiveTab.HEALTH
                val onDtcTab = _activeTab.value == ActiveTab.DTC

                val session = activeSession
                when {
                    inForeground && (onOverview ||
                        (onHealthTab && _healthAutoUpdateEnabled.value)) -> {
                        if (session != null) {
                            lastDtcMs = performHealthCheck(session, lastDtcMs)
                            delay(CONTINUOUS_HEALTH_CHECK_INTERVAL_MS)
                        } else {
                            delay(500L)
                        }
                    }
                    inForeground && onDtcTab -> {
                        if (session != null) lastDtcMs = performHealthCheck(session, lastDtcMs)
                        delay(PERIODIC_FOREGROUND_HEALTH_CHECK_INTERVAL_MS)
                    }
                    !inForeground -> {
                        delay(_bgCheckIntervalMs.value)
                        if (session != null && isActive) {
                            val prevActiveIds = _activeFindings.value
                                .filter { it.value.status == FindingStatus.ACTIVE }.keys
                            lastDtcMs = performHealthCheck(session, lastDtcMs)
                            val newlyActive = _activeFindings.value
                                .filter { (id, r) -> r.status == FindingStatus.ACTIVE && id !in prevActiveIds }
                                .values.map { it.finding }
                            maybeNotify(newlyActive)
                            val connected = _connectionState.value
                            if (connected is ConnectionState.Connected) {
                                val latestSummary = _healthState.value
                                if (latestSummary is HealthState.Ready) {
                                    notificationHelper?.showStandingNotification(connected.deviceName, latestSummary.summary)
                                }
                            }
                        }
                    }
                    else -> delay(PERIODIC_FOREGROUND_HEALTH_CHECK_INTERVAL_MS)
                }
            }
        }
    }

    // Runs a diagnostics pass. Returns updatedLastDtcMs.
    private suspend fun performHealthCheck(session: Session, lastDtcMs: Long): Long {
        var updatedDtcMs = lastDtcMs
        val now = System.currentTimeMillis()
        if (now - lastDtcMs >= 5 * 60_000L) {
            val result = withContext(Dispatchers.IO) {
                try { session.readDtcs() } catch (_: Exception) { _dtcResult.value }
            }
            _dtcResult.value = result
            if (_dtcScreenState.value is DtcScreenState.Idle ||
                _dtcScreenState.value is DtcScreenState.Loaded
            ) {
                _dtcScreenState.value = DtcScreenState.Loaded(result)
            }
            updatedDtcMs = now
        }
        val recentStates = vehicleStateHistory.toList()
        val summary = withContext(Dispatchers.Default) {
            val state = VehicleState.fromSampleStore(sampleStore)
                .copy(activeDtcs = _dtcResult.value.stored)
            DiagnosticsInterpreter.interpret(state, sampleStore, recentStates)
        }
        applyHealthCheckResult(summary)
        return updatedDtcMs
    }

    private fun applyHealthCheckResult(summary: DiagnosticSummary) {
        val now = System.currentTimeMillis()
        val updatedRecords = findingStateManager.merge(summary.findings, summary.eligibleFindingIds, now)
        _activeFindings.value = updatedRecords
        _findingEventLog.value = findingStateManager.eventLog()
        operatingStateCounts[summary.operatingState] =
            (operatingStateCounts[summary.operatingState] ?: 0) + 1
        vehicleStateHistory.add(0, summary.vehicleState)
        if (vehicleStateHistory.size > MAX_VEHICLE_STATE_HISTORY) {
            vehicleStateHistory.removeAt(vehicleStateHistory.lastIndex)
        }
        totalHealthChecks++
        _healthState.value = HealthState.Ready(summary)
    }

    private fun maybeNotify(newlyActiveFindings: List<com.obdvis.android.domain.health.DiagnosticFinding>) {
        if (!_notificationsEnabled.value) return
        val minSeverity = _notificationMinSeverity.value
        val toNotify = newlyActiveFindings
            .filter { it.severity.ordinal >= minSeverity.ordinal }
            .filter { it.id !in notifiedFindingIds }
        if (toNotify.isEmpty()) return
        notifiedFindingIds += toNotify.map { it.id }
        notificationHelper?.notifyFindings(toNotify)
    }

    @SuppressLint("MissingPermission")
    fun connect(info: BluetoothDeviceInfo) {
        val device = info.device
        if (_connectionState.value is ConnectionState.Connecting) return
        disconnect()
        if (_activeTab.value == ActiveTab.HEALTH) _healthAutoUpdateEnabled.value = true

        _connectionState.value = ConnectionState.Connecting
        _latestValues.value = emptyMap()
        _chartData.value = emptyMap()
        sampleStore.clear()
        peakRpm = 0f; peakSpeedKph = 0f; peakCoolantTempC = 0f

        sessionJob = viewModelScope.launch(Dispatchers.IO) {
            var btSocket: android.bluetooth.BluetoothSocket? = null
            try {
                btSocket = try {
                    device.createRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
                } catch (_: IOException) {
                    device.createInsecureRfcommSocketToServiceRecord(SPP_UUID).also { it.connect() }
                }

                val socketStream = BluetoothSocketStream(btSocket)
                stream = socketStream
                _connectionState.value = ConnectionState.Initializing

                val session = OBDSession(socketStream, ::pidsForActiveTab, ::currentWeightFn, sampleStore)
                activeSession = session
                sessionStartMs = System.currentTimeMillis()
                engineWasRunning = false
                _peakGForce.value = PeakGForce()
                _connectionState.value = ConnectionState.Connected(info.name)
                startBackgroundChecks()

                session.readings().collect { sample -> onSample(sample) }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _connectionState.value = ConnectionState.Error(friendlyBtError(e.message))
            } finally {
                activeSession = null
                btSocket?.close()
                stream = null
                if (_connectionState.value is ConnectionState.Connected ||
                    _connectionState.value is ConnectionState.Initializing
                ) {
                    capturePostDriveIfEligible()
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }
    }

    private fun onSample(sample: SensorSample) {
        if (sample.pidId == "rpm") {
            val rpm = sample.value
            if (rpm >= ENGINE_RUNNING_RPM) {
                engineWasRunning = true
                engineOffJob?.cancel()
                engineOffJob = null
                _driveEndDetected.value = false
            } else if (rpm < ENGINE_OFF_RPM && engineWasRunning && engineOffJob == null) {
                // Only arm the timer when the vehicle is also stopped — on a hybrid the ICE
                // shuts off while cruising in EV mode, which should not trigger end-of-drive.
                val currentSpeed = _latestValues.value["speed"] ?: 0f
                if (currentSpeed < VEHICLE_STOPPED_KPH) {
                    engineOffJob = viewModelScope.launch {
                        delay(ENGINE_OFF_DELAY_MS)
                        _driveEndDetected.value = true
                        engineOffJob = null
                    }
                }
            }
        }
        if (sample.pidId == "speed" && sample.value >= VEHICLE_STOPPED_KPH) {
            // Vehicle started moving while RPM was still at zero (hybrid accelerating on
            // electric power) — cancel the pending end-of-drive timer.
            engineOffJob?.cancel()
            engineOffJob = null
        }

        when (sample.pidId) {
            "rpm"     -> if (sample.value > peakRpm)          peakRpm = sample.value
            "speed"   -> if (sample.value > peakSpeedKph)     peakSpeedKph = sample.value
            "coolant" -> if (sample.value > peakCoolantTempC)  peakCoolantTempC = sample.value
        }

        sampleStore.add(sample)
        _latestValues.update { it + (sample.pidId to sample.value) }
        _chartData.update { current ->
            val entries = (current[sample.pidId] ?: emptyList()).toMutableList()
            entries.add(Entry(sample.elapsedSeconds, sample.value))
            if (entries.size > MAX_ENTRIES) entries.removeAt(0)
            current + (sample.pidId to entries.toList())
        }
    }

    fun startDemo() {
        if (_connectionState.value is ConnectionState.Connecting) return
        disconnect()
        if (_activeTab.value == ActiveTab.HEALTH) _healthAutoUpdateEnabled.value = true

        _connectionState.value = ConnectionState.Connected("Demo Mode")
        _latestValues.value = emptyMap()
        _chartData.value = emptyMap()
        sampleStore.clear()
        peakRpm = 0f; peakSpeedKph = 0f; peakCoolantTempC = 0f

        sessionStartMs = System.currentTimeMillis()
        engineWasRunning = false

        sessionJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val session = DemoSession(simulatedDtcs = listOf("P0300", "P0420"), ::pidsForActiveTab)
                activeSession = session
                startBackgroundChecks()
                session.readings().collect { sample -> onSample(sample) }
            } catch (e: CancellationException) {
                throw e
            } finally {
                activeSession = null
                if (_connectionState.value is ConnectionState.Connected) {
                    _connectionState.value = ConnectionState.Disconnected
                }
            }
        }
    }

    fun disconnect() {
        engineOffJob?.cancel()
        engineOffJob = null
        engineWasRunning = false
        _driveEndDetected.value = false
        bgHealthJob?.cancel()
        bgHealthJob = null
        sessionJob?.cancel()
        sessionJob = null
        activeSession = null
        stream?.close()
        stream = null
        sampleStore.clear()
        _dtcResult.value = DtcReadResult()
        _dtcScreenState.value = DtcScreenState.Idle
        capturePostDriveIfEligible()
        // Reset all per-session health state after capture
        findingStateManager.reset()
        _activeFindings.value = emptyMap()
        _findingEventLog.value = emptyList()
        operatingStateCounts.clear()
        vehicleStateHistory.clear()
        totalHealthChecks = 0
        _healthState.value = HealthState.Idle
        _healthAutoUpdateEnabled.value = false
        _fuelTrimFocused.value = false
        notifiedFindingIds.clear()
        notificationHelper?.cancelStandingNotification()
        if (_connectionState.value !is ConnectionState.Disconnected) {
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    // ── Vehicle Health ─────────────────────────────────────────────────────────

    private val findingStateManager = FindingStateManager()

    /** Live per-finding state — ACTIVE and FADING records only (not PENDING). */
    private val _activeFindings = MutableStateFlow<Map<String, FindingRecord>>(emptyMap())
    val activeFindings: StateFlow<Map<String, FindingRecord>> = _activeFindings.asStateFlow()

    /** Most-recent-first log of finding lifecycle events. */
    private val _findingEventLog = MutableStateFlow<List<FindingEvent>>(emptyList())
    val findingEventLog: StateFlow<List<FindingEvent>> = _findingEventLog.asStateFlow()

    // Per-session operating state distribution and vehicle state history for interpreter
    private val operatingStateCounts = mutableMapOf<OperatingState, Int>()
    private val vehicleStateHistory = mutableListOf<VehicleState>()
    private var totalHealthChecks = 0

    private var peakRpm = 0f
    private var peakSpeedKph = 0f
    private var peakCoolantTempC = 0f
    private val _peakGForce = MutableStateFlow(PeakGForce())
    val peakGForce: StateFlow<PeakGForce> = _peakGForce.asStateFlow()

    private val _healthState = MutableStateFlow<HealthState>(HealthState.Idle)
    val healthState: StateFlow<HealthState> = _healthState.asStateFlow()

    private val _driveEndDetected = MutableStateFlow(false)
    val driveEndDetected: StateFlow<Boolean> = _driveEndDetected.asStateFlow()

    private val _postDriveData = MutableStateFlow<PostDriveData?>(null)
    val postDriveData: StateFlow<PostDriveData?> = _postDriveData.asStateFlow()

    fun dismissPostDrive() {
        _postDriveData.value = null
    }

    private fun capturePostDriveIfEligible() {
        if (totalHealthChecks < MIN_SNAPSHOTS_FOR_SUMMARY) return
        _postDriveData.value = PostDriveData(
            sessionStartMs = sessionStartMs,
            sessionEndMs = System.currentTimeMillis(),
            findingRecords = findingStateManager.snapshotAllRecords(),
            totalHealthChecks = totalHealthChecks,
            operatingStateCounts = operatingStateCounts.toMap(),
            dtcResult = _dtcResult.value,
            peakRpm = peakRpm.takeIf { it > 0f },
            peakSpeedKph = peakSpeedKph.takeIf { it > 0f },
            peakCoolantTempC = peakCoolantTempC.takeIf { it > 0f },
            peakGForce = _peakGForce.value.takeIf { it.left + it.right + it.forward + it.backward > 0.05f },
        )
    }

    // Cached stored DTCs — shared between health analysis and DTC screen
    private val _dtcResult = MutableStateFlow(DtcReadResult())

    private val _dtcScreenState = MutableStateFlow<DtcScreenState>(DtcScreenState.Idle)
    val dtcScreenState: StateFlow<DtcScreenState> = _dtcScreenState.asStateFlow()

    /** Reads DTCs from the ECU and updates the DTC screen state. */
    fun readDtcs() {
        if (activeSession == null) {
            _dtcScreenState.value = DtcScreenState.Error("No active session — connect to a vehicle first.")
            return
        }
        _dtcScreenState.value = DtcScreenState.Reading
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                try { activeSession?.readDtcs() ?: DtcReadResult() }
                catch (e: Exception) { null }
            }
            if (result == null) {
                _dtcScreenState.value = DtcScreenState.Error("Failed to read DTCs from ECU.")
            } else {
                _dtcResult.value = result
                _dtcScreenState.value = DtcScreenState.Loaded(result)
            }
        }
    }

    fun clearDtcs() {
        val session = activeSession ?: return
        val current = _dtcScreenState.value
        if (current is DtcScreenState.Clearing || current is DtcScreenState.Reading) return

        _dtcScreenState.value = DtcScreenState.Clearing
        viewModelScope.launch {
            val cleared = withContext(Dispatchers.IO) {
                try { session.clearDtcs() } catch (_: Exception) { false }
            }
            if (!cleared) {
                _dtcScreenState.value = DtcScreenState.Error("ECU did not acknowledge the clear command.")
                return@launch
            }
            val result = withContext(Dispatchers.IO) {
                try { session.readDtcs() } catch (_: Exception) { DtcReadResult() }
            }
            _dtcResult.value = result
            _dtcScreenState.value = DtcScreenState.Loaded(result, justCleared = true)
        }
    }

    private val _healthAutoUpdateEnabled = MutableStateFlow(false)
    val healthAutoUpdateEnabled: StateFlow<Boolean> = _healthAutoUpdateEnabled.asStateFlow()

    fun toggleHealthAutoUpdate() {
        val nowEnabled = !_healthAutoUpdateEnabled.value
        _healthAutoUpdateEnabled.value = nowEnabled
        if (nowEnabled && activeSession != null) startBackgroundChecks(skipSettle = true)
    }

    private val _normalizeChart = MutableStateFlow(false)
    val normalizeChart: StateFlow<Boolean> = _normalizeChart.asStateFlow()

    fun toggleNormalizeChart() { _normalizeChart.update { !it } }

    fun togglePid(pid: PidDefinition) {
        _enabledPids.update { if (pid in it) it - pid else it + pid }
    }

    fun buildCsvContent(): String {
        val sb = StringBuilder("elapsed_s,pid,value\n")
        val allEntries = mutableListOf<Triple<Float, String, Float>>()
        for ((pidId, entries) in _chartData.value) {
            for (entry in entries) {
                allEntries.add(Triple(entry.x, pidId, entry.y))
            }
        }
        allEntries.sortBy { it.first }
        for ((elapsed, pid, value) in allEntries) {
            sb.append("%.3f,%s,%.4f\n".format(elapsed, pid, value))
        }
        return sb.toString()
    }

    private val _gForce = MutableStateFlow(0f to 0f)
    val gForce: StateFlow<Pair<Float, Float>> = _gForce.asStateFlow()

    fun updateGForce(lateralG: Float, longitudinalG: Float) {
        val (prevLat, prevLon) = _gForce.value
        val alpha = 0.15f
        val smoothedLat = alpha * lateralG + (1f - alpha) * prevLat
        val smoothedLon = alpha * longitudinalG + (1f - alpha) * prevLon
        _gForce.value = smoothedLat to smoothedLon
        val p = _peakGForce.value
        _peakGForce.value = p.copy(
            left     = maxOf(p.left,     (-smoothedLat).coerceAtLeast(0f)),
            right    = maxOf(p.right,    smoothedLat.coerceAtLeast(0f)),
            forward  = maxOf(p.forward,  smoothedLon.coerceAtLeast(0f)),
            backward = maxOf(p.backward, (-smoothedLon).coerceAtLeast(0f)),
        )
    }

    fun resetPeakGForce() { _peakGForce.value = PeakGForce() }

    fun enableAllPids() { _enabledPids.value = PidRegistry.live.toSet() }
    fun disableAllPids() { _enabledPids.value = emptySet() }

    init {
        if (sharedAutoState != null) {
            viewModelScope.launch {
                launch { _connectionState.collect { sharedAutoState.updateConnection(it) } }
                launch { _latestValues.collect { sharedAutoState.updateLatestValues(it) } }
                launch {
                    _activeFindings.collect { records ->
                        val findings = records.values
                            .filter { it.status == FindingStatus.ACTIVE }
                            .map { it.finding }
                        sharedAutoState.updateHealthFindings(findings)
                    }
                }
            }
        }
    }

    private fun friendlyBtError(msg: String?): String = when {
        msg == null -> "Connection failed"
        msg.contains("socket might closed", ignoreCase = true) -> "Bluetooth connection lost"
        msg.contains("socket closed", ignoreCase = true) -> "Bluetooth connection lost"
        msg.contains("Broken pipe", ignoreCase = true) -> "Bluetooth connection lost"
        else -> msg
    }

    override fun onCleared() {
        disconnect()
        super.onCleared()
    }
}
