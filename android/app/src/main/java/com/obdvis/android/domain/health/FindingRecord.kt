package com.obdvis.android.domain.health

enum class FindingStatus { ACTIVE, FADING }

/**
 * Persistent, lifecycle-aware wrapper around a [DiagnosticFinding].
 *
 * Unlike a raw finding (which is ephemeral per interpreter cycle), a FindingRecord survives
 * across cycles: it enters as ACTIVE when first detected, lingers as FADING for
 * [FindingStateManager.FADING_DURATION_MS] after the condition clears, then is removed.
 * [occurrenceCount] accumulates across re-activations within the same session.
 */
data class FindingRecord(
    val finding: DiagnosticFinding,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val occurrenceCount: Int,
    val peakSeverity: FindingSeverity,
    val status: FindingStatus,
    val fadingSinceMs: Long? = null,
    /** How many interpreter cycles this finding's rule was eligible to evaluate. */
    val eligibleCount: Int = 0,
)
