package com.obdvis.android.data.obd

import kotlinx.coroutines.delay
import java.io.IOException

class ElmInitializer(private val sender: ElmCommandSender) {

    /**
     * Sends the standard AT init sequence, then queries which OBD PIDs this ECU supports.
     * Returns hex codes of supported PIDs (e.g. "0C", "0D"). Returns empty set if PID
     * discovery fails — callers should fall back to polling all known PIDs.
     * Throws [IOException] if the ELM327 itself doesn't respond.
     */
    suspend fun initialize(): Set<String> {
        // Reset — some cheap clones are slow, give them 3 seconds
        val resetResp = sender.send("ATZ\r", timeoutMs = 3000)
        if (resetResp.isBlank()) throw IOException("No response to ATZ — is this an ELM327?")
        delay(200) // Clones often need a moment after reset

        sender.send("ATE0\r")   // Echo off
        sender.send("ATL0\r")   // Linefeeds off
        sender.send("ATS0\r")   // Spaces off  (OBD data bytes arrive without spaces)
        sender.send("ATAL\r")   // Allow long messages (>7 bytes)
        sender.send("ATAT1\r")  // Adaptive timing mode 1
        sender.send("ATSP0\r")  // Auto-select OBD protocol

        return querySupportedPids()
    }

    /**
     * Queries OBD service 01 group PIDs (00, 20, 40, 60…) to discover which PIDs this
     * ECU actually supports. Each group response is a 32-bit bitmask: bit 31 = PID base+1,
     * bit 0 = PID base+0x20 (also the "next group available" flag).
     *
     * Returns a set of uppercase 2-char hex strings, e.g. {"0C", "0D", "05"}.
     * Returns an empty set on any parse failure so the caller can fall back gracefully.
     */
    private suspend fun querySupportedPids(): Set<String> {
        val supported = mutableSetOf<String>()
        var base = 0

        while (true) {
            val groupHex = base.toString(16).padStart(2, '0').uppercase()
            val response = try {
                sender.send("01$groupHex\r", timeoutMs = 1500)
            } catch (_: Exception) {
                break
            }

            // With ATS0, response looks like "4100BE1FA813" (no spaces)
            val clean = response.replace("\\s".toRegex(), "").uppercase()
            val prefix = "41$groupHex"
            val dataIdx = clean.indexOf(prefix)
            if (dataIdx == -1 || clean.length < dataIdx + 12) break

            val mask = clean.substring(dataIdx + 4, dataIdx + 12).toLongOrNull(16) ?: break

            for (bit in 0..31) {
                if (mask and (1L shl (31 - bit)) != 0L) {
                    val pid = base + bit + 1
                    supported.add(pid.toString(16).padStart(2, '0').uppercase())
                }
            }

            // Bit 0 of the mask = PID (base+0x20) supported = next group available
            if (mask and 1L == 0L) break
            base += 0x20
            if (base > 0xC0) break
        }

        return supported
    }
}
