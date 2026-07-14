package com.obdvis.android.domain.health

/**
 * Derives broad, non-diagnostic information from the structure of a DTC code.
 *
 * Exact code descriptions intentionally live outside the app. A DTC identifies the
 * system that reported a fault, but it does not by itself prove which component failed.
 */
object DtcInfo {

    fun systemLabel(code: String): String {
        if (code.isEmpty()) return "Unknown system"
        val system = when (code.first().uppercaseChar()) {
            'P' -> "Powertrain"
            'C' -> "Chassis"
            'B' -> "Body"
            'U' -> "Network / communication"
            else -> "Unknown system"
        }
        val specificity = if (code.length >= 2) when (code[1]) {
            '0', '2' -> "generic"
            '1', '3' -> "manufacturer-specific"
            else -> null
        } else null
        return if (specificity != null) "$system — $specificity" else system
    }

    fun genericExplanation(code: String): String = when (code.firstOrNull()?.uppercaseChar()) {
        'P' -> "Powertrain-related fault"
        'C' -> "Chassis-related fault"
        'B' -> "Body-system fault"
        'U' -> "Network or communication fault"
        else -> "Unknown-system fault"
    }
}
