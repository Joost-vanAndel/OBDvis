package com.obdvis.android.ui.health

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.obdvis.android.domain.PidRegistry
import com.obdvis.android.domain.health.DiagnosticFinding
import com.obdvis.android.domain.health.FindingSeverity
import com.obdvis.android.domain.health.HealthState
import com.obdvis.android.ui.theme.*

private val FUEL_TRIM_IDS = listOf("stft", "ltft", "stft2", "ltft2")
private val fuelTrimPidDefs = FUEL_TRIM_IDS.mapNotNull { PidRegistry.byId[it] }

private val ZoneNormal  = Color(0xFF4CD97B)
private val ZoneCaution = Color(0xFFFF9F43)
private val ZoneLean    = Color(0xFFCF6679)
private val ZoneRich    = Color(0xFF5B8FFF)

private fun String.isFuelTrimFinding() =
    startsWith("lean_") || startsWith("rich_") || startsWith("fuel_trim_") ||
    startsWith("vacuum_leak_") || startsWith("fuel_delivery_") || startsWith("maf_underreporting_")

private fun FindingSeverity.toColor(): Color = when (this) {
    FindingSeverity.HIGH   -> ZoneLean
    FindingSeverity.MEDIUM -> ZoneCaution
    FindingSeverity.LOW    -> ZoneCaution.copy(alpha = 0.75f)
    FindingSeverity.INFO   -> ZoneNormal
}

@Composable
fun FuelTrimDivePanel(
    chartData: Map<String, List<Entry>>,
    latestValues: Map<String, Float>,
    healthState: HealthState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)

    val findings = remember(healthState) {
        when (healthState) {
            is HealthState.Ready -> healthState.summary.findings
            else                 -> emptyList()
        }.filter { it.id.isFuelTrimFinding() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Close",
                    tint = SubText,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(4.dp))
            Text("Fuel Trim Deep Dive", style = MaterialTheme.typography.titleSmall, color = OnSurface)
            Spacer(Modifier.weight(1f))
            Surface(
                color = Primary.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    "Focused polling",
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }

        FuelTrimChart(
            chartData = chartData,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp),
        )

        FuelTrimValueGrid(latestValues)

        FuelTrimFindingsCard(findings, latestValues)
    }
}

