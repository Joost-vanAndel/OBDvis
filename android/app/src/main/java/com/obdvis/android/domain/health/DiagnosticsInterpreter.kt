package com.obdvis.android.domain.health

import com.obdvis.android.domain.model.SensorSample
import com.obdvis.android.domain.store.SampleStore
import java.util.Locale
import kotlin.math.abs

/**
 * Stateless, rule-based interpreter that derives diagnostic findings from a [VehicleState].
 *
 * Rules are kept explicit and isolated so thresholds are easy to tune and
 * new rules can be added without touching existing ones.
 *
 * Time-based rules consume [SampleStore] rolling windows so they reflect true
 * elapsed time rather than snapshot counts. State-conditioned rules (idle RPM
 * stability, fuel-trim pattern detection) still use [recentStates] because they
 * need operating-state context that is only available in captured snapshots.
 *
 * Each private rule function returns a [RuleResult] carrying both the findings it
 * produced and the set of finding IDs whose rule was eligible to evaluate this cycle
 * (i.e., all preconditions were met). The eligible set is used by [FindingStateManager]
 * to compute per-finding hit rates for the post-drive summary.
 */
object DiagnosticsInterpreter {

    // ── Thresholds — easy to tune ──────────────────────────────────────────
    private const val COLD_COOLANT_C        = 40f   // below → engine cold
    private const val WARM_COOLANT_C        = 80f   // above → fully warm
    private const val HOT_COOLANT_C         = 105f  // above → overheating warning
    private const val IDLE_RPM_MAX          = 1600f
    private const val IDLE_SPEED_KPH_MAX    = 5f
    private const val IDLE_THROTTLE_MAX     = 12f
    private const val IDLE_RPM_HIGH         = 1100f // warm idle RPM above this → possible issue
    private const val IDLE_RPM_LOW          = 500f  // warm idle RPM below this → rough idle
    private const val IDLE_RPM_WINDOW_MS    = 30_000L // time-based window of warm-idle readings
    private const val IDLE_RPM_MIN_SAMPLES  = 5     // minimum before flagging any idle-RPM finding
    private const val IDLE_RPM_ROUGH_STDDEV = 100f  // RPM std-dev above this → hunting/unstable idle
    private const val ACCEL_THROTTLE_MIN    = 40f
    private const val CRUISE_SPEED_MIN      = 10f
    private const val CRUISE_THROTTLE_MAX   = 50f
    private const val DECEL_THROTTLE_MAX    = 5f
    private const val DECEL_SPEED_MIN       = 10f
    private const val DECEL_TREND_WINDOW_MS       = 5_000L
    private const val DECEL_TREND_MIN_SAMPLES     = 3
    private const val DECEL_TREND_MIN_COVERAGE_MS = 2_000L
    private const val DECEL_RATE_MAX_KPH_PER_SEC  = -0.5f
    private const val DECEL_MIN_SPEED_DROP_KPH    = 2f
    private const val FUEL_TRIM_WARN_PCT          = 10f  // |avg combined| above → warning
    private const val FUEL_TRIM_LIKELY_FAULT_PCT  = 15f  // |avg combined| above → likely fault
    private const val FUEL_TRIM_STRONG_FAULT_PCT  = 20f  // |avg combined| above → strong fault
    private const val FUEL_TRIM_WINDOW_MS         = 30_000L // rolling window for per-bank lean/rich checks
    private const val FUEL_TRIM_MIN_SAMPLES       = 5    // minimum readings before flagging (store-based)
    private const val FUEL_TRIM_PATTERN_WINDOW_MS  = 300_000L // 5 min time-based window for cross-condition pattern detection
    private const val FUEL_TRIM_MIN_COND_SAMPLES  = 3    // min samples per operating condition for patterns
    private const val FUEL_TRIM_VACUUM_LEAK_DELTA = 8f   // idle trim − cruise trim → vacuum leak pattern
    private const val FUEL_TRIM_IDLE_NORMAL_PCT   = 8f   // idle trim below this = "normal" for fuel delivery check
    private const val FUEL_TRIM_ACCEL_LEAN_PCT    = 10f  // accel trim above this for fuel delivery pattern
    private const val FUEL_TRIM_CROSS_COND_PCT    = 5f   // both idle+cruise positive/negative → MAF/rich running
    private const val FUEL_TRIM_BANK_DIFF_PCT     = 8f   // inter-bank difference → bank-specific issue
    private const val CHARGING_VOLTAGE_LOW         = 13.0f // system voltage below this while running → possible charging issue
    private const val CHARGING_VOLTAGE_WINDOW_MS   = 30_000L
    private const val CHARGING_VOLTAGE_MIN_SAMPLES = 3
    private const val OIL_TEMP_COLD_C       = 60f   // below this + high RPM → oil not yet at operating temp
    private const val OIL_TEMP_HOT_C        = 140f  // above this → oil overheating
    private const val OIL_HIGH_RPM          = 3000f // threshold for "high RPM" in cold-oil check
    private const val TIMING_LOW_DEG            = 5f        // below this at cruise/load → retard concern
    private const val TIMING_WINDOW_MS          = 180_000L  // 3 min time-based window for timing retard checks
    private const val TIMING_MIN_SAMPLES        = 3         // min under-load readings before flagging
    private const val TIMING_CONFIDENT_SAMPLES  = 6         // samples needed to raise confidence to MEDIUM
    private const val O2_STUCK_LOW_V        = 0.35f // upstream O2 stuck in this range → possible lazy sensor
    private const val O2_STUCK_HIGH_V       = 0.55f
    private const val O2_SENSOR_DEAD_V      = 0.05f // upstream below this → possible dead sensor
    private const val O2_WINDOW_MS          = 30_000L // rolling window for pattern-based O2 checks
    private const val O2_MIN_SAMPLES        = 5     // minimum readings before pattern checks fire
    private const val O2_LAZY_RANGE_V       = 0.15f // upstream voltage range below this over window → not switching
    private const val O2_UPSTREAM_SWING_V   = 0.30f // upstream must swing at least this to be "cycling"
    private const val O2_DOWNSTREAM_SWING_V = 0.25f // minimum rear activity before comparing it with the front sensor
    private const val CATALYST_MIN_SAMPLES             = 8
    private const val CATALYST_MIN_COVERAGE_MS         = 15_000L
    private const val CATALYST_SAMPLE_MAX_AGE_MS       = 10_000L
    private const val CATALYST_MIN_ENGINE_RUNTIME_SEC  = 300f
    private const val CATALYST_MIN_SPEED_KPH           = 20f
    private const val CATALYST_MAX_SPEED_KPH           = 130f
    private const val CATALYST_MIN_THROTTLE_PCT        = 3f
    private const val CATALYST_MAX_THROTTLE_PCT        = 45f
    private const val CATALYST_MIN_LOAD_PCT            = 10f
    private const val CATALYST_MAX_LOAD_PCT            = 70f
    private const val CATALYST_MAX_SPEED_RANGE_KPH     = 15f
    private const val CATALYST_MAX_THROTTLE_RANGE_PCT  = 10f
    private const val CATALYST_MAX_LOAD_RANGE_PCT      = 20f
    private const val CATALYST_MIN_ACTIVITY_RATIO      = 0.50f
    private const val AFR_SENSOR_DEAD_LAMBDA   = 0.05f // wideband lambda near zero → dead/shorted sensor
    private const val AFR_MIN_VARIATION_LAMBDA = 0.02f // max-min range below this in closed loop → sensor not varying
    private const val WARM_OL_WARN_SEC      = 60f   // engine warm but still OL for this long → flag it
    private const val MIL_RUNTIME_WARN_MIN  = 30f   // MIL on for this long → surface it
    private const val DIST_SINCE_CLEAR_KM   = 50f   // below this → monitors may be incomplete (fallback when 0x01 unavailable)
    private const val EGR_COMMANDED_MIN_PCT = 5f    // ignore EGR error below this commanded value
    private const val EGR_ERROR_WARN_PCT    = 25f   // EGR error above this → valve not following commands
    private const val EVAP_PURGE_MIN_PCT    = 10f   // purge must be active before checking pressure
    private const val EVAP_LEAK_PRESSURE_PA = -250f // less negative than this during active purge → possible leak
    private const val EVAP_WINDOW_MS        = 30_000L
    private const val EVAP_MIN_SAMPLES      = 3
    private const val FUEL_RATE_IDLE_HIGH    = 2.5f  // L/h at idle above this → rich/injector concern
    private const val FUEL_RATE_CRUISE_HIGH  = 12f   // L/h at cruise above this → unusually high
    private const val FUEL_RATE_WINDOW_MS    = 30_000L
    private const val FUEL_RATE_MIN_SAMPLES  = 3
    private const val VOLTAGE_DROP_WARN_V          = 0.5f  // OBD port vs ECU supply drop above this → wiring concern
    private const val VOLTAGE_DROP_HIGH_V          = 1.0f  // above this → likely fault
    private const val VOLTAGE_DROP_WINDOW_MS       = 30_000L
    private const val VOLTAGE_DROP_MIN_SAMPLES     = 3
    private const val THERMOSTAT_WARMUP_SEC        = 600f  // engine must have run this long before flagging
    private const val THERMOSTAT_STUCK_OPEN_C      = 70f   // coolant below this after warm-up → possible stuck-open thermostat
    private const val THERMOSTAT_AMBIENT_COLD_C    = -10f  // below this ambient, reduce confidence on thermostat finding
    private const val EV_DRIVE_SPEED_MIN_KPH       = 5f    // vehicle moving above this with engine off → hybrid EV drive

    // ── RuleResult ─────────────────────────────────────────────────────────

    /**
     * Return type for every private rule function.
     * [findings] — zero or more findings produced this cycle.
     * [eligibleIds] — all finding IDs whose rule was eligible to evaluate (preconditions met),
     *                 regardless of whether a finding actually fired.
     */
    private data class RuleResult(
        val findings: List<DiagnosticFinding> = emptyList(),
        val eligibleIds: Set<String> = emptySet(),
    ) {
        operator fun plus(other: RuleResult) = RuleResult(
            findings   = this.findings + other.findings,
            eligibleIds = this.eligibleIds + other.eligibleIds,
        )
        companion object {
            val NONE = RuleResult()
        }
    }

