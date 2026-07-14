package com.obdvis.android.domain.model

import androidx.compose.ui.graphics.Color

data class PidDefinition(
    val id: String,
    val hex: String?,  // null for AT-command-sourced PIDs (e.g. ATRV); never OBD-polled
    val name: String,
    val unit: String,
    val min: Float,
    val max: Float,
    val color: Color,
    val formula: (List<Int>) -> Float,
    val displayInLive: Boolean = true,
)
