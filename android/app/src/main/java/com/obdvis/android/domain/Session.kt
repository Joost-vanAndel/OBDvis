package com.obdvis.android.domain

import com.obdvis.android.domain.health.DtcReadResult
import com.obdvis.android.domain.model.SensorSample
import kotlinx.coroutines.flow.Flow

/**
 * Common interface for a live OBD data session.
 * Implemented by [OBDSession] (real hardware) and [DemoSession] (simulated).
 */
interface Session {
    /** Continuously emits [SensorSample] values until the collecting coroutine is cancelled. */
    fun readings(): Flow<SensorSample>

    /**
     * Reads stored (mode 03) and pending (mode 07) DTCs from the ECU.
     * Returns empty lists if no codes are present, the mode is unsupported, or communication fails.
     */
    suspend fun readDtcs(): DtcReadResult

    /**
     * Sends OBD mode 04 to clear stored DTCs and reset readiness monitors.
     * Returns true if the ECU acknowledged the command.
     *
     * NOTE: this erases all stored fault codes on the vehicle.
     */
    suspend fun clearDtcs(): Boolean

    /**
     * Polls all ECU-supported PIDs once and returns the results as a [pidId → value] map.
     * Used for periodic background health snapshots; runs on Dispatchers.IO.
     * Returns an empty map if the session is not yet initialized.
     */
    suspend fun pollSnapshot(): Map<String, Float>
}
