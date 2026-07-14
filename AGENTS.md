# OBDvis

Android app that visualises real-time OBD-II sensor data from an ELM327 Bluetooth adapter.  
Single activity, Jetpack Compose UI, no third-party architecture libraries.

# Development style

Prioritize correctness, maintainability, and architectural fit

Before implementing non-trivial changes:
1. Inspect the relevant code paths first.
2. Explain the current architecture briefly.
3. Propose 2-3 possible implementation approaches.
4. Compare tradeoffs: simplicity, maintainability, testability, performance, extensibility, and risk.
5. Recommend the best approach.
6. Only then implement.

Avoid quick fixes, duplicated logic, or local hacks unless explicitly requested.

Prefer small, well-contained changes that fit the existing architecture.

When modifying existing behavior:
- Preserve current public APIs unless there is a strong reason to change them.
- Check for related code that should be updated as well.
- Consider edge cases and failure modes.
- Add or update tests where appropriate.
- Run relevant tests or explain why they were not run.

When uncertain, stop and ask rather than guessing.

Do not optimize for minimal diff if a slightly larger refactor produces a cleaner and safer result.

## Key architecture

```
BluetoothSocketStream      — raw I/O wrapper around BluetoothSocket
ElmCommandSender           — sends AT / OBD commands, reads until '>' prompt
ElmInitializer             — ATZ / ATE0 / ATS0 … init sequence
ObdResponseParser          — hex response → float (e.g. "410C1AF8" → RPM)
DtcParser                  — OBD modes 03/07 → DtcReadResult (SAE J2012 encoding)
Session                    — common interface: readings(), readDtcs(), clearDtcs(), pollSnapshot()
OBDSession                 — implements Session; priority-scheduled polling loop, emits Flow<SensorSample>
                             receives enabledPids: () -> List<PidDefinition> and weightFn: () -> (String) -> Float
                             so tab switches take effect immediately without restarting the session
DemoSession                — implements Session; simulated data + fake DTC support
PidPriorityGroups          — two tab-context weight functions + per-signal max-age limits:
                               overviewWeightOf  — rpm/speed/throttle=4, coolant/voltage=2, everything else=1
                               diagnosticWeightOf — stft/ltft/o2/maf/rpm/speed/throttle=4, coolant/timing/etc=2, counters=1
PriorityPollScheduler      — picks next PID via score = age_ms × weightFn(pidId); weight function injected per call
SampleStore                — thread-safe rolling buffer (2-min retention per PID); freshness-gated lookups, window stats (avg/min/max/stdDev)
MainViewModel              — single ViewModel; owns SampleStore + all UI state: connection, chart, health, DTCs, G-force
                             accepts `Application?`; if it is an `OBDVisApp`, bridges connectionState/latestValues/healthFindings into SharedAutoState
AppSettings                — SharedPreferences wrapper: bg check interval, notifications toggle/severity
NotificationHelper         — creates "obdvis_health" channel; notifyFindings() posts alert for worst finding
OBDVisApp                  — Application subclass; holds SharedAutoState singleton
SharedAutoState            — StateFlow bridge between MainViewModel and the Android Auto CarAppService;
                             exposes connectionState, latestValues, healthFindings
```

Screens (all Compose): `PermissionScreen` → `DevicePickerScreen` → `DashboardScreen` → `PostDriveScreen` → `SettingsScreen`

`DashboardScreen` has four active tabs (chip navigation, not TabRow; BackHandler returns to Overview):
1. **Overview** (default) — arc gauges, operating state, G-force indicator, health/DTC quick-nav
2. **Live** — `LineChart` with sidebar and normalize mode
3. **Health** — rule-based diagnostics + monitor readiness status; includes a **Fuel Trim Deep Dive** sub-panel (`FuelTrimDivePanel`) accessible from the health screen, showing a 60 s rolling fuel trim chart, per-bank value grid, and fuel-trim-specific findings
4. **DTCs** — read stored/pending fault codes, clear with confirmation

