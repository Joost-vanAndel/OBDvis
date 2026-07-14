package com.obdvis.android.auto

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.GridItem
import androidx.car.app.model.GridTemplate
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.SectionedItemList
import androidx.car.app.model.Tab
import androidx.car.app.model.TabContents
import androidx.car.app.model.TabTemplate
import androidx.car.app.model.Template
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.obdvis.android.OBDVisApp
import com.obdvis.android.domain.PidRegistry
import com.obdvis.android.domain.health.DiagnosticFinding
import com.obdvis.android.domain.health.FindingSeverity
import com.obdvis.android.domain.model.ConnectionState
import kotlinx.coroutines.launch

private val SENSOR_SECTIONS = listOf(
    "Engine"      to listOf("rpm", "load", "coolant", "oil_temp", "intake_temp", "run_time"),
    "Fuel"        to listOf("fuel_sys_status", "fuel", "stft", "ltft", "stft2", "ltft2", "maf", "manifold", "fuel_pressure", "fuel_rate"),
    "Performance" to listOf("speed", "throttle", "timing", "accel_pedal", "engine_torque", "driver_torque"),
    "Electrical"  to listOf("ecu_voltage", "obd_voltage"),
    "Emissions"   to listOf("o2_b1s1", "o2_b1s2", "catalyst_temp_b1s1", "catalyst_temp_b1s2", "egr", "egr_error", "evap_purge", "evap_pressure", "lambda", "ethanol"),
    "Environment" to listOf("baro", "ambient_temp"),
)

class AutoDashboardScreen(carContext: CarContext) : Screen(carContext) {

    private val sharedState = (carContext.applicationContext as OBDVisApp).sharedAutoState

    private var connectionState: ConnectionState = ConnectionState.Disconnected
    private var latestValues: Map<String, Float> = emptyMap()
    private var findings: List<DiagnosticFinding> = emptyList()
    private var activeTabId = TAB_OVERVIEW

