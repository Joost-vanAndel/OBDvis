package com.obdvis.android.domain.health

data class AggregatedFinding(
    val finding: DiagnosticFinding,
    val occurrences: Int,
    val totalSnapshots: Int,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
)

data class PostDriveData(
    val sessionStartMs: Long,
    val sessionEndMs: Long,
    /** All findings seen during the session — active, fading, and fully-cleared. */
    val findingRecords: List<FindingRecord>,
    val totalHealthChecks: Int,
    val operatingStateCounts: Map<OperatingState, Int> = emptyMap(),
    val dtcResult: DtcReadResult = DtcReadResult(),
    val peakRpm: Float? = null,
    val peakSpeedKph: Float? = null,
    val peakCoolantTempC: Float? = null,
    val peakGForce: PeakGForce? = null,
) {
    val durationMs: Long get() = sessionEndMs - sessionStartMs

    val operatingStateBreakdown: Map<OperatingState, Int> get() = operatingStateCounts

    val aggregatedFindings: List<AggregatedFinding> get() =
        findingRecords
            .map { record ->
                // Use peakSeverity so the post-drive badge reflects the worst it got
                AggregatedFinding(
                    finding = record.finding.copy(severity = record.peakSeverity),
                    occurrences = record.occurrenceCount,
                    totalSnapshots = record.eligibleCount.takeIf { it > 0 } ?: totalHealthChecks,
                    firstSeenMs = record.firstSeenMs,
                    lastSeenMs = record.lastSeenMs,
                )
            }
            .sortedWith(
                compareByDescending<AggregatedFinding> { it.finding.severity.ordinal }
                    .thenByDescending { it.occurrences }
            )
}