    // ──────────────────────────────────────────────────────────────────────

    fun interpret(
        state: VehicleState,
        store: SampleStore,
        recentStates: List<VehicleState> = emptyList(),
    ): DiagnosticSummary {
        val currentOperatingState = detectOperatingState(
            state = state,
            speedIsDecreasing = hasSustainedSpeedDrop(store),
        )
        val opStateCache: Map<VehicleState, OperatingState> =
            recentStates.associateWith { detectOperatingState(it, speedIsDecreasing = false) }
        val opStateOf: (VehicleState) -> OperatingState = { s ->
            if (s === state) currentOperatingState
            else opStateCache.getOrElse(s) { detectOperatingState(s, speedIsDecreasing = false) }
        }

        if (!hasMinimumData(state)) {
            return DiagnosticSummary(
                timestamp        = state.timestamp,
                operatingState   = OperatingState.UNKNOWN,
                vehicleState     = state,
                findings         = listOf(insufficientDataFinding()),
                dataLimitations  = buildDataLimitations(state, store, recentStates, opStateOf),
                windowContext    = buildWindowContext(store, recentStates, opStateOf),
            )
        }

        val operatingState = currentOperatingState
        val results = buildList {
            add(temperatureFinding(state))
            add(stuckThermostatFinding(state, recentStates, opStateOf))
            add(oilTempFinding(state))
            add(operatingStateFinding(operatingState, state))
            add(idleRpmFinding(operatingState, state, recentStates, opStateOf))
            add(fuelTrimFinding(state, store, recentStates, opStateOf))
            add(o2Finding(state, store))
            add(timingFinding(operatingState, state, recentStates, opStateOf))
            add(fuelRateFinding(operatingState, state, store))
            add(chargingFinding(state, store))
            add(voltageDropFinding(state, store))
            add(milRuntimeFinding(state))
            add(fuelLoopFinding(state))
            add(egrErrorFinding(state))
            add(evapFinding(state, store))
            add(monitorReadinessFinding(state))
            add(dtcFinding(state))
        }

        return DiagnosticSummary(
            timestamp           = state.timestamp,
            operatingState      = operatingState,
            vehicleState        = state,
            findings            = results.flatMap { it.findings },
            eligibleFindingIds  = results.flatMapTo(mutableSetOf()) { it.eligibleIds },
            dataLimitations     = buildDataLimitations(state, store, recentStates, opStateOf),
            windowContext       = buildWindowContext(store, recentStates, opStateOf),
        )
    }

    // ── Operating state detection ──────────────────────────────────────────

    private fun detectOperatingState(
        state: VehicleState,
        speedIsDecreasing: Boolean,
    ): OperatingState {
        if (!state.engineOn) {
            return if ((state.speedKph ?: 0f) >= EV_DRIVE_SPEED_MIN_KPH) OperatingState.EV_DRIVE
                   else OperatingState.UNKNOWN
        }
        val rpm = state.rpm ?: return OperatingState.UNKNOWN

        val coolant = state.coolantTempC
        if (coolant != null && coolant < COLD_COOLANT_C) return OperatingState.COLD_START

        val speed = state.speedKph ?: return OperatingState.UNKNOWN
        val throttle = state.throttlePct ?: return OperatingState.UNKNOWN

        if (speed <= IDLE_SPEED_KPH_MAX && rpm <= IDLE_RPM_MAX && throttle <= IDLE_THROTTLE_MAX) {
            return if (coolant != null && coolant < WARM_COOLANT_C) OperatingState.COLD_START
            else OperatingState.WARM_IDLE
        }

        if (throttle >= ACCEL_THROTTLE_MIN) return OperatingState.ACCELERATION

        if (speedIsDecreasing && throttle <= DECEL_THROTTLE_MAX && speed >= DECEL_SPEED_MIN) {
            return OperatingState.DECELERATION
        }

        if (speed >= CRUISE_SPEED_MIN && throttle <= CRUISE_THROTTLE_MAX) return OperatingState.CRUISE

        return OperatingState.UNKNOWN
    }

    /**
     * Uses a least-squares speed slope so one quantized or noisy PID 0x0D reading does not
     * turn low-throttle cruising into deceleration. Both a minimum rate and a minimum total
     * drop are required over a sufficiently covered window.
     */
    private fun hasSustainedSpeedDrop(store: SampleStore): Boolean {
        val samples = store.window("speed", DECEL_TREND_WINDOW_MS)
            .sortedBy { it.timestampMs }
        if (samples.size < DECEL_TREND_MIN_SAMPLES) return false

        val firstTimestamp = samples.first().timestampMs
        val coverageMs = samples.last().timestampMs - firstTimestamp
        if (coverageMs < DECEL_TREND_MIN_COVERAGE_MS) return false

        val timesSec = samples.map { (it.timestampMs - firstTimestamp) / 1_000f }
        val meanTime = timesSec.average().toFloat()
        val meanSpeed = samples.map { it.value }.average().toFloat()
        var covariance = 0f
        var timeVariance = 0f
        samples.indices.forEach { index ->
            val timeDelta = timesSec[index] - meanTime
            covariance += timeDelta * (samples[index].value - meanSpeed)
            timeVariance += timeDelta * timeDelta
        }
        if (timeVariance == 0f) return false

        val rateKphPerSec = covariance / timeVariance
        val estimatedDropKph = -rateKphPerSec * (coverageMs / 1_000f)
        return rateKphPerSec <= DECEL_RATE_MAX_KPH_PER_SEC &&
            estimatedDropKph >= DECEL_MIN_SPEED_DROP_KPH
    }

    // ── Individual rules ───────────────────────────────────────────────────

    private fun temperatureFinding(state: VehicleState): RuleResult {
        val coolant = state.coolantTempC ?: return RuleResult.NONE
        val eligible = setOf("engine_overheating", "engine_cold", "engine_warming", "engine_warm")
        val finding = when {
            coolant >= HOT_COOLANT_C -> DiagnosticFinding(
                id          = "engine_overheating",
                title       = "Engine overheating",
                description = "Above the safe operating limit. Stop as soon as it is safe — continuing to drive risks severe engine damage.",
                severity    = FindingSeverity.HIGH,
                confidence  = FindingConfidence.HIGH,
                evidence    = mapOf("Coolant temp" to "${coolant.toInt()}°C"),
            )
            coolant < COLD_COOLANT_C -> DiagnosticFinding(
                id          = "engine_cold",
                title       = "Engine warming up",
                description = "Normal shortly after a cold start. Avoid high loads until the engine reaches operating temperature.",
                severity    = FindingSeverity.INFO,
                confidence  = FindingConfidence.HIGH,
                evidence    = mapOf("Coolant temp" to "${coolant.toInt()}°C"),
            )
            coolant < WARM_COOLANT_C -> DiagnosticFinding(
                id          = "engine_warming",
                title       = "Engine still warming",
                description = "Engine is approaching normal operating temperature.",
                severity    = FindingSeverity.INFO,
                confidence  = FindingConfidence.HIGH,
                evidence    = mapOf("Coolant temp" to "${coolant.toInt()}°C"),
            )
            else -> DiagnosticFinding(
                id          = "engine_warm",
                title       = "Engine at operating temperature",
                description = "Temperature within normal operating range.",
                severity    = FindingSeverity.INFO,
                confidence  = FindingConfidence.HIGH,
                evidence    = mapOf("Coolant temp" to "${coolant.toInt()}°C"),
            )
        }
        return RuleResult(listOf(finding), eligible)
    }

    private fun stuckThermostatFinding(
        state: VehicleState,
        recentStates: List<VehicleState>,
        opStateOf: (VehicleState) -> OperatingState,
    ): RuleResult {
        val runTimeSec = state.engineRunTimeSec ?: return RuleResult.NONE
        val coolant    = state.coolantTempC     ?: return RuleResult.NONE
        if (runTimeSec < THERMOSTAT_WARMUP_SEC) return RuleResult.NONE
        if (coolant >= THERMOSTAT_STUCK_OPEN_C) return RuleResult.NONE
        // On hybrids the engine runs intermittently, so accumulated run time can be spread
        // over a much longer wall-clock drive. If EV_DRIVE states are present in recent
        // history, the slow warm-up is expected rather than a fault.
        if (recentStates.any { opStateOf(it) == OperatingState.EV_DRIVE }) return RuleResult.NONE
        val eligible   = setOf("thermostat_stuck_open")
        val ambient    = state.ambientTempC
        val coldAmbient = ambient != null && ambient < THERMOSTAT_AMBIENT_COLD_C
        val description = buildString {
            append("Engine has been running for ${(runTimeSec / 60).toInt()} minutes but coolant temperature has not reached normal operating range. ")
            append("A stuck-open thermostat prevents the engine from warming up, causing poor fuel economy, higher emissions, and accelerated wear. ")
            append("A failed coolant temperature sensor reporting falsely low values is an alternative cause.")
            if (coldAmbient) append(" Cold ambient conditions (${ambient!!.toInt()}°C) slow warmup — a true stuck-thermostat fault remains possible, but confidence is reduced.")
        }
        return RuleResult(
            listOf(DiagnosticFinding(
                id          = "thermostat_stuck_open",
                title       = "Thermostat may be stuck open",
                description = description,
                severity    = FindingSeverity.MEDIUM,
                confidence  = if (coldAmbient) FindingConfidence.LOW else FindingConfidence.MEDIUM,
                evidence    = buildMap {
                    put("Coolant temp", "${coolant.toInt()}°C")
                    put("Run time",     "${(runTimeSec / 60).toInt()} min")
                    ambient?.let { put("Ambient temp", "${it.toInt()}°C") }
                },
            )),
            eligible,
        )
    }