> **Torque tab removed** — the former Torque tab, estimated torque chart, car-spec dialog, and associated inactive source files were removed during the open-source cleanup. Torque-related OBD PIDs remain in the registry because they are standard sensor values.

Layout adapts to orientation (Live tab): portrait stacks chart over sidebar (max 220 dp); landscape puts sidebar (200 dp) left, content right.

## Android Auto

`auto/` package — read-only driver-safe view powered by the Car App Library (`androidx.car.app:app:1.7.0`):

```
AutoCarAppService   — CarAppService entry point; declared in manifest with category IOT
OBDAutoSession      — Car App Library Session; creates AutoDashboardScreen as the root screen
AutoDashboardScreen — PaneTemplate: RPM / Speed / Coolant / Voltage rows + "All Sensors" and "Health" Pane actions
                      shows MessageTemplate when phone is not connected
AutoSensorListScreen — ListTemplate with sensors grouped by section (Engine / Fuel / Performance / Electrical / Emissions / Environment)
AutoHealthScreen    — ListTemplate of DiagnosticFindings sorted by severity; taps open AutoFindingScreen
AutoFindingScreen   — LongMessageTemplate: full finding description + evidence key-value pairs
```

State is shared via `SharedAutoState` (owned by `OBDVisApp`). `MainViewModel` publishes into it; Auto screens read from it. The Auto interface is **read-only** — no connection initiation, no DTC clearing.

**Manifest requirements (learned during implementation):**
- `androidx.car.app.minCarApiLevel` meta-data must be in **both** `<application>` and `<service>` — 1.7.0 reads from `ApplicationInfo`
- `com.google.android.gms.car.application` pointing to `res/xml/automotive_app_desc.xml` (contains `<uses name="template"/>`) is required for Android Auto discovery
- `PaneTemplate` ActionStrip requires icon-backed actions in 1.7.0 — use `Pane.addAction()` for text-only navigation buttons instead

## PID registry

All supported sensors live in `PidRegistry.kt`. Each `PidDefinition` has:
`id`, `hex`, `name`, `unit`, `min`, `max`, `color`, `formula: (ByteArray) -> Float`, `displayInLive: Boolean`

`displayInLive = false` hides a PID from the Live tab chart/sidebar (e.g. `monitor_readiness`, `o2_present`).

48 PIDs total:
`fuel_sys_status`, `rpm`, `speed`, `coolant`, `load`, `throttle`, `intake_temp`, `maf`, `manifold`,
`fuel`, `stft`, `ltft`, `stft2`, `ltft2`, `fuel_pressure`, `timing`, `o2_b1s1`, `o2_b1s2`,
`run_time`, `dist_cleared`, `baro`, `abs_load`, `rel_throttle`, `ambient_temp`, `mil_time`,
`oil_temp`, `fuel_rate`, `engine_torque`, `ecu_voltage`, `obd_voltage`,
`catalyst_temp_b1s1`, `catalyst_temp_b1s2`, `ref_torque`, `dist_mil`, `time_cleared`,
`warmups_cleared`, `fuel_rail_pressure`, `fuel_rail_pressure_gdi`, `egr`, `ethanol`,
`accel_pedal`, `egr_error`, `lambda`, `driver_torque`, `evap_purge`, `evap_pressure`,
`o2_present`, `monitor_readiness`

`PidRegistry.health` — the 40-PID subset actually consumed by `VehicleState` / `DiagnosticsInterpreter`.
Excludes: `ref_torque`, `dist_mil`, `time_cleared`, `warmups_cleared`, `fuel_rail_pressure`, `fuel_rail_pressure_gdi`, `accel_pedal`, `ethanol`.

## Data flow

