package com.obdvis.android.domain.health

/**
 * Manages persistent per-finding state across repeated interpreter cycles.
 *
 * Findings from each [DiagnosticsInterpreter.interpret] call are merged into an internal map that
 * tracks lifecycle: PENDING → ACTIVE → FADING → removed. PENDING is an internal-only buffer for
 * findings in [REQUIRES_PERSISTENCE] — they must be continuously present for
 * [PERSISTENCE_THRESHOLD_MS] before graduating to ACTIVE. FADING findings linger for
 * [FADING_DURATION_MS] after a condition clears, giving the user time to notice.
 *
 * [merge] is not thread-safe; call it only from the main thread (viewModelScope coroutines).
 */
class FindingStateManager {

    companion object {
        val REQUIRES_PERSISTENCE = setOf(
            "o2_upstream_dead", "o2_upstream_lazy",
            "o2_upstream_dead_b2", "o2_upstream_lazy_b2",
            "o2_downstream_cycling", "o2_downstream_cycling_b2",
            "timing_retarded",
            "charging_voltage_low",
        )
        const val PERSISTENCE_THRESHOLD_MS = 15_000L
        const val FADING_DURATION_MS = 15_000L
        private const val MAX_EVENT_LOG = 200
    }

    private enum class InternalStatus { PENDING, ACTIVE, FADING }

    private data class InternalRecord(
        val finding: DiagnosticFinding,
        val firstSeenMs: Long,
        val lastSeenMs: Long,
        val occurrenceCount: Int,
        val peakSeverity: FindingSeverity,
        val status: InternalStatus,
        val pendingSinceMs: Long? = null,
        val fadingSinceMs: Long? = null,
    )

    // Active and fading records (not yet expired). Keyed by finding id.
    private val records = mutableMapOf<String, InternalRecord>()

    // Records that have fully cleared this session — kept for post-drive capture.
    private val clearedRecords = mutableMapOf<String, InternalRecord>()

    // Per-finding count of interpreter cycles where the rule was eligible to evaluate.
    private val eligibleCounts = mutableMapOf<String, Int>()

    private val events = ArrayDeque<FindingEvent>()