    private fun operatingStateFinding(
        opState: OperatingState,
        state: VehicleState,
    ): RuleResult {
        val eligible = setOf(
            "idle_state", "cold_start", "cruise_state",
            "acceleration_state", "deceleration_state", "ev_drive_state",
        )
        val evidence = buildEvidenceMap(state)
        val finding = when (opState) {
            OperatingState.WARM_IDLE -> DiagnosticFinding(
                id          = "idle_state",
                title       = "At idle",
                description = "Vehicle is at idle. Sensor readings look consistent with normal idle conditions.",
                severity    = FindingSeverity.INFO,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = evidence,
            )
            OperatingState.COLD_START -> DiagnosticFinding(
                id          = "cold_start",
                title       = "Cold start / warm-up",
                description = "Engine is running but not yet at full operating temperature. Elevated idle RPM may be normal.",
                severity    = FindingSeverity.INFO,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = evidence,
            )
            OperatingState.CRUISE -> DiagnosticFinding(
                id          = "cruise_state",
                title       = "Steady cruise",
                description = "Vehicle appears to be in steady-state cruise. Good conditions for evaluating baseline sensor values.",
                severity    = FindingSeverity.INFO,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = evidence,
            )
            OperatingState.ACCELERATION -> DiagnosticFinding(
                id          = "acceleration_state",
                title       = "Under acceleration",
                description = "Vehicle is under active acceleration. Load and fuel demand are elevated — this is expected.",
                severity    = FindingSeverity.INFO,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = evidence,
            )
            OperatingState.DECELERATION -> DiagnosticFinding(
                id          = "deceleration_state",
                title       = "Decelerating",
                description = "Vehicle is decelerating with low throttle. Fuel cut may be active.",
                severity    = FindingSeverity.INFO,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = evidence,
            )
            OperatingState.EV_DRIVE -> DiagnosticFinding(
                id          = "ev_drive_state",
                title       = "Running on electric drive",
                description = "Engine is off while the vehicle is moving — ICE-specific diagnostics (fuel trim, O2 sensors, fuel loop) are paused until the engine restarts.",
                severity    = FindingSeverity.INFO,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = evidence,
            )
            OperatingState.UNKNOWN -> return RuleResult.NONE
        }
        return RuleResult(listOf(finding), eligible)
    }

    private fun fuelTrimFinding(state: VehicleState, store: SampleStore, recentStates: List<VehicleState>, opStateOf: (VehicleState) -> OperatingState): RuleResult {
        // Severity and bank-diff rules consume 30s store stats that may include open-loop STFT
        // readings where the ECU ignores O2 feedback. Skip them when confirmed open loop.
        // Pattern findings classify each historical state by operating condition and exclude
        // COLD_START/UNKNOWN points, so they are naturally immune to transient open-loop periods.
        val closedLoopResult: RuleResult
        val severityResult: RuleResult
        if (state.isClosedLoop != false) {
            severityResult = fuelTrimSeverityFindings(state, store)
            closedLoopResult = severityResult + fuelTrimBankDiffFinding(state, store)
        } else {
            severityResult = RuleResult.NONE
            closedLoopResult = RuleResult.NONE
        }
        // Track which banks already carry a MEDIUM+ severity finding so pattern findings
        // (which add root-cause specificity) can be capped rather than piling on severity.
        val peakSeverityByBank: Map<Int, FindingSeverity> = buildMap {
            for (f in severityResult.findings) {
                if (f.severity.ordinal < FindingSeverity.MEDIUM.ordinal) continue
                val bank = when {
                    f.id.endsWith("_b1") -> 1
                    f.id.endsWith("_b2") -> 2
                    else -> continue
                }
                val current = get(bank)
                if (current == null || f.severity.ordinal > current.ordinal) put(bank, f.severity)
            }
        }
        return closedLoopResult + fuelTrimPatternFindings(state, recentStates, opStateOf, peakSeverityByBank)
    }

    private fun fuelTrimSeverityFindings(state: VehicleState, store: SampleStore): RuleResult {
        val findings    = mutableListOf<DiagnosticFinding>()
        val eligibleIds = mutableSetOf<String>()
        for (bank in 1..2) {
            val stft = if (bank == 1) state.stftBank1Pct else state.stftBank2Pct
            stft ?: continue
            val ltft   = if (bank == 1) state.ltftBank1Pct else state.ltftBank2Pct
            val stftId = if (bank == 1) "stft" else "stft2"
            val ltftId = if (bank == 1) "ltft" else "ltft2"

            val stftStats = store.stats(stftId, FUEL_TRIM_WINDOW_MS)
            val ltftStats = store.stats(ltftId, FUEL_TRIM_WINDOW_MS)

            val sampleCount = stftStats?.count ?: 1
            if (sampleCount < FUEL_TRIM_MIN_SAMPLES) continue

            eligibleIds += setOf(
                "lean_strong_b$bank", "lean_likely_b$bank", "lean_warning_b$bank",
                "rich_strong_b$bank", "rich_likely_b$bank", "rich_warning_b$bank",
                "fuel_trim_normal_b$bank",
            )

            val avgStft = stftStats?.avg ?: stft
            val avgLtft = ltftStats?.avg ?: (ltft ?: 0f)
            val avg = avgStft + avgLtft

            val bankLabel = "Bank $bank"
            val windowSec = (FUEL_TRIM_WINDOW_MS / 1000).toInt()
            val evidence = buildMap {
                put("STFT $bankLabel", "${stft.toInt()}%")
                ltft?.let { put("LTFT $bankLabel", "${it.toInt()}%") }
                put("Avg combined ($bankLabel)", "${avg.toInt()}% over $sampleCount samples / ${windowSec}s")
            }
            findings += when {
                avg >= FUEL_TRIM_STRONG_FAULT_PCT -> DiagnosticFinding(
                    id          = "lean_strong_b$bank",
                    title       = "Strong lean condition ($bankLabel)",
                    description = "Strongly lean. Possible causes: major vacuum or air leak, severely restricted injector(s), failed MAF sensor, or significant fuel pressure loss.",
                    severity    = FindingSeverity.HIGH,
                    confidence  = FindingConfidence.HIGH,
                    evidence    = evidence,
                )
                avg >= FUEL_TRIM_LIKELY_FAULT_PCT -> DiagnosticFinding(
                    id          = "lean_likely_b$bank",
                    title       = "Likely lean condition ($bankLabel)",
                    description = "Likely lean. Typically indicates a vacuum leak, MAF sensor under-reading, or low fuel pressure.",
                    severity    = FindingSeverity.MEDIUM,
                    confidence  = FindingConfidence.HIGH,
                    evidence    = evidence,
                )
                avg >= FUEL_TRIM_WARN_PCT -> DiagnosticFinding(
                    id          = "lean_warning_b$bank",
                    title       = "Lean bias detected ($bankLabel)",
                    description = "Above the normal ±10% range. Sustained lean bias can indicate a developing issue worth monitoring.",
                    severity    = FindingSeverity.LOW,
                    confidence  = FindingConfidence.MEDIUM,
                    evidence    = evidence,
                )
                avg <= -FUEL_TRIM_STRONG_FAULT_PCT -> DiagnosticFinding(
                    id          = "rich_strong_b$bank",
                    title       = "Strong rich condition ($bankLabel)",
                    description = "Strongly rich. Possible causes: leaking injector(s), high fuel pressure, MAF over-reporting, or excessive EVAP purge.",
                    severity    = FindingSeverity.HIGH,
                    confidence  = FindingConfidence.HIGH,
                    evidence    = evidence,
                )
                avg <= -FUEL_TRIM_LIKELY_FAULT_PCT -> DiagnosticFinding(
                    id          = "rich_likely_b$bank",
                    title       = "Likely rich condition ($bankLabel)",
                    description = "Likely rich. Possible causes include a leaking injector, elevated fuel pressure, or EVAP purge issue.",
                    severity    = FindingSeverity.MEDIUM,
                    confidence  = FindingConfidence.HIGH,
                    evidence    = evidence,
                )
                avg <= -FUEL_TRIM_WARN_PCT -> DiagnosticFinding(
                    id          = "rich_warning_b$bank",
                    title       = "Rich bias detected ($bankLabel)",
                    description = "Below the normal ±10% range. Sustained rich bias may indicate a developing fuelling issue.",
                    severity    = FindingSeverity.LOW,
                    confidence  = FindingConfidence.MEDIUM,
                    evidence    = evidence,
                )
                else -> DiagnosticFinding(
                    id          = "fuel_trim_normal_b$bank",
                    title       = "Fuel trims normal ($bankLabel)",
                    description = "Within the normal ±10% range.",
                    severity    = FindingSeverity.INFO,
                    confidence  = FindingConfidence.MEDIUM,
                    evidence    = evidence,
                )
            }
        }
        return RuleResult(findings, eligibleIds)
    }

    private fun fuelTrimBankDiffFinding(state: VehicleState, store: SampleStore): RuleResult {
        state.stftBank2Pct ?: return RuleResult.NONE  // single-bank vehicle
        val stft1Stats = store.stats("stft",  FUEL_TRIM_WINDOW_MS) ?: return RuleResult.NONE
        val stft2Stats = store.stats("stft2", FUEL_TRIM_WINDOW_MS) ?: return RuleResult.NONE
        if (stft1Stats.count < FUEL_TRIM_MIN_SAMPLES || stft2Stats.count < FUEL_TRIM_MIN_SAMPLES) return RuleResult.NONE
        val eligible = setOf("fuel_trim_bank_diff")
        val avgB1 = stft1Stats.avg + (store.stats("ltft",  FUEL_TRIM_WINDOW_MS)?.avg ?: 0f)
        val avgB2 = stft2Stats.avg + (store.stats("ltft2", FUEL_TRIM_WINDOW_MS)?.avg ?: 0f)
        val diff = kotlin.math.abs(avgB1 - avgB2)
        if (diff < FUEL_TRIM_BANK_DIFF_PCT) return RuleResult(emptyList(), eligible)
        val leanerBank = if (avgB1 > avgB2) "Bank 1" else "Bank 2"
        return RuleResult(
            listOf(DiagnosticFinding(
                id          = "fuel_trim_bank_diff",
                title       = "Bank-specific fuel trim imbalance",
                description = "$leanerBank is running notably leaner. Possible causes: bank-specific air leak, injector problem, or exhaust leak affecting one bank's O2 sensor.",
                severity    = FindingSeverity.MEDIUM,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = mapOf(
                    "Avg combined Bank 1" to "${avgB1.toInt()}%",
                    "Avg combined Bank 2" to "${avgB2.toInt()}%",
                    "Difference"         to "${diff.toInt()} pp",
                ),
            )),
            eligible,
        )
    }