@Composable
private fun FuelTrimChart(
    chartData: Map<String, List<Entry>>,
    modifier: Modifier = Modifier,
) {
    Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = modifier) {
        AndroidView(
            factory = { context ->
                LineChart(context).apply {
                    setBackgroundColor(Surface.toArgb())
                    setDrawGridBackground(false)
                    description.isEnabled = false
                    legend.isEnabled = false
                    setTouchEnabled(true)
                    isDragEnabled = true
                    setScaleEnabled(false)
                    setScaleXEnabled(true)
                    setScaleYEnabled(false)
                    setPinchZoom(false)
                    isDoubleTapToZoomEnabled = false
                    setNoDataText("Waiting for fuel trim data…")
                    setNoDataTextColor(SubText.toArgb())

                    xAxis.apply {
                        position = XAxis.XAxisPosition.BOTTOM
                        textColor = SubText.toArgb()
                        gridColor = Border.toArgb()
                        axisLineColor = Border.toArgb()
                        granularity = 1f
                        setDrawAxisLine(true)
                        setDrawGridLines(true)
                        valueFormatter = object : ValueFormatter() {
                            override fun getFormattedValue(value: Float): String {
                                val s = value.toInt()
                                return "%d:%02d".format(s / 60, s % 60)
                            }
                        }
                    }

                    axisLeft.apply {
                        textColor = SubText.toArgb()
                        gridColor = Border.toArgb()
                        axisLineColor = Border.toArgb()
                        axisMinimum = -28f
                        axisMaximum = 28f
                        setDrawAxisLine(true)
                        setDrawGridLines(true)
                        setDrawLimitLinesBehindData(true)

                        removeAllLimitLines()
                        addLimitLine(LimitLine(10f).apply {
                            lineColor = ZoneCaution.toArgb()
                            lineWidth = 0.8f
                            enableDashedLine(8f, 4f, 0f)
                        })
                        addLimitLine(LimitLine(-10f).apply {
                            lineColor = ZoneCaution.toArgb()
                            lineWidth = 0.8f
                            enableDashedLine(8f, 4f, 0f)
                        })
                        addLimitLine(LimitLine(5f).apply {
                            lineColor = ZoneNormal.copy(alpha = 0.5f).toArgb()
                            lineWidth = 0.5f
                            enableDashedLine(4f, 4f, 0f)
                        })
                        addLimitLine(LimitLine(-5f).apply {
                            lineColor = ZoneNormal.copy(alpha = 0.5f).toArgb()
                            lineWidth = 0.5f
                            enableDashedLine(4f, 4f, 0f)
                        })
                    }

                    axisRight.isEnabled = false
                }
            },
            update = { chart ->
                val maxX = fuelTrimPidDefs.mapNotNull { chartData[it.id]?.lastOrNull()?.x }.maxOrNull() ?: 0f
                val windowStart = maxX - 60f
                val dataSets = fuelTrimPidDefs.mapNotNull { pid ->
                    val entries = chartData[pid.id]?.filter { it.x >= windowStart }
                    if (entries.isNullOrEmpty()) return@mapNotNull null
                    LineDataSet(entries, pid.name).apply {
                        color = pid.color.toArgb()
                        setDrawCircles(false)
                        setDrawValues(false)
                        lineWidth = 1.8f
                        mode = LineDataSet.Mode.LINEAR
                        setDrawFilled(false)
                    }
                }
                if (dataSets.isEmpty()) {
                    chart.clear()
                } else {
                    chart.data = LineData(dataSets.map { it as ILineDataSet })
                    chart.notifyDataSetChanged()
                    chart.setVisibleXRangeMaximum(60f)
                    chart.moveViewToX(maxX)
                    chart.invalidate()
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
        )
    }
}

@Composable
private fun FuelTrimValueGrid(latestValues: Map<String, Float>) {
    val hasB2 = latestValues.containsKey("stft2") || latestValues.containsKey("ltft2")
    Surface(color = Surface, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("CURRENT VALUES", style = MaterialTheme.typography.labelSmall, color = DimText)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrimValueCell("STFT B1", "stft", latestValues, Modifier.weight(1f))
                TrimValueCell("LTFT B1", "ltft", latestValues, Modifier.weight(1f))
            }
            if (hasB2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TrimValueCell("STFT B2", "stft2", latestValues, Modifier.weight(1f))
                    TrimValueCell("LTFT B2", "ltft2", latestValues, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun TrimValueCell(
    label: String,
    pidId: String,
    latestValues: Map<String, Float>,
    modifier: Modifier = Modifier,
) {
    val value = latestValues[pidId]
    val (valueColor, zoneLabel) = when {
        value == null  -> DimText      to "No data"
        value > 10f    -> ZoneLean    to "Lean"
        value < -10f   -> ZoneRich    to "Rich"
        value > 5f     -> ZoneCaution to "Mild lean"
        value < -5f    -> ZoneCaution to "Mild rich"
        else           -> ZoneNormal  to "Normal"
    }
    Surface(color = SurfaceVar, shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = DimText)
            Spacer(Modifier.height(4.dp))
            Text(
                if (value != null) "%.1f%%".format(value) else "—",
                style = MaterialTheme.typography.titleMedium,
                color = valueColor,
            )
            Text(
                zoneLabel,
                style = MaterialTheme.typography.labelSmall,
                color = valueColor.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun FuelTrimFindingsCard(findings: List<DiagnosticFinding>, latestValues: Map<String, Float>) {
    val compensationNote = compensationInsight(latestValues)
    if (findings.isEmpty() && compensationNote == null) return

    Surface(
        color = Surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Border, RoundedCornerShape(8.dp)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("INTERPRETATION", style = MaterialTheme.typography.labelSmall, color = DimText)
            findings.forEach { finding ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "• ${finding.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = finding.severity.toColor(),
                    )
                    Text(
                        finding.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = SubText,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
            compensationNote?.let { (text, color) ->
                Text("• $text", style = MaterialTheme.typography.bodySmall, color = color)
            }
        }
    }
}

private fun compensationInsight(latestValues: Map<String, Float>): Pair<String, Color>? {
    val stft = latestValues["stft"] ?: return null
    val ltft = latestValues["ltft"] ?: return null
    // LTFT carries the lean signal but STFT has settled — ECU has compensated for the bias.
    // Only surface this when combined is still in lean territory (≥10%) so it never contradicts a "normal" finding.
    if (ltft > 8f && kotlin.math.abs(stft) < 5f && (stft + ltft) >= 10f) {
        return ("Bank 1 LTFT is ${ltft.fmt()}% but STFT is near zero — the ECU has compensated. " +
            "The lean cause still exists; LTFT will continue to reflect it until the root cause is fixed.") to ZoneCaution
    }
    return null
}

private fun Float.fmt() = if (this >= 0f) "+%.1f".format(this) else "%.1f".format(this)
