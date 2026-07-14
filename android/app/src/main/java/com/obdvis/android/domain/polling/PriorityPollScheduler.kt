package com.obdvis.android.domain.polling

import com.obdvis.android.domain.model.PidDefinition
import com.obdvis.android.domain.store.SampleStore

/**
 * Chooses the next PID to poll based on priority group and time since last read.
 *
 * Score = (time since last poll in ms) × priority weight.
 * HIGH-group PIDs have weight 4, MEDIUM weight 2, LOW weight 1, so a HIGH PID
 * that was read 500 ms ago outscores a LOW PID that was read 1500 ms ago.
 */
class PriorityPollScheduler(private val store: SampleStore) {

    fun nextPid(candidates: List<PidDefinition>, weightFn: (String) -> Float): PidDefinition? {
        if (candidates.isEmpty()) return null
        val now = System.currentTimeMillis()
        return candidates.maxByOrNull { pid ->
            val age = now - (store.latestTimestamp(pid.id) ?: 0L)
            age * weightFn(pid.id)
        }
    }
}
