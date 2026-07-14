package com.obdvis.android.domain.health

/**
 * Result of an OBD DTC query: stored codes (mode 03) and pending codes (mode 07).
 * Both lists are empty when no codes are present or communication fails.
 */
data class DtcReadResult(
    val stored:  List<String> = emptyList(),
    val pending: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = stored.isEmpty() && pending.isEmpty()
    val totalCount: Int  get() = stored.size + pending.size
}
