package com.obdvis.android.ui.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
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
    activeFindings: Map<String, FindingRecord>,
    dtcScreenState: DtcScreenState,
    gForce: Pair<Float, Float>,
    peakGForce: PeakGForce,
    onResetPeaks: () -> Unit,
    onNavigateToHealth: () -> Unit,
    onNavigateToDtcs: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speed    = latestValues["speed"]    ?: 0f
    val rpm      = latestValues["rpm"]      ?: 0f
    val coolant  = latestValues["coolant"]
    val battery  = latestValues["obd_voltage"] ?: latestValues["ecu_voltage"]
    val throttle = latestValues["throttle"]
    val speedEntries = chartData["speed"] ?: emptyList()
    val (lateralG, longitudinalG) = gForce

    val drivingState = healthState.summaryOrNull()?.operatingState

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Gauges ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ArcGauge(
                value      = speed,
                min        = 0f,
                max        = 250f,
                unit       = "km/h",
                label      = "SPEED",
                arcColor   = Color(0xFFF87C52),
                modifier   = Modifier.weight(1f).aspectRatio(1f),
            )
            ArcGauge(
                value        = rpm,
                min          = 0f,
                max          = 8000f,
                unit         = "rpm",
                label        = "ENGINE",
                arcColor     = Color(0xFF4C9EFF),
                redlineFrom  = 6000f,
                modifier     = Modifier.weight(1f).aspectRatio(1f),
            )
        }

        // ── G-Force indicator ─────────────────────────────────────────────────
        GForceIndicator(
            lateralG      = lateralG,
            longitudinalG = longitudinalG,
            peakGForce    = peakGForce,
            onResetPeaks  = onResetPeaks,
            modifier      = Modifier.width(190.dp).height(130.dp).align(Alignment.CenterHorizontally),
        )

        // ── Throttle bar ──────────────────────────────────────────────────────
        if (throttle != null) {
            ThrottleBar(throttle = throttle)
        }

        // ── Speed sparkline ───────────────────────────────────────────────────
        if (speedEntries.size >= 3) {
            SpeedSparkline(
                entries  = speedEntries.takeLast(60),
                modifier = Modifier.fillMaxWidth().height(36.dp),
            )
        }

        // ── Quick stats ───────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(
                label    = "COOLANT",
                value    = if (coolant != null) "%.0f °C".format(coolant) else "—",
                dotColor = coolantColor(coolant),
                modifier = Modifier.weight(1f),
            )
            StatCard(
                label    = "BATTERY",
                value    = if (battery != null) "%.1f V".format(battery) else "—",
                dotColor = batteryColor(battery),
                modifier = Modifier.weight(1f),
            )
        }

        // ── Health summary ────────────────────────────────────────────────────
        HealthSummaryCard(
            healthState = healthState,
            activeFindings = activeFindings,
            onClick = onNavigateToHealth,
        )

        // ── DTC summary ───────────────────────────────────────────────────────
        DtcSummaryCard(dtcScreenState = dtcScreenState, onClick = onNavigateToDtcs)

        // ── Driving state chip ────────────────────────────────────────────────
        if (drivingState != null) {
            DrivingStateChip(state = drivingState)
        }
    }
}

// ── Gauge ──────────────────────────────────────────────────────────────────────

@Composable
private fun ArcGauge(
    value: Float,
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
    val redColor    = Color(0xFFCF6679)
    val redZoneBg   = Color(0xFF3A1A1E)

    // Box lets us layer the arc canvas and Compose Text overlays
    Box(modifier = modifier, contentAlignment = Alignment.Center) {

        Canvas(modifier = Modifier.fillMaxSize()) {
            val w  = size.width
            val h  = size.height
            val s  = minOf(w, h)
            val cx = w / 2f
            val cy = h * 0.46f
            val r  = s * 0.38f
            val sw = r * 0.15f

            val fraction    = ((value - min) / (max - min)).coerceIn(0f, 1f)
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
                val inRedline = redlineFrom < max && value > redlineFrom
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
                text       = "%.0f".format(value),
                fontSize   = 28.sp,
                fontWeight = FontWeight.Bold,
                color      = OnSurface,
            )
            Text(
                text  = unit,
                fontSize = 11.sp,
                color = SubText,
            )
        }

        Text(
            text     = label,
            fontSize = 10.sp,
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
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("THROTTLE", style = MaterialTheme.typography.labelSmall, color = DimText)
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
                    .background(Color(0xFFF5D547), RoundedCornerShape(2.dp)),
            )
        }
    }
}

// ── Speed sparkline ────────────────────────────────────────────────────────────

@Composable
private fun SpeedSparkline(entries: List<Entry>, modifier: Modifier = Modifier) {
    val speedColor = Color(0xFFF87C52)
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
        color    = Surface,
        shape    = RoundedCornerShape(8.dp),
        modifier = modifier.border(0.5.dp, Border, RoundedCornerShape(8.dp)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
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
                Text(value, style = MaterialTheme.typography.titleSmall, color = OnSurface)
            }
        }
    }
}

private fun coolantColor(temp: Float?): Color = when {
    temp == null -> SubText
    temp < 60f   -> Color(0xFF4C9EFF)  // cold: blue
    temp < 95f   -> Color(0xFF4CD97B)  // normal: green
    temp < 110f  -> Color(0xFFF5D547)  // warm: yellow
    else         -> Color(0xFFCF6679)  // hot: red
}

