package com.obdvis.android.domain.health

/** Coarse label describing what the vehicle is currently doing. */
enum class OperatingState(val label: String) {
    COLD_START("Cold start"),
    WARM_IDLE("Warm idle"),
    CRUISE("Cruising"),
    ACCELERATION("Accelerating"),
    DECELERATION("Decelerating"),
    /** Engine off while vehicle is moving — hybrid electric drive or regenerative deceleration. */
    EV_DRIVE("Electric drive"),
    UNKNOWN("Unknown"),
}

/**
 * Compact, structured summary for diagnostics UI and session reporting.
 * Contains the current operating state, a snapshot of key sensor values,
 * the list of derived findings, and rolling-window context for rule evidence.
 *
 * Keep serialization-friendly: no non-primitive types beyond what is necessary.
 */
data class DiagnosticSummary(
    val timestamp: Long,
    val operatingState: OperatingState,
    val vehicleState: VehicleState,
    val findings: List<DiagnosticFinding>,
    /** Signals that are missing, stale, or whose coverage was insufficient for a rule to run. */
    val dataLimitations: List<String> = emptyList(),
    /** Aggregated window statistics populated when a SampleStore is available. */
    val windowContext: DiagnosticWindowContext? = null,
    /** IDs of all finding rules that were eligible to evaluate this cycle (preconditions met). */
    val eligibleFindingIds: Set<String> = emptySet(),
)
