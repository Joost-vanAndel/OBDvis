package com.obdvis.android.domain.store

import com.obdvis.android.domain.model.SensorSample
import kotlin.math.sqrt

/**
 * Thread-safe rolling store for timestamped sensor samples.
 *
 * Keeps up to [RETENTION_MS] of history per PID. Provides freshness-gated lookups
 * and rolling-window statistics consumed by the diagnostic layer.
 */
class SampleStore {

    private val retentionMs = RETENTION_MS
    private val samples = HashMap<String, ArrayDeque<SensorSample>>()
    private val lock = Any()

    fun add(sample: SensorSample) {
        synchronized(lock) {
            val deque = samples.getOrPut(sample.pidId) { ArrayDeque() }
            deque.addLast(sample)
            val cutoff = sample.timestampMs - retentionMs
            while (deque.isNotEmpty() && deque.first().timestampMs < cutoff) {
                deque.removeFirst()
            }
        }
    }

    fun latest(pidId: String): SensorSample? = synchronized(lock) {
        samples[pidId]?.lastOrNull()
    }

    fun latestTimestamp(pidId: String): Long? = synchronized(lock) {
        samples[pidId]?.lastOrNull()?.timestampMs
    }

    /** Returns the most recent sample for [pidId] only if it is newer than [maxAgeMs]. */
    fun latestFresh(pidId: String, maxAgeMs: Long): SensorSample? {
        val sample = latest(pidId) ?: return null
        return if (System.currentTimeMillis() - sample.timestampMs <= maxAgeMs) sample else null
    }

    fun latestFreshValue(pidId: String, maxAgeMs: Long): Float? =
        latestFresh(pidId, maxAgeMs)?.value

    /** Returns all samples for [pidId] within the last [windowMs] milliseconds. */
    fun window(pidId: String, windowMs: Long): List<SensorSample> {
        val cutoff = System.currentTimeMillis() - windowMs
        return synchronized(lock) {
            samples[pidId]?.filter { it.timestampMs >= cutoff }?.toList() ?: emptyList()
        }
    }

    /** Computes min/max/avg/stdDev over the last [windowMs] ms. Returns null if no data. */
    fun stats(pidId: String, windowMs: Long): WindowStats? {
        val values = window(pidId, windowMs).map { it.value }
        if (values.isEmpty()) return null
        val avg = values.average().toFloat()
        val min = values.min()
        val max = values.max()
        val stdDev = sqrt(values.map { (it - avg) * (it - avg) }.average()).toFloat()
        return WindowStats(avg = avg, min = min, max = max, stdDev = stdDev, count = values.size)
    }

    fun clear() = synchronized(lock) { samples.clear() }

    data class WindowStats(
        val avg: Float,
        val min: Float,
        val max: Float,
        val stdDev: Float,
        val count: Int,
    )

    companion object {
        /** How long to retain samples per PID. */
        const val RETENTION_MS = 2 * 60 * 1000L

        val WINDOW_10S  = 10_000L
        val WINDOW_30S  = 30_000L
        val WINDOW_2MIN = 120_000L
    }
}