    private enum class TrimCondition { IDLE, CRUISE, ACCEL, OTHER }

    private fun classifyTrimCondition(s: VehicleState, opStateOf: (VehicleState) -> OperatingState): TrimCondition = when (opStateOf(s)) {
        OperatingState.WARM_IDLE    -> TrimCondition.IDLE
        OperatingState.CRUISE       -> TrimCondition.CRUISE
        OperatingState.ACCELERATION -> TrimCondition.ACCEL
        else                        -> TrimCondition.OTHER
    }

    private fun fuelTrimPatternFindings(
        state: VehicleState,
        recentStates: List<VehicleState>,
        opStateOf: (VehicleState) -> OperatingState,
        peakSeverityByBank: Map<Int, FindingSeverity> = emptyMap(),
    ): RuleResult {
        val cutoff = state.timestamp - FUEL_TRIM_PATTERN_WINDOW_MS
        val allStates = (recentStates + state).filter { it.timestamp >= cutoff }
        val banks = if (allStates.any { it.stftBank2Pct != null }) listOf(1, 2) else listOf(1)
        return banks.fold(RuleResult.NONE) { acc, bank ->
            acc + fuelTrimPatternFindingsForBank(bank, allStates, opStateOf, peakSeverityByBank[bank])
        }
    }

    private fun fuelTrimPatternFindingsForBank(
        bank: Int,
        allStates: List<VehicleState>,
        opStateOf: (VehicleState) -> OperatingState,
        bankPeakSeverity: FindingSeverity? = null,
    ): RuleResult {
        val points = allStates.mapNotNull { s ->
            val stft = if (bank == 1) s.stftBank1Pct else s.stftBank2Pct
            stft ?: return@mapNotNull null
            val ltft = if (bank == 1) s.ltftBank1Pct else s.ltftBank2Pct
            Pair(stft + (ltft ?: 0f), classifyTrimCondition(s, opStateOf))
        }

        val idleTrims   = points.filter { it.second == TrimCondition.IDLE   }.map { it.first }
        val cruiseTrims = points.filter { it.second == TrimCondition.CRUISE }.map { it.first }
        val accelTrims  = points.filter { it.second == TrimCondition.ACCEL  }.map { it.first }

        val avgIdle   = if (idleTrims.size   >= FUEL_TRIM_MIN_COND_SAMPLES) idleTrims.average().toFloat()   else null
        val avgCruise = if (cruiseTrims.size >= FUEL_TRIM_MIN_COND_SAMPLES) cruiseTrims.average().toFloat() else null
        val avgAccel  = if (accelTrims.size  >= FUEL_TRIM_MIN_COND_SAMPLES) accelTrims.average().toFloat()  else null

        val bankLabel = "Bank $bank"
        val findings    = mutableListOf<DiagnosticFinding>()
        val eligibleIds = mutableSetOf<String>()

        // When a severity finding at MEDIUM+ already covers this bank, cap pattern findings at LOW.
        // They still appear (root-cause specificity is valuable) but don't pile on severity.
        fun patternSev(default: FindingSeverity) =
            if (bankPeakSeverity != null && default.ordinal > FindingSeverity.LOW.ordinal) FindingSeverity.LOW
            else default

        // Vacuum leak: trims strongly positive at idle, improve significantly at cruise
        if (avgIdle != null && avgCruise != null) {
            eligibleIds += "vacuum_leak_pattern_b$bank"
            eligibleIds += "maf_underreporting_pattern_b$bank"
            eligibleIds += "rich_running_pattern_b$bank"
            val delta = avgIdle - avgCruise
            if (avgIdle >= FUEL_TRIM_WARN_PCT && delta >= FUEL_TRIM_VACUUM_LEAK_DELTA) {
                findings += DiagnosticFinding(
                    id          = "vacuum_leak_pattern_b$bank",
                    title       = "Vacuum leak pattern — $bankLabel",
                    description = "Trim is high at idle but improves significantly at cruise — a classic manifold vacuum leak signature.",
                    severity    = patternSev(FindingSeverity.MEDIUM),
                    confidence  = FindingConfidence.MEDIUM,
                    evidence    = mapOf(
                        "Avg trim at idle ($bankLabel)"   to "${avgIdle.toInt()}%",
                        "Avg trim at cruise ($bankLabel)" to "${avgCruise.toInt()}%",
                        "Delta"                          to "${delta.toInt()} pp",
                    ),
                )
            }
        }

        // Fuel delivery issue: idle trim normal, but trims worsen under acceleration
        if (avgIdle != null && avgAccel != null) {
            eligibleIds += "fuel_delivery_pattern_b$bank"
            if (avgIdle < FUEL_TRIM_IDLE_NORMAL_PCT && avgAccel >= FUEL_TRIM_ACCEL_LEAN_PCT) {
                findings += DiagnosticFinding(
                    id          = "fuel_delivery_pattern_b$bank",
                    title       = "Possible fuel delivery issue — $bankLabel",
                    description = "Trim is normal at idle but rises sharply under acceleration, suggesting insufficient fuel delivery on demand. Possible causes: weak fuel pump, clogged injector(s), or restricted fuel filter.",
                    severity    = patternSev(FindingSeverity.MEDIUM),
                    confidence  = FindingConfidence.MEDIUM,
                    evidence    = mapOf(
                        "Avg trim at idle ($bankLabel)"         to "${avgIdle.toInt()}%",
                        "Avg trim at acceleration ($bankLabel)" to "${avgAccel.toInt()}%",
                    ),
                )
            }
        }

        // MAF underreporting: positive across both idle and cruise (but not explained by vacuum leak)
        if (avgIdle != null && avgCruise != null) {
            val vacuumLeakFlagged = findings.any { it.id == "vacuum_leak_pattern_b$bank" }
            if (!vacuumLeakFlagged && avgIdle >= FUEL_TRIM_CROSS_COND_PCT && avgCruise >= FUEL_TRIM_CROSS_COND_PCT) {
                findings += DiagnosticFinding(
                    id          = "maf_underreporting_pattern_b$bank",
                    title       = "MAF / intake measurement issue — $bankLabel",
                    description = "ECU is adding fuel across all conditions. Points to the air-measurement side: dirty or failing MAF sensor, small persistent air leak, or partially clogged injectors.",
                    severity    = FindingSeverity.LOW,
                    confidence  = FindingConfidence.MEDIUM,
                    evidence    = mapOf(
                        "Avg trim at idle ($bankLabel)"   to "${avgIdle.toInt()}%",
                        "Avg trim at cruise ($bankLabel)" to "${avgCruise.toInt()}%",
                    ),
                )
            }

            // Rich running: negative across both conditions
            if (avgIdle <= -FUEL_TRIM_CROSS_COND_PCT && avgCruise <= -FUEL_TRIM_CROSS_COND_PCT) {
                findings += DiagnosticFinding(
                    id          = "rich_running_pattern_b$bank",
                    title       = "Rich running across conditions — $bankLabel",
                    description = "ECU is removing fuel across all conditions. Possible causes: leaking injector(s), elevated fuel pressure, MAF over-reading, or excessive EVAP purge.",
                    severity    = FindingSeverity.LOW,
                    confidence  = FindingConfidence.MEDIUM,
                    evidence    = mapOf(
                        "Avg trim at idle ($bankLabel)"   to "${avgIdle.toInt()}%",
                        "Avg trim at cruise ($bankLabel)" to "${avgCruise.toInt()}%",
                    ),
                )
            }
        }

        return RuleResult(findings, eligibleIds)
    }

    private fun chargingFinding(state: VehicleState, store: SampleStore): RuleResult {
        if (!state.engineOn) return RuleResult.NONE
        val stats = store.stats("obd_voltage", CHARGING_VOLTAGE_WINDOW_MS)
            ?: store.stats("ecu_voltage", CHARGING_VOLTAGE_WINDOW_MS)
            ?: return RuleResult.NONE
        if (stats.count < CHARGING_VOLTAGE_MIN_SAMPLES) return RuleResult.NONE
        val eligible = setOf("charging_voltage_low")
        if (stats.avg >= CHARGING_VOLTAGE_LOW) return RuleResult(emptyList(), eligible)
        val windowSec = (CHARGING_VOLTAGE_WINDOW_MS / 1000).toInt()
        return RuleResult(
            listOf(DiagnosticFinding(
                id          = "charging_voltage_low",
                title       = "Possible charging issue",
                description = "Low while the engine is running — may indicate a charging system issue (alternator or DC-DC converter on hybrids). A healthy system reads 13.5–14.8V under load.",
                severity    = FindingSeverity.MEDIUM,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = mapOf(
                    "Avg system voltage" to "${"%.1f".format(stats.avg)}V",
                    "Samples"            to "${stats.count} over ${windowSec}s",
                ),
            )),
            eligible,
        )
    }