    /**
     * Merges [newFindings] from one interpreter cycle into the persistent state.
     * [eligibleIds] is the set of finding IDs whose rule was eligible to evaluate this cycle.
     * Returns the current set of ACTIVE and FADING records (not PENDING).
     */
    fun merge(newFindings: List<DiagnosticFinding>, eligibleIds: Set<String>, now: Long): Map<String, FindingRecord> {
        for (id in eligibleIds) eligibleCounts[id] = (eligibleCounts[id] ?: 0) + 1
        val newById = newFindings.associateBy { it.id }

        for (finding in newFindings) {
            val existing = records[finding.id]
            when {
                existing == null -> {
                    // Brand new finding — check cleared history for cumulative counts
                    val cleared = clearedRecords[finding.id]
                    val prevCount = cleared?.occurrenceCount ?: 0
                    val prevPeak = cleared?.peakSeverity ?: finding.severity
                    val needsPersistence = finding.id in REQUIRES_PERSISTENCE
                    records[finding.id] = InternalRecord(
                        finding = finding,
                        firstSeenMs = now,
                        lastSeenMs = now,
                        occurrenceCount = prevCount + 1,
                        peakSeverity = higherSeverity(prevPeak, finding.severity),
                        status = if (needsPersistence) InternalStatus.PENDING else InternalStatus.ACTIVE,
                        pendingSinceMs = if (needsPersistence) now else null,
                    )
                    if (!needsPersistence) emitEvent(FindingEvent.Appeared(finding, now))
                    clearedRecords.remove(finding.id)
                }
                existing.status == InternalStatus.PENDING -> {
                    val graduated = (now - (existing.pendingSinceMs ?: now)) >= PERSISTENCE_THRESHOLD_MS
                    records[finding.id] = existing.copy(
                        finding = finding,
                        lastSeenMs = now,
                        occurrenceCount = existing.occurrenceCount + 1,
                        peakSeverity = higherSeverity(existing.peakSeverity, finding.severity),
                        status = if (graduated) InternalStatus.ACTIVE else InternalStatus.PENDING,
                        pendingSinceMs = if (graduated) null else existing.pendingSinceMs,
                    )
                    if (graduated) emitEvent(FindingEvent.Appeared(finding, now))
                }
                existing.status == InternalStatus.FADING -> {
                    // Re-activate — resume accumulating from where we left off
                    records[finding.id] = existing.copy(
                        finding = finding,
                        lastSeenMs = now,
                        occurrenceCount = existing.occurrenceCount + 1,
                        peakSeverity = higherSeverity(existing.peakSeverity, finding.severity),
                        status = InternalStatus.ACTIVE,
                        fadingSinceMs = null,
                    )
                }
                existing.status == InternalStatus.ACTIVE -> {
                    val prevSeverity = existing.finding.severity
                    records[finding.id] = existing.copy(
                        finding = finding,
                        lastSeenMs = now,
                        occurrenceCount = existing.occurrenceCount + 1,
                        peakSeverity = higherSeverity(existing.peakSeverity, finding.severity),
                    )
                    if (finding.severity != prevSeverity) {
                        emitEvent(FindingEvent.SeverityChanged(finding, prevSeverity, now))
                    }
                }
            }
        }

        // Process records no longer in new interpreter output
        val toRemove = mutableListOf<String>()
        for ((id, record) in records) {
            if (id in newById) continue
            when (record.status) {
                InternalStatus.PENDING -> toRemove.add(id) // disappeared before graduating — silent
                InternalStatus.ACTIVE -> records[id] = record.copy(
                    status = InternalStatus.FADING,
                    fadingSinceMs = now,
                )
                InternalStatus.FADING -> {
                    if ((now - (record.fadingSinceMs ?: now)) >= FADING_DURATION_MS) {
                        clearedRecords[id] = record
                        toRemove.add(id)
                        emitEvent(FindingEvent.Cleared(id, record.finding.title, now))
                    }
                }
            }
        }
        toRemove.forEach { records.remove(it) }

        return buildPublicMap()
    }

    fun eventLog(): List<FindingEvent> = events.toList()

    /**
     * Returns all records seen this session — including fully-cleared ones.
     * Used when capturing [PostDriveData] at session end.
     */
    fun snapshotAllRecords(): List<FindingRecord> {
        val active = buildPublicMap()
        val cleared = clearedRecords.mapValues { (id, r) ->
            FindingRecord(
                finding = r.finding,
                firstSeenMs = r.firstSeenMs,
                lastSeenMs = r.lastSeenMs,
                occurrenceCount = r.occurrenceCount,
                peakSeverity = r.peakSeverity,
                status = FindingStatus.FADING,
                eligibleCount = eligibleCounts[id] ?: 0,
            )
        }
        // Active/fading records take precedence over cleared ones with the same id
        return (cleared + active).values.toList()
    }

    fun reset() {
        records.clear()
        clearedRecords.clear()
        eligibleCounts.clear()
        events.clear()
    }

    private fun buildPublicMap(): Map<String, FindingRecord> =
        records
            .filter { (_, r) -> r.status != InternalStatus.PENDING }
            .mapValues { (id, r) ->
                FindingRecord(
                    finding = r.finding,
                    firstSeenMs = r.firstSeenMs,
                    lastSeenMs = r.lastSeenMs,
                    occurrenceCount = r.occurrenceCount,
                    peakSeverity = r.peakSeverity,
                    status = if (r.status == InternalStatus.ACTIVE) FindingStatus.ACTIVE else FindingStatus.FADING,
                    fadingSinceMs = r.fadingSinceMs,
                    eligibleCount = eligibleCounts[id] ?: 0,
                )
            }

    private fun emitEvent(event: FindingEvent) {
        events.addFirst(event)
        while (events.size > MAX_EVENT_LOG) events.removeLast()
    }

    private fun higherSeverity(a: FindingSeverity, b: FindingSeverity): FindingSeverity =
        if (b.ordinal > a.ordinal) b else a
}
