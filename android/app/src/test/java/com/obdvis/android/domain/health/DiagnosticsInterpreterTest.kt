package com.obdvis.android.domain.health

import com.obdvis.android.domain.store.SampleStore
import com.obdvis.android.domain.model.SensorSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsInterpreterTest {

    private val emptyStore = SampleStore()

    /**
     * Minimal VehicleState that passes hasMinimumData (rpm != null || coolantTempC != null).
     * Override only the fields relevant to each test.
     */
    private fun state(
        rpm: Float? = 800f,
        speedKph: Float? = 0f,
        throttlePct: Float? = 5f,
        coolantTempC: Float? = 90f,
        stftBank1Pct: Float? = null,
        ltftBank1Pct: Float? = null,
        fuelSystemStatusRaw: Float? = null,
        milRunTimeMin: Float? = null,
        activeDtcs: List<String> = emptyList(),
        oilTempC: Float? = null,
        obdVoltage: Float? = null,
        ecuVoltage: Float? = null,
        engineRunTimeSec: Float? = null,
        distSinceCodesClearedKm: Float? = null,
    ) = VehicleState(
        timestamp = System.currentTimeMillis(),
        rpm = rpm,
        speedKph = speedKph,
        coolantTempC = coolantTempC,
        intakeTempC = null,
        mafGps = null,
        mapKpa = null,
        throttlePct = throttlePct,
        engineLoadPct = null,
        fuelLevelPct = null,
        stftBank1Pct = stftBank1Pct,
        ltftBank1Pct = ltftBank1Pct,
        fuelSystemStatusRaw = fuelSystemStatusRaw,
        milRunTimeMin = milRunTimeMin,
        activeDtcs = activeDtcs,
        oilTempC = oilTempC,
        obdVoltage = obdVoltage,
        ecuVoltage = ecuVoltage,
        engineRunTimeSec = engineRunTimeSec,
        distSinceCodesClearedKm = distSinceCodesClearedKm,
    )

    private fun hasFinding(id: String, summary: DiagnosticSummary): Boolean =
        summary.findings.any { it.id == id }

    // ── hasMinimumData gate ───────────────────────────────────────────────────

    @Test
    fun `interpret with no rpm and no coolant returns insufficient_data finding`() {
        val summary = DiagnosticsInterpreter.interpret(
            state(rpm = null, coolantTempC = null),
            emptyStore,
        )
        assertTrue(hasFinding("insufficient_data", summary))
        assertEquals(1, summary.findings.size)
    }

    @Test
    fun `interpret with only coolant available passes the data gate`() {
        val summary = DiagnosticsInterpreter.interpret(
            state(rpm = null, coolantTempC = 90f),
            emptyStore,
        )
        assertFalse(hasFinding("insufficient_data", summary))
    }

    @Test
    fun `interpret with only rpm available passes the data gate`() {
        val summary = DiagnosticsInterpreter.interpret(
            state(rpm = 800f, coolantTempC = null),
            emptyStore,
        )
        assertFalse(hasFinding("insufficient_data", summary))
    }

    // ── temperature findings ──────────────────────────────────────────────────

    @Test
    fun `interpret cold coolant produces engine_cold INFO finding`() {
        val summary = DiagnosticsInterpreter.interpret(
            state(coolantTempC = 30f), // below COLD_COOLANT_C = 40
            emptyStore,
        )
        val finding = summary.findings.firstOrNull { it.id == "engine_cold" }
        assertTrue("Expected engine_cold finding", finding != null)
        assertEquals(FindingSeverity.INFO, finding!!.severity)
    }

    @Test
    fun `interpret overheating coolant produces engine_overheating HIGH finding`() {
        val summary = DiagnosticsInterpreter.interpret(
            state(coolantTempC = 110f), // above HOT_COOLANT_C = 105
            emptyStore,
        )
        val finding = summary.findings.firstOrNull { it.id == "engine_overheating" }
        assertTrue("Expected engine_overheating finding", finding != null)
        assertEquals(FindingSeverity.HIGH, finding!!.severity)
        assertEquals(FindingConfidence.HIGH, finding.confidence)
    }

    @Test
    fun `interpret normal coolant temp produces no temperature warning finding`() {
        val summary = DiagnosticsInterpreter.interpret(
            state(coolantTempC = 90f),
            emptyStore,
        )
        assertFalse(hasFinding("engine_cold", summary))
        assertFalse(hasFinding("engine_overheating", summary))
    }

    // ── operating state detection ─────────────────────────────────────────────

    @Test
    fun `interpret warm idle conditions produces WARM_IDLE operating state`() {
        val summary = DiagnosticsInterpreter.interpret(
            state(rpm = 800f, speedKph = 0f, throttlePct = 5f, coolantTempC = 90f),
            emptyStore,
        )
        assertEquals(OperatingState.WARM_IDLE, summary.operatingState)
    }

    @Test
    fun `interpret cold engine conditions produces COLD_START operating state`() {
        val summary = DiagnosticsInterpreter.interpret(
            state(rpm = 1000f, speedKph = 0f, throttlePct = 5f, coolantTempC = 30f),
            emptyStore,
        )
        assertEquals(OperatingState.COLD_START, summary.operatingState)
    }

    @Test
    fun `interpret high throttle produces ACCELERATION operating state`() {
        val summary = DiagnosticsInterpreter.interpret(
            state(rpm = 3000f, speedKph = 40f, throttlePct = 70f, coolantTempC = 90f),
            emptyStore,
        )
        assertEquals(OperatingState.ACCELERATION, summary.operatingState)
    }

    @Test
    fun `interpret cruising conditions produces CRUISE operating state`() {
        val summary = DiagnosticsInterpreter.interpret(
            state(rpm = 2000f, speedKph = 80f, throttlePct = 30f, coolantTempC = 90f),
            emptyStore,
        )
        assertEquals(OperatingState.CRUISE, summary.operatingState)
    }

    @Test
    fun `interpret steady low throttle speed produces CRUISE operating state`() {
        val store = speedStore(80f, 80f, 80f, 80f, 80f)
        val summary = DiagnosticsInterpreter.interpret(
            state(rpm = 2000f, speedKph = 80f, throttlePct = 3f, coolantTempC = 90f),
            store,
        )
        assertEquals(OperatingState.CRUISE, summary.operatingState)
    }

    @Test
    fun `interpret sustained speed drop with low throttle produces DECELERATION operating state`() {
        val store = speedStore(80f, 77f, 74f, 71f, 68f)
        val summary = DiagnosticsInterpreter.interpret(
            state(rpm = 1800f, speedKph = 68f, throttlePct = 2f, coolantTempC = 90f),
            store,
        )
        assertEquals(OperatingState.DECELERATION, summary.operatingState)
    }

    @Test
    fun `interpret moving vehicle with missing throttle produces UNKNOWN operating state`() {
        val summary = DiagnosticsInterpreter.interpret(
            state(rpm = 2000f, speedKph = 80f, throttlePct = null, coolantTempC = 90f),
            speedStore(80f, 77f, 74f, 71f, 68f),
        )
        assertEquals(OperatingState.UNKNOWN, summary.operatingState)
    }

    @Test
    fun `interpret engine off (low rpm) produces UNKNOWN operating state`() {
        // RPM = 50 → engineOn = false → UNKNOWN
        val summary = DiagnosticsInterpreter.interpret(
            state(rpm = 50f, coolantTempC = 90f),
            emptyStore,
        )
        assertEquals(OperatingState.UNKNOWN, summary.operatingState)
    }

    private fun speedStore(vararg speeds: Float): SampleStore {
        val store = SampleStore()
        val now = System.currentTimeMillis()
        speeds.forEachIndexed { index, speed ->
            store.add(
                SensorSample(
                    pidId = "speed",
                    elapsedSeconds = index.toFloat(),
                    value = speed,
                    timestampMs = now - (speeds.lastIndex - index) * 1_000L,
                )
            )
        }
        return store
    }

    // ── DTC findings ──────────────────────────────────────────────────────────

    @Test
    fun `interpret with active DTCs produces dtc_present finding`() {
        val summary = DiagnosticsInterpreter.interpret(
            state(activeDtcs = listOf("P0300", "P0171")),
            emptyStore,
        )
        val finding = summary.findings.first { it.id == "dtc_present" }
        assertEquals(
            DtcInfo.genericExplanation("P0300"),
            finding.evidence["P0300"],
        )
        assertEquals(
            DtcInfo.genericExplanation("P0171"),
            finding.evidence["P0171"],
        )
    }

    @Test
    fun `interpret with no active DTCs produces no dtc_present finding`() {
        val summary = DiagnosticsInterpreter.interpret(
            state(activeDtcs = emptyList()),
            emptyStore,
        )
        assertFalse(hasFinding("dtc_present", summary))
    }

    // ── MIL runtime ──────────────────────────────────────────────────────────

    @Test
    fun `interpret with long MIL runtime produces finding`() {
        // MIL_RUNTIME_WARN_MIN = 30 → 60 minutes should trigger
        val summary = DiagnosticsInterpreter.interpret(
            state(milRunTimeMin = 60f),
            emptyStore,
        )
        assertTrue(hasFinding("mil_runtime", summary))
    }

    @Test
    fun `interpret with short MIL runtime produces no mil finding`() {
        val summary = DiagnosticsInterpreter.interpret(
            state(milRunTimeMin = 5f),
            emptyStore,
        )
        assertFalse(hasFinding("mil_runtime", summary))
    }

    // ── Wideband (AFR) O2 sensor findings ────────────────────────────────────

    private fun warmRunningState(
        afrBank1Sensor1Lambda: Float? = null,
        afrBank1Sensor2Lambda: Float? = null,
        afrCurBank1Sensor1Lambda: Float? = null,
        o2Bank1Sensor1V: Float? = null,
        o2Bank1Sensor2V: Float? = null,
        fuelSystemStatusRaw: Float? = 2f, // closed loop
    ) = VehicleState(
        timestamp               = System.currentTimeMillis(),
        rpm                     = 800f,
        speedKph                = 0f,
        coolantTempC            = 90f,
        intakeTempC             = null,
        mafGps                  = null,
        mapKpa                  = null,
        throttlePct             = 5f,
        engineLoadPct           = null,
        fuelLevelPct            = null,
        stftBank1Pct            = null,
        ltftBank1Pct            = null,
        fuelSystemStatusRaw     = fuelSystemStatusRaw,
        afrBank1Sensor1Lambda   = afrBank1Sensor1Lambda,
        afrBank1Sensor2Lambda   = afrBank1Sensor2Lambda,
        afrCurBank1Sensor1Lambda = afrCurBank1Sensor1Lambda,
        o2Bank1Sensor1V         = o2Bank1Sensor1V,
        o2Bank1Sensor2V         = o2Bank1Sensor2V,
    )

    @Test
    fun `wideband upstream dead sensor single reading triggers o2_upstream_dead`() {
        val summary = DiagnosticsInterpreter.interpret(
            warmRunningState(afrBank1Sensor1Lambda = 0.01f), // below AFR_SENSOR_DEAD_LAMBDA = 0.05
            emptyStore,
        )
        assertTrue(hasFinding("o2_upstream_dead", summary))
    }

    @Test
    fun `wideband upstream dead sensor window triggers o2_upstream_dead`() {
        val store = SampleStore()
        val now = System.currentTimeMillis()
        repeat(6) { i ->
            store.add(SensorSample(
                pidId          = "afr_b1s1",
                elapsedSeconds = i.toFloat(),
                value          = 0.01f, // near zero
                timestampMs    = now - (i * 1_000L),
            ))
        }
        val summary = DiagnosticsInterpreter.interpret(
            warmRunningState(afrBank1Sensor1Lambda = 0.01f),
            store,
        )
        assertTrue(hasFinding("o2_upstream_dead", summary))
        val finding = summary.findings.first { it.id == "o2_upstream_dead" }
        assertEquals(FindingConfidence.MEDIUM, finding.confidence)
        assertTrue("Evidence should show λ units", finding.evidence.values.any { it.contains("λ") })
    }

    @Test
    fun `current-type wideband upstream dead sensor window triggers o2_upstream_dead`() {
        // Vehicle only implements PID 0x34-0x37 (current-type), not 0x24-0x27 — afr_b1s1 has no data.
        val store = SampleStore()
        val now = System.currentTimeMillis()
        repeat(6) { i ->
            store.add(SensorSample(
                pidId          = "afr_i_b1s1",
                elapsedSeconds = i.toFloat(),
                value          = 0.01f, // near zero
                timestampMs    = now - (i * 1_000L),
            ))
        }
        val summary = DiagnosticsInterpreter.interpret(
            warmRunningState(afrCurBank1Sensor1Lambda = 0.01f),
            store,
        )
        assertTrue(hasFinding("o2_upstream_dead", summary))
        val finding = summary.findings.first { it.id == "o2_upstream_dead" }
        assertTrue("Evidence should show λ units", finding.evidence.values.any { it.contains("λ") })
    }

    @Test
    fun `wideband upstream not varying in closed loop triggers o2_upstream_lazy`() {
        val store = SampleStore()
        val now = System.currentTimeMillis()
        // Lambda stuck at 1.0 with zero variation
        repeat(6) { i ->
            store.add(SensorSample(
                pidId          = "afr_b1s1",
                elapsedSeconds = i.toFloat(),
                value          = 1.000f,
                timestampMs    = now - (i * 1_000L),
            ))
        }
        val summary = DiagnosticsInterpreter.interpret(
            warmRunningState(afrBank1Sensor1Lambda = 1.0f, fuelSystemStatusRaw = 2f),
            store,
        )
        assertTrue(hasFinding("o2_upstream_lazy", summary))
        val finding = summary.findings.first { it.id == "o2_upstream_lazy" }
        assertEquals(FindingConfidence.LOW, finding.confidence)
    }

    @Test
    fun `wideband upstream healthy sensor does not trigger any upstream O2 finding`() {
        val store = SampleStore()
        val now = System.currentTimeMillis()
        // Lambda varying normally between 0.95 and 1.05
        val values = listOf(0.95f, 1.02f, 0.97f, 1.04f, 0.96f, 1.03f)
        values.forEachIndexed { i, v ->
            store.add(SensorSample(
                pidId          = "afr_b1s1",
                elapsedSeconds = i.toFloat(),
                value          = v,
                timestampMs    = now - (i * 1_000L),
            ))
        }
        val summary = DiagnosticsInterpreter.interpret(
            warmRunningState(afrBank1Sensor1Lambda = 1.0f),
            store,
        )
        assertFalse(hasFinding("o2_upstream_dead", summary))
        assertFalse(hasFinding("o2_upstream_lazy", summary))
    }

    @Test
    fun `wideband upstream and narrowband downstream do not infer catalyst efficiency`() {
        val store = SampleStore()
        val now = System.currentTimeMillis()
        // Both signals vary substantially, but generic wideband data is not used for catalyst inference.
        val afrValues = listOf(0.96f, 1.02f, 0.97f, 1.03f, 0.95f, 1.04f)
        afrValues.forEachIndexed { i, v ->
            store.add(SensorSample(
                pidId          = "afr_b1s1",
                elapsedSeconds = i.toFloat(),
                value          = v,
                timestampMs    = now - (i * 1_000L),
            ))
        }
        // Downstream narrowband also swings.
        val o2Values = listOf(0.15f, 0.75f, 0.20f, 0.80f, 0.18f, 0.78f)
        o2Values.forEachIndexed { i, v ->
            store.add(SensorSample(
                pidId          = "o2_b1s2",
                elapsedSeconds = i.toFloat(),
                value          = v,
                timestampMs    = now - (i * 1_000L),
            ))
        }
        val summary = DiagnosticsInterpreter.interpret(
            warmRunningState(afrBank1Sensor1Lambda = 1.0f, o2Bank1Sensor2V = 0.5f),
            store,
        )
        assertFalse(hasFinding("o2_downstream_cycling", summary))
    }

    @Test
    fun `wideband upstream and downstream do not infer catalyst efficiency`() {
        val store = SampleStore()
        val now = System.currentTimeMillis()
        // Both wideband signals vary substantially.
        val upValues = listOf(0.96f, 1.02f, 0.97f, 1.03f, 0.95f, 1.04f)
        upValues.forEachIndexed { i, v ->
            store.add(SensorSample(
                pidId          = "afr_b1s1",
                elapsedSeconds = i.toFloat(),
                value          = v,
                timestampMs    = now - (i * 1_000L),
            ))
        }
        // Downstream wideband also swings.
        val downValues = listOf(0.94f, 1.01f, 0.95f, 1.02f, 0.94f, 1.03f)
        downValues.forEachIndexed { i, v ->
            store.add(SensorSample(
                pidId          = "afr_b1s2",
                elapsedSeconds = i.toFloat(),
                value          = v,
                timestampMs    = now - (i * 1_000L),
            ))
        }
        val summary = DiagnosticsInterpreter.interpret(
            warmRunningState(afrBank1Sensor1Lambda = 1.0f, afrBank1Sensor2Lambda = 1.0f),
            store,
        )
        assertFalse(hasFinding("o2_downstream_cycling", summary))
    }

    @Test
    fun `narrowband upstream and wideband downstream do not infer catalyst efficiency`() {
        val store = SampleStore()
        val now = System.currentTimeMillis()
        // Upstream narrowband cycling — range > O2_UPSTREAM_SWING_V = 0.30
        val upValues = listOf(0.10f, 0.85f, 0.12f, 0.88f, 0.11f, 0.87f)
        upValues.forEachIndexed { i, v ->
            store.add(SensorSample(
                pidId          = "o2_b1s1",
                elapsedSeconds = i.toFloat(),
                value          = v,
                timestampMs    = now - (i * 1_000L),
            ))
        }
        // Downstream wideband also swings.
        val downValues = listOf(0.94f, 1.01f, 0.95f, 1.02f, 0.94f, 1.03f)
        downValues.forEachIndexed { i, v ->
            store.add(SensorSample(
                pidId          = "afr_b1s2",
                elapsedSeconds = i.toFloat(),
                value          = v,
                timestampMs    = now - (i * 1_000L),
            ))
        }
        // o2SensorsPresentRaw = 1 (0x01) → hasUpstreamO2B1 = true, hasDownstreamO2B1 = false
        val summary = DiagnosticsInterpreter.interpret(
            warmRunningState(o2Bank1Sensor1V = 0.5f, afrBank1Sensor2Lambda = 1.0f)
                .copy(o2SensorsPresentRaw = 1f),
            store,
        )
        assertFalse(hasFinding("o2_downstream_cycling", summary))
    }

    private fun catalystCruiseState(fuelSystemStatusRaw: Float = 2f) = warmRunningState(
        o2Bank1Sensor1V = 0.5f,
        o2Bank1Sensor2V = 0.6f,
        fuelSystemStatusRaw = fuelSystemStatusRaw,
    ).copy(
        speedKph = 60f,
        throttlePct = 20f,
        engineLoadPct = 30f,
        engineRunTimeSec = 600f,
        o2SensorsPresentRaw = 3f,
    )

    private fun catalystStore(
        upstream: List<Float>,
        downstream: List<Float>,
        throttle: List<Float> = List(10) { 20f + (it % 2) },
        fuelStatus: Float = 2f,
    ): SampleStore {
        val store = SampleStore()
        val now = System.currentTimeMillis()
        fun addSeries(pidId: String, values: List<Float>) {
            values.forEachIndexed { index, value ->
                val ageSteps = values.lastIndex - index
                store.add(SensorSample(
                    pidId = pidId,
                    elapsedSeconds = index * 2f,
                    value = value,
                    timestampMs = now - ageSteps * 2_000L,
                ))
            }
        }
        addSeries("o2_b1s1", upstream)
        addSeries("o2_b1s2", downstream)
        addSeries("speed", List(10) { 60f + (it % 2) })
        addSeries("throttle", throttle)
        addSeries("load", List(10) { 30f + (it % 2) })
        addSeries("fuel_sys_status", List(10) { fuelStatus })
        return store
    }

    private val activeUpstream = List(10) { if (it % 2 == 0) 0.10f else 0.90f }
    private val activeDownstream = List(10) { if (it % 2 == 0) 0.15f else 0.80f }

    @Test
    fun `stable closed loop cruise with high rear front activity ratio produces catalyst screening finding`() {
        val summary = DiagnosticsInterpreter.interpret(
            catalystCruiseState(),
            catalystStore(activeUpstream, activeDownstream),
        )

        val finding = summary.findings.first { it.id == "o2_downstream_cycling" }
        assertEquals(FindingSeverity.LOW, finding.severity)
        assertEquals(FindingConfidence.LOW, finding.confidence)
        assertTrue(finding.evidence.containsKey("Rear/front activity ratio"))
    }

    @Test
    fun `steady high downstream voltage does not produce catalyst finding`() {
        val summary = DiagnosticsInterpreter.interpret(
            catalystCruiseState(),
            catalystStore(activeUpstream, List(10) { 0.82f }),
        )

        assertFalse(hasFinding("o2_downstream_cycling", summary))
        assertFalse(hasFinding("o2_downstream_high", summary))
    }

    @Test
    fun `single downstream excursion does not produce catalyst finding`() {
        val isolatedExcursion = listOf(0.60f, 0.60f, 0.60f, 0.20f, 0.80f, 0.60f, 0.60f, 0.60f, 0.60f, 0.60f)
        val summary = DiagnosticsInterpreter.interpret(
            catalystCruiseState(),
            catalystStore(activeUpstream, isolatedExcursion),
        )

        assertFalse(hasFinding("o2_downstream_cycling", summary))
    }

    @Test
    fun `open loop activity does not produce catalyst finding`() {
        val summary = DiagnosticsInterpreter.interpret(
            catalystCruiseState(fuelSystemStatusRaw = 1f),
            catalystStore(activeUpstream, activeDownstream, fuelStatus = 1f),
        )

        assertFalse(hasFinding("o2_downstream_cycling", summary))
    }

    @Test
    fun `unstable throttle activity does not produce catalyst finding`() {
        val transientThrottle = List(10) { if (it % 2 == 0) 10f else 60f }
        val summary = DiagnosticsInterpreter.interpret(
            catalystCruiseState(),
            catalystStore(activeUpstream, activeDownstream, throttle = transientThrottle),
        )

        assertFalse(hasFinding("o2_downstream_cycling", summary))
    }

    @Test
    fun `sparse O2 history does not produce catalyst finding`() {
        val summary = DiagnosticsInterpreter.interpret(
            catalystCruiseState(),
            catalystStore(activeUpstream.take(6), activeDownstream.take(6)),
        )

        assertFalse(hasFinding("o2_downstream_cycling", summary))
    }

    @Test
    fun `wideband upstream present clears upstream O2 data limitation`() {
        val summary = DiagnosticsInterpreter.interpret(
            warmRunningState(afrBank1Sensor1Lambda = 1.0f),
            emptyStore,
        )
        assertFalse(
            summary.dataLimitations.any { it.contains("Upstream O2") },
        )
    }

    // ── Fuel trim severity suppressed in open loop ───────────────────────────────

    private fun storeWithLeanSamples(pidId: String = "stft", count: Int = 6): SampleStore {
        val store = SampleStore()
        val now = System.currentTimeMillis()
        repeat(count) { i ->
            store.add(SensorSample(
                pidId          = pidId,
                elapsedSeconds = i.toFloat(),
                value          = 22f, // above FUEL_TRIM_STRONG_FAULT_PCT = 20
                timestampMs    = now - (i * 1_000L),
            ))
        }
        return store
    }

    @Test
    fun `fuel trim severity fires in closed loop`() {
        val store = storeWithLeanSamples()
        val summary = DiagnosticsInterpreter.interpret(
            state(stftBank1Pct = 22f, ltftBank1Pct = 0f, fuelSystemStatusRaw = 2f), // bit 0x02 = closed loop
            store,
        )
        assertTrue(hasFinding("lean_strong_b1", summary))
    }

    @Test
    fun `fuel trim severity suppressed when confirmed open loop`() {
        val store = storeWithLeanSamples()
        val summary = DiagnosticsInterpreter.interpret(
            state(stftBank1Pct = 22f, ltftBank1Pct = 0f, fuelSystemStatusRaw = 1f), // bit 0x01 = OL-cold
            store,
        )
        assertFalse(hasFinding("lean_strong_b1", summary))
        assertFalse(hasFinding("lean_likely_b1", summary))
        assertFalse(hasFinding("lean_warning_b1", summary))
    }

    @Test
    fun `fuel trim severity runs when loop state unknown (no PID 0x03)`() {
        val store = storeWithLeanSamples()
        val summary = DiagnosticsInterpreter.interpret(
            state(stftBank1Pct = 22f, ltftBank1Pct = 0f, fuelSystemStatusRaw = null),
            store,
        )
        assertTrue(hasFinding("lean_strong_b1", summary))
    }

    // ── Store-based findings require populated store ───────────────────────────

    @Test
    fun `interpret with populated charging voltage data produces charging finding when voltage low`() {
        val store = SampleStore()
        val now = System.currentTimeMillis()
        // Add several low voltage samples within the 30 s window
        repeat(5) { i ->
            store.add(SensorSample(
                pidId = "obd_voltage",
                elapsedSeconds = i.toFloat(),
                value = 12.5f, // below CHARGING_VOLTAGE_LOW = 13.0
                timestampMs = now - (i * 1_000L),
            ))
        }
        val summary = DiagnosticsInterpreter.interpret(
            state(rpm = 800f, coolantTempC = 90f, obdVoltage = 12.5f),
            store,
        )
        assertTrue(hasFinding("charging_voltage_low", summary))
    }
}
