package com.obdvis.android.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import com.obdvis.android.ui.components.SectionHeading
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.github.mikephil.charting.data.Entry
import com.obdvis.android.domain.health.*
import com.obdvis.android.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun OverviewScreen(
    latestValues: Map<String, Float>,
    chartData: Map<String, List<Entry>>,
    healthState: HealthState,
    gForce: Pair<Float, Float>,
    peakGForce: PeakGForce,
    onResetPeaks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speed = latestValues["speed"]
    val rpm = latestValues["rpm"]
    val coolant = latestValues["coolant"]
    val battery = latestValues["obd_voltage"] ?: latestValues["ecu_voltage"]
    val throttle = latestValues["throttle"]
    val speedEntries = chartData["speed"] ?: emptyList()
    val drivingState = healthState.summaryOrNull()?.operatingState
    val largeText = LocalDensity.current.fontScale > 1.3f

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(AppSpace.md),
            verticalArrangement = Arrangement.spacedBy(AppSpace.lg),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
                Text("Overview", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (drivingState != null) DrivingStateChip(drivingState)
            }

            if (largeText) {
                SpeedReading(speed)
                ArcGauge(rpm, 0f, 8000f, "rpm", "Engine speed", EngineSignal,
                    redlineFrom = 6000f, modifier = Modifier.fillMaxWidth().height(240.dp))
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(AppSpace.md)) {
                    SpeedReading(speed, Modifier.weight(1.2f))
                    ArcGauge(rpm, 0f, 8000f, "rpm", "Engine speed", EngineSignal,
                        redlineFrom = 6000f,
                        modifier = Modifier.weight(1f).height(176.dp))
                }
            }

            HorizontalDivider(color = Border)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpace.md)) {
                StatCard("Coolant", coolant?.let { "%.0f °C".format(it) } ?: "—", coolantColor(coolant), Modifier.weight(1f))
                StatCard("Battery", battery?.let { "%.1f V".format(it) } ?: "—", batteryColor(battery), Modifier.weight(1f))
            }

            Column(verticalArrangement = Arrangement.spacedBy(AppSpace.md)) {
                SectionHeading("Motion & response")
                if (throttle != null) ThrottleBar(throttle)
                if (speedEntries.size >= 3) {
                    Text("Recent speed", style = MaterialTheme.typography.labelSmall, color = SubText)
                    SpeedSparkline(speedEntries.takeLast(60), Modifier.fillMaxWidth().height(48.dp))
                }
                GForceIndicator(
                    lateralG = gForce.first, longitudinalG = gForce.second,
                    peakGForce = peakGForce, onResetPeaks = onResetPeaks,
                    modifier = Modifier.width(260.dp).height(168.dp).align(Alignment.CenterHorizontally),
                )
                TextButton(onClick = onResetPeaks, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Reset G-force peaks")
                }
            }
        }
    }
}

@Composable
private fun SpeedReading(speed: Float?, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AppSpace.xxs)) {
        Text("Speed", style = MaterialTheme.typography.labelLarge, color = SubText)
        Text(speed?.let { "%.0f".format(it) } ?: "—", style = AppType.speed, color = OnSurface)
        Text("km/h", style = MaterialTheme.typography.bodyMedium, color = SpeedSignal)
    }
}

// ── Gauge ──────────────────────────────────────────────────────────────────────

