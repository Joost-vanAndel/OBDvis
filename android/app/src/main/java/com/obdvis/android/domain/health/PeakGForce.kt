package com.obdvis.android.domain.health

data class PeakGForce(
    val left: Float = 0f,
    val right: Float = 0f,
    val forward: Float = 0f,
    val backward: Float = 0f,
)
