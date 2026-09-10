package com.obdvis.android.ui.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.obdvis.android.domain.health.*
import com.obdvis.android.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val MonitorComplete    = Color(0xFF4CD97B)
private val MonitorIncomplete  = Color(0xFFFF9F43)
private val MonitorUnsupported = Color(0xFF444444)
private val AllClearGreen      = Color(0xFF4CD97B)

@Composable
fun VehicleHealthScreen(
    healthState: HealthState,
    activeFindings: Map<String, FindingRecord>,
    findingEventLog: List<FindingEvent>,
    autoUpdateEnabled: Boolean = false,
    onToggleAutoUpdate: () -> Unit = {},
    onOpenFuelTrimDive: () -> Unit = {},
    onOpenDtcs: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val summary = healthState.summaryOrNull()
    val activeIssues = activeFindings.values.count {
        it.status == FindingStatus.ACTIVE && it.finding.severity != FindingSeverity.INFO
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AutoUpdateBar(enabled = autoUpdateEnabled, onToggle = onToggleAutoUpdate)

        when {
            healthState is HealthState.Idle && activeFindings.isEmpty() ->
                IdleContent()

            else -> {
                HealthStatusHeader(summary = summary, activeIssueCount = activeIssues)
                ContinuousFindingsList(activeFindings, onOpenDtcs)
                FuelTrimDeepDiveCard(onOpen = onOpenFuelTrimDive)
                DataLimitationsCard(summary?.dataLimitations ?: emptyList())
                MonitorReadinessCard(summary?.vehicleState?.monitorStatuses)
                DriveCycleGuideCard(summary?.vehicleState?.incompleteMonitorNames ?: emptyList())
            }
        }

        if (findingEventLog.isNotEmpty()) {
            FindingEventLogSection(findingEventLog)
        }
    }
}

// ── Header ─────────────────────────────────────────────────────────────────────

@Composable
private fun AutoUpdateBar(enabled: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (enabled) "Live" else "Paused",
            style = MaterialTheme.typography.labelSmall,
            color = if (enabled) Primary else DimText,
            modifier = Modifier.weight(1f).padding(vertical = 4.dp),
        )
        IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (enabled) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (enabled) "Pause" else "Resume",
                tint = if (enabled) Primary else SubText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun HealthStatusHeader(
    summary: DiagnosticSummary?,
    activeIssueCount: Int,
) {
    val issueText = when {
        summary == null -> "Waiting for data…"
        activeIssueCount == 0 -> "All clear"
        activeIssueCount == 1 -> "1 active issue"
        else -> "$activeIssueCount active issues"
    }
    val issueColor = when {
        summary == null -> SubText
        activeIssueCount == 0 -> AllClearGreen
        else -> SubText
    }
    val wc = summary?.windowContext
    val coverageLine = remember(wc) {
        if (wc == null) return@remember null
        val parts = mutableListOf("${wc.windowSeconds}s window")
        wc.rpm?.count?.let { parts += "$it rpm samples" }
        if (wc.operatingStatesObserved.size > 1) parts += wc.operatingStatesObserved.joinToString(" · ")
        parts.joinToString("  ·  ")
    }

    Surface(
        color = Surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                summary?.operatingState?.label ?: "—",
                style = MaterialTheme.typography.titleSmall,
                color = OnSurface,
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                if (summary != null && activeIssueCount == 0) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = AllClearGreen,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Text(
                    issueText,
                    style = MaterialTheme.typography.bodySmall,
                    color = issueColor,
                )
            }
            if (coverageLine != null) {
                Spacer(Modifier.height(3.dp))
                Text(
                    coverageLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = DimText,
                )
            }
        }
    }
}

@Composable
private fun IdleContent() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("No analysis yet", style = MaterialTheme.typography.bodyMedium, color = SubText)
    }
}

// ── Continuous findings list ───────────────────────────────────────────────────

@Composable
private fun ContinuousFindingsList(
    activeFindings: Map<String, FindingRecord>,
    onOpenDtcs: () -> Unit,
) {
    val issues = remember(activeFindings) {
        activeFindings.values
            .filter { it.finding.severity != FindingSeverity.INFO }
            .sortedWith(compareByDescending<FindingRecord> { it.peakSeverity.ordinal }.thenBy { it.firstSeenMs })
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        issues.forEach { record ->
            key(record.finding.id) {
                var visible by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { visible = true }
                AnimatedVisibility(
                    visible = visible,
                    enter = fadeIn(tween(250)) + expandVertically(tween(250)),
                    exit = fadeOut(tween(400)) + shrinkVertically(tween(400)),
                ) {
                    if (record.status == FindingStatus.FADING) {
                        ResolvedFindingCard(record)
                    } else {
                        FindingCard(record, onOpenDtcs)
                    }
                }
            }
        }
    }
}