@Composable
private fun ArcGauge(
    value: Float?,
    min: Float,
    max: Float,
    unit: String,
    label: String,
    arcColor: Color,
    redlineFrom: Float = Float.MAX_VALUE,
    modifier: Modifier = Modifier,
) {
    // Arc geometry: 240° sweep centred at 12 o'clock (start=150°, end=30°)
    val startAngle  = 150f
    val totalSweep  = 240f
    val redColor    = Danger
    val redZoneBg   = DangerContainer

    // Box lets us layer the arc canvas and Compose Text overlays
    Box(modifier = modifier.semantics(mergeDescendants = true) {}, contentAlignment = Alignment.Center) {

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w  = size.width
            val h  = size.height
            val s  = minOf(w, h)
            val cx = w / 2f
            val cy = h * 0.46f
            val r  = s * 0.38f
            val sw = r * 0.15f

            val fraction    = (((value ?: min) - min) / (max - min)).coerceIn(0f, 1f)
            val redFraction = ((redlineFrom - min) / (max - min)).coerceIn(0f, 1f)

            // ── Background track ────────────────────────────────────────────
            drawArc(
                color      = Border,
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter  = false,
                style      = Stroke(width = sw, cap = StrokeCap.Round),
                topLeft    = Offset(cx - r, cy - r),
                size       = Size(r * 2, r * 2),
            )

            // ── Red zone background ─────────────────────────────────────────
            if (redlineFrom < max) {
                drawArc(
                    color      = redZoneBg,
                    startAngle = startAngle + totalSweep * redFraction,
                    sweepAngle = totalSweep * (1f - redFraction),
                    useCenter  = false,
                    style      = Stroke(width = sw, cap = StrokeCap.Butt),
                    topLeft    = Offset(cx - r, cy - r),
                    size       = Size(r * 2, r * 2),
                )
            }

            // ── Value arc ───────────────────────────────────────────────────
            if (fraction > 0.005f) {
                val inRedline = redlineFrom < max && (value ?: min) > redlineFrom
                if (inRedline) {
                    drawArc(
                        color      = arcColor,
                        startAngle = startAngle,
                        sweepAngle = totalSweep * redFraction,
                        useCenter  = false,
                        style      = Stroke(width = sw, cap = StrokeCap.Round),
                        topLeft    = Offset(cx - r, cy - r),
                        size       = Size(r * 2, r * 2),
                    )
                    drawArc(
                        color      = redColor,
                        startAngle = startAngle + totalSweep * redFraction,
                        sweepAngle = totalSweep * (fraction - redFraction),
                        useCenter  = false,
                        style      = Stroke(width = sw, cap = StrokeCap.Round),
                        topLeft    = Offset(cx - r, cy - r),
                        size       = Size(r * 2, r * 2),
                    )
                } else {
                    drawArc(
                        color      = arcColor,
                        startAngle = startAngle,
                        sweepAngle = totalSweep * fraction,
                        useCenter  = false,
                        style      = Stroke(width = sw, cap = StrokeCap.Round),
                        topLeft    = Offset(cx - r, cy - r),
                        size       = Size(r * 2, r * 2),
                    )
                }

                // Needle tip dot
                val needleRad = Math.toRadians((startAngle + totalSweep * fraction).toDouble()).toFloat()
                drawCircle(
                    color  = if (inRedline) redColor else arcColor,
                    radius = sw * 0.55f,
                    center = Offset(cx + r * cos(needleRad), cy + r * sin(needleRad)),
                )
            }
        }

        // ── Text overlay ─────────────────────────────────────────────────────
        // Arc centre sits at ~46% from top; Box.Center is at 50%, so offset up 4%
        // of the box height. A small fixed offset works well across sizes.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.offset(y = (-4).dp),
        ) {
            Text(
                text       = value?.let { "%.0f".format(it) } ?: "—",
                style = AppType.reading,
                color      = OnSurface,
            )
            Text(
                text  = unit,
                fontSize = 12.sp,
                color = SubText,
            )
        }

        Text(
            text     = label,
            fontSize = 12.sp,
            color    = DimText,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 4.dp),
        )
    }
}

// ── Throttle bar ───────────────────────────────────────────────────────────────

@Composable
private fun ThrottleBar(throttle: Float, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 0.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Throttle", style = MaterialTheme.typography.labelSmall, color = DimText)
            Text("%.0f%%".format(throttle), style = MaterialTheme.typography.labelSmall, color = SubText)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Border, RoundedCornerShape(2.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((throttle / 100f).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(Caution, RoundedCornerShape(2.dp)),
            )
        }
    }
}

// ── Speed sparkline ────────────────────────────────────────────────────────────

@Composable
private fun SpeedSparkline(entries: List<Entry>, modifier: Modifier = Modifier) {
    val speedColor = SpeedSignal
    Canvas(modifier = modifier) {
        val w      = size.width
        val h      = size.height
        val maxY   = entries.maxOf { it.y }.coerceAtLeast(10f)
        val xStart = entries.first().x
        val xRange = (entries.last().x - xStart).coerceAtLeast(0.001f)

        fun xPx(e: Entry) = (e.x - xStart) / xRange * w
        fun yPx(e: Entry) = h - (e.y / maxY) * h * 0.85f - h * 0.075f

        for (i in 1 until entries.size) {
            drawLine(
                color       = speedColor.copy(alpha = 0.55f),
                start       = Offset(xPx(entries[i - 1]), yPx(entries[i - 1])),
                end         = Offset(xPx(entries[i]),     yPx(entries[i])),
                strokeWidth = 1.5.dp.toPx(),
                cap         = StrokeCap.Round,
            )
        }
        // Current-value dot
        drawCircle(
            color  = speedColor,
            radius = 3.dp.toPx(),
            center = Offset(w, yPx(entries.last())),
        )
    }
}

// ── Stat cards ─────────────────────────────────────────────────────────────────

@Composable
private fun StatCard(
    label: String,
    value: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Surface(
        color    = Background,
        shape    = MaterialTheme.shapes.medium,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = AppSpace.xxs),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = DimText)
            Row(
                verticalAlignment    = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(dotColor, RoundedCornerShape(4.dp))
                )
                Text(value, style = AppType.reading, color = OnSurface)
            }
        }
    }
}

private fun coolantColor(temp: Float?): Color = when {
    temp == null -> SubText
    temp < 60f   -> EngineSignal  // cold: blue
    temp < 95f   -> Success  // normal: green
    temp < 110f  -> Caution  // warm: yellow
    else         -> Danger  // hot: red
}

private fun batteryColor(v: Float?): Color = when {
    v == null  -> SubText
    v < 11.5f  -> Danger   // low battery: red
    v < 12.0f  -> Caution   // below-normal: yellow
    v > 15.0f  -> Danger   // overcharge: red
    v > 14.8f  -> Caution   // high: yellow
    else       -> Success   // normal: green
}