private fun batteryColor(v: Float?): Color = when {
    v == null  -> SubText
    v < 11.5f  -> Color(0xFFCF6679)   // low battery: red
    v < 12.0f  -> Color(0xFFF5D547)   // below-normal: yellow
    v > 15.0f  -> Color(0xFFCF6679)   // overcharge: red
    v > 14.8f  -> Color(0xFFF5D547)   // high: yellow
    else       -> Color(0xFF4CD97B)   // normal: green
}

// ── Health summary card ────────────────────────────────────────────────────────

@Composable
private fun HealthSummaryCard(
    healthState: HealthState,
    activeFindings: Map<String, FindingRecord>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary        = healthState.summaryOrNull()
    val issues         = activeFindings.values.filter {
        it.status == FindingStatus.ACTIVE && it.finding.severity != FindingSeverity.INFO
    }
    val worstSeverity  = issues.maxByOrNull { it.finding.severity.ordinal }?.finding?.severity
    val borderColor    = when (worstSeverity) {
        FindingSeverity.HIGH   -> Color(0xFFCF6679)
        FindingSeverity.MEDIUM -> Color(0xFFFF9F43)
        FindingSeverity.LOW    -> Color(0xFFF5D547)
        else                   -> Border
    }

    Surface(
        color    = Surface,
        shape    = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(0.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("HEALTH", style = MaterialTheme.typography.labelSmall, color = DimText)
                Spacer(Modifier.height(3.dp))
                when {
                    summary == null        -> Text("Not analysed",
                        style = MaterialTheme.typography.bodyMedium, color = SubText)
                    issues.isEmpty()       -> Text("No issues found",
                        style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CD97B))
                    else -> Text(
                        "${issues.size} issue${if (issues.size == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface,
                    )
                }
            }
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = "Go to Health",
                tint               = SubText,
                modifier           = Modifier.size(16.dp),
            )
        }
    }
}

// ── DTC summary card ───────────────────────────────────────────────────────────

@Composable
private fun DtcSummaryCard(
    dtcScreenState: DtcScreenState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasDtcs     = dtcScreenState is DtcScreenState.Loaded && !dtcScreenState.result.isEmpty
    val borderColor = if (hasDtcs) Color(0xFFCF6679) else Border

    Surface(
        color    = Surface,
        shape    = RoundedCornerShape(8.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(0.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier              = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("DTCs", style = MaterialTheme.typography.labelSmall, color = DimText)
                Spacer(Modifier.height(3.dp))
                when (val s = dtcScreenState) {
                    is DtcScreenState.Idle     -> Text("Not read",
                        style = MaterialTheme.typography.bodyMedium, color = SubText)
                    is DtcScreenState.Reading  -> Text("Reading…",
                        style = MaterialTheme.typography.bodyMedium, color = SubText)
                    is DtcScreenState.Clearing -> Text("Clearing…",
                        style = MaterialTheme.typography.bodyMedium, color = SubText)
                    is DtcScreenState.Error    -> Text("Read error",
                        style = MaterialTheme.typography.bodyMedium, color = Color(0xFFCF6679))
                    is DtcScreenState.Loaded   -> {
                        val r = s.result
                        if (r.isEmpty) {
                            Text("No codes", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF4CD97B))
                        } else {
                            Text(
                                "${r.stored.size} stored · ${r.pending.size} pending",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFCF6679),
                            )
                        }
                    }
                }
            }
            Icon(
                Icons.Default.ArrowForward,
                contentDescription = "Go to DTCs",
                tint               = SubText,
                modifier           = Modifier.size(16.dp),
            )
        }
    }
}

// ── Driving state chip ─────────────────────────────────────────────────────────

@Composable
private fun DrivingStateChip(state: OperatingState, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (state) {
        OperatingState.COLD_START    -> Color(0xFF4C9EFF).copy(alpha = 0.15f) to Color(0xFF4C9EFF)
        OperatingState.WARM_IDLE     -> Border                                to SubText
        OperatingState.CRUISE        -> Color(0xFF4CD97B).copy(alpha = 0.15f) to Color(0xFF4CD97B)
        OperatingState.ACCELERATION  -> Color(0xFFF5D547).copy(alpha = 0.15f) to Color(0xFFF5D547)
        OperatingState.DECELERATION  -> Color(0xFFFF9F43).copy(alpha = 0.15f) to Color(0xFFFF9F43)
        OperatingState.EV_DRIVE      -> Color(0xFF00D2D3).copy(alpha = 0.15f) to Color(0xFF00D2D3)
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
        totalG < 0.3f -> Color(0xFF4CD97B)
        totalG < 0.7f -> Color(0xFFF5D547)
        else          -> Color(0xFFCF6679)
    }

    val trail = remember { ArrayDeque<Pair<Float, Float>>(TRAIL_LENGTH + 1) }
    LaunchedEffect(lateralG, longitudinalG) {
        if (trail.size >= TRAIL_LENGTH) trail.removeFirst()
        trail.addLast(lateralG to longitudinalG)
    }

    val peakColor = SubText.copy(alpha = 0.75f)
    val peakFontSize = 9.sp

    Box(
        modifier = modifier.combinedClickable(onClick = {}, onLongClick = onResetPeaks),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(106.dp)) {
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
            drawCircle(Color.White.copy(alpha = 0.85f), r * 0.028f, Offset(cx, cy))

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

