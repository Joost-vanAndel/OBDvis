package com.obdvis.android.domain.health

enum class FindingSeverity { INFO, LOW, MEDIUM, HIGH }

enum class FindingConfidence { LOW, MEDIUM, HIGH }

object DiagnosticFindingIds {
    const val DTC_PRESENT = "dtc_present"
}

/**
 * A single interpreted finding derived from vehicle sensor data.
 *
 * [id] is a stable identifier for the finding type (e.g. "engine_cold").
 * [evidence] maps human-readable labels to the formatted sensor values that triggered the finding.
 */
data class DiagnosticFinding(
    val id: String,
    val title: String,
    val description: String,
    val severity: FindingSeverity,
    val confidence: FindingConfidence,
    val evidence: Map<String, String> = emptyMap(),
)