// ── Driving state chip ─────────────────────────────────────────────────────────

@Composable
private fun DrivingStateChip(state: OperatingState, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (state) {
        OperatingState.COLD_START    -> EngineSignal.copy(alpha = 0.15f) to EngineSignal
        OperatingState.WARM_IDLE     -> Border                                to SubText
        OperatingState.CRUISE        -> Success.copy(alpha = 0.15f) to Success
        OperatingState.ACCELERATION  -> Caution.copy(alpha = 0.15f) to Caution
        OperatingState.DECELERATION  -> Warning.copy(alpha = 0.15f) to Warning
        OperatingState.EV_DRIVE      -> ElectricSignal.copy(alpha = 0.15f) to ElectricSignal
        OperatingState.UNKNOWN       -> Border                                to SubText
    }
    Box(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(state.label, style = MaterialTheme.typography.labelMedium, color = textColor)
    }
}

// ── G-Force indicator ──────────────────────────────────────────────────────────

private const val TRAIL_LENGTH = 30

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GForceIndicator(
    lateralG: Float,
    longitudinalG: Float,
    peakGForce: PeakGForce,
    onResetPeaks: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalG = sqrt(lateralG * lateralG + longitudinalG * longitudinalG)
    val ballColor = when {
        totalG < 0.3f -> Success
        totalG < 0.7f -> Caution
        else          -> Danger
    }

    val trail = remember { ArrayDeque<Pair<Float, Float>>(TRAIL_LENGTH + 1) }
    LaunchedEffect(lateralG, longitudinalG) {
        if (trail.size >= TRAIL_LENGTH) trail.removeFirst()
        trail.addLast(lateralG to longitudinalG)
    }

    val peakColor = SubText
    val peakFontSize = 12.sp

    Box(
        modifier = modifier.semantics {
            contentDescription = "G-force: lateral %.2f g, longitudinal %.2f g".format(lateralG, longitudinalG)
        }.combinedClickable(onClick = {}, onLongClickLabel = "Reset G-force peaks", onLongClick = onResetPeaks),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(128.dp)) {
            val s  = minOf(size.width, size.height)
            val cx = size.width  / 2f
            val cy = size.height / 2f
            val r  = s * 0.42f

            // 0.5 g ring
            drawCircle(
                color  = Border.copy(alpha = 0.45f),
                radius = r * 0.5f,
                center = Offset(cx, cy),
                style  = Stroke(width = 1.dp.toPx()),
            )
            // 1 g ring
            drawCircle(
                color  = Border,
                radius = r,
                center = Offset(cx, cy),
                style  = Stroke(width = 1.5.dp.toPx()),
            )
            // crosshairs
            val hairColor = Border.copy(alpha = 0.4f)
            drawLine(hairColor, Offset(cx - r, cy), Offset(cx + r, cy), 1.dp.toPx())
            drawLine(hairColor, Offset(cx, cy - r), Offset(cx, cy + r), 1.dp.toPx())

            // centre point
            drawCircle(Border.copy(alpha = 0.6f), r * 0.055f, Offset(cx, cy))
            drawCircle(OnSurface, r * 0.028f, Offset(cx, cy))

            // ghost trail — oldest first, fading toward newest
            trail.forEachIndexed { i, (tLat, tLon) ->
                val t = (i + 1f) / trail.size
                val tmag = sqrt(tLat * tLat + tLon * tLon).coerceAtLeast(0.001f)
                val tclamp = tmag.coerceAtMost(1f)
                val tx = cx + (tLat / tmag) * tclamp * r
                val ty = cy - (tLon / tmag) * tclamp * r
                drawCircle(
                    color  = ballColor.copy(alpha = t * 0.35f),
                    radius = r * 0.10f * t,
                    center = Offset(tx, ty),
                )
            }

            // ball — clamped to 1 g radius
            val mag = sqrt(lateralG * lateralG + longitudinalG * longitudinalG).coerceAtLeast(0.001f)
            val clamp = mag.coerceAtMost(1f)
            val bx = cx + (lateralG / mag) * clamp * r
            val by = cy - (longitudinalG / mag) * clamp * r  // +y = forward (up on screen)

            drawCircle(ballColor.copy(alpha = 0.25f), r * 0.16f, Offset(bx, by))
            drawCircle(ballColor, r * 0.10f, Offset(bx, by))
        }

        // Peak labels at cardinal positions of the outer box
        Text(
            text     = "↑ %.2fg".format(peakGForce.forward),
            fontSize = peakFontSize,
            color    = peakColor,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        Text(
            text     = "↓ %.2fg".format(peakGForce.backward),
            fontSize = peakFontSize,
            color    = peakColor,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        Text(
            text     = "← %.2fg".format(peakGForce.left),
            fontSize = peakFontSize,
            color    = peakColor,
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text     = "→ %.2fg".format(peakGForce.right),
            fontSize = peakFontSize,
            color    = peakColor,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