`OBDSession` / `DemoSession` → `Flow<SensorSample>` → `MainViewModel.onSample()` → StateFlows + `SampleStore`:
- `_latestValues`    — `Map<pidId, Float>` for sidebar / overview gauges
- `_chartData`       — `Map<pidId, List<Entry>>` capped at 600 entries (~60 s at ~10 Hz)
- `_healthState`     — `HealthState` sealed class (`Idle / Ready`); `Ready` wraps the latest `DiagnosticSummary` for health tab display
- `_activeFindings`  — `Map<String, FindingRecord>` managed by `FindingStateManager`; persists across interpreter cycles with ACTIVE/FADING lifecycle
- `_findingEventLog` — `List<FindingEvent>` (max 200); timestamped `Appeared / Cleared / SeverityChanged` events shown on the Health tab event log
- `vehicleStateHistory` — rolling `List<VehicleState>` (max 300); passed as `recentStates` to `DiagnosticsInterpreter` for state-conditioned rules
- `sampleStore`      — rolling per-PID buffer; freshness-gated; feeds diagnostics and the priority scheduler

Each `SensorSample` now carries `timestampMs` (wall-clock ms) and `latencyMs` (ECU round-trip time). Do not assume readings within one cycle share a timestamp.

`pidsForActiveTab()` and `currentWeightFn()` in `MainViewModel` control what gets polled and at what relative frequency. Both are re-evaluated every poll iteration so tab switches take effect immediately.

| Context | PID list | Weight function |
|---|---|---|
| Overview (foregrounded) | `PidRegistry.health` (40 PIDs) | `overviewWeightOf` — rpm/speed/throttle=4, coolant/voltage=2, rest=1 |
| Health tab (foregrounded) | `PidRegistry.health` (40 PIDs) | `diagnosticWeightOf` — fuel-trim/O2/MAF/rpm=4, coolant/timing/etc=2, counters=1 |
| Live tab | user-enabled PIDs | `diagnosticWeightOf` |
| DTC tab | `PidRegistry.health` (40 PIDs) | `diagnosticWeightOf` |
| Backgrounded | `PidRegistry.health` (40 PIDs) | `diagnosticWeightOf` — health checks still run, no point polling display-only PIDs |
| Fuel Trim Deep Dive open | `stft`, `ltft`, `stft2`, `ltft2` only | `diagnosticWeightOf` |

The enabled/disabled PID toggle on the Live tab is **display-only** (chart lines + sidebar); it does not affect which PIDs are polled on other tabs.

`OBDSession` uses `PriorityPollScheduler` to pick one PID per loop iteration (score = age_ms × weightFn(pidId)) rather than iterating all PIDs in fixed order. Voltage (ATRV) is interleaved every 5 s on its own schedule.

`Session.pollSnapshot()` — still exists on the interface and both implementations, but is **no longer called by the health-check path**; diagnostics now consume `SampleStore` directly.

## G-force / accelerometer

`MainActivity` registers a `SensorEventListener` on `Sensor.TYPE_LINEAR_ACCELERATION`. Raw m/s² values are divided by 9.81 and passed to `MainViewModel` as `(lateralG, longitudinalG)`. The Overview tab displays a 2D G-force vector indicator.

## Background health monitoring

### Call chain

`startBackgroundChecks()` — coroutine loop; runs for the lifetime of the session. Restarts on tab change (to pick up timing changes) or when `healthAutoUpdateEnabled` is toggled. Takes `skipSettle: Boolean` — if false, waits 10 s after connect for PIDs to settle before the first check.

Each iteration calls `performHealthCheck(session, lastDtcMs)`:
1. If > 5 min since last DTC read: calls `session.readDtcs()` on `Dispatchers.IO`, updates `_dtcResult` and `_dtcScreenState`
2. Builds `VehicleState.fromSampleStore(sampleStore)` with the latest DTC list attached
3. Calls `DiagnosticsInterpreter.interpret(state, sampleStore, vehicleStateHistory.toList())` on `Dispatchers.Default`
4. Calls `applyHealthCheckResult(summary)`:
   - Calls `findingStateManager.merge(summary.findings, now)` → returns `Map<String, FindingRecord>`; updates `_activeFindings` and `_findingEventLog`
   - Appends `summary.vehicleState` to `vehicleStateHistory` (max 300 entries)
   - Increments `totalHealthChecks`; updates `operatingStateCounts`
   - Sets `_healthState = HealthState.Ready(summary)`

### Timing per context

