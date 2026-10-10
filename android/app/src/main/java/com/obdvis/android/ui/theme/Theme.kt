/* Hallmark · modern-minimal · instrument workbench · design-system: design.md
 * Pre-emit critique: P5 H4 E4 S5 R5 V4 — native snapshot review complete; hardware checks pending.
 */
package com.obdvis.android.ui.theme

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Runtime tokens. Portable OKLCH equivalents are documented in tokens.css.
val Background = Color(0xFF10151D)
val Surface = Color(0xFF19222E)
val SurfaceVar = Color(0xFF232F3E)
val Border = Color(0xFF3B4A5D)
val ControlOutline = Color(0xFF73849A)
val Primary = Color(0xFF91BAFF)
val PrimaryVar = Color(0xFFB8D2FF)
val PrimaryContainer = Color(0xFF263E60)
val OnPrimary = Color(0xFF10233F)
val OnSurface = Color(0xFFEDF2F8)
val SubText = Color(0xFFB6C2D2)
val DimText = Color(0xFF9EADC0)
val Success = Color(0xFF7CDCB1)
val Warning = Color(0xFFFFC17A)
val Caution = Color(0xFFE8D582)
val Danger = Color(0xFFFF9BA5)
val DangerContainer = Color(0xFF452831)
val SpeedSignal = Color(0xFFFFAD8C)
val EngineSignal = Primary
val ElectricSignal = Color(0xFF87D8DC)

object AppSpace {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 24.dp
    val xl = 32.dp
    val touch = 48.dp
}

object AppType {
    val body = FontFamily.SansSerif
    val numeric = TextStyle(fontFamily = body, fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum", letterSpacing = (-0.5).sp)
    val instrumentLabel = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.5.sp)
    val instrumentReading = numeric.copy(fontFamily = FontFamily.Monospace, fontSize = 32.sp, lineHeight = 40.sp)
    val speed = numeric.copy(fontSize = 64.sp, lineHeight = 72.sp)
    val reading = numeric.copy(fontSize = 28.sp, lineHeight = 36.sp)
}

private val AppTypography = Typography(
    displayLarge = AppType.speed,
    displayMedium = AppType.numeric.copy(fontSize = 48.sp, lineHeight = 56.sp),
    headlineLarge = AppType.numeric.copy(fontSize = 32.sp, lineHeight = 40.sp),
    headlineMedium = AppType.reading,
    titleLarge = TextStyle(fontFamily = AppType.body, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleMedium = TextStyle(fontFamily = AppType.body, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleSmall = TextStyle(fontFamily = AppType.body, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    bodyLarge = TextStyle(fontFamily = AppType.body, fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = AppType.body, fontSize = 14.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = AppType.body, fontSize = 13.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontFamily = AppType.body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = AppType.body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontFamily = AppType.body, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
)

private val DarkColors = darkColorScheme(
    primary = Primary, onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer, onPrimaryContainer = PrimaryVar,
    secondary = Primary, onSecondary = OnPrimary,
    secondaryContainer = PrimaryContainer, onSecondaryContainer = PrimaryVar,
    tertiary = Success, onTertiary = Background,
    background = Background, onBackground = OnSurface,
    surface = Surface, onSurface = OnSurface,
    surfaceVariant = SurfaceVar, onSurfaceVariant = SubText,
    surfaceContainer = Surface, surfaceContainerHigh = SurfaceVar,
    outline = ControlOutline, outlineVariant = Border,
    error = Danger, onError = Background,
    errorContainer = DangerContainer, onErrorContainer = Danger,
)

@Composable
fun OBDvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(4.dp), small = RoundedCornerShape(8.dp),
            medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(12.dp),
            extraLarge = RoundedCornerShape(16.dp),
        ),
    ) {
        // Establish background and content colors even on screens opened outside Dashboard.
        androidx.compose.material3.Surface(modifier = Modifier.fillMaxSize(), color = Background) {
            content()
        }
    }
}
