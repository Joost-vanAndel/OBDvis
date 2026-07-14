package com.obdvis.android.domain

import androidx.compose.ui.graphics.Color
import com.obdvis.android.domain.model.PidDefinition

object PidRegistry {
    val all: List<PidDefinition> = listOf(
        PidDefinition(
            id = "fuel_sys_status", hex = "03", name = "Fuel System Status", unit = "",
            min = 0f, max = 16f,
            color = Color(0xFF80DEEA),
            formula = { b -> b[0].toFloat() },
        ),
        PidDefinition(
            id = "rpm", hex = "0C", name = "Engine RPM", unit = "rpm",
            min = 0f, max = 8000f,
            color = Color(0xFF4C9EFF),
            formula = { b -> ((b[0] * 256) + b[1]) / 4f },
        ),
        PidDefinition(
            id = "speed", hex = "0D", name = "Vehicle Speed", unit = "km/h",
            min = 0f, max = 250f,
            color = Color(0xFFF87C52),
            formula = { b -> b[0].toFloat() },
        ),
        PidDefinition(
            id = "coolant", hex = "05", name = "Coolant Temp", unit = "°C",
            min = -40f, max = 215f,
            color = Color(0xFF4CD97B),
            formula = { b -> b[0] - 40f },
        ),
        PidDefinition(
            id = "load", hex = "04", name = "Engine Load", unit = "%",
            min = 0f, max = 100f,
            color = Color(0xFFF5D547),
            formula = { b -> b[0] * 100f / 255f },
        ),
        PidDefinition(
            id = "throttle", hex = "11", name = "Throttle", unit = "%",
            min = 0f, max = 100f,
            color = Color(0xFFC97AFF),
            formula = { b -> b[0] * 100f / 255f },
        ),
        PidDefinition(
            id = "intake_temp", hex = "0F", name = "Intake Temp", unit = "°C",
            min = -40f, max = 215f,
            color = Color(0xFFFF6B9D),
            formula = { b -> b[0] - 40f },
        ),
        PidDefinition(
            id = "maf", hex = "10", name = "MAF Flow", unit = "g/s",
            min = 0f, max = 655f,
            color = Color(0xFF47E5E5),
            formula = { b -> ((b[0] * 256) + b[1]) / 100f },
        ),
        PidDefinition(
            id = "manifold", hex = "0B", name = "Manifold Pressure", unit = "kPa",
            min = 0f, max = 255f,
            color = Color(0xFFFF9F43),
            formula = { b -> b[0].toFloat() },
        ),
        PidDefinition(
            id = "fuel", hex = "2F", name = "Fuel Level", unit = "%",
            min = 0f, max = 100f,
            color = Color(0xFFA8E063),
            formula = { b -> b[0] * 100f / 255f },
        ),
        PidDefinition(
            id = "stft", hex = "06", name = "Short Fuel Trim", unit = "%",
            min = -25f, max = 25f,
            color = Color(0xFFFF6B6B),
            formula = { b -> (b[0] / 128f - 1f) * 100f },
        ),
        PidDefinition(
            id = "ltft", hex = "07", name = "Long Fuel Trim", unit = "%",
            min = -25f, max = 25f,
            color = Color(0xFF4ECDC4),
            formula = { b -> (b[0] / 128f - 1f) * 100f },
        ),
        PidDefinition(
            id = "stft2", hex = "08", name = "Short Fuel Trim B2", unit = "%",
            min = -25f, max = 25f,
            color = Color(0xFFFF8B94),
            formula = { b -> (b[0] / 128f - 1f) * 100f },
        ),
        PidDefinition(
            id = "ltft2", hex = "09", name = "Long Fuel Trim B2", unit = "%",
            min = -25f, max = 25f,
            color = Color(0xFF88D8B0),
            formula = { b -> (b[0] / 128f - 1f) * 100f },
        ),
        PidDefinition(
            id = "fuel_pressure", hex = "0A", name = "Fuel Pressure", unit = "kPa",
            min = 0f, max = 765f,
            color = Color(0xFFFFB347),
            formula = { b -> b[0] * 3f },
        ),
        PidDefinition(
            id = "timing", hex = "0E", name = "Timing Advance", unit = "°",
            min = -64f, max = 64f,
            color = Color(0xFF6BCB77),
            formula = { b -> b[0] / 2f - 64f },
        ),
        PidDefinition(
            id = "o2_b1s1", hex = "14", name = "O2 Sensor B1S1", unit = "V",
            min = 0f, max = 1.275f,
            color = Color(0xFFE2B4FF),
            formula = { b -> b[0] * 0.005f },
        ),
        PidDefinition(
            id = "o2_b1s2", hex = "15", name = "O2 Sensor B1S2", unit = "V",
            min = 0f, max = 1.275f,
            color = Color(0xFFC7B4E2),
            formula = { b -> b[0] * 0.005f },
        ),
        PidDefinition(
            id = "o2_b2s1", hex = "16", name = "O2 Sensor B2S1", unit = "V",
            min = 0f, max = 1.275f,
            color = Color(0xFFB4D4FF),
            formula = { b -> b[0] * 0.005f },
        ),
        PidDefinition(
            id = "o2_b2s2", hex = "17", name = "O2 Sensor B2S2", unit = "V",
            min = 0f, max = 1.275f,
            color = Color(0xFF9EC0E8),
            formula = { b -> b[0] * 0.005f },
        ),
        // Wideband (wide-range) O2 sensors — PID 0x24-0x27.
        // Bytes 0-1 = equivalence ratio λ (0-2 range); bytes 2-3 = voltage (unused here).
        // Vehicles with wideband upstream sensors respond to these instead of 0x14-0x15.
        PidDefinition(
            id = "afr_b1s1", hex = "24", name = "AFR Sensor B1S1", unit = "λ",
            min = 0f, max = 2f,
            color = Color(0xFFEFC8FF),
            formula = { b -> (b[0] * 256 + b[1]) / 32768f },
        ),
        PidDefinition(
            id = "afr_b1s2", hex = "25", name = "AFR Sensor B1S2", unit = "λ",
            min = 0f, max = 2f,
            color = Color(0xFFD9C8F0),
            formula = { b -> (b[0] * 256 + b[1]) / 32768f },
        ),
        PidDefinition(
            id = "afr_b2s1", hex = "26", name = "AFR Sensor B2S1", unit = "λ",
            min = 0f, max = 2f,
            color = Color(0xFFC8E4FF),
            formula = { b -> (b[0] * 256 + b[1]) / 32768f },
        ),
        PidDefinition(
            id = "afr_b2s2", hex = "27", name = "AFR Sensor B2S2", unit = "λ",
            min = 0f, max = 2f,
            color = Color(0xFFB8D8F0),
            formula = { b -> (b[0] * 256 + b[1]) / 32768f },
        ),
        // Wideband (current-type) O2 sensors — PID 0x34-0x37.
        // Same 4-byte layout as 0x24-0x27, but bytes 2-3 carry sensor current (mA) instead of
        // voltage. A vehicle implements one wideband PID group or the other, never both — these
        // are the current-type equivalents, exposed both as λ (for the same diagnostics as
        // afr_b1s1 etc.) and as raw current (informational; a shorted/open pump-cell circuit
        // shows up as implausible current even when the ratio bytes still look plausible).
        PidDefinition(
            id = "afr_i_b1s1", hex = "34", name = "AFR Sensor B1S1 (I-type)", unit = "λ",
            min = 0f, max = 2f,
            color = Color(0xFFF0D8FF),
            formula = { b -> (b[0] * 256 + b[1]) / 32768f },
        ),
        PidDefinition(
            id = "afr_i_b1s2", hex = "35", name = "AFR Sensor B1S2 (I-type)", unit = "λ",
            min = 0f, max = 2f,
            color = Color(0xFFE4D4F5),
            formula = { b -> (b[0] * 256 + b[1]) / 32768f },
        ),
        PidDefinition(
            id = "afr_i_b2s1", hex = "36", name = "AFR Sensor B2S1 (I-type)", unit = "λ",
            min = 0f, max = 2f,
            color = Color(0xFFD4E8FF),
            formula = { b -> (b[0] * 256 + b[1]) / 32768f },
        ),
        PidDefinition(
            id = "afr_i_b2s2", hex = "37", name = "AFR Sensor B2S2 (I-type)", unit = "λ",
            min = 0f, max = 2f,
            color = Color(0xFFC4D8F5),
            formula = { b -> (b[0] * 256 + b[1]) / 32768f },
        ),
        PidDefinition(
            id = "afr_ma_b1s1", hex = "34", name = "AFR Current B1S1", unit = "mA",
            min = -128f, max = 128f,
            color = Color(0xFFFFB3A7),
            formula = { b -> (b[2] * 256 + b[3]) / 256f - 128f },
        ),
        PidDefinition(
            id = "afr_ma_b1s2", hex = "35", name = "AFR Current B1S2", unit = "mA",
            min = -128f, max = 128f,
            color = Color(0xFFFFA396),
            formula = { b -> (b[2] * 256 + b[3]) / 256f - 128f },
        ),
        PidDefinition(
            id = "afr_ma_b2s1", hex = "36", name = "AFR Current B2S1", unit = "mA",
            min = -128f, max = 128f,
            color = Color(0xFFFF9385),
            formula = { b -> (b[2] * 256 + b[3]) / 256f - 128f },
        ),
        PidDefinition(
            id = "afr_ma_b2s2", hex = "37", name = "AFR Current B2S2", unit = "mA",
            min = -128f, max = 128f,
            color = Color(0xFFFF8374),
            formula = { b -> (b[2] * 256 + b[3]) / 256f - 128f },
        ),
        PidDefinition(
            id = "run_time", hex = "1F", name = "Engine Run Time", unit = "s",
            min = 0f, max = 65535f,
            color = Color(0xFF4D96FF),
            formula = { b -> (b[0] * 256 + b[1]).toFloat() },
        ),
        PidDefinition(
            id = "dist_cleared", hex = "31", name = "Dist Since Cleared", unit = "km",
            min = 0f, max = 65535f,
            color = Color(0xFFCED4DA),
            formula = { b -> (b[0] * 256 + b[1]).toFloat() },
        ),
        PidDefinition(
            id = "baro", hex = "33", name = "Baro Pressure", unit = "kPa",
            min = 0f, max = 255f,
            color = Color(0xFFB5EAD7),
            formula = { b -> b[0].toFloat() },
        ),
        PidDefinition(
            id = "abs_load", hex = "43", name = "Absolute Load", unit = "%",
            min = 0f, max = 25700f,
            color = Color(0xFF98D8C8),
            formula = { b -> (b[0] * 256 + b[1]) * 100f / 255f },
        ),
        PidDefinition(
            id = "rel_throttle", hex = "45", name = "Rel Throttle Pos", unit = "%",
            min = 0f, max = 100f,
            color = Color(0xFFDDA0DD),
            formula = { b -> b[0] * 100f / 255f },
        ),
        PidDefinition(
            id = "ambient_temp", hex = "46", name = "Ambient Air Temp", unit = "°C",
            min = -40f, max = 215f,
            color = Color(0xFFFFDAC1),
            formula = { b -> b[0] - 40f },
        ),
        PidDefinition(
            id = "mil_time", hex = "4D", name = "MIL Run Time", unit = "min",
            min = 0f, max = 65535f,
            color = Color(0xFFADB5BD),
            formula = { b -> (b[0] * 256 + b[1]).toFloat() },
        ),
        PidDefinition(
            id = "oil_temp", hex = "5C", name = "Engine Oil Temp", unit = "°C",
            min = -40f, max = 215f,
            color = Color(0xFFD4A373),
            formula = { b -> b[0] - 40f },
        ),
        PidDefinition(
            id = "fuel_rate", hex = "5E", name = "Fuel Rate", unit = "L/h",
            min = 0f, max = 3276.75f,
            color = Color(0xFFF4A261),
            formula = { b -> (b[0] * 256 + b[1]) / 20f },
        ),
        PidDefinition(
            id = "engine_torque", hex = "62", name = "Engine Torque", unit = "%",
            min = -125f, max = 130f,
            color = Color(0xFFFF7F7F),
            formula = { b -> b[0] - 125f },
        ),
        PidDefinition(
            id = "ecu_voltage", hex = "42", name = "ECU Supply Voltage", unit = "V",
            min = 6f, max = 16f,
            color = Color(0xFFFFD93D),
            formula = { b -> (b[0] * 256 + b[1]) / 1000f },
        ),
        PidDefinition(
            id = "obd_voltage", hex = null, name = "OBD Port Voltage", unit = "V",
            min = 6f, max = 16f,
            color = Color(0xFFFFE97A),
            formula = { _ -> 0f },
        ),
        PidDefinition(
            id = "catalyst_temp_b1s1", hex = "3C", name = "Catalyst Temp B1S1", unit = "°C",
            min = -40f, max = 1000f,
            color = Color(0xFFFF6347),
            formula = { b -> (b[0] * 256 + b[1]) / 10f - 40f },
        ),
        PidDefinition(
            id = "catalyst_temp_b1s2", hex = "3E", name = "Catalyst Temp B1S2", unit = "°C",
            min = -40f, max = 1000f,
            color = Color(0xFFFF7043),
            formula = { b -> (b[0] * 256 + b[1]) / 10f - 40f },
        ),
        PidDefinition(
            id = "ref_torque", hex = "63", name = "Reference Torque", unit = "Nm",
            min = 0f, max = 65535f,
            color = Color(0xFF26C6DA),
            formula = { b -> (b[0] * 256 + b[1]).toFloat() },
        ),
        PidDefinition(
            id = "dist_mil", hex = "21", name = "Dist with MIL On", unit = "km",
            min = 0f, max = 65535f,
            color = Color(0xFFEF9A9A),
            formula = { b -> (b[0] * 256 + b[1]).toFloat() },
        ),
        PidDefinition(
            id = "time_cleared", hex = "4E", name = "Time Since Cleared", unit = "min",
            min = 0f, max = 65535f,
            color = Color(0xFF90CAF9),
            formula = { b -> (b[0] * 256 + b[1]).toFloat() },
        ),
        PidDefinition(
            id = "warmups_cleared", hex = "30", name = "Warm-ups Since Cleared", unit = "",
            min = 0f, max = 255f,
            color = Color(0xFFCE93D8),
            formula = { b -> b[0].toFloat() },
        ),
        PidDefinition(
            id = "fuel_rail_pressure", hex = "22", name = "Fuel Rail Pressure", unit = "kPa",
            min = 0f, max = 5177f,
            color = Color(0xFFFFCA28),
            formula = { b -> (b[0] * 256 + b[1]) * 0.079f },
        ),
        PidDefinition(
            id = "fuel_rail_pressure_gdi", hex = "23", name = "Fuel Rail Pressure (GDI)", unit = "kPa",
            min = 0f, max = 655350f,
            color = Color(0xFFFFB300),
            formula = { b -> (b[0] * 256 + b[1]) * 10f },
        ),
        PidDefinition(
            id = "egr", hex = "2C", name = "Commanded EGR", unit = "%",
            min = 0f, max = 100f,
            color = Color(0xFFA5D6A7),
            formula = { b -> b[0] * 100f / 255f },
        ),
        PidDefinition(
            id = "ethanol", hex = "52", name = "Ethanol %", unit = "%",
            min = 0f, max = 100f,
            color = Color(0xFFD4E157),
            formula = { b -> b[0] * 100f / 255f },
        ),
        PidDefinition(
            id = "accel_pedal", hex = "5A", name = "Accel Pedal Pos", unit = "%",
            min = 0f, max = 100f,
            color = Color(0xFFFFAB40),
            formula = { b -> b[0] * 100f / 255f },
        ),
        PidDefinition(
            id = "egr_error", hex = "2D", name = "EGR Error", unit = "%",
            min = -100f, max = 100f,
            color = Color(0xFF81C784),
            formula = { b -> (b[0] / 128f - 1f) * 100f },
        ),
        PidDefinition(
            id = "lambda", hex = "44", name = "Commanded Lambda", unit = "λ",
            min = 0f, max = 2f,
            color = Color(0xFF4DB6AC),
            formula = { b -> (b[0] * 256 + b[1]) / 32768f },
        ),
        PidDefinition(
            id = "driver_torque", hex = "61", name = "Driver Demand Torque", unit = "%",
            min = -125f, max = 130f,
            color = Color(0xFFE57373),
            formula = { b -> b[0] - 125f },
        ),
        PidDefinition(
            id = "evap_purge", hex = "2E", name = "Commanded Evap Purge", unit = "%",
            min = 0f, max = 100f,
            color = Color(0xFF80CBC4),
            formula = { b -> b[0] * 100f / 255f },
        ),
        PidDefinition(
            id = "evap_pressure", hex = "32", name = "Evap Vapor Pressure", unit = "Pa",
            min = -8192f, max = 8192f,
            color = Color(0xFFBA68C8),
            formula = { b -> (((b[0].toInt() shl 8) or (b[1].toInt() and 0xFF)).toShort().toFloat()) / 4f },
        ),
        PidDefinition(
            id = "o2_present", hex = "13", name = "O2 Sensors Present", unit = "",
            min = 0f, max = 255f,
            color = Color(0xFF90A4AE),
            formula = { b -> b[0].toFloat() },
        ),
        // PID 0x01 bytes C+D packed: high byte = availability bitmask, low byte = incomplete bitmask.
        // Bits (same position in both bytes): 0=catalyst, 1=heated catalyst, 2=evap, 3=secondary air,
        //   4=A/C, 5=O2 sensor, 6=O2 heater, 7=EGR
        PidDefinition(
            id = "monitor_readiness", hex = "01", name = "Monitor Readiness", unit = "",
            min = 0f, max = 65535f,
            color = Color(0xFFFFB74D),
            formula = { b -> ((b[2].toInt() and 0xFF) * 256 + (b[3].toInt() and 0xFF)).toFloat() },
            displayInLive = false,
        ),
    )

