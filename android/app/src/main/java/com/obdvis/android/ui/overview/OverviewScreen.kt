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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import com.github.mikephil.charting.data.Entry
import com.obdvis.android.domain.health.*
import com.obdvis.android.ui.theme.*
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
    val oil = latestValues["oil_temp"]
    val battery = latestValues["obd_voltage"] ?: latestValues["ecu_voltage"]
    val throttle = latestValues["throttle"]
    val speedEntries = chartData["speed"] ?: emptyList()
    val drivingState = healthState.summaryOrNull()?.operatingState
    val largeText = LocalDensity.current.fontScale > 1.3f

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 920.dp).fillMaxWidth()
                .verticalScroll(rememberScrollState()).padding(AppSpace.md),
            verticalArrangement = Arrangement.spacedBy(AppSpace.sm),
        ) {
            InstrumentPanel {
                Text("DRIVE TELEMETRY", style = AppType.instrumentLabel, color = Primary)
                InstrumentPair(largeText,
                    first = { itemModifier ->
                        Column(itemModifier, verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
                            Text("Overview", style = MaterialTheme.typography.titleMedium)
                            if (drivingState != null) DrivingStateChip(drivingState)
                            else Text("Waiting for health data", style = MaterialTheme.typography.bodySmall, color = SubText)
                        }
                    },
                    second = { itemModifier ->
                        Column(itemModifier, verticalArrangement = Arrangement.spacedBy(AppSpace.xxs)) {
                            Text("BATTERY", style = AppType.instrumentLabel, color = SubText)
                            Text(battery?.let { "%.1f V".format(it) } ?: "—",
                                style = AppType.reading, color = batteryColor(battery))
                            Text(if (battery == null) "Awaiting data" else "System voltage",
                                style = MaterialTheme.typography.labelSmall, color = SubText)
                        }
                    },
                )
            }

            InstrumentPair(largeText,
                first = { InstrumentCard("ENGINE SPEED", rpm, "rpm", 0f, 8000f,
                    EngineSignal, "0–8,000 rpm scale", it) },
                second = { InstrumentCard("ROAD SPEED", speed, "km/h", 0f, 240f,
                    Primary, "OBD vehicle speed", it) },
            )
            InstrumentPair(largeText,
                first = { InstrumentCard("COOLANT", coolant, "°C", -40f, 150f,
                    coolantColor(coolant), "Engine coolant", it) },
                second = { InstrumentCard("ENGINE OIL", oil, "°C", -40f, 150f,
                    PrimaryVar, "Oil temperature", it) },
            )

            InstrumentPanel {
                SectionHeading("G-force dynamics", "Chassis accelerometer · 1 g outer ring")
                GForceIndicator(
                    lateralG = gForce.first, longitudinalG = gForce.second,
                    onResetPeaks = onResetPeaks,
                    modifier = Modifier.widthIn(max = 320.dp).fillMaxWidth().height(220.dp)
                        .align(Alignment.CenterHorizontally),
                )
                MotionReading("LATERAL", gForce.first)
                MotionReading("LONGITUDINAL", gForce.second)
                Text("Directional peaks", style = MaterialTheme.typography.labelSmall, color = SubText)
                Text("Left %.2f g · Right %.2f g\nForward %.2f g · Braking %.2f g".format(
                    peakGForce.left, peakGForce.right, peakGForce.forward, peakGForce.backward),
                    style = MaterialTheme.typography.bodySmall, color = SubText)
                TextButton(onClick = onResetPeaks, modifier = Modifier.align(Alignment.End)) {
                    Text("Reset G-force peaks")
                }
            }

            if (throttle != null || speedEntries.size >= 3) {
                InstrumentPanel {
                    SectionHeading("Motion & response")
                    if (throttle != null) ThrottleBar(throttle)
                    if (speedEntries.size >= 3) {
                        Text("Recent speed", style = MaterialTheme.typography.labelSmall, color = SubText)
                        SpeedSparkline(speedEntries.takeLast(60), Modifier.fillMaxWidth().height(48.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InstrumentPanel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Surface, shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(AppSpace.sm), verticalArrangement = Arrangement.spacedBy(AppSpace.sm),
            content = content)
    }
}

@Composable
private fun InstrumentPair(
    stacked: Boolean,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit,
) {
    if (stacked) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpace.sm)) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
            first(Modifier.weight(1f))
            second(Modifier.weight(1f))
        }
    }
}

@Composable
private fun InstrumentCard(
    label: String, value: Float?, unit: String, min: Float, max: Float,
    accent: Color, detail: String, modifier: Modifier = Modifier,
) {
    InstrumentPanel(modifier.semantics(mergeDescendants = true) {}) {
        Text(label, style = AppType.instrumentLabel, color = SubText)
        Text(value?.let { "%.0f".format(it) } ?: "—", style = AppType.instrumentReading, color = OnSurface)
        Text(unit, style = MaterialTheme.typography.labelSmall, color = accent)
        // Scale is visual context, not a vehicle-specific safe range or redline.
        Canvas(Modifier.fillMaxWidth().height(5.dp)) {
            drawLine(Border, Offset(0f, size.height / 2), Offset(size.width, size.height / 2),
                size.height, StrokeCap.Round)
            if (value != null) {
                val fraction = ((value - min) / (max - min)).coerceIn(0f, 1f)
                if (fraction > 0f) drawLine(accent, Offset(0f, size.height / 2),
                    Offset(size.width * fraction, size.height / 2), size.height, StrokeCap.Round)
            }
        }
        Text(if (value == null) "Awaiting data" else detail,
            style = MaterialTheme.typography.labelSmall, color = SubText)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MotionReading(label: String, value: Float) {
    // FlowRow also lets the measurement wrap at large accessibility font sizes.
    FlowRow(Modifier.fillMaxWidth().background(Background, MaterialTheme.shapes.small).padding(AppSpace.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(AppSpace.xxs)) {
        Text(label, style = AppType.instrumentLabel, color = SubText, modifier = Modifier.padding(end = AppSpace.xs))
        Text("%+.2f g".format(value), style = AppType.numeric, color = Primary)
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


    Box(
        modifier = modifier.semantics {
            contentDescription = "G-force: lateral %.2f g, longitudinal %.2f g".format(lateralG, longitudinalG)
        }.combinedClickable(onClick = {}, onLongClickLabel = "Reset G-force peaks", onLongClick = onResetPeaks),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(176.dp)) {
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

            drawLine(Primary, Offset(cx, cy), Offset(bx, by), 1.5.dp.toPx())
            drawCircle(ballColor.copy(alpha = 0.25f), r * 0.16f, Offset(bx, by))
            drawCircle(ballColor, r * 0.10f, Offset(bx, by))
        }

    }
}
