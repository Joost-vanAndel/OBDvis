package com.obdvis.android.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Background  = Color(0xFF0F1117)
val Surface     = Color(0xFF1A1D27)
val SurfaceVar  = Color(0xFF22253A)
val Border      = Color(0xFF2D3040)
val Primary     = Color(0xFF3B5BDB)
val PrimaryVar  = Color(0xFF4C6EF5)
val OnSurface   = Color(0xFFE0E0E0)
val SubText     = Color(0xFF888888)
val DimText     = Color(0xFF555566)

private val DarkColors = darkColorScheme(
    primary             = Primary,
    onPrimary           = Color.White,
    primaryContainer    = Color(0xFF2A2F50),
    onPrimaryContainer  = Color(0xFFC0C8FF),
    background          = Background,
    onBackground        = OnSurface,
    surface             = Surface,
    onSurface           = OnSurface,
    surfaceVariant      = SurfaceVar,
    onSurfaceVariant    = SubText,
    outline             = Border,
    error               = Color(0xFFCF6679),
    onError             = Color.White,
)

@Composable
fun OBDvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        content = content,
    )
}