    private fun voltageDropFinding(state: VehicleState, store: SampleStore): RuleResult {
        val obdStats = store.stats("obd_voltage", VOLTAGE_DROP_WINDOW_MS) ?: return RuleResult.NONE
        val ecuStats = store.stats("ecu_voltage", VOLTAGE_DROP_WINDOW_MS) ?: return RuleResult.NONE
        if (obdStats.count < VOLTAGE_DROP_MIN_SAMPLES || ecuStats.count < VOLTAGE_DROP_MIN_SAMPLES) return RuleResult.NONE
        val eligible = setOf("voltage_drop_ecu")
        val drop = obdStats.avg - ecuStats.avg
        if (drop < VOLTAGE_DROP_WARN_V) return RuleResult(emptyList(), eligible)
        val (severity, confidence) = if (drop >= VOLTAGE_DROP_HIGH_V)
            FindingSeverity.HIGH   to FindingConfidence.HIGH
        else
            FindingSeverity.MEDIUM to FindingConfidence.MEDIUM
        val windowSec = (VOLTAGE_DROP_WINDOW_MS / 1000).toInt()
        return RuleResult(
            listOf(DiagnosticFinding(
                id          = "voltage_drop_ecu",
                title       = "Voltage drop to ECU",
                description = "Resistance in the wiring between the battery and ECU — possibly a corroded connector, marginal fuse, or failing ground.",
                severity    = severity,
                confidence  = confidence,
                evidence    = mapOf(
                    "Avg OBD port voltage"   to "${"%.2f".format(obdStats.avg)}V",
                    "Avg ECU supply voltage" to "${"%.2f".format(ecuStats.avg)}V",
                    "Avg drop"               to "${"%.2f".format(drop)}V",
                    "Samples"                to "OBD: ${obdStats.count}, ECU: ${ecuStats.count} over ${windowSec}s",
                ),
            )),
            eligible,
        )
    }

    private fun dtcFinding(state: VehicleState): RuleResult {
        val eligible = setOf("dtc_present")
        if (state.activeDtcs.isEmpty()) return RuleResult(emptyList(), eligible)
        val evidence = state.activeDtcs.associateWith(DtcInfo::genericExplanation)
        return RuleResult(
            listOf(DiagnosticFinding(
                id          = "dtc_present",
                title       = "Fault codes present",
                description = "These may indicate current or intermittent faults. Use the DTCs tab to read and clear them.",
                severity    = FindingSeverity.HIGH,
                confidence  = FindingConfidence.HIGH,
                evidence    = evidence,
            )),
            eligible,
        )
    }

    private fun oilTempFinding(state: VehicleState): RuleResult {
        val oil = state.oilTempC ?: return RuleResult.NONE
        val rpm = state.rpm
        val eligible = setOf("oil_overheating", "oil_cold_high_rpm")
        val finding = when {
            oil >= OIL_TEMP_HOT_C -> DiagnosticFinding(
                id          = "oil_overheating",
                title       = "Oil temperature high",
                description = "Exceeds safe operating limits. High oil temperature degrades lubrication and risks engine damage — reduce load and check the cooling system.",
                severity    = FindingSeverity.HIGH,
                confidence  = FindingConfidence.HIGH,
                evidence    = mapOf("Oil temp" to "${oil.toInt()}°C"),
            )
            oil < OIL_TEMP_COLD_C && rpm != null && rpm > OIL_HIGH_RPM -> DiagnosticFinding(
                id          = "oil_cold_high_rpm",
                title       = "High RPM with cold oil",
                description = "Cold oil is thicker and provides less lubrication at high RPM. Allow the engine to warm up before driving hard.",
                severity    = FindingSeverity.MEDIUM,
                confidence  = FindingConfidence.HIGH,
                evidence    = mapOf("Oil temp" to "${oil.toInt()}°C", "RPM" to "${rpm.toInt()}"),
            )
            else -> return RuleResult(emptyList(), eligible)
        }
        return RuleResult(listOf(finding), eligible)
    }

    private fun idleRpmFinding(opState: OperatingState, state: VehicleState, recentStates: List<VehicleState>, opStateOf: (VehicleState) -> OperatingState): RuleResult {
        if (opState != OperatingState.WARM_IDLE) return RuleResult.NONE
        val currentRpm = state.rpm ?: return RuleResult.NONE

        val cutoff = state.timestamp - IDLE_RPM_WINDOW_MS
        val idleRpms = (recentStates + state)
            .filter { it.timestamp >= cutoff && opStateOf(it) == OperatingState.WARM_IDLE }
            .mapNotNull { it.rpm }

        if (idleRpms.size < IDLE_RPM_MIN_SAMPLES) return RuleResult.NONE

        val eligible = setOf("idle_rpm_high", "idle_rpm_low", "idle_rpm_hunting")
        val avg = idleRpms.average().toFloat()
        val stdDev = kotlin.math.sqrt(idleRpms.map { (it - avg) * (it - avg) }.average()).toFloat()
        val windowSec = (IDLE_RPM_WINDOW_MS / 1000).toInt()
        val evidence = mapOf(
            "Avg RPM at idle" to "${avg.toInt()} rpm over ${idleRpms.size} samples / ${windowSec}s",
            "Current RPM"     to "${currentRpm.toInt()} rpm",
        )

        val finding = when {
            avg > IDLE_RPM_HIGH -> DiagnosticFinding(
                id          = "idle_rpm_high",
                title       = "Elevated idle RPM",
                description = "Above the normal 600–1000 RPM range. Possible causes: vacuum leak, stuck-open IAC valve, or throttle body deposits.",
                severity    = FindingSeverity.LOW,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = evidence,
            )
            avg < IDLE_RPM_LOW -> DiagnosticFinding(
                id          = "idle_rpm_low",
                title       = "Rough idle — RPM very low",
                description = "Unusually low. May indicate a misfiring cylinder, clogged idle air passage, or failing IAC valve.",
                severity    = FindingSeverity.LOW,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = evidence,
            )
            stdDev > IDLE_RPM_ROUGH_STDDEV -> DiagnosticFinding(
                id          = "idle_rpm_hunting",
                title       = "Unstable idle — RPM hunting",
                description = "RPM is swinging well beyond the normal ±30–50 RPM range. Suggests a vacuum leak, dirty throttle body, failing IAC valve, or intermittent ignition issue.",
                severity    = FindingSeverity.LOW,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = evidence + mapOf("RPM std-dev" to "±${stdDev.toInt()} rpm"),
            )
            else -> return RuleResult(emptyList(), eligible)
        }
        return RuleResult(listOf(finding), eligible)
    }

    private fun fuelLoopFinding(state: VehicleState): RuleResult {
        if (!state.engineOn) return RuleResult.NONE
        val raw = state.fuelSystemStatusRaw ?: return RuleResult.NONE
        val status = raw.toInt()
        val eligible = setOf("fuel_loop_fault", "fuel_loop_cl_fault", "fuel_loop_warm_ol", "fuel_loop_open_load")

        if (state.isOpenLoopFault) return RuleResult(
            listOf(DiagnosticFinding(
                id          = "fuel_loop_fault",
                title       = "Fuel system open loop — fault detected",
                description = "The ECU reports open-loop operation due to a system failure. The engine is not using O2 sensor " +
                              "feedback to correct fuelling. This typically sets a fault code — check the DTCs tab.",
                severity    = FindingSeverity.HIGH,
                confidence  = FindingConfidence.HIGH,
                evidence    = mapOf("Fuel system status" to "0x${status.toString(16).uppercase()} (open loop, fault)"),
            )),
            eligible,
        )

        if (state.isClosedLoopFault) return RuleResult(
            listOf(DiagnosticFinding(
                id          = "fuel_loop_cl_fault",
                title       = "Closed loop fuel control has a fault",
                description = "The ECU is running closed loop but reports a problem with the O2 feedback system. " +
                              "Fuelling corrections may be inaccurate. Check O2 sensor readings and DTCs.",
                severity    = FindingSeverity.MEDIUM,
                confidence  = FindingConfidence.HIGH,
                evidence    = mapOf("Fuel system status" to "0x${status.toString(16).uppercase()} (closed loop, fault)"),
            )),
            eligible,
        )

        val engineWarm = (state.coolantTempC ?: 0f) >= WARM_COOLANT_C
        val openLoopCold = status and 0x01 != 0
        val openLoopLoad = status and 0x04 != 0

        if (engineWarm && openLoopCold) return RuleResult(
            listOf(DiagnosticFinding(
                id          = "fuel_loop_warm_ol",
                title       = "Open loop despite warm engine",
                description = "ECU reports open loop (insufficient temperature) despite a warm coolant reading. May indicate a faulty coolant temp sensor, stuck thermostat, or ECU calibration issue.",
                severity    = FindingSeverity.MEDIUM,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = mapOf(
                    "Fuel system status" to "open loop (cold)",
                    "Coolant temp"       to "${state.coolantTempC?.toInt()}°C",
                ),
            )),
            eligible,
        )

        if (openLoopLoad) return RuleResult(
            listOf(DiagnosticFinding(
                id          = "fuel_loop_open_load",
                title       = "Fuel system in open loop",
                description = "The ECU is running open loop due to high engine load or active fuel cut. " +
                              "This is normal during wide-open throttle or engine braking and will revert to closed loop automatically.",
                severity    = FindingSeverity.INFO,
                confidence  = FindingConfidence.HIGH,
                evidence    = mapOf("Fuel system status" to "open loop (load/decel)"),
            )),
            eligible,
        )

        return RuleResult(emptyList(), eligible)
    }

