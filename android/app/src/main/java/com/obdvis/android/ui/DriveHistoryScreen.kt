package com.obdvis.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.obdvis.android.domain.health.FindingSeverity
import com.obdvis.android.domain.health.SavedDrive
import com.obdvis.android.ui.theme.Background
import com.obdvis.android.ui.theme.Border
import com.obdvis.android.ui.theme.OBDvisTheme
import com.obdvis.android.ui.theme.OnSurface
import com.obdvis.android.ui.theme.Primary
import com.obdvis.android.ui.theme.SubText
import com.obdvis.android.ui.theme.SurfaceVar
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun DriveHistoryScreen(
    drives: List<SavedDrive>,
    onBack: () -> Unit,
    onOpen: (SavedDrive) -> Unit,
    onDelete: (SavedDrive) -> Unit,
) {
    BackHandler(onBack = onBack)
    var pendingDelete by remember { mutableStateOf<SavedDrive?>(null) }

    OBDvisTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Background),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    "Drive History",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
            }
            HorizontalDivider(color = Border)

            if (drives.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Default.DirectionsCar,
                            contentDescription = null,
                            tint = SubText,
                            modifier = Modifier.size(40.dp),
                        )
                        Text("No saved drives yet", color = OnSurface)
                        Text(
                            "Eligible summaries are saved when a drive ends.",
                            color = SubText,
                            fontSize = 12.sp,
                        )
                    }
                }
            } else {
                HistoryStorageOverview(
                    driveCount = drives.size,
                    totalSizeBytes = drives.sumOf { it.fileSizeBytes },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(drives, key = { it.id }) { drive ->
                        DriveHistoryRow(
                            drive = drive,
                            onOpen = { onOpen(drive) },
                            onDelete = { pendingDelete = drive },
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }
            }
        }

        pendingDelete?.let { drive ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete saved drive?") },
                text = { Text("This drive summary and its CSV data will be removed from this device.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            pendingDelete = null
                            onDelete(drive)
                        }
                    ) { Text("Delete") }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                },
            )
        }
    }
}

@Composable
private fun HistoryStorageOverview(
    driveCount: Int,
    totalSizeBytes: Long,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Border, RoundedCornerShape(10.dp)),
        color = SurfaceVar,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    "ON-DEVICE HISTORY",
                    color = SubText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.8.sp,
                )
                Text(
                    "$driveCount ${if (driveCount == 1) "drive" else "drives"}",
                    color = OnSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatFileSize(totalSizeBytes),
                    color = OnSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text("Storage used", color = SubText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun DriveHistoryRow(
    drive: SavedDrive,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = drive.summary
    val dateText = remember(summary.sessionEndMs) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(summary.sessionEndMs))
    }
    val durationMinutes = TimeUnit.MILLISECONDS.toMinutes(summary.durationMs)
    val durationText = if (durationMinutes >= 60) {
        "${durationMinutes / 60}h ${durationMinutes % 60}m"
    } else {
        "${durationMinutes}m"
    }
    val worstSeverity = summary.aggregatedFindings
        .map { it.finding.severity }
        .filter { it != FindingSeverity.INFO }
        .maxByOrNull { it.ordinal }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen),
        color = SurfaceVar,
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.DirectionsCar,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(dateText, color = OnSurface, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(durationText, color = SubText, fontSize = 12.sp)
                    Text(formatFileSize(drive.fileSizeBytes), color = SubText, fontSize = 12.sp)
                    summary.peakSpeedKph?.let {
                        Text("%.0f km/h peak".format(it), color = SubText, fontSize = 12.sp)
                    }
                    if (!summary.dtcResult.isEmpty) {
                        Text("${summary.dtcResult.totalCount} DTC", color = Color(0xFFCF6679), fontSize = 12.sp)
                    }
                }
                worstSeverity?.let {
                    Text(
                        "${it.name.lowercase().replaceFirstChar { char -> char.uppercase() }} severity finding",
                        color = severityColor(it),
                        fontSize = 11.sp,
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Delete drive", tint = SubText)
            }
        }
    }
}

private fun severityColor(severity: FindingSeverity): Color = when (severity) {
    FindingSeverity.HIGH -> Color(0xFFCF6679)
    FindingSeverity.MEDIUM -> Color(0xFFFF9F43)
    FindingSeverity.LOW -> Color(0xFF4C6EF5)
    FindingSeverity.INFO -> Color(0xFF888888)
}
