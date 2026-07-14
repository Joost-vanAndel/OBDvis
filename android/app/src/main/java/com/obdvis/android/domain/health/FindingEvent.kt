package com.obdvis.android.domain.health

/** Timestamped lifecycle event emitted by [FindingStateManager]. Used for the event log on the Health tab. */
sealed class FindingEvent {
    abstract val timestampMs: Long

    data class Appeared(
        val finding: DiagnosticFinding,
        override val timestampMs: Long,
    ) : FindingEvent()

    data class Cleared(
        val id: String,
        val title: String,
        override val timestampMs: Long,
    ) : FindingEvent()

    data class SeverityChanged(
        val finding: DiagnosticFinding,
        val from: FindingSeverity,
        override val timestampMs: Long,
    ) : FindingEvent()
}
