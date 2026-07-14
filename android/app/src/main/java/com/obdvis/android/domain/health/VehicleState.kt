package com.obdvis.android.domain.health

import com.obdvis.android.domain.polling.PidPriorityGroups
import com.obdvis.android.domain.store.SampleStore

enum class MonitorReadinessState { COMPLETE, INCOMPLETE, NOT_SUPPORTED }

data class MonitorStatus(val name: String, val state: MonitorReadinessState)

/**
 * Normalized snapshot of vehicle sensor state, independent of raw ELM327 strings.
 * Built from the latest PID values collected by the polling loop.
 *
 * All fields are nullable — a null means the sensor was not available or not yet received.
 */
data class VehicleState(
    val timestamp: Long,
    val rpm: Float?,
    val speedKph: Float?,
    val coolantTempC: Float?,
    val intakeTempC: Float?,
    val mafGps: Float?,
    val mapKpa: Float?,
    val throttlePct: Float?,
    val engineLoadPct: Float?,
    val fuelLevelPct: Float?,
    val stftBank1Pct: Float?,
    val ltftBank1Pct: Float?,
    val stftBank2Pct: Float? = null,
    val ltftBank2Pct: Float? = null,
    val fuelPressureKpa: Float? = null,
    val timingAdvanceDeg: Float? = null,
    val o2Bank1Sensor1V: Float? = null,
    val o2Bank1Sensor2V: Float? = null,
    val o2Bank2Sensor1V: Float? = null,
    val o2Bank2Sensor2V: Float? = null,
    /** Wideband AFR sensors (PID 0x24-0x27), λ units. Present on vehicles where the upstream sensor
     *  is a UEGO/wideband type that does not respond to the narrowband PID 0x14. */
    val afrBank1Sensor1Lambda: Float? = null,
    val afrBank1Sensor2Lambda: Float? = null,
    val afrBank2Sensor1Lambda: Float? = null,
    val afrBank2Sensor2Lambda: Float? = null,
    /** Wideband AFR sensors, current-type variant (PID 0x34-0x37), λ units. Vehicles implement
     *  either this group or 0x24-0x27, never both — DiagnosticsInterpreter merges the two. */
    val afrCurBank1Sensor1Lambda: Float? = null,
    val afrCurBank1Sensor2Lambda: Float? = null,
    val afrCurBank2Sensor1Lambda: Float? = null,
    val afrCurBank2Sensor2Lambda: Float? = null,
    val engineRunTimeSec: Float? = null,
    val distSinceCodesClearedKm: Float? = null,
    val baroKpa: Float? = null,
    val absLoadPct: Float? = null,
    val relThrottlePct: Float? = null,
    val ambientTempC: Float? = null,
    val milRunTimeMin: Float? = null,
    val oilTempC: Float? = null,
    val fuelRateLph: Float? = null,
    val engineTorquePct: Float? = null,
    val batteryVoltage: Float? = null,
    val obdVoltage: Float? = null,
    val ecuVoltage: Float? = null,
    val activeDtcs: List<String> = emptyList(),
    /** Raw bitmask from PID 0x03: 1=OL-cold, 2=CL, 4=OL-load/decel, 8=OL-fault, 16=CL-fault */
    val fuelSystemStatusRaw: Float? = null,
    val egrPct: Float? = null,
    val egrErrorPct: Float? = null,
    val commandedLambda: Float? = null,
    val driverDemandTorquePct: Float? = null,
    val evapPurgePct: Float? = null,
    /** Evap system vapor pressure in Pa (signed); negative = vacuum held, near zero = possible leak */
    val evapPressurePa: Float? = null,
    /** Raw bitmask from PID 0x13: bit0=B1S1, bit1=B1S2, bit4=B2S1, bit5=B2S2, etc. */
    val o2SensorsPresentRaw: Float? = null,
    /** PID 0x01 bytes C+D packed: high byte = availability mask, low byte = incomplete mask. */
    val monitorReadinessPacked: Float? = null,
) {
    /** True when RPM data is available and above cranking threshold. */
    val engineOn: Boolean get() = (rpm ?: 0f) > 100f

    /** Null when PID 0x03 unavailable. True = closed loop (bit 1 set). */
    val isClosedLoop: Boolean? get() = fuelSystemStatusRaw?.let { (it.toInt() and 0x02) != 0 }

    /** True when open loop due to a system failure (bit 3). */
    val isOpenLoopFault: Boolean get() = (fuelSystemStatusRaw?.toInt() ?: 0) and 0x08 != 0

    /** True when closed loop but feedback system has a fault (bit 4). */
    val isClosedLoopFault: Boolean get() = (fuelSystemStatusRaw?.toInt() ?: 0) and 0x10 != 0

    val hasUpstreamO2B1: Boolean get() = (o2SensorsPresentRaw?.toInt() ?: 0x01) and 0x01 != 0
    val hasDownstreamO2B1: Boolean get() = (o2SensorsPresentRaw?.toInt() ?: 0x02) and 0x02 != 0
    val hasWidebandUpstreamB1: Boolean get() = afrBank1Sensor1Lambda != null || afrCurBank1Sensor1Lambda != null
    // Default to absent for Bank 2 — only enable if PID 0x13 explicitly reports these bits
    val hasUpstreamO2B2: Boolean get() = (o2SensorsPresentRaw?.toInt() ?: 0) and 0x10 != 0
    val hasDownstreamO2B2: Boolean get() = (o2SensorsPresentRaw?.toInt() ?: 0) and 0x20 != 0

    /** Per-monitor readiness status decoded from PID 0x01 bytes C+D. Null when PID unavailable. */
    val monitorStatuses: List<MonitorStatus>? get() {
        val packed = monitorReadinessPacked?.toInt() ?: return null
        val available = (packed shr 8) and 0xFF
        val incomplete = packed and 0xFF
        return MONITOR_BITS.map { (bit, name) ->
            val state = when {
                available and bit == 0  -> MonitorReadinessState.NOT_SUPPORTED
                incomplete and bit != 0 -> MonitorReadinessState.INCOMPLETE
                else                    -> MonitorReadinessState.COMPLETE
            }
            MonitorStatus(name, state)
        }
    }

    /** Names of supported monitors that have not yet completed. */
    val incompleteMonitorNames: List<String> get() =
        monitorStatuses?.filter { it.state == MonitorReadinessState.INCOMPLETE }
            ?.map { it.name } ?: emptyList()

    companion object {
        private val MONITOR_BITS = listOf(
            0x01 to "Catalyst",
            0x02 to "Heated Catalyst",
            0x04 to "Evap System",
            0x08 to "Secondary Air",
            0x10 to "A/C System",
            0x20 to "O2 Sensor",
            0x40 to "O2 Heater",
            0x80 to "EGR System",
        )

        /**
         * Builds a [VehicleState] from a [SampleStore], applying per-signal freshness limits.
         * A PID whose most recent reading is older than its max age becomes null rather than
         * silently contributing a stale value to diagnostics.
         */
        fun fromSampleStore(store: SampleStore): VehicleState {
            fun fresh(pidId: String) = store.latestFreshValue(pidId, PidPriorityGroups.maxAgeMs(pidId))
            fun slow(pidId: String) = store.latestFreshValue(pidId, maxAgeMs = 60_000L)
            return VehicleState(
                timestamp                = System.currentTimeMillis(),
                rpm                      = fresh("rpm"),
                speedKph                 = fresh("speed"),
                coolantTempC             = fresh("coolant"),
                intakeTempC              = fresh("intake_temp"),
                mafGps                   = fresh("maf"),
                mapKpa                   = fresh("manifold"),
                throttlePct              = fresh("throttle"),
                engineLoadPct            = fresh("load"),
                fuelLevelPct             = slow("fuel"),
                stftBank1Pct             = fresh("stft"),
                ltftBank1Pct             = fresh("ltft"),
                stftBank2Pct             = fresh("stft2"),
                ltftBank2Pct             = fresh("ltft2"),
                fuelPressureKpa          = fresh("fuel_pressure"),
                timingAdvanceDeg         = fresh("timing"),
                o2Bank1Sensor1V          = fresh("o2_b1s1"),
                o2Bank1Sensor2V          = fresh("o2_b1s2"),
                o2Bank2Sensor1V          = fresh("o2_b2s1"),
                o2Bank2Sensor2V          = fresh("o2_b2s2"),
                afrBank1Sensor1Lambda    = fresh("afr_b1s1"),
                afrBank1Sensor2Lambda    = fresh("afr_b1s2"),
                afrBank2Sensor1Lambda    = fresh("afr_b2s1"),
                afrBank2Sensor2Lambda    = fresh("afr_b2s2"),
                afrCurBank1Sensor1Lambda = fresh("afr_i_b1s1"),
                afrCurBank1Sensor2Lambda = fresh("afr_i_b1s2"),
                afrCurBank2Sensor1Lambda = fresh("afr_i_b2s1"),
                afrCurBank2Sensor2Lambda = fresh("afr_i_b2s2"),
                engineRunTimeSec         = slow("run_time"),
                distSinceCodesClearedKm  = slow("dist_cleared"),
                baroKpa                  = slow("baro"),
                absLoadPct               = fresh("abs_load"),
                relThrottlePct           = fresh("rel_throttle"),
                ambientTempC             = slow("ambient_temp"),
                milRunTimeMin            = slow("mil_time"),
                oilTempC                 = fresh("oil_temp"),
                fuelRateLph              = fresh("fuel_rate"),
                engineTorquePct          = fresh("engine_torque"),
                batteryVoltage           = fresh("obd_voltage") ?: fresh("ecu_voltage"),
                obdVoltage               = fresh("obd_voltage"),
                ecuVoltage               = fresh("ecu_voltage"),
                fuelSystemStatusRaw      = fresh("fuel_sys_status"),
                egrPct                   = fresh("egr"),
                egrErrorPct              = fresh("egr_error"),
                commandedLambda          = fresh("lambda"),
                driverDemandTorquePct    = fresh("driver_torque"),
                evapPurgePct             = fresh("evap_purge"),
                evapPressurePa           = fresh("evap_pressure"),
                o2SensorsPresentRaw      = slow("o2_present"),
                monitorReadinessPacked   = slow("monitor_readiness"),
            )
        }

        fun fromLatestValues(values: Map<String, Float>): VehicleState = VehicleState(
            timestamp                = System.currentTimeMillis(),
            rpm                      = values["rpm"],
            speedKph                 = values["speed"],
            coolantTempC             = values["coolant"],
            intakeTempC              = values["intake_temp"],
            mafGps                   = values["maf"],
            mapKpa                   = values["manifold"],
            throttlePct              = values["throttle"],
            engineLoadPct            = values["load"],
            fuelLevelPct             = values["fuel"],
            stftBank1Pct             = values["stft"],
            ltftBank1Pct             = values["ltft"],
            stftBank2Pct             = values["stft2"],
            ltftBank2Pct             = values["ltft2"],
            fuelPressureKpa          = values["fuel_pressure"],
            timingAdvanceDeg         = values["timing"],
            o2Bank1Sensor1V          = values["o2_b1s1"],
            o2Bank1Sensor2V          = values["o2_b1s2"],
            o2Bank2Sensor1V          = values["o2_b2s1"],
            o2Bank2Sensor2V          = values["o2_b2s2"],
            afrBank1Sensor1Lambda    = values["afr_b1s1"],
            afrBank1Sensor2Lambda    = values["afr_b1s2"],
            afrBank2Sensor1Lambda    = values["afr_b2s1"],
            afrBank2Sensor2Lambda    = values["afr_b2s2"],
            afrCurBank1Sensor1Lambda = values["afr_i_b1s1"],
            afrCurBank1Sensor2Lambda = values["afr_i_b1s2"],
            afrCurBank2Sensor1Lambda = values["afr_i_b2s1"],
            afrCurBank2Sensor2Lambda = values["afr_i_b2s2"],
            engineRunTimeSec         = values["run_time"],
            distSinceCodesClearedKm  = values["dist_cleared"],
            baroKpa                  = values["baro"],
            absLoadPct               = values["abs_load"],
            relThrottlePct           = values["rel_throttle"],
            ambientTempC             = values["ambient_temp"],
            milRunTimeMin            = values["mil_time"],
            oilTempC                 = values["oil_temp"],
            fuelRateLph              = values["fuel_rate"],
            engineTorquePct          = values["engine_torque"],
            batteryVoltage           = values["obd_voltage"] ?: values["ecu_voltage"],
            obdVoltage               = values["obd_voltage"],
            ecuVoltage               = values["ecu_voltage"],
            fuelSystemStatusRaw      = values["fuel_sys_status"],
            egrPct                   = values["egr"],
            egrErrorPct              = values["egr_error"],
            commandedLambda          = values["lambda"],
            driverDemandTorquePct    = values["driver_torque"],
            evapPurgePct             = values["evap_purge"],
            evapPressurePa           = values["evap_pressure"],
            o2SensorsPresentRaw      = values["o2_present"],
            monitorReadinessPacked    = values["monitor_readiness"],
        )
    }
}
