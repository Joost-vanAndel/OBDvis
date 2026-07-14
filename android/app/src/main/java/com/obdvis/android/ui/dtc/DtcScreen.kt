package com.obdvis.android.ui.dtc

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.obdvis.android.domain.health.DtcInfo
import com.obdvis.android.domain.health.DtcReadResult
import com.obdvis.android.domain.health.DtcScreenState
import com.obdvis.android.ui.theme.*

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
        // ── Action row ──────────────────────────────────────────────────────
        val isBusy = state is DtcScreenState.Reading || state is DtcScreenState.Clearing
        val canClear = state is DtcScreenState.Loaded && !state.result.isEmpty

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick  = onRead,
                enabled  = !isBusy,
                modifier = Modifier.weight(1f),
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
                Text(if (state is DtcScreenState.Reading) "Reading…" else "Read DTCs")
            }

            OutlinedButton(
                onClick  = { showClearConfirm = true },
                enabled  = canClear && !isBusy,
                modifier = Modifier.weight(1f),
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
                    Text("Clear DTCs")
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
    val accentColor = if (isStored) Color(0xFFCF6679) else Color(0xFFF5D547)

    Surface(
        color    = Surface,
        shape    = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .clickable(onClickLabel = "Search for $code") { onSearch() },
    ) {
        Row(
            modifier            = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Severity dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(accentColor, RoundedCornerShape(4.dp))
            )

            Column(modifier = Modifier.weight(1f)) {
                Text(code, style = MaterialTheme.typography.labelLarge, color = OnSurface)
                Spacer(Modifier.height(2.dp))
                Text(
                    DtcInfo.genericExplanation(code),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurface,
                )
                Text(
                    DtcInfo.systemLabel(code),
                    style = MaterialTheme.typography.labelSmall,
                    color = SubText,
                )
            }

            Icon(
                Icons.Default.Search,
                contentDescription = "Search for $code",
                tint = SubText,
                modifier = Modifier.size(18.dp),
            )

            Surface(
                color = SurfaceVar,
                shape = RoundedCornerShape(4.dp),
            ) {
                Text(
                    if (isStored) "stored" else "pending",
                    style    = MaterialTheme.typography.labelSmall,
                    color    = if (isStored) accentColor else Color(0xFFF5D547),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
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