    val live: List<PidDefinition> = all.filter { it.displayInLive }
    val byId: Map<String, PidDefinition> = all.associateBy { it.id }
    val byHex: Map<String, PidDefinition> = all.filter { it.hex != null }.associateBy { it.hex!!.uppercase() }

    // PIDs actually consumed by VehicleState / DiagnosticsInterpreter.
    // Excludes ref_torque, dist_mil, time_cleared, warmups_cleared,
    // fuel_rail_pressure, fuel_rail_pressure_gdi, accel_pedal, ethanol.
    private val HEALTH_IDS = setOf(
        "fuel_sys_status", "rpm", "speed", "coolant", "load", "throttle",
        "intake_temp", "maf", "manifold", "fuel", "stft", "ltft", "stft2", "ltft2",
        "fuel_pressure", "timing", "o2_b1s1", "o2_b1s2", "o2_b2s1", "o2_b2s2",
        "afr_b1s1", "afr_b1s2", "afr_b2s1", "afr_b2s2",
        "afr_i_b1s1", "afr_i_b1s2", "afr_i_b2s1", "afr_i_b2s2",
        "run_time", "dist_cleared", "baro", "abs_load", "rel_throttle",
        "ambient_temp", "mil_time", "oil_temp", "fuel_rate", "engine_torque",
        "ecu_voltage", "obd_voltage",
        "catalyst_temp_b1s1", "catalyst_temp_b1s2",
        "egr", "egr_error", "lambda", "driver_torque",
        "evap_purge", "evap_pressure", "o2_present", "monitor_readiness",
    )

    val health: List<PidDefinition> = all.filter { it.id in HEALTH_IDS }
}
