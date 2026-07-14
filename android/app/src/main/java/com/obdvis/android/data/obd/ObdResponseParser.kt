package com.obdvis.android.data.obd

import com.obdvis.android.domain.model.PidDefinition

class ObdResponseParser {

    private val errorTokens = listOf("NODATA", "UNABLETOCONNECT", "CANERROR", "BUSERROR", "ERROR", "?")

    /**
     * Parses an ELM327 response string for [pid] into a Float value.
     * Returns null if the response is an error or cannot be parsed.
     *
     * With ATS0 active, response is like "410C1AF8" (no spaces).
     * ATS0 only removes spaces from OBD data bytes, not error messages,
     * so "NO DATA" may appear with or without a space — we strip all spaces first.
     */
    fun parse(pid: PidDefinition, response: String): Float? {
        val cleaned = response.replace("\\s".toRegex(), "").uppercase()

        if (cleaned.isEmpty()) return null
        if (errorTokens.any { cleaned.contains(it) }) return null

        // Find response header "41XX" where XX = pid.hex
        val header = "41${pid.hex?.uppercase() ?: return null}"
        val headerIdx = cleaned.indexOf(header)
        if (headerIdx == -1) return null

        val dataHex = cleaned.substring(headerIdx + header.length)
        if (dataHex.isEmpty()) return null

        val bytes = try {
            dataHex.chunked(2).map { it.toInt(16) }
        } catch (_: NumberFormatException) {
            return null
        }

        if (bytes.isEmpty()) return null

        return try {
            pid.formula(bytes)
        } catch (_: Exception) {
            null
        }
    }
}
