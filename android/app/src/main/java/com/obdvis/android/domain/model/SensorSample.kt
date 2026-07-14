package com.obdvis.android.domain.model

data class SensorSample(
    val pidId: String,
    val elapsedSeconds: Float,
    val value: Float,
    /** Wall-clock time when this reading completed, in milliseconds since epoch. */
    val timestampMs: Long = System.currentTimeMillis(),
    /** Time spent waiting for the ECU response, in milliseconds. */
    val latencyMs: Long = 0,
)
