package com.obdvis.android.ui

import java.util.Locale

internal fun formatFileSize(bytes: Long): String = when {
    bytes < 1_024L -> "$bytes B"
    bytes < 1_024L * 1_024L -> String.format(Locale.US, "%.1f KB", bytes / 1_024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1_024.0 * 1_024.0))
}
