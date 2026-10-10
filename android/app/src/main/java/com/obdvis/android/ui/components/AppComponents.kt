/* Hallmark · modern-minimal · design-system: design.md · designed-as-app */
package com.obdvis.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.CarRepair
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.obdvis.android.ui.ActiveTab
import com.obdvis.android.ui.theme.*
import androidx.compose.ui.unit.dp

@Composable
fun SessionHeader(deviceName: String, driveEndDetected: Boolean, onAbout: () -> Unit,
    onSettings: () -> Unit, onEndDrive: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = AppSpace.md, end = AppSpace.xxs,
        top = AppSpace.xs, bottom = AppSpace.xs), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpace.xxs)) {
            Text("OBDvis", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpace.xxs)) {
                Icon(Icons.Default.FiberManualRecord, null, tint = Success, modifier = Modifier.size(8.dp))
                Text(deviceName, style = MaterialTheme.typography.labelSmall, color = SubText,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onAbout) { Icon(Icons.Default.Info, "About", tint = SubText) }
        IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings", tint = SubText) }
        IconButton(onClick = onEndDrive) {
            Icon(Icons.Default.StopCircle, "End drive", tint = if (driveEndDetected) Warning else SubText)
        }
    }
}

@Composable
fun AppHeader(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .padding(horizontal = AppSpace.xs, vertical = AppSpace.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = OnSurface)
                }
            } else {
                Spacer(Modifier.width(AppSpace.xs))
            }
            Text(title, style = MaterialTheme.typography.titleMedium, color = OnSurface,
                modifier = Modifier.weight(1f))
            actions()
        }
        HorizontalDivider(color = Border)
    }
}

@Composable
fun SectionHeading(title: String, subtitle: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(AppSpace.xxs)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = OnSurface)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = SubText)
    }
}

@Composable
fun NavigationRow(
    title: String,
    detail: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detailColor: Color = SubText,
) {
    Surface(
        onClick = onClick,
        color = Surface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, Border),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.heightIn(min = 72.dp).padding(AppSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpace.sm),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpace.xxs)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = OnSurface)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = detailColor)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SubText)
        }
    }
}

@Composable
fun DashboardTabs(selected: ActiveTab, findingCount: Int, dtcCount: Int, onSelect: (ActiveTab) -> Unit) {
    val largeText = LocalDensity.current.fontScale > 1.2f
    Row(
        modifier = Modifier.fillMaxWidth().then(if (largeText) Modifier.horizontalScroll(rememberScrollState()) else Modifier)
            .selectableGroup().padding(horizontal = AppSpace.sm, vertical = AppSpace.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpace.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(ActiveTab.OVERVIEW to "Overview", ActiveTab.LIVE to "Live",
            ActiveTab.HEALTH to "Health", ActiveTab.DTC to "DTCs").forEach { (tab, label) ->
            val count = when (tab) { ActiveTab.HEALTH -> findingCount; ActiveTab.DTC -> dtcCount; else -> 0 }
            val icon = when (tab) {
                ActiveTab.OVERVIEW -> Icons.Outlined.Speed
                ActiveTab.LIVE -> Icons.Outlined.Tune
                ActiveTab.HEALTH -> Icons.Outlined.MonitorHeart
                ActiveTab.DTC -> Icons.Outlined.CarRepair
            }
            val tint = if (selected == tab) PrimaryVar else SubText
            val width = if (largeText) Modifier.widthIn(min = 112.dp) else Modifier.weight(1f)
            Surface(
                color = if (selected == tab) SurfaceVar else Background,
                shape = MaterialTheme.shapes.small,
                modifier = width.selectable(selected == tab, role = Role.Tab, onClick = { onSelect(tab) })
                    .semantics { contentDescription = if (count > 0) "$label, $count" else label },
            ) {
                Column(
                    Modifier.heightIn(min = 64.dp).padding(horizontal = AppSpace.xxs, vertical = AppSpace.xs),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AppSpace.xxs, Alignment.CenterVertically),
                ) {
                    BadgedBox(badge = {
                        if (count > 0) {
                            Badge(containerColor = DangerContainer, contentColor = Danger) {
                                Text(if (count > 99) "99+" else "$count", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
                    }
                    Text(label, style = MaterialTheme.typography.labelSmall,
                        color = tint, maxLines = 1)
                }
            }
        }
    }
}
