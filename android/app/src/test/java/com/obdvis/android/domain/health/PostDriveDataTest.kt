package com.obdvis.android.domain.health

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PostDriveDataTest {

    private fun finding(id: String, severity: FindingSeverity) = DiagnosticFinding(
        id = id,
        title = id,
        description = "",
        severity = severity,
        confidence = FindingConfidence.HIGH,
    )

    private fun record(
        id: String,
        severity: FindingSeverity,
        occurrences: Int = 1,
        peakSeverity: FindingSeverity = severity,
    ) = FindingRecord(
        finding = finding(id, severity),
        firstSeenMs = 0L,
        lastSeenMs = 1000L,
        occurrenceCount = occurrences,
        peakSeverity = peakSeverity,
        status = FindingStatus.ACTIVE,
    )

    private fun data(
        findingRecords: List<FindingRecord> = emptyList(),
        totalHealthChecks: Int = findingRecords.size.coerceAtLeast(1),
        operatingStateCounts: Map<OperatingState, Int> = emptyMap(),
        dtcResult: DtcReadResult = DtcReadResult(),
    ) = PostDriveData(
        sessionStartMs = 1_000L,
        sessionEndMs = 61_000L,
        findingRecords = findingRecords,
        totalHealthChecks = totalHealthChecks,
        operatingStateCounts = operatingStateCounts,
        dtcResult = dtcResult,
    )

    // ── durationMs ────────────────────────────────────────────────────────────

    @Test
    fun `durationMs returns difference between end and start`() {
        assertEquals(60_000L, data().durationMs)
    }

    // ── operatingStateBreakdown ───────────────────────────────────────────────

    @Test
    fun `operatingStateBreakdown reflects operatingStateCounts`() {
        val counts = mapOf(
            OperatingState.WARM_IDLE to 2,
            OperatingState.CRUISE to 1,
            OperatingState.ACCELERATION to 1,
        )
        val breakdown = data(operatingStateCounts = counts).operatingStateBreakdown
        assertEquals(2, breakdown[OperatingState.WARM_IDLE])
        assertEquals(1, breakdown[OperatingState.CRUISE])
        assertEquals(1, breakdown[OperatingState.ACCELERATION])
    }

    @Test
    fun `operatingStateBreakdown is empty when no counts`() {
        assertTrue(data().operatingStateBreakdown.isEmpty())
    }

    // ── aggregatedFindings ────────────────────────────────────────────────────

    @Test
    fun `aggregatedFindings is empty when no records`() {
        assertTrue(data().aggregatedFindings.isEmpty())
    }

    @Test
    fun `aggregatedFindings maps each record to AggregatedFinding`() {
        val records = listOf(record("engine_cold", FindingSeverity.INFO, occurrences = 5))
        val agg = data(records, totalHealthChecks = 10).aggregatedFindings
        assertEquals(1, agg.size)
        assertEquals("engine_cold", agg[0].finding.id)
        assertEquals(5, agg[0].occurrences)
        assertEquals(10, agg[0].totalSnapshots)
    }

    @Test
    fun `aggregatedFindings uses peakSeverity for the representative finding`() {
        // Finding's current severity is LOW but peaked at HIGH
        val records = listOf(record("fuel_trim_lean", FindingSeverity.LOW, peakSeverity = FindingSeverity.HIGH))
        val agg = data(records).aggregatedFindings
        assertEquals(FindingSeverity.HIGH, agg[0].finding.severity)
    }

    @Test
    fun `aggregatedFindings sorted by severity descending then occurrences descending`() {
        val records = listOf(
            record("overheating", FindingSeverity.HIGH, occurrences = 1),
            record("fuel_trim", FindingSeverity.MEDIUM, occurrences = 3),
            record("engine_cold", FindingSeverity.INFO, occurrences = 10),
        )
        val agg = data(records, totalHealthChecks = 10).aggregatedFindings
        assertEquals("overheating", agg[0].finding.id)
        assertEquals("fuel_trim", agg[1].finding.id)
        assertEquals("engine_cold", agg[2].finding.id)
    }

    @Test
    fun `aggregatedFindings secondary sort by occurrences descending`() {
        val records = listOf(
            record("charging_low", FindingSeverity.MEDIUM, occurrences = 2),
            record("lean_b1", FindingSeverity.MEDIUM, occurrences = 5),
        )
        val agg = data(records).aggregatedFindings
        assertEquals("lean_b1", agg[0].finding.id)   // more occurrences first
        assertEquals("charging_low", agg[1].finding.id)
    }

    @Test
    fun `aggregatedFindings totalSnapshots reflects totalHealthChecks`() {
        val records = listOf(record("charging_low", FindingSeverity.LOW, occurrences = 2))
        val agg = data(records, totalHealthChecks = 50).aggregatedFindings
        assertEquals(50, agg[0].totalSnapshots)
    }

    // ── dtcResult ─────────────────────────────────────────────────────────────

    @Test
    fun `dtcResult is empty by default`() {
        assertTrue(data().dtcResult.isEmpty)
    }

    @Test
    fun `dtcResult reflects provided stored and pending codes`() {
        val result = DtcReadResult(
            stored = listOf("P0300"),
            pending = listOf("P0420"),
        )
        assertEquals(result, data(dtcResult = result).dtcResult)
    }
}