    private fun o2Finding(state: VehicleState, store: SampleStore): RuleResult {
        if (!state.engineOn) return RuleResult.NONE
        val coolant = state.coolantTempC
        if (coolant == null || coolant < WARM_COOLANT_C) return RuleResult.NONE
        val catalystWindowEligible = isCatalystWindowEligible(state, store)
        return o2FindingsForBank(
            bankNum                    = 1,
            hasUpstreamNarrow          = state.hasUpstreamO2B1,
            hasDownstream              = state.hasDownstreamO2B1,
            narrowUpstreamPid          = "o2_b1s1",
            widebandUpstreamPid        = "afr_b1s1",
            widebandCurrentUpstreamPid = "afr_i_b1s1",
            narrowDownstreamPid        = "o2_b1s2",
            narrowUpstreamLatest       = state.o2Bank1Sensor1V,
            widebandUpstreamLatest     = state.afrBank1Sensor1Lambda,
            widebandCurrentUpstreamLatest = state.afrCurBank1Sensor1Lambda,
            isClosedLoop               = state.isClosedLoop,
            catalystWindowEligible     = catalystWindowEligible,
            nowMs                      = state.timestamp,
            store                      = store,
            findingIdSuffix            = "",
        ) + o2FindingsForBank(
            bankNum                    = 2,
            hasUpstreamNarrow          = state.hasUpstreamO2B2,
            hasDownstream              = state.hasDownstreamO2B2,
            narrowUpstreamPid          = "o2_b2s1",
            widebandUpstreamPid        = "afr_b2s1",
            widebandCurrentUpstreamPid = "afr_i_b2s1",
            narrowDownstreamPid        = "o2_b2s2",
            narrowUpstreamLatest       = state.o2Bank2Sensor1V,
            widebandUpstreamLatest     = state.afrBank2Sensor1Lambda,
            widebandCurrentUpstreamLatest = state.afrCurBank2Sensor1Lambda,
            isClosedLoop               = state.isClosedLoop,
            catalystWindowEligible     = catalystWindowEligible,
            nowMs                      = state.timestamp,
            store                      = store,
            findingIdSuffix            = "_b2",
        )
    }

    /**
     * Per-bank O2 diagnostics: upstream dead/lazy plus a conservative narrowband-only
     * catalyst screening check. Upstream sensor checks prefer narrowband data and fall
     * back to wideband lambda when available.
     * Returns an empty result for any bank where no sensor data exists (e.g. single-bank vehicles).
     */
    @Suppress("LongParameterList")
    private fun o2FindingsForBank(
        bankNum: Int,
        hasUpstreamNarrow: Boolean,
        hasDownstream: Boolean,
        narrowUpstreamPid: String,
        widebandUpstreamPid: String,
        widebandCurrentUpstreamPid: String,
        narrowDownstreamPid: String,
        narrowUpstreamLatest: Float?,
        widebandUpstreamLatest: Float?,
        widebandCurrentUpstreamLatest: Float?,
        isClosedLoop: Boolean?,
        catalystWindowEligible: Boolean,
        nowMs: Long,
        store: SampleStore,
        findingIdSuffix: String,
    ): RuleResult {
        val findings    = mutableListOf<DiagnosticFinding>()
        val eligibleIds = mutableSetOf<String>()
        val bankLabel   = if (bankNum == 1) "" else " — Bank $bankNum"
        val upLabel     = "B${bankNum}S1"
        val windowSec   = (O2_WINDOW_MS / 1000).toInt()

        // A vehicle implements either the voltage-type (afr_*) or current-type (afr_i_*) wideband
        // PID group, never both. Merge to whichever actually has data so the branching below only
        // ever has to reason about one narrowband source and one wideband source.
        val widebandUpstreamPid = if (store.window(widebandUpstreamPid, O2_WINDOW_MS).isNotEmpty() || widebandUpstreamLatest != null)
            widebandUpstreamPid else widebandCurrentUpstreamPid
        val widebandUpstreamLatest = widebandUpstreamLatest ?: widebandCurrentUpstreamLatest
        // Narrowband upstream guarded by PID 0x13 bitmask; wideband loaded unconditionally —
        // if the wideband PID isn't polled/stored the list is simply empty.
        val narrowbandUpstreamSamples = if (hasUpstreamNarrow) store.window(narrowUpstreamPid, O2_WINDOW_MS) else emptyList()
        val narrowbandUpstreamStats   = if (narrowbandUpstreamSamples.isNotEmpty()) store.stats(narrowUpstreamPid, O2_WINDOW_MS) else null
        val widebandUpstreamSamples   = store.window(widebandUpstreamPid, O2_WINDOW_MS)
        val widebandUpstreamStats     = if (widebandUpstreamSamples.isNotEmpty()) store.stats(widebandUpstreamPid, O2_WINDOW_MS) else null

        val narrowDownstreamSamples   = if (hasDownstream) store.window(narrowDownstreamPid, O2_WINDOW_MS) else emptyList()
        val narrowDownstreamStats     = if (narrowDownstreamSamples.isNotEmpty()) store.stats(narrowDownstreamPid, O2_WINDOW_MS) else null

        // ── Upstream: prefer narrowband; fall back to wideband ────────────────
        when {
            narrowbandUpstreamSamples.size >= O2_MIN_SAMPLES && narrowbandUpstreamStats != null -> {
                eligibleIds += "o2_upstream_dead$findingIdSuffix"
                eligibleIds += "o2_upstream_lazy$findingIdSuffix"
                val range    = narrowbandUpstreamStats.max - narrowbandUpstreamStats.min
                val mean     = narrowbandUpstreamStats.avg
                val evidence = mapOf(
                    "O2 upstream ($upLabel)" to "${"%.2f".format(mean)}V avg",
                    "Voltage swing"          to "${"%.2f".format(range)}V over ${narrowbandUpstreamSamples.size} samples / ${windowSec}s",
                )
                when {
                    narrowbandUpstreamSamples.all { it.value < O2_SENSOR_DEAD_V } -> findings += DiagnosticFinding(
                        id          = "o2_upstream_dead$findingIdSuffix",
                        title       = "Upstream O2 sensor may be dead$bankLabel",
                        description = "Stuck near zero across multiple readings on a warm engine. A healthy sensor cycles between ~0.1V and ~0.9V. Possible causes: failed sensor, open circuit, or heater failure.",
                        severity    = FindingSeverity.MEDIUM,
                        confidence  = FindingConfidence.MEDIUM,
                        evidence    = evidence,
                    )
                    // Only flag lazy sensor in closed loop: in open loop the ECU ignores O2 feedback,
                    // so a steady ~0.45V reading is normal (sensor sits at its reference mid-point).
                    isClosedLoop != false &&
                    mean in O2_STUCK_LOW_V..O2_STUCK_HIGH_V &&
                    range < O2_LAZY_RANGE_V -> findings += DiagnosticFinding(
                        id          = "o2_upstream_lazy$findingIdSuffix",
                        title       = "Upstream O2 sensor not switching$bankLabel",
                        description = "Not cycling normally in closed loop — a healthy sensor swings between ~0.1V and ~0.9V. A sluggish sensor forces the ECU to rely on fuel trims. Possible causes: contaminated or ageing sensor, heater circuit fault.",
                        severity    = FindingSeverity.LOW,
                        confidence  = FindingConfidence.MEDIUM,
                        evidence    = evidence,
                    )
                }
            }
            widebandUpstreamSamples.size >= O2_MIN_SAMPLES && widebandUpstreamStats != null -> {
                eligibleIds += "o2_upstream_dead$findingIdSuffix"
                eligibleIds += "o2_upstream_lazy$findingIdSuffix"
                val range    = widebandUpstreamStats.max - widebandUpstreamStats.min
                val mean     = widebandUpstreamStats.avg
                val evidence = mapOf(
                    "AFR upstream ($upLabel)" to "${"%.3f".format(mean)} λ avg",
                    "Lambda swing"            to "${"%.3f".format(range)} λ over ${widebandUpstreamSamples.size} samples / ${windowSec}s",
                )
                when {
                    widebandUpstreamSamples.all { it.value < AFR_SENSOR_DEAD_LAMBDA } -> findings += DiagnosticFinding(
                        id          = "o2_upstream_dead$findingIdSuffix",
                        title       = "Upstream AFR sensor may be dead$bankLabel",
                        description = "Lambda stuck near zero across multiple readings on a warm engine. Possible causes: failed sensor, open circuit, or heater failure.",
                        severity    = FindingSeverity.MEDIUM,
                        confidence  = FindingConfidence.MEDIUM,
                        evidence    = evidence,
                    )
                    // Only flag stuck wideband sensor in closed loop — low confidence because
                    // a very stable-running engine can genuinely show small lambda variation.
                    isClosedLoop != false && range < AFR_MIN_VARIATION_LAMBDA -> findings += DiagnosticFinding(
                        id          = "o2_upstream_lazy$findingIdSuffix",
                        title       = "Upstream AFR sensor not varying$bankLabel",
                        description = "Lambda is not changing in closed loop across ${widebandUpstreamSamples.size} readings. " +
                                      "A working wideband sensor should vary with fuel control corrections. " +
                                      "May indicate a stuck sensor or heater circuit fault — verify with scan data.",
                        severity    = FindingSeverity.LOW,
                        confidence  = FindingConfidence.LOW,
                        evidence    = evidence,
                    )
                }
            }
            else -> {
                // Single-reading fallback: only flag the unambiguous dead case
                val narrowLatest = if (hasUpstreamNarrow) narrowUpstreamLatest else null
                when {
                    narrowLatest != null && narrowLatest < O2_SENSOR_DEAD_V -> {
                        eligibleIds += "o2_upstream_dead$findingIdSuffix"
                        findings += DiagnosticFinding(
                            id          = "o2_upstream_dead$findingIdSuffix",
                            title       = "Upstream O2 sensor may be dead$bankLabel",
                            description = "Near zero on a warm engine. A healthy sensor cycles between ~0.1V and ~0.9V — may indicate a failed sensor or wiring issue.",
                            severity    = FindingSeverity.MEDIUM,
                            confidence  = FindingConfidence.LOW,
                            evidence    = mapOf("O2 upstream ($upLabel)" to "${"%.2f".format(narrowLatest)}V"),
                        )
                    }
                    widebandUpstreamLatest != null && widebandUpstreamLatest < AFR_SENSOR_DEAD_LAMBDA -> {
                        eligibleIds += "o2_upstream_dead$findingIdSuffix"
                        findings += DiagnosticFinding(
                            id          = "o2_upstream_dead$findingIdSuffix",
                            title       = "Upstream AFR sensor may be dead$bankLabel",
                            description = "Lambda near zero on a warm engine — may indicate a failed sensor or wiring issue.",
                            severity    = FindingSeverity.MEDIUM,
                            confidence  = FindingConfidence.LOW,
                            evidence    = mapOf("AFR upstream ($upLabel)" to "${"%.3f".format(widebandUpstreamLatest)} λ"),
                        )
                    }
                }
            }
        }

        // ── Downstream / catalyst screening ────────────────────────────────
        // Generic scan data cannot reproduce an ECU's calibrated catalyst monitor. Restrict the
        // passive heuristic to two switching-type sensors under stable, warm, closed-loop cruise.
        // Wideband layouts intentionally defer to P0420/P0430, readiness and future Mode $06 data.
        if (catalystWindowEligible && hasUpstreamNarrow && hasDownstream) {
            val upstreamActivity = signalActivity(narrowbandUpstreamSamples, nowMs)
            val downstreamActivity = signalActivity(narrowDownstreamSamples, nowMs)
            val upstreamRange = narrowbandUpstreamStats?.let { it.max - it.min }
            val downstreamRange = narrowDownstreamStats?.let { it.max - it.min }

            if (upstreamActivity != null && downstreamActivity != null &&
                upstreamRange != null && downstreamRange != null &&
                upstreamRange > O2_UPSTREAM_SWING_V
            ) {
                eligibleIds += "o2_downstream_cycling$findingIdSuffix"
                val activityRatio = downstreamActivity.voltsPerSecond / upstreamActivity.voltsPerSecond
                if (downstreamRange > O2_DOWNSTREAM_SWING_V &&
                    activityRatio >= CATALYST_MIN_ACTIVITY_RATIO
                ) {
                    findings += DiagnosticFinding(
                        id          = "o2_downstream_cycling$findingIdSuffix",
                        title       = "Possible low catalyst oxygen storage$bankLabel",
                        description = "During a stable closed-loop cruise, downstream O2 activity remained high relative to the upstream sensor. " +
                                      "This is a passive screening pattern, not a converter diagnosis; confirm it with P0420/P0430 or ECU Mode \$06 results.",
                        severity    = FindingSeverity.LOW,
                        confidence  = FindingConfidence.LOW,
                        evidence    = mapOf(
                            "Rear/front activity ratio" to "${"%.2f".format(activityRatio)}",
                            "Upstream activity ($upLabel)" to "${"%.3f".format(upstreamActivity.voltsPerSecond)} V/s over ${upstreamActivity.sampleCount} samples",
                            "Downstream activity (B${bankNum}S2)" to "${"%.3f".format(downstreamActivity.voltsPerSecond)} V/s over ${downstreamActivity.sampleCount} samples",
                            "Observation time" to "${minOf(upstreamActivity.coverageMs, downstreamActivity.coverageMs) / 1000}s stable cruise",
                        ),
                    )
                }
            }
        }

        return RuleResult(findings, eligibleIds)
    }

