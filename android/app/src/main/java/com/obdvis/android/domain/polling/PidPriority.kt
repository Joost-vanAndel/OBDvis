package com.obdvis.android.domain.polling

/**
 * Tab-specific polling weight functions and per-signal freshness limits.
 *
 * The scheduler score = age_ms × weightFn(pidId). Call overviewWeightOf on the Overview
 * tab and diagnosticWeightOf everywhere else (Health tab, background checks, Live tab).
 */
object PidPriorityGroups {

    // ── Overview weight function ───────────────────────────────────────────────
    // Prioritises the three live-display gauges. Everything else is background fill
    // for health checks — relevant but not time-critical for the Overview UI.
    private val OVERVIEW_HIGH   = setOf("rpm", "speed", "throttle")
    private val OVERVIEW_MEDIUM = setOf("coolant", "obd_voltage", "ecu_voltage")

    fun overviewWeightOf(pidId: String): Float = when (pidId) {
        in OVERVIEW_HIGH   -> 4f
        in OVERVIEW_MEDIUM -> 2f
        else               -> 1f
    }

    // ── Diagnostic weight function ────────────────────────────────────────────
    // Optimised for health-check quality. Real-time diagnostic signals highest,
    // slower-changing signals medium, historical counters lowest.
    private val DIAG_HIGH = setOf(
        "rpm", "speed", "throttle", "load",
        "fuel_sys_status", "stft", "ltft", "stft2", "ltft2",
        "o2_b1s1", "o2_b1s2", "o2_b2s1", "o2_b2s2",
        "afr_b1s1", "afr_i_b1s1", "lambda", "maf", "manifold",
    )
    private val DIAG_MEDIUM = setOf(
        "coolant", "oil_temp", "abs_load", "rel_throttle",
        "intake_temp", "timing", "ecu_voltage", "obd_voltage",
        "afr_b1s2", "afr_b2s1", "afr_b2s2",
        "afr_i_b1s2", "afr_i_b2s1", "afr_i_b2s2",
        "fuel_pressure", "catalyst_temp_b1s1", "catalyst_temp_b1s2",
        "fuel", "baro", "fuel_rate",
    )

    fun diagnosticWeightOf(pidId: String): Float = when (pidId) {
        in DIAG_HIGH   -> 4f
        in DIAG_MEDIUM -> 2f
        else           -> 1f
    }

    // Maximum acceptable age (ms) for each PID before it's considered stale for diagnostics.
    private val MAX_AGE_MS: Map<String, Long> = mapOf(
        "rpm"             to 2_000L,
        "speed"           to 2_000L,
        "throttle"        to 2_000L,
        "load"            to 2_000L,
        "stft"            to 2_000L,
        "stft2"           to 2_000L,
        "o2_b1s1"         to 2_000L,
        "afr_b1s1"        to 2_000L,
        "lambda"          to 2_000L,
        "fuel_sys_status" to 5_000L,
        "ltft"            to 5_000L,
        "ltft2"           to 5_000L,
        "maf"             to 5_000L,
        "manifold"        to 5_000L,
        "timing"          to 5_000L,
        "coolant"         to 15_000L,
        "o2_b1s2"         to 10_000L,
        "afr_b1s2"        to 10_000L,
        "fuel_pressure"   to 15_000L,
        "intake_temp"     to 30_000L,
        "ecu_voltage"     to 30_000L,
        "obd_voltage"     to 30_000L,
    )

    fun maxAgeMs(pidId: String): Long = MAX_AGE_MS[pidId] ?: when {
        pidId in DIAG_HIGH   -> 2_000L
        pidId in DIAG_MEDIUM -> 10_000L
        else                 -> 60_000L
    }
}