    private val tabCallback = object : TabTemplate.TabCallback {
        override fun onTabSelected(tabContentId: String) {
            activeTabId = tabContentId
            invalidate()
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                lifecycleScope.launch { sharedState.connectionState.collect { connectionState = it; invalidate() } }
                lifecycleScope.launch {
                    sharedState.latestValues.collect {
                        latestValues = it
                        // Only redraw for Overview — rebuilding list templates resets scroll position
                        if (activeTabId == TAB_OVERVIEW) invalidate()
                    }
                }
                lifecycleScope.launch {
                    sharedState.healthFindings.collect {
                        findings = it
                        if (activeTabId == TAB_HEALTH) invalidate()
                    }
                }
            }
        })
    }

    override fun onGetTemplate(): Template {
        if (connectionState !is ConnectionState.Connected) {
            return MessageTemplate.Builder("Open OBDvis on your phone and connect to a vehicle.")
                .setTitle("OBDvis")
                .build()
        }

        val contentTemplate = when (activeTabId) {
            TAB_SENSORS -> buildSensorsTemplate()
            TAB_HEALTH  -> buildHealthTemplate()
            else        -> buildOverviewTemplate()
        }

        return TabTemplate.Builder(tabCallback)
            .setHeaderAction(Action.APP_ICON)
            .addTab(Tab.Builder()
                .setTitle("Overview")
                .setIcon(CarIcon.Builder(IconCompat.createWithBitmap(tabIconBitmap(COLOR_OVERVIEW))).build())
                .setContentId(TAB_OVERVIEW)
                .build())
            .addTab(Tab.Builder()
                .setTitle("Sensors")
                .setIcon(CarIcon.Builder(IconCompat.createWithBitmap(tabIconBitmap(COLOR_SENSORS))).build())
                .setContentId(TAB_SENSORS)
                .build())
            .addTab(Tab.Builder()
                .setTitle("Health")
                .setIcon(CarIcon.Builder(IconCompat.createWithBitmap(tabIconBitmap(COLOR_HEALTH))).build())
                .setContentId(TAB_HEALTH)
                .build())
            .setActiveTabContentId(activeTabId)
            .setTabContents(TabContents.Builder(contentTemplate).build())
            .build()
    }

    private fun buildOverviewTemplate(): GridTemplate {
        val voltage = latestValues["ecu_voltage"] ?: latestValues["obd_voltage"]
        val items = ItemList.Builder()
            .addItem(dialGridItem("Speed",   latestValues["speed"],   0f,   240f,  "km/h", COLOR_SPEED)   { "%.0f".format(it) })
            .addItem(dialGridItem("RPM",     latestValues["rpm"],     0f,   8000f, "rpm",  COLOR_RPM)     { "%.0f".format(it) })
            .addItem(dialGridItem("Coolant", latestValues["coolant"], -20f, 130f,  "°C",   COLOR_COOLANT) { "%.0f".format(it) })
            .addItem(dialGridItem("Voltage", voltage,                 10f,  16f,   "V",    COLOR_VOLTAGE) { "%.1f".format(it) })
            .build()
        return GridTemplate.Builder().setSingleList(items).build()
    }

    private fun buildSensorsTemplate(): ListTemplate {
        val builder = ListTemplate.Builder()
        var hasSections = false
        for ((sectionTitle, pidIds) in SENSOR_SECTIONS) {
            val rows = pidIds.mapNotNull { pidId ->
                val value = latestValues[pidId] ?: return@mapNotNull null
                val pid = PidRegistry.byId[pidId] ?: return@mapNotNull null
                Row.Builder()
                    .setTitle(pid.name)
                    .addText(formatSensorValue(pid.unit, value))
                    .build()
            }
            if (rows.isNotEmpty()) {
                val itemList = ItemList.Builder().apply { rows.forEach { addItem(it) } }.build()
                builder.addSectionedList(SectionedItemList.create(itemList, sectionTitle))
                hasSections = true
            }
        }
        if (!hasSections) {
            builder.setSingleList(ItemList.Builder().setNoItemsMessage("No sensor data available yet.").build())
        }
        return builder.build()
    }

    private fun buildHealthTemplate(): ListTemplate {
        val sortedFindings = findings.sortedByDescending { it.severity.ordinal }
        val itemList = ItemList.Builder().apply {
            if (sortedFindings.isEmpty()) {
                setNoItemsMessage("No issues detected.")
            } else {
                sortedFindings.forEach { finding ->
                    addItem(Row.Builder()
                        .setTitle("${finding.severity.badge()} ${finding.title}")
                        .addText(finding.description.take(120))
                        .setOnClickListener { screenManager.push(AutoFindingScreen(carContext, finding)) }
                        .build())
                }
            }
        }.build()
        return ListTemplate.Builder().setSingleList(itemList).build()
    }

    private fun dialGridItem(
        label: String,
        value: Float?,
        min: Float,
        max: Float,
        unit: String,
        accentColor: Int,
        format: (Float) -> String
    ): GridItem {
        val text = if (value != null) "${format(value)} $unit" else "—"
        val bitmap = drawDialBitmap(value, min, max, accentColor)
        return GridItem.Builder()
            .setTitle(label)
            .setText(text)
            .setImage(CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build())
            .build()
    }

    private fun tabIconBitmap(color: Int): Bitmap {
        val size = 64
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)
        return bitmap
    }

    private fun drawDialBitmap(value: Float?, min: Float, max: Float, accentColor: Int): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val strokeWidth = 16f
        val radius = cx - strokeWidth / 2f - 2f
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF3A3A3A.toInt()
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
        }
        val fgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor
            style = Paint.Style.STROKE
            this.strokeWidth = strokeWidth
            strokeCap = Paint.Cap.ROUND
        }
        val startAngle = 135f
        val totalSweep = 270f
        val rect = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        canvas.drawArc(rect, startAngle, totalSweep, false, bgPaint)
        if (value != null) {
            val fraction = ((value - min) / (max - min)).coerceIn(0f, 1f)
            if (fraction > 0f) {
                canvas.drawArc(rect, startAngle, fraction * totalSweep, false, fgPaint)
            }
        }
        return bitmap
    }

    private fun formatSensorValue(unit: String, value: Float): String = when (unit) {
        "rpm"  -> "%.0f rpm".format(value)
        "km/h" -> "%.0f km/h".format(value)
        "°C"   -> "%.0f °C".format(value)
        "V"    -> "%.2f V".format(value)
        "%"    -> "%.1f%%".format(value)
        "g/s"  -> "%.2f g/s".format(value)
        "kPa"  -> "%.0f kPa".format(value)
        "ms"   -> "%.1f ms".format(value)
        ""     -> "%.0f".format(value)
        else   -> "%.2f $unit".format(value)
    }

    companion object {
        private const val TAB_OVERVIEW = "overview"
        private const val TAB_SENSORS  = "sensors"
        private const val TAB_HEALTH   = "health"

        private val COLOR_SPEED    = 0xFF4FC3F7.toInt()
        private val COLOR_RPM      = 0xFFBA68C8.toInt()
        private val COLOR_COOLANT  = 0xFF81C784.toInt()
        private val COLOR_VOLTAGE  = 0xFFFFD54F.toInt()
        private val COLOR_OVERVIEW = 0xFF4FC3F7.toInt()
        private val COLOR_SENSORS  = 0xFF78909C.toInt()
        private val COLOR_HEALTH   = 0xFFEF9A9A.toInt()
    }
}

private fun FindingSeverity.badge(): String = when (this) {
    FindingSeverity.HIGH   -> "[HIGH]"
    FindingSeverity.MEDIUM -> "[MED]"
    FindingSeverity.LOW    -> "[LOW]"
    FindingSeverity.INFO   -> "[INFO]"
}
