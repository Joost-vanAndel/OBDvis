package com.obdvis.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdvis.android.domain.health.AggregatedFinding
import com.obdvis.android.domain.health.DtcInfo
import com.obdvis.android.domain.health.FindingSeverity
import com.obdvis.android.domain.health.OperatingState
import com.obdvis.android.domain.health.PeakGForce
import com.obdvis.android.domain.health.PostDriveData
import com.obdvis.android.ui.theme.*
import java.util.concurrent.TimeUnit
import kotlin.math.sqrt

@Composable
fun PostDriveScreen(
    data: PostDriveData,
    csvContent: String,
    onDismiss: () -> Unit,
    onSearchDtc: (String) -> Unit,
    title: String = "Drive Summary",
    onDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDismiss)
    val exportCsv = rememberCsvExportAction { csvContent }
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                color = OnSurface,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { exportCsv("obd_drive_${data.sessionStartMs}.csv") },
                enabled = csvContent.lineSequence().drop(1).any { it.isNotBlank() },
                contentPadding = PaddingValues(horizontal = 8.dp),
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text("CSV")
            }
            if (onDelete != null) {
                IconButton(onClick = { showDeleteConfirmation = true }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete drive", tint = SubText)
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = SubText)
            }
        }

        HorizontalDivider(color = Border, thickness = 1.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SessionOverviewCard(data)
            OperatingStatesCard(data)
            PeakValuesCard(data)
            GForceCard(data)
            DtcSummaryCard(data, onSearchDtc)
            NotableFindingsCard(data)
        }
    }

    if (showDeleteConfirmation && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete saved drive?") },
            text = { Text("This drive summary and its CSV data will be removed from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    }
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            },
        )
    }
}

// ── Cards ──────────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(10.dp))
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = SubText, fontSize = 11.sp, fontWeight = FontWeight.Medium,
            letterSpacing = 0.8.sp)
        content()
    }
}