    private data class SignalActivity(
        val voltsPerSecond: Float,
        val sampleCount: Int,
        val coverageMs: Long,
    )

    private fun signalActivity(samples: List<SensorSample>, nowMs: Long): SignalActivity? {
        if (samples.size < CATALYST_MIN_SAMPLES) return null
        val ordered = samples.sortedBy { it.timestampMs }
        val coverageMs = ordered.last().timestampMs - ordered.first().timestampMs
        if (coverageMs < CATALYST_MIN_COVERAGE_MS) return null
        if (nowMs - ordered.last().timestampMs > CATALYST_SAMPLE_MAX_AGE_MS) return null
        val totalVariation = ordered.zipWithNext()
            .sumOf { (a, b) -> abs(b.value - a.value).toDouble() }
            .toFloat()
        val rate = totalVariation / (coverageMs / 1000f)
        return rate.takeIf { it > 0f }?.let { SignalActivity(it, ordered.size, coverageMs) }
    }

    private fun isCatalystWindowEligible(state: VehicleState, store: SampleStore): Boolean {
        if (state.isClosedLoop != true) return false
        val speed = state.speedKph ?: return false
        val throttle = state.throttlePct ?: return false
        val load = state.engineLoadPct ?: return false
        if (speed !in CATALYST_MIN_SPEED_KPH..CATALYST_MAX_SPEED_KPH) return false
        if (throttle !in CATALYST_MIN_THROTTLE_PCT..CATALYST_MAX_THROTTLE_PCT) return false
        if (load !in CATALYST_MIN_LOAD_PCT..CATALYST_MAX_LOAD_PCT) return false
        if (state.engineRunTimeSec?.let { it < CATALYST_MIN_ENGINE_RUNTIME_SEC } == true) return false

        val speedSamples = store.window("speed", O2_WINDOW_MS)
        val throttleSamples = store.window("throttle", O2_WINDOW_MS)
        val loadSamples = store.window("load", O2_WINDOW_MS)
        val fuelStatusSamples = store.window("fuel_sys_status", O2_WINDOW_MS)
        return hasStableCoverage(speedSamples, state.timestamp, CATALYST_MIN_SPEED_KPH, CATALYST_MAX_SPEED_KPH, CATALYST_MAX_SPEED_RANGE_KPH) &&
               hasStableCoverage(throttleSamples, state.timestamp, CATALYST_MIN_THROTTLE_PCT, CATALYST_MAX_THROTTLE_PCT, CATALYST_MAX_THROTTLE_RANGE_PCT) &&
               hasStableCoverage(loadSamples, state.timestamp, CATALYST_MIN_LOAD_PCT, CATALYST_MAX_LOAD_PCT, CATALYST_MAX_LOAD_RANGE_PCT) &&
               hasClosedLoopCoverage(fuelStatusSamples, state.timestamp)
    }

    private fun hasStableCoverage(
        samples: List<SensorSample>,
        nowMs: Long,
        minValue: Float,
        maxValue: Float,
        maxRange: Float,
    ): Boolean {
        if (samples.size < O2_MIN_SAMPLES) return false
        val ordered = samples.sortedBy { it.timestampMs }
        if (ordered.last().timestampMs - ordered.first().timestampMs < CATALYST_MIN_COVERAGE_MS) return false
        if (nowMs - ordered.last().timestampMs > CATALYST_SAMPLE_MAX_AGE_MS) return false
        if (ordered.any { it.value !in minValue..maxValue }) return false
        return ordered.maxOf { it.value } - ordered.minOf { it.value } <= maxRange
    }

    private fun hasClosedLoopCoverage(samples: List<SensorSample>, nowMs: Long): Boolean {
        if (samples.size < O2_MIN_SAMPLES) return false
        val ordered = samples.sortedBy { it.timestampMs }
        if (ordered.last().timestampMs - ordered.first().timestampMs < CATALYST_MIN_COVERAGE_MS) return false
        if (nowMs - ordered.last().timestampMs > CATALYST_SAMPLE_MAX_AGE_MS) return false
        return ordered.all { (it.value.toInt() and 0x02) != 0 }
    }

    private fun timingFinding(opState: OperatingState, state: VehicleState, recentStates: List<VehicleState>, opStateOf: (VehicleState) -> OperatingState): RuleResult {
        val cutoff = state.timestamp - TIMING_WINDOW_MS
        val allStates = (recentStates + state).filter { it.timestamp >= cutoff }
        val samples = allStates.mapNotNull { s ->
            val timing = s.timingAdvanceDeg ?: return@mapNotNull null
            val op = opStateOf(s)
            if (op != OperatingState.CRUISE && op != OperatingState.ACCELERATION) return@mapNotNull null
            timing
        }
        if (samples.size < TIMING_MIN_SAMPLES) return RuleResult.NONE
        val eligible = setOf("timing_retarded")
        val avg = samples.average().toFloat()
        if (avg >= TIMING_LOW_DEG) return RuleResult(emptyList(), eligible)
        val confidence = if (samples.size >= TIMING_CONFIDENT_SAMPLES) FindingConfidence.MEDIUM else FindingConfidence.LOW
        val windowSec = (TIMING_WINDOW_MS / 1000).toInt()
        val evidence = buildMap {
            put("Avg timing advance", "${avg.toInt()}° over ${samples.size} samples / ${windowSec}s")
            state.timingAdvanceDeg?.let { put("Current timing", "${it.toInt()}°") }
        }
        return RuleResult(
            listOf(DiagnosticFinding(
                id          = "timing_retarded",
                title       = "Timing persistently retarded under load",
                description = "Consistently retarded under load. The ECU may be pulling timing to prevent knock — typically from low-octane fuel, carbon deposits, or a faulty knock sensor.",
                severity    = FindingSeverity.MEDIUM,
                confidence  = confidence,
                evidence    = evidence,
            )),
            eligible,
        )
    }

    private fun fuelRateFinding(opState: OperatingState, state: VehicleState, store: SampleStore): RuleResult {
        if (!state.engineOn) return RuleResult.NONE
        val rateStats = store.stats("fuel_rate", FUEL_RATE_WINDOW_MS) ?: return RuleResult.NONE
        if (rateStats.count < FUEL_RATE_MIN_SAMPLES) return RuleResult.NONE
        val eligible = setOf("fuel_rate_idle_high", "fuel_rate_cruise_high")
        val avg = rateStats.avg
        val windowSec = (FUEL_RATE_WINDOW_MS / 1000).toInt()
        val finding = when {
            opState == OperatingState.WARM_IDLE && avg > FUEL_RATE_IDLE_HIGH -> DiagnosticFinding(
                id          = "fuel_rate_idle_high",
                title       = "High fuel consumption at idle",
                description = "Above the typical 1–2 L/h at idle. May indicate a leaking injector, rich fuel trim, or elevated idle RPM.",
                severity    = FindingSeverity.LOW,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = mapOf(
                    "Avg fuel rate" to "${String.format(Locale.ROOT, "%.1f", avg)} L/h",
                    "Samples"       to "${rateStats.count} over ${windowSec}s",
                ),
            )
            opState == OperatingState.CRUISE && avg > FUEL_RATE_CRUISE_HIGH -> DiagnosticFinding(
                id          = "fuel_rate_cruise_high",
                title       = "High fuel consumption at cruise",
                description = "Unusually high for steady-state driving — investigate alongside fuel trims.",
                severity    = FindingSeverity.LOW,
                confidence  = FindingConfidence.LOW,
                evidence    = mapOf(
                    "Avg fuel rate" to "${String.format(Locale.ROOT, "%.1f", avg)} L/h",
                    "Samples"       to "${rateStats.count} over ${windowSec}s",
                ),
            )
            else -> return RuleResult(emptyList(), eligible)
        }
        return RuleResult(listOf(finding), eligible)
    }

