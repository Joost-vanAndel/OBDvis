package com.obdvis.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.obdvis.android.domain.model.PidDefinition
import com.obdvis.android.ui.theme.*

@Composable
fun SensorSidebar(
    pids: List<PidDefinition>,
    enabledPids: Set<PidDefinition>,
    latestValues: Map<String, Float>,
    onToggle: (PidDefinition) -> Unit,
    onEnableAll: () -> Unit,
    onDisableAll: () -> Unit,
    normalizeChart: Boolean,
    onToggleNormalize: () -> Unit,
    onExportCsv: () -> Unit,
    canExportCsv: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Surface),
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "SENSORS",
                    style = MaterialTheme.typography.labelSmall,
                    color = SubText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                FilterChip(
                    selected = normalizeChart,
                    onClick = onToggleNormalize,
                    label = { Text("%", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.height(28.dp),
                )

                IconButton(
                    onClick = onExportCsv,
                    enabled = canExportCsv,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Save CSV",
                        tint = if (canExportCsv) SubText else SubText.copy(alpha = 0.3f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    "${enabledPids.size}/${pids.size} sensors",
                    style = MaterialTheme.typography.labelSmall,
                    color = SubText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onEnableAll,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("All", style = MaterialTheme.typography.labelSmall)
                }
                TextButton(
                    onClick = onDisableAll,
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("None", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        HorizontalDivider(color = Border, thickness = 0.5.dp)

        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(pids) { pid ->
                SensorRow(
                    pid = pid,
                    enabled = pid in enabledPids,
                    latestValue = latestValues[pid.id],
                    onClick = { onToggle(pid) },
                )
            }
        }
    }
}

@Composable
private fun SensorRow(
    pid: PidDefinition,
    enabled: Boolean,
    latestValue: Float?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // Color dot
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(
                    if (enabled) pid.color
                    else pid.color.copy(alpha = 0.2f)
                ),
        )

        // Name
        Text(
            pid.name,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) OnSurface else DimText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        // Current value
        if (latestValue != null && enabled) {
            Text(
                "%.1f".format(latestValue),
                style = MaterialTheme.typography.bodySmall,
                color = SubText,
            )
            Text(
                pid.unit,
                style = MaterialTheme.typography.labelSmall,
                color = DimText,
            )
        }
    }
}