@Composable
private fun SessionOverviewCard(data: PostDriveData) {
    val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(data.durationMs)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val durationText = when {
        hours > 0 -> "${hours}h ${minutes}m"
        else      -> "${minutes}m"
    }

    SummaryCard("SESSION OVERVIEW") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            OverviewStat("Duration", durationText, modifier = Modifier.weight(1f))
            OverviewStat("Health checks", "${data.totalHealthChecks}", modifier = Modifier.weight(1f))
            if (!data.dtcResult.isEmpty) {
                OverviewStat("Active DTCs", "${data.dtcResult.totalCount}", modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OverviewStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            value,
            color = OnSurface,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            label,
            color = SubText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OperatingStatesCard(data: PostDriveData) {
    val breakdown = data.operatingStateBreakdown
    val total = data.totalHealthChecks.coerceAtLeast(1)
    val order = listOf(
        OperatingState.COLD_START,
        OperatingState.WARM_IDLE,
        OperatingState.CRUISE,
        OperatingState.ACCELERATION,
        OperatingState.DECELERATION,
        OperatingState.EV_DRIVE,
        OperatingState.UNKNOWN,
    )

    SummaryCard("OPERATING STATES") {
        order.forEach { state ->
            val count = breakdown[state] ?: 0
            if (count == 0) return@forEach
            val pct = count * 100 / total
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    state.label,
                    color = OnSurface,
                    fontSize = 13.sp,
                    modifier = Modifier.width(110.dp),
                )
                LinearProgressIndicator(
                    progress = { count.toFloat() / total },
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = Primary,
                    trackColor = Border,
                )
                Text("$pct%", color = SubText, fontSize = 12.sp, modifier = Modifier.width(34.dp))
            }
        }
    }
}

@Composable
private fun PeakValuesCard(data: PostDriveData) {
    val peaks = listOfNotNull(
        data.peakRpm?.let { "Peak RPM" to "%.0f".format(it) },
        data.peakSpeedKph?.let { "Peak speed" to "%.0f km/h".format(it) },
        data.peakCoolantTempC?.let { "Peak coolant" to "%.0f °C".format(it) },
    )
    if (peaks.isEmpty()) return

    SummaryCard("PEAK VALUES") {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            peaks.forEach { (label, value) ->
                OverviewStat(label, value, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GForceCard(data: PostDriveData) {
    val pg = data.peakGForce ?: return
    if (pg.left + pg.right + pg.forward + pg.backward < 0.05f) return

    SummaryCard("PEAK G-FORCE") {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier.width(220.dp).height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                val peakDotColor = Color(0xFFCF6679)

                Canvas(modifier = Modifier.size(130.dp)) {
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

                    // Peak dots — each placed on its axis, clamped to the 1 g ring
                    fun drawPeakDot(lat: Float, lon: Float) {
                        val g = sqrt(lat * lat + lon * lon).coerceAtLeast(0.001f)
                        val clamped = g.coerceAtMost(1f)
                        val px = cx + (lat / g) * clamped * r
                        val py = cy - (lon / g) * clamped * r
                        drawCircle(peakDotColor.copy(alpha = 0.25f), r * 0.16f, Offset(px, py))
                        drawCircle(peakDotColor, r * 0.10f, Offset(px, py))
                    }

                    if (pg.forward  > 0.05f) drawPeakDot(0f,         pg.forward)
                    if (pg.backward > 0.05f) drawPeakDot(0f,        -pg.backward)
                    if (pg.left     > 0.05f) drawPeakDot(-pg.left,   0f)
                    if (pg.right    > 0.05f) drawPeakDot(pg.right,   0f)
                }

                val labelColor = SubText
                val labelSize  = 9.sp
                Text("↑ %.2fg".format(pg.forward),  color = labelColor, fontSize = labelSize,
                    modifier = Modifier.align(Alignment.TopCenter))
                Text("↓ %.2fg".format(pg.backward), color = labelColor, fontSize = labelSize,
                    modifier = Modifier.align(Alignment.BottomCenter))
                Text("← %.2fg".format(pg.left),     color = labelColor, fontSize = labelSize,
                    modifier = Modifier.align(Alignment.CenterStart))
                Text("→ %.2fg".format(pg.right),    color = labelColor, fontSize = labelSize,
                    modifier = Modifier.align(Alignment.CenterEnd))
            }
        }
    }
}

@Composable
private fun DtcSummaryCard(data: PostDriveData, onSearchDtc: (String) -> Unit) {
    if (data.dtcResult.isEmpty) return

    SummaryCard("FAULT CODES") {
        if (data.dtcResult.stored.isNotEmpty()) {
            DtcCodeSection(
                label = "Stored",
                codes = data.dtcResult.stored,
                isStored = true,
                onSearchDtc = onSearchDtc,
            )
        }
        if (data.dtcResult.pending.isNotEmpty()) {
            DtcCodeSection(
                label = "Pending",
                codes = data.dtcResult.pending,
                isStored = false,
                onSearchDtc = onSearchDtc,
            )
        }
    }
}

@Composable
private fun DtcCodeSection(
    label: String,
    codes: List<String>,
    isStored: Boolean,
    onSearchDtc: (String) -> Unit,
) {
    val accentColor = if (isStored) Color(0xFFCF6679) else Color(0xFFF5D547)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "$label (${codes.size})",
            color = SubText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
        codes.forEach { code ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(0.5.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                    .background(SurfaceVar, RoundedCornerShape(8.dp))
                    .clickable(onClickLabel = "Search for $code") { onSearchDtc(code) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(accentColor, RoundedCornerShape(4.dp))
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(code, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = OnSurface)
                    Text(
                        DtcInfo.genericExplanation(code),
                        fontSize = 12.sp,
                        color = OnSurface,
                        lineHeight = 16.sp,
                    )
                    Text(DtcInfo.systemLabel(code), fontSize = 11.sp, color = SubText)
                }
                Icon(
                    Icons.Default.Search,
                    contentDescription = "Search for $code",
                    tint = SubText,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun NotableFindingsCard(data: PostDriveData) {
    val findings = data.aggregatedFindings.filter { it.finding.severity != FindingSeverity.INFO }
    if (findings.isEmpty()) {
        SummaryCard("FINDINGS") {
            Text("No notable findings this drive.", color = SubText, fontSize = 13.sp)
        }
        return
    }

    SummaryCard("FINDINGS") {
        findings.forEach { agg -> FindingRow(agg, sessionStartMs = data.sessionStartMs) }
    }
}

// ── Finding row ────────────────────────────────────────────────────────────────

private val FindingSeverity.severityColor: Color get() = when (this) {
    FindingSeverity.HIGH   -> Color(0xFFCF6679)
    FindingSeverity.MEDIUM -> Color(0xFFFF9F43)
    FindingSeverity.LOW    -> Color(0xFF4C6EF5)
    FindingSeverity.INFO   -> Color(0xFF888888)
}

private val FindingSeverity.severityLabel: String get() = when (this) {
    FindingSeverity.HIGH   -> "HIGH"
    FindingSeverity.MEDIUM -> "MED"
    FindingSeverity.LOW    -> "LOW"
    FindingSeverity.INFO   -> "INFO"
}

@Composable
private fun FindingRow(agg: AggregatedFinding, sessionStartMs: Long) {
    var expanded by remember { mutableStateOf(agg.finding.severity == FindingSeverity.HIGH) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Border, RoundedCornerShape(8.dp))
            .background(SurfaceVar, RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                agg.finding.severity.severityLabel,
                color = agg.finding.severity.severityColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(agg.finding.severity.severityColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
            Text(
                agg.finding.title,
                color = OnSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = { expanded = !expanded },
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
            ) {
                Text(if (expanded) "Less" else "More", color = SubText, fontSize = 11.sp)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "Found in ${agg.occurrences * 100 / agg.totalSnapshots.coerceAtLeast(1)}% of checks",
                color = SubText,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                "Confidence: ${agg.finding.confidence.name.lowercase().replaceFirstChar { it.uppercase() }}",
                color = SubText,
                fontSize = 11.sp,
            )
        }
        if (expanded) {
            Text(
                agg.finding.description,
                color = OnSurface.copy(alpha = 0.8f),
                fontSize = 12.sp,
                lineHeight = 17.sp,
            )
            if (agg.finding.evidence.isNotEmpty()) {
                HorizontalDivider(color = Border, thickness = 0.5.dp)
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    agg.finding.evidence.forEach { (label, value) ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, color = SubText, fontSize = 11.sp, modifier = Modifier.weight(1f))
                            Text(value, color = OnSurface, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            val firstOffset = driveOffsetLabel(agg.firstSeenMs - sessionStartMs)
            val lastOffset  = driveOffsetLabel(agg.lastSeenMs  - sessionStartMs)
            val timingText = if (firstOffset == lastOffset) "At $firstOffset" else "$firstOffset – $lastOffset"
            Text(timingText, color = SubText, fontSize = 11.sp)
        }
    }
}

private fun driveOffsetLabel(offsetMs: Long): String {
    val totalSec = (offsetMs / 1000).coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return if (min > 0) "${min}m ${sec}s" else "${sec}s"
}
