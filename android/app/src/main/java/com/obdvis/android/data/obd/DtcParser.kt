package com.obdvis.android.data.obd

/**
 * Parses ELM327 responses to OBD mode 03 (stored DTCs) and mode 07 (pending DTCs).
 *
 * With ATS0 active, a typical response looks like:
 *   "430102"      — one DTC (P0102)
 *   "4301020304"  — two DTCs (P0102, C0304)
 *   "43"          — no DTCs
 *   "NODATA"      — ECU does not support the mode or no codes stored
 *
 * DTC encoding per SAE J2012 / ISO 15031-6:
 *   Byte 1: bits 7–6 → type (00=P, 01=C, 10=B, 11=U)
 *           bits 5–4 → second digit
 *           bits 3–0 → third digit
 *   Byte 2: bits 7–4 → fourth digit
 *           bits 3–0 → fifth digit
 *
 * Null-padded pairs (0x00, 0x00) are skipped.
 */
object DtcParser {

    private val errorTokens = listOf("NODATA", "UNABLETOCONNECT", "CANERROR", "BUSERROR", "ERROR", "?")
    private val typeChars   = arrayOf('P', 'C', 'B', 'U')

    /**
     * Parses a raw ELM327 response string and returns a list of DTC strings.
     * Returns an empty list for error responses or if no codes are stored.
     *
     * [responseMode] should be "43" for stored DTCs (mode 03) or
     * "47" for pending DTCs (mode 07).
     */
    fun parse(response: String, responseMode: String = "43"): List<String> {
        val cleaned = response.replace("\\s".toRegex(), "").uppercase()

        if (cleaned.isEmpty()) return emptyList()
        if (errorTokens.any { cleaned.contains(it) }) return emptyList()

        // Find the response header — may appear more than once if the ECU
        // sends multiple frames; collect all DTCs from all frames.
        val dtcs = mutableListOf<String>()
        var searchFrom = 0
        while (true) {
            val headerIdx = cleaned.indexOf(responseMode.uppercase(), searchFrom)
            if (headerIdx == -1) break

            val nextHeaderIdx = cleaned.indexOf(responseMode.uppercase(), headerIdx + responseMode.length)
            val dataHex = if (nextHeaderIdx == -1) {
                cleaned.substring(headerIdx + responseMode.length)
            } else {
                cleaned.substring(headerIdx + responseMode.length, nextHeaderIdx)
            }
            var bytes = try {
                dataHex.chunked(2).mapNotNull { chunk ->
                    if (chunk.length == 2) chunk.toInt(16) else null
                }
            } catch (_: NumberFormatException) {
                break
            }

            // Some ELM327 clones include a count byte before the DTC pairs.
            // DTC data is always an even number of bytes, so an odd count means
            // the first byte is a count prefix — drop it.
            if (bytes.size % 2 != 0) bytes = bytes.drop(1)

            // Bytes come in pairs; stop at first malformed or all-zero padding pair.
            var i = 0
            while (i + 1 < bytes.size) {
                val b1 = bytes[i]
                val b2 = bytes[i + 1]
                i += 2

                if (b1 == 0 && b2 == 0) continue  // padding — skip

                val type    = (b1 shr 6) and 0x03
                val digit2  = (b1 shr 4) and 0x03
                val digit3  =  b1        and 0x0F
                val digit4  = (b2 shr 4) and 0x0F
                val digit5  =  b2        and 0x0F

                val dtc = "${typeChars[type]}${digit2}${digit3}${digit4.toString(16).uppercase()}${digit5.toString(16).uppercase()}"
                dtcs += dtc
            }

            searchFrom = headerIdx + responseMode.length + 1
        }

        return dtcs.distinct()
    }
}
