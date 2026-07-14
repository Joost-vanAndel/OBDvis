package com.obdvis.android.domain.health

sealed class DtcScreenState {
    /** No read has been attempted yet this session. */
    data object Idle : DtcScreenState()

    /** DTC read in progress. */
    data object Reading : DtcScreenState()

    /** DTCs loaded. [justCleared] is true immediately after a successful clear. */
    data class Loaded(
        val result: DtcReadResult,
        val justCleared: Boolean = false,
    ) : DtcScreenState()

    /** Clear command in progress. */
    data object Clearing : DtcScreenState()

    /** Last operation failed. */
    data class Error(val message: String) : DtcScreenState()
}
