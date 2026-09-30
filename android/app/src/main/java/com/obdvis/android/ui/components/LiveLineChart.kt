package com.obdvis.android.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.interfaces.datasets.ILineDataSet
import com.obdvis.android.domain.PidRegistry
import com.obdvis.android.domain.model.PidDefinition
import com.obdvis.android.ui.theme.Background
import com.obdvis.android.ui.theme.Border
import com.obdvis.android.ui.theme.SubText

@Composable
fun LiveLineChart(
    chartData: Map<String, List<Entry>>,
    enabledPids: Set<PidDefinition>,
    normalize: Boolean = false,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { context ->
            LineChart(context).apply {
                setBackgroundColor(Background.toArgb())
                setDrawGridBackground(false)
                setNoDataText("Waiting for selected sensor data…")
                setNoDataTextColor(SubText.toArgb())
                setExtraOffsets(8f, 12f, 12f, 8f)
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(false)
                setScaleXEnabled(true)
                setScaleYEnabled(false)
                setPinchZoom(false)
                isDoubleTapToZoomEnabled = false

                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    textSize = 12f
                    textColor = SubText.toArgb()
                    gridColor = Border.toArgb()
                    axisLineColor = Border.toArgb()
                    granularity = 1f
                    setDrawAxisLine(true)
                    setDrawGridLines(true)
                    valueFormatter = object : ValueFormatter() {
                        override fun getFormattedValue(value: Float): String {
                            val totalSec = value.toInt()
                            return "%d:%02d".format(totalSec / 60, totalSec % 60)
                        }
                    }
                }

                axisLeft.apply {
                    textSize = 12f
                    textColor = SubText.toArgb()
                    gridColor = Border.toArgb()
                    axisLineColor = Border.toArgb()
                    setDrawAxisLine(true)
                    setDrawGridLines(true)
                }

                axisRight.isEnabled = false
            }
        },
        update = { chart ->
            if (normalize) {
                chart.axisLeft.axisMinimum = 0f
                chart.axisLeft.axisMaximum = 100f
            } else {
                chart.axisLeft.resetAxisMinimum()
                chart.axisLeft.resetAxisMaximum()
            }

            val dataSets = PidRegistry.all
                .filter { it in enabledPids }
                .mapNotNull { pid ->
                    val rawEntries = chartData[pid.id]
                    if (rawEntries.isNullOrEmpty()) return@mapNotNull null
                    val entries = if (normalize) {
                        val range = pid.max - pid.min
                        rawEntries.map { e -> Entry(e.x, (e.y - pid.min) / range * 100f) }
                    } else {
                        rawEntries
                    }
                    LineDataSet(entries, pid.name).apply {
                        color = pid.color.toArgb()
                        setDrawCircles(false)
                        setDrawValues(false)
                        lineWidth = 2f
                        mode = LineDataSet.Mode.LINEAR
                        setDrawFilled(false)
                        highLightColor = pid.color.copy(alpha = 0.5f).toArgb()
                    }
                }

            if (dataSets.isEmpty()) {
                chart.setNoDataText(if (enabledPids.isEmpty()) "Select sensors below to start plotting" else "Waiting for selected sensor data…")
                chart.clear()
            } else {
                chart.data = LineData(dataSets.map { it as ILineDataSet })
                chart.notifyDataSetChanged()
                chart.invalidate()
            }
        },
        modifier = modifier,
    )
}
