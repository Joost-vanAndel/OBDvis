package com.obdvis.android.domain.health

/** UI state for the Vehicle Health screen. */
sealed class HealthState {
    /** No analysis has been run yet this session. */
    data object Idle : HealthState()

    /** Diagnostic analysis is complete. */
    data class Ready(val summary: DiagnosticSummary) : HealthState()
}

fun HealthState.summaryOrNull(): DiagnosticSummary? = when (this) {
    is HealthState.Ready -> summary
    else                 -> null
}
