package com.obdvis.android.ui.dtc

import com.obdvis.android.ui.components.SectionHeading
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.obdvis.android.domain.health.DtcInfo
import com.obdvis.android.domain.health.DtcScreenState
import com.obdvis.android.ui.theme.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DtcScreen(
    state: DtcScreenState,
    onRead: () -> Unit,
    onClear: () -> Unit,
    onSearchDtc: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title   = { Text("Clear fault codes?") },
            text    = {
                Text(
                    "This will erase all stored DTCs from the ECU and reset readiness monitors. " +
                    "The check-engine light will turn off. This cannot be undone.",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = { showClearConfirm = false; onClear() }) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            },
            containerColor = Surface,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionHeading("Fault codes", "Stored and pending codes from the ECU")

        // ── Action row ──────────────────────────────────────────────────────
        val isBusy = state is DtcScreenState.Reading || state is DtcScreenState.Clearing
        val canClear = state is DtcScreenState.Loaded && !state.result.isEmpty

        FlowRow(maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.2f) 1 else 2,
            horizontalArrangement = Arrangement.spacedBy(AppSpace.xs), verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
            Button(shape = MaterialTheme.shapes.small,
                onClick  = onRead,
                enabled  = !isBusy,
                modifier = Modifier.weight(1f).heightIn(min = AppSpace.touch),
            ) {
                if (state is DtcScreenState.Reading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(6.dp))
                Text(if (state is DtcScreenState.Reading) "Reading…" else "Read DTCs", maxLines = 1)
            }

            OutlinedButton(shape = MaterialTheme.shapes.small,
                onClick  = { showClearConfirm = true },
                enabled  = canClear && !isBusy,
                modifier = Modifier.weight(1f).heightIn(min = AppSpace.touch),
                colors   = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(
                        if (canClear && !isBusy) MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        else MaterialTheme.colorScheme.outline
                    )
                ),
            ) {
                if (state is DtcScreenState.Clearing) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color       = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("Clearing…")
                } else {
                    Text("Clear DTCs", maxLines = 1)
                }
            }
        }

        // ── Content ─────────────────────────────────────────────────────────
        when (state) {
            is DtcScreenState.Idle -> IdleHint()

            is DtcScreenState.Reading,
            is DtcScreenState.Clearing -> { /* progress shown in buttons */ }

            is DtcScreenState.Loaded -> LoadedContent(state, onSearchDtc)

            is DtcScreenState.Error -> ErrorCard(state.message)
        }
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun IdleHint() {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 40.dp), contentAlignment = Alignment.Center) {
        Text(
            "Tap \"Read DTCs\" to query fault codes from the ECU.",
            style = MaterialTheme.typography.bodySmall,
            color = SubText,
        )
    }
}

@Composable
private fun LoadedContent(state: DtcScreenState.Loaded, onSearchDtc: (String) -> Unit) {
    if (state.justCleared) {
        InfoBanner("DTCs cleared. Readiness monitors have been reset.")
    }

    if (state.result.isEmpty) {
        Box(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) {
            Text("No fault codes found.", style = MaterialTheme.typography.bodySmall, color = SubText)
        }
        return
    }

    if (state.result.stored.isNotEmpty()) {
        DtcSection(
            title = "Stored codes",
            codes = state.result.stored,
            isStored = true,
            onSearchDtc = onSearchDtc,
        )
    }

    if (state.result.pending.isNotEmpty()) {
        DtcSection(
            title = "Pending codes",
            codes = state.result.pending,
            isStored = false,
            onSearchDtc = onSearchDtc,
        )
    }
}

@Composable
private fun DtcSection(
    title: String,
    codes: List<String>,
    isStored: Boolean,
    onSearchDtc: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "$title (${codes.size})",
            style = MaterialTheme.typography.labelMedium,
            color = SubText,
        )
        codes.forEach { code ->
            DtcCard(code = code, isStored = isStored, onSearch = { onSearchDtc(code) })
        }
    }
}

@Composable
private fun DtcCard(code: String, isStored: Boolean, onSearch: () -> Unit) {
    val accent = if (isStored) Danger else Caution
    Surface(onClick = onSearch, color = Surface, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(AppSpace.md), horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
            verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpace.xs)) {
                Text(code, style = AppType.reading, color = OnSurface)
                Text(if (isStored) "Stored" else "Pending", style = MaterialTheme.typography.labelSmall, color = accent)
                Text(DtcInfo.genericExplanation(code), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                Text(DtcInfo.systemLabel(code), style = MaterialTheme.typography.bodySmall, color = SubText)
            }
            Icon(Icons.Default.Search, "Search for $code", tint = Primary, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun InfoBanner(message: String) {
    Surface(
        color    = Primary.copy(alpha = 0.15f),
        shape    = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            style    = MaterialTheme.typography.bodySmall,
            color    = PrimaryVar,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        color    = Surface,
        shape    = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
    ) {
        Text(
            message,
            style    = MaterialTheme.typography.bodySmall,
            color    = SubText,
            modifier = Modifier.padding(14.dp),
        )
    }
}

// ── Helpers ────────────────────────────────────────────────────────────────────