    private fun milRuntimeFinding(state: VehicleState): RuleResult {
        val milMin = state.milRunTimeMin ?: return RuleResult.NONE
        val eligible = setOf("mil_runtime")
        if (milMin < MIL_RUNTIME_WARN_MIN) return RuleResult(emptyList(), eligible)
        val hours = (milMin / 60).toInt()
        val display = if (hours >= 1) "${hours}h ${(milMin.toInt() % 60)}m" else "${milMin.toInt()}m"
        return RuleResult(
            listOf(DiagnosticFinding(
                id          = "mil_runtime",
                title       = "Check engine light has been on for $display",
                description = "Check the DTCs tab for stored fault codes. Long MIL run-times increase the risk of secondary damage.",
                severity    = FindingSeverity.MEDIUM,
                confidence  = FindingConfidence.HIGH,
                evidence    = mapOf("MIL runtime" to display),
            )),
            eligible,
        )
    }

    private fun egrErrorFinding(state: VehicleState): RuleResult {
        val commanded = state.egrPct ?: return RuleResult.NONE
        val error = state.egrErrorPct ?: return RuleResult.NONE
        if (commanded < EGR_COMMANDED_MIN_PCT) return RuleResult.NONE
        val eligible = setOf("egr_error_high")
        if (kotlin.math.abs(error) < EGR_ERROR_WARN_PCT) return RuleResult(emptyList(), eligible)
        val direction = if (error > 0) "flowing more than commanded" else "flowing less than commanded"
        return RuleResult(
            listOf(DiagnosticFinding(
                id          = "egr_error_high",
                title       = "EGR valve not following commands",
                description = "The valve is $direction. May indicate a stuck or clogged EGR valve, faulty position sensor, or carbon build-up in the EGR passage.",
                severity    = FindingSeverity.MEDIUM,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = mapOf(
                    "Commanded EGR" to "${commanded.toInt()}%",
                    "EGR error"     to "${error.toInt()}%",
                ),
            )),
            eligible,
        )
    }

    private fun evapFinding(state: VehicleState, store: SampleStore): RuleResult {
        val purgeStats    = store.stats("evap_purge",    EVAP_WINDOW_MS) ?: return RuleResult.NONE
        val pressureStats = store.stats("evap_pressure", EVAP_WINDOW_MS) ?: return RuleResult.NONE
        if (purgeStats.count < EVAP_MIN_SAMPLES || pressureStats.count < EVAP_MIN_SAMPLES) return RuleResult.NONE
        if (purgeStats.avg < EVAP_PURGE_MIN_PCT) return RuleResult.NONE
        val eligible = setOf("evap_leak_possible")
        if (pressureStats.avg <= EVAP_LEAK_PRESSURE_PA) return RuleResult(emptyList(), eligible)
        val windowSec = (EVAP_WINDOW_MS / 1000).toInt()
        return RuleResult(
            listOf(DiagnosticFinding(
                id          = "evap_leak_possible",
                title       = "Possible evap system leak",
                description = "System not holding vacuum while purging. Possible causes: loose fuel cap, cracked evap hose, or faulty purge/vent valve.",
                severity    = FindingSeverity.LOW,
                confidence  = FindingConfidence.LOW,
                evidence    = mapOf(
                    "Avg evap purge"    to "${purgeStats.avg.toInt()}%",
                    "Avg evap pressure" to "${pressureStats.avg.toInt()} Pa",
                    "Samples"           to "${purgeStats.count} over ${windowSec}s",
                ),
            )),
            eligible,
        )
    }

    private fun monitorReadinessFinding(state: VehicleState): RuleResult {
        val statuses = state.monitorStatuses
            ?: return distSinceClearedFinding(state) // PID 0x01 not available
        val eligible = setOf("monitors_incomplete")
        val incomplete = statuses.filter { it.state == MonitorReadinessState.INCOMPLETE }
        if (incomplete.isEmpty()) return RuleResult(emptyList(), eligible)
        val names = incomplete.joinToString(", ") { it.name }
        return RuleResult(
            listOf(DiagnosticFinding(
                id          = "monitors_incomplete",
                title       = "${incomplete.size} readiness monitor(s) incomplete",
                description = "Monitors complete after sufficient mixed driving following a code clear. An emissions test will fail while any supported monitor is incomplete.",
                severity    = FindingSeverity.INFO,
                confidence  = FindingConfidence.HIGH,
                evidence    = mapOf("Incomplete" to names),
            )),
            eligible,
        )
    }

    private fun distSinceClearedFinding(state: VehicleState): RuleResult {
        val dist = state.distSinceCodesClearedKm ?: return RuleResult.NONE
        val eligible = setOf("monitors_incomplete")
        if (dist >= DIST_SINCE_CLEAR_KM) return RuleResult(emptyList(), eligible)
        return RuleResult(
            listOf(DiagnosticFinding(
                id          = "monitors_incomplete",
                title       = "Readiness monitors may be incomplete",
                description = "Readiness monitors typically need 50–100 km of mixed driving to complete. Diagnostics and emissions test results may be unreliable until then.",
                severity    = FindingSeverity.INFO,
                confidence  = FindingConfidence.MEDIUM,
                evidence    = mapOf("Distance since clear" to "${dist.toInt()} km"),
            )),
            eligible,
        )
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun hasMinimumData(state: VehicleState): Boolean =
        state.rpm != null || state.coolantTempC != null

    private fun buildDataLimitations(
        state: VehicleState,
        store: SampleStore,
        recentStates: List<VehicleState>,
        opStateOf: (VehicleState) -> OperatingState,
    ): List<String> {
        val out = mutableListOf<String>()
        val evDriveDetected = recentStates.any { opStateOf(it) == OperatingState.EV_DRIVE }
        if (evDriveDetected)             out += "Hybrid electric drive detected — fuel trim, O2, and thermostat rules paused during engine-off periods"
        if (state.rpm == null)           out += "RPM signal not fresh"
        if (state.coolantTempC == null)  out += "Coolant temperature not fresh"
        if (state.stftBank1Pct == null)  out += "Fuel trim data not available"
        else if (state.stftBank2Pct == null) out += "Bank 2 fuel trims not supported by this vehicle"
        if (state.o2Bank1Sensor1V == null && state.commandedLambda == null &&
            state.afrBank1Sensor1Lambda == null && state.afrCurBank1Sensor1Lambda == null) out += "Upstream O2 / lambda signal not available"
        val stftCount = store.stats("stft", FUEL_TRIM_WINDOW_MS)?.count ?: 0
        if (stftCount < FUEL_TRIM_MIN_SAMPLES) out += "Insufficient fuel trim history (${stftCount} samples, need $FUEL_TRIM_MIN_SAMPLES)"
        val patternCutoff = state.timestamp - FUEL_TRIM_PATTERN_WINDOW_MS
        val recentInWindow = recentStates.filter { it.timestamp >= patternCutoff }
        val cruiseCount = recentInWindow.count { opStateOf(it) == OperatingState.CRUISE }
        if (cruiseCount < FUEL_TRIM_MIN_COND_SAMPLES) out += "No steady-cruise window available yet for pattern analysis"
        val idleCount = recentInWindow.count { opStateOf(it) == OperatingState.WARM_IDLE }
        if (idleCount < FUEL_TRIM_MIN_COND_SAMPLES) out += "Insufficient warm-idle data for idle pattern analysis"
        return out
    }

    private fun buildWindowContext(
        store: SampleStore,
        recentStates: List<VehicleState>,
        opStateOf: (VehicleState) -> OperatingState,
    ): DiagnosticWindowContext {
        val windowMs = SampleStore.WINDOW_30S
        val windowSec = (windowMs / 1000).toInt()
        val observedStates = recentStates
            .map { opStateOf(it).label }
            .toSet()
        return DiagnosticWindowContext(
            windowSeconds            = windowSec,
            rpm                      = store.stats("rpm",      windowMs),
            speed                    = store.stats("speed",    windowMs),
            coolant                  = store.stats("coolant",  windowMs),
            stftBank1                = store.stats("stft",     windowMs),
            ltftBank1                = store.stats("ltft",     windowMs),
            stftBank2                = store.stats("stft2",    windowMs),
            ltftBank2                = store.stats("ltft2",    windowMs),
            mafGps                   = store.stats("maf",      windowMs),
            o2Upstream               = store.stats("o2_b1s1",  windowMs),
            afrUpstream              = store.stats("afr_b1s1", windowMs) ?: store.stats("afr_i_b1s1", windowMs),
            o2Downstream             = store.stats("o2_b1s2",  windowMs),
            throttle                 = store.stats("throttle", windowMs),
            engineLoad               = store.stats("load",     windowMs),
            operatingStatesObserved  = observedStates,
        )
    }

    private fun buildEvidenceMap(state: VehicleState): Map<String, String> = buildMap {
        state.rpm?.let         { put("RPM",      "${it.toInt()} rpm") }
        state.speedKph?.let    { put("Speed",    "${it.toInt()} km/h") }
        state.throttlePct?.let { put("Throttle", "${it.toInt()}%") }
        state.engineLoadPct?.let { put("Load",   "${it.toInt()}%") }
    }

    private fun insufficientDataFinding() = DiagnosticFinding(
        id          = "insufficient_data",
        title       = "No sensor data available",
        description = "Not enough sensor data is available to derive a meaningful diagnosis. " +
                      "Connect to a vehicle and allow a few seconds for readings to populate.",
        severity    = FindingSeverity.INFO,
        confidence  = FindingConfidence.LOW,
    )
}