| Context | Interval |
|---|---|
| Health tab (foregrounded, auto-update on) | 1 s between iterations |
| Overview tab (foregrounded) | 30 s |
| DTC tab (foregrounded) | 30 s |
| Other foreground tabs | 30 s |
| Backgrounded | `bgCheckIntervalMs` (default 2 min, user-configurable in `AppSettings`) |

Background path also calls `maybeNotify(newlyActiveFindings)` — posts a notification via `NotificationHelper` for findings at or above `notificationMinSeverity` if `notificationsEnabled` is true. Only findings not already in `notifiedFindingIds` are posted.

### Finding lifecycle (`FindingStateManager`)

`FindingStateManager.merge(newFindings, now)` is called after every interpreter cycle. It maintains a persistent `Map<String, InternalRecord>` with three internal states:

- **PENDING** — finding appeared but has not yet been continuously present for `PERSISTENCE_THRESHOLD_MS` (15 s). Used for inherently noisy signals: `o2_upstream_dead`, `o2_upstream_lazy`, `timing_retarded`, `charging_voltage_low`. Disappears silently if the condition clears before graduating.
- **ACTIVE** — finding is visible in `_activeFindings` and displayed on the Health tab.
- **FADING** — condition cleared; remains in `_activeFindings` as `FindingStatus.FADING` for `FADING_DURATION_MS` (15 s) so the user has time to notice, then moves to `clearedRecords`.

`FindingRecord` (public type) carries: `finding`, `firstSeenMs`, `lastSeenMs`, `occurrenceCount` (cumulative across re-activations), `peakSeverity`, `status` (ACTIVE/FADING), `fadingSinceMs`.

`FindingEvent` — timestamped lifecycle events emitted by `FindingStateManager`: `Appeared`, `Cleared`, `SeverityChanged`. Kept in a 200-entry ring buffer; exposed as `_findingEventLog`.

`snapshotAllRecords()` — returns all records seen this session including fully-cleared ones; used when capturing `PostDriveData`.

`reset()` — called on disconnect/new session.

### `_healthAutoUpdateEnabled`

`StateFlow<Boolean>` that gates the Health tab's continuous-update branch. User-togglable via the **Auto** chip on the Health tab — pausing simply freezes the current view (there is no manual refresh; resuming picks continuous updates back up).

## Post-drive summary

`PostDriveScreen` appears when a drive ends and a summary is captured — triggered either by:
- **"End Drive" button** (repurposed disconnect button in `DashboardScreen`): calls `disconnect()` → `capturePostDriveIfEligible()` → sets `postDriveData` directly. Button tint changes to amber when engine-off is detected, to prompt the user.
- **BT drop** (finally block in session coroutine): same capture, guarded by `MIN_SNAPSHOTS_FOR_SUMMARY = 3`

**Engine-off detection** (`_driveEndDetected: StateFlow<Boolean>`): RPM first exceeds 200 RPM (sets `engineWasRunning = true`), then stays below 50 RPM for 5 s with speed < `VEHICLE_STOPPED_KPH` (5 kph) → sets `_driveEndDetected = true` (highlights the End Drive button). RPM coming back above threshold resets the flag. Hybrid-aware: timer is cancelled if a speed sample shows the car moving while RPM is zero.

`PostDriveData` (in `domain/health/`) is built by `capturePostDriveIfEligible()` using session accumulators from `MainViewModel`:
- `findingRecords: List<FindingRecord>` — all findings seen this session (active, fading, and cleared), from `findingStateManager.snapshotAllRecords()`
- `operatingStateCounts` / `operatingStateBreakdown` — count per `OperatingState`, accumulated in `applyHealthCheckResult()`
- `aggregatedFindings: List<AggregatedFinding>` — computed from `findingRecords`; uses `peakSeverity`, ranked by severity then `occurrenceCount`; `AggregatedFinding` carries `occurrences` and `totalSnapshots`
- `peakRpm`, `peakSpeedKph`, `peakCoolantTempC`, `peakGForce` — tracked in `MainViewModel` directly from `onSample()` / G-force events