// ── Finding cards ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FindingCard(record: FindingRecord, onOpenDtcs: () -> Unit) {
    val accentColor = record.finding.severity.color()
    val navigationModifier = if (record.finding.id == DiagnosticFindingIds.DTC_PRESENT) {
        Modifier.clickable(onClickLabel = "Open DTCs", onClick = onOpenDtcs)
    } else {
        Modifier
    }
    Surface(
        color = Surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .then(navigationModifier),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SeverityDot(accentColor)
                Text(
                    record.finding.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurface,
                    modifier = Modifier.weight(1f),
                )
                ConfidenceBadge(record.finding.confidence)
            }
            Spacer(Modifier.height(4.dp))
            FindingLifecycleBadge(record)
            if (record.finding.description.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    record.finding.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = SubText,
                )
            }
            if (record.finding.evidence.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    record.finding.evidence.forEach { (label, value) -> EvidenceChip(label, value) }
                }
            }
        }
    }
}

@Composable
private fun ResolvedFindingCard(record: FindingRecord) {
    val fadingSince = record.fadingSinceMs ?: record.lastSeenMs
    val now = tickingNow()
    val secsAgo = ((now - fadingSince) / 1000).coerceAtLeast(0)
    val resolvedLabel = if (secsAgo < 5) "Not detected" else "Not detected · ${secsAgo}s"

    Surface(
        color = Surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.45f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SeverityDot(record.finding.severity.color())
            Text(
                record.finding.title,
                style = MaterialTheme.typography.labelMedium,
                color = SubText,
                modifier = Modifier.weight(1f),
            )
            Surface(
                color = AllClearGreen.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    resolvedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = AllClearGreen,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun FindingLifecycleBadge(record: FindingRecord) {
    val now = tickingNow()
    val ageMs = now - record.firstSeenMs
    val durationLabel = if (ageMs < 120_000L) {
        val secs = (ageMs / 1000).coerceAtLeast(1)
        "Active ${secs}s"
    } else {
        val mins = (ageMs / 60_000).toInt()
        "Active ${mins}m"
    }
    Surface(
        color = Color(0xFF888888).copy(alpha = 0.12f),
        shape = RoundedCornerShape(4.dp),
    ) {
        Text(
            durationLabel,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF888888),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun SeverityDot(color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .background(color, shape = RoundedCornerShape(4.dp))
    )
}

@Composable
private fun ConfidenceBadge(confidence: FindingConfidence) {
    val label = when (confidence) {
        FindingConfidence.HIGH   -> "high confidence"
        FindingConfidence.MEDIUM -> "medium confidence"
        FindingConfidence.LOW    -> "low confidence"
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = DimText)
}

@Composable
private fun EvidenceChip(label: String, value: String) {
    Surface(color = SurfaceVar, shape = RoundedCornerShape(4.dp)) {
        Text(
            "$label: $value",
            style = MaterialTheme.typography.labelSmall,
            color = SubText,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

// ── Data coverage ──────────────────────────────────────────────────────────────

@Composable
private fun DataLimitationsCard(limitations: List<String>) {
    if (limitations.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = Surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "DATA COVERAGE",
                    style = MaterialTheme.typography.labelSmall,
                    color = DimText,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${limitations.size} gap${if (limitations.size == 1) "" else "s"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = DimText,
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = DimText,
                    modifier = Modifier.size(16.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    limitations.forEach { limitation ->
                        Text("· $limitation", style = MaterialTheme.typography.bodySmall, color = DimText)
                    }
                }
            }
        }
    }
}

// ── Monitor readiness ──────────────────────────────────────────────────────────

@Composable
private fun MonitorReadinessCard(statuses: List<MonitorStatus>?) {
    if (statuses == null) return
    Surface(
        color = Surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "READINESS MONITORS",
                style = MaterialTheme.typography.labelSmall,
                color = DimText,
                modifier = Modifier.padding(bottom = 6.dp),
            )
            statuses.chunked(2).forEach { row ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    row.forEachIndexed { col, status ->
                        MonitorRow(
                            status,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = if (col == 0) 12.dp else 0.dp, start = if (col == 1) 12.dp else 0.dp),
                        )
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MonitorRow(status: MonitorStatus, modifier: Modifier = Modifier) {
    val (dotColor, label) = when (status.state) {
        MonitorReadinessState.COMPLETE      -> MonitorComplete    to "OK"
        MonitorReadinessState.INCOMPLETE    -> MonitorIncomplete  to "—"
        MonitorReadinessState.NOT_SUPPORTED -> MonitorUnsupported to "N/A"
    }
    Row(
        modifier = modifier.padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(7.dp).background(dotColor, RoundedCornerShape(4.dp)))
        Text(
            status.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (status.state == MonitorReadinessState.NOT_SUPPORTED) DimText else SubText,
            modifier = Modifier.weight(1f),
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = dotColor)
    }
}

// ── Drive cycle guide ─────────────────────────────────────────────────────────

@Composable
private fun DriveCycleGuideCard(incompleteMonitors: List<String>) {
    if (incompleteMonitors.isEmpty()) return
    val steps = remember(incompleteMonitors) { driveCycleStepsFor(incompleteMonitors) }
    var expanded by remember { mutableStateOf(false) }

    Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "DRIVE CYCLE GUIDE",
                    style = MaterialTheme.typography.labelSmall,
                    color = DimText,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = DimText,
                    modifier = Modifier.size(16.dp),
                )
            }
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    steps.forEachIndexed { index, step ->
                        DriveCycleStepRow(index + 1, step, incompleteMonitors.toSet())
                    }
                    Text(
                        "Based on the EPA generic OBD-II drive cycle. Manufacturer-specific procedures may differ.",
                        style = MaterialTheme.typography.labelSmall,
                        color = DimText,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DriveCycleStepRow(number: Int, step: DriveCycleStep, incompleteMonitors: Set<String>) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            "$number",
            style = MaterialTheme.typography.labelSmall,
            color = Primary,
            modifier = Modifier.width(16.dp).padding(top = 1.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(step.title, style = MaterialTheme.typography.bodySmall, color = SubText)
            Text(step.detail, style = MaterialTheme.typography.bodySmall, color = DimText)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                step.monitors.filter { it in incompleteMonitors }.forEach { MonitorTag(it) }
            }
        }
    }
}

@Composable
private fun MonitorTag(name: String) {
    Surface(color = MonitorIncomplete.copy(alpha = 0.15f), shape = RoundedCornerShape(4.dp)) {
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = MonitorIncomplete,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

// ── Fuel Trim Deep Dive entry card ────────────────────────────────────────────

@Composable
private fun FuelTrimDeepDiveCard(onOpen: () -> Unit) {
    Surface(
        color = Surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .border(0.5.dp, Primary.copy(alpha = 0.25f), RoundedCornerShape(8.dp)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Fuel Trim Deep Dive", style = MaterialTheme.typography.labelMedium, color = OnSurface)
                Spacer(Modifier.height(2.dp))
                Text("STFT & LTFT trend chart with zone analysis", style = MaterialTheme.typography.bodySmall, color = SubText)
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = "Open fuel trim deep dive",
                tint = SubText,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ── Finding event log ──────────────────────────────────────────────────────────

@Composable
private fun FindingEventLogSection(events: List<FindingEvent>) {
    var expanded by remember { mutableStateOf(false) }
    val now = tickingNow()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "EVENT LOG",
                style = MaterialTheme.typography.labelSmall,
                color = DimText,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${events.size}",
                style = MaterialTheme.typography.labelSmall,
                color = DimText,
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = DimText,
                modifier = Modifier.size(16.dp),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                events.take(40).forEach { event -> EventLogRow(event, now) }
            }
        }
    }
}

@Composable
private fun EventLogRow(event: FindingEvent, now: Long) {
    val ageMs = now - event.timestampMs
    val timeLabel = when {
        ageMs < 60_000L -> "${(ageMs / 1000).toInt()}s ago"
        else -> "${(ageMs / 60_000).toInt()}m ago"
    }
    val (icon, title, color) = when (event) {
        is FindingEvent.Appeared -> Triple("→", event.finding.title, event.finding.severity.color())
        is FindingEvent.Cleared -> Triple("–", event.title, Color(0xFF888888))
        is FindingEvent.SeverityChanged -> Triple(
            if (event.finding.severity.ordinal > event.from.ordinal) "↑" else "↓",
            event.finding.title,
            event.finding.severity.color(),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Surface, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(icon, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.width(14.dp))
        Text(title, style = MaterialTheme.typography.bodySmall, color = SubText, modifier = Modifier.weight(1f))
        Text(timeLabel, style = MaterialTheme.typography.labelSmall, color = DimText)
    }
}

// ── Ticking clock ─────────────────────────────────────────────────────────────

@Composable
private fun tickingNow(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

// ── Color helpers ──────────────────────────────────────────────────────────────

private fun FindingSeverity.color(): Color = when (this) {
    FindingSeverity.INFO   -> Color(0xFF888888)
    FindingSeverity.LOW    -> Color(0xFFF5D547)
    FindingSeverity.MEDIUM -> Color(0xFFFF9F43)
    FindingSeverity.HIGH   -> Color(0xFFCF6679)
}