Once `postDriveData != null`, `PostDriveScreen` takes precedence over all other screens. Dismissing the summary calls `viewModel.dismissPostDrive()` which nulls `postDriveData`.

## Vehicle health & diagnostics

`DiagnosticsInterpreter` (stateless object) derives `DiagnosticFinding` list from a `VehicleState` + `SampleStore` + optional `recentStates`:

Signature: `interpret(state: VehicleState, store: SampleStore, recentStates: List<VehicleState> = emptyList())`

- **Time-based rules** (fuel trim severity, bank diff, O2 patterns) consume `store.stats(pidId, 30_000)` — rolling 30-second windows of real sensor readings, not snapshot counts
- **State-conditioned rules** (idle RPM stability, fuel-trim pattern detection — vacuum leak / fuel delivery / MAF / rich-running — timing retard) still use `recentStates` because they require per-snapshot operating-state classification
- Operating state detection: `COLD_START`, `WARM_IDLE`, `CRUISE`, `ACCELERATION`, `DECELERATION`, `UNKNOWN`
- Rules (in order): coolant temperature, oil temperature (cold at high RPM / overheating), operating state label, idle RPM stability (high / low / hunting, rolling std-dev over 5-snapshot window), fuel trim severity per bank (warn / likely / strong lean or rich), inter-bank fuel trim imbalance, fuel trim cross-condition patterns (vacuum leak, fuel delivery under load, MAF under-reporting, rich-running), upstream O2 (dead sensor, lazy/not-switching in closed loop), downstream O2 / catalyst (tracking upstream or persistently high voltage), ignition timing retard under load, fuel consumption rate at idle and cruise, charging voltage, ECU voltage drop vs OBD port, MIL runtime, distance since codes cleared / monitor readiness, fuel loop status (open-loop fault, closed-loop fault, warm-engine open loop), EGR error, EVAP leak, monitor readiness, DTC presence
- All thresholds are explicit private constants — no ML
- Produces `dataLimitations: List<String>` (missing/stale signals, absent condition windows) and `windowContext: DiagnosticWindowContext` (30 s stats for all key PIDs) alongside findings

`VehicleState` has two factories:
- `fromSampleStore(store)` — **primary path**; each field uses `PidPriorityGroups.maxAgeMs(pidId)` as its freshness limit; stale readings become `null` rather than silently lagging
- `fromLatestValues(map)` — legacy; still used in tests and any path without a live store

`HealthState` sealed class: `Idle → Ready`

## DTC reading & clearing

`DtcParser` handles modes 03 (stored) and 07 (pending), multi-frame responses, P/C/B/U prefix decoding.  
`DtcScreenState` sealed class: `Idle → Reading → Loaded → Clearing`.  
Clear requires confirmation dialog; success shows a banner.

## Live chart

MPAndroidChart `LineChart` wrapped in `AndroidView`. Key behaviours:
- ELM327 uses `ATS0` (no spaces), so responses look like `"410C1AF8"` not `"41 0C 1A F8"`
- Normalize mode (`%` chip in header): scales each PID to `(v − min)/(max − min) × 100` so sensors with very different ranges are directly comparable on one axis
- CSV export via `MainViewModel.buildCsvContent()` (all chart entries, sorted by elapsed time)

## Demo mode

`DevicePickerScreen` has a **Demo** button (footer). Starts `DemoSession`: a 60 s repeating driving cycle — idle → WOT acceleration → cruise → deceleration. RPM and speed use exponential-smoothing inertia. `DemoSession` also returns fake DTC codes for testing the DTC tab.

## Conventions

- Coroutines: IO-bound work on `Dispatchers.IO`, UI state via `MutableStateFlow`
- No dependency injection framework — dependencies passed directly
- Bump `versionCode` and `versionName` in `android/app/build.gradle.kts` with every meaningful change; the version is displayed in `SettingsScreen`
- Dark theme only (`Theme.kt`): `Background`, `Surface`, `Border`, `SubText`, `Primary` colour tokens
- No error handling for scenarios that can't happen; no speculative abstractions
