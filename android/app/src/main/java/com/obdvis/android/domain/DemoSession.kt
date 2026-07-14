package com.obdvis.android.domain

import com.obdvis.android.domain.health.DtcReadResult
import com.obdvis.android.domain.model.PidDefinition
import com.obdvis.android.domain.model.SensorSample
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Simulates an OBD session without any hardware.
 *
 * Generates a 60-second repeating driving cycle:
 *   0–8 s   idle
 *   8–22 s  WOT acceleration (RPM builds with inertia toward redline)
 *   22–38 s cruise
 *   38–52 s deceleration
 *   52–60 s idle
 *
 * Coolant temperature ramps from 20 °C to ~92 °C over the first ~90 s.
 */
class DemoSession(
    /** Optional simulated DTCs — useful for exercising the health/diagnostic UI. */
    private val simulatedDtcs: List<String> = emptyList(),
    private val enabledPids: () -> List<PidDefinition>,
) : Session {
    private val random = Random(seed = 42)
    // Mutable so clearDtcs() takes effect immediately
    private val currentDtcs = simulatedDtcs.toMutableList()
    private val startMs = System.currentTimeMillis()
    @Volatile private var currentSpeedKmh = 0f
    @Volatile private var currentRpm = 850f

    override suspend fun readDtcs(): DtcReadResult = DtcReadResult(stored = currentDtcs.toList())

    override suspend fun clearDtcs(): Boolean {
        currentDtcs.clear()
        return true
    }

    override suspend fun pollSnapshot(): Map<String, Float> {
        val elapsed = (System.currentTimeMillis() - startMs) / 1000f
        return simulate(elapsed, currentSpeedKmh, currentRpm)
    }

    override fun readings(): Flow<SensorSample> = flow {
        while (currentCoroutineContext().isActive) {
            val pids = enabledPids()

            if (pids.isEmpty()) {
                delay(200)
                continue
            }

            val elapsed = (System.currentTimeMillis() - startMs) / 1000f
            val values = simulate(elapsed, currentSpeedKmh, currentRpm)
            currentSpeedKmh = values["speed"] ?: currentSpeedKmh
            currentRpm      = values["rpm"]   ?: currentRpm

            for (pid in pids) {
                if (!currentCoroutineContext().isActive) break
                val value = values[pid.id] ?: continue
                val now = System.currentTimeMillis()
                emit(SensorSample(pidId = pid.id, elapsedSeconds = elapsed, value = value,
                    timestampMs = now, latencyMs = 80))
                delay(80)  // ~10 Hz per PID, mirroring real ELM polling
            }
        }
    }

    private fun simulate(t: Float, prevSpeed: Float, prevRpm: Float): Map<String, Float> {
        val cycle = t % 60.0

        // Throttle position drives the rest of the simulation
        val throttlePct = when {
            cycle < 8  -> 5f
            cycle < 22 -> 100f
            cycle < 38 -> 28f
            cycle < 52 -> 28f * ((52 - cycle) / 14f).toFloat()
            else       -> 5f
        }.coerceIn(2f, 100f) + noise(2f)

        // RPM target: idle at ~850, WOT pulls toward ~6500, cruise/decel follow throttle
        val targetRpm = when {
            throttlePct > 90f -> 6500f
            else              -> 850f + throttlePct * 38f
        }
        // Asymmetric inertia: RPM builds slowly (engine/drivetrain mass), drops faster (engine brake)
        val rpmAlpha = if (targetRpm > prevRpm) 0.12f else 0.25f
        val rpm = (prevRpm * (1f - rpmAlpha) + targetRpm * rpmAlpha + noise(50f))
            .coerceIn(650f, 8000f)

        // Speed: lags RPM via exponential smoothing
        val targetSpeed = ((rpm - 850f) / 44f).coerceIn(0f, 130f)
        val speedKmh = (prevSpeed * 0.88f + targetSpeed * 0.12f + noise(0.8f)).coerceIn(0f, 250f)

        // Engine load: driven by throttle and rpm
        val load = (throttlePct * 0.75f + rpm / 6500f * 25f + noise(2f)).coerceIn(0f, 100f)

        // Coolant: cold start, warms to 92 °C over ~90 s
        val coolant = (20f + 72f * (1f - exp(-t / 90f)) + noise(0.4f)).coerceIn(-40f, 215f)

        // Intake temp: ambient + slight engine heat coupling
        val intakeTemp = (26f + coolant * 0.04f + noise(0.5f)).coerceIn(-40f, 215f)

        // MAF: proportional to rpm × load
        val maf = (rpm / 1000f * (load / 100f) * 7f + noise(0.4f)).coerceIn(0f, 655f)

        // Manifold pressure: low (vacuum) at idle, rises under load
        val manifold = (28f + throttlePct * 1.1f + noise(2f)).coerceIn(0f, 255f)

        // Fuel level: very slow drain
        val fuel = (65f - t * 0.008f).coerceIn(0f, 100f)

        // Monitor readiness: all 8 monitors supported (0xFF available), catalyst + evap incomplete (0x05)
        val monitorReadiness = (0xFF * 256 + 0x05).toFloat()

        // Fuel trims: bank 1 has a sustained lean bias (~11%) to trigger a lean warning and
        // inter-bank imbalance finding; bank 2 stays mild so the asymmetry is detectable.
        val ltft  = (11f * (1f - exp(-t / 40f)) + noise(0.5f)).coerceIn(-25f, 25f)
        val stft  = (sin(t * 0.28f) * 3.5f + noise(0.6f)).coerceIn(-25f, 25f)
        val ltft2 = (2f  * (1f - exp(-t / 70f)) + noise(0.3f)).coerceIn(-25f, 25f)
        val stft2 = (sin(t * 0.28f + 1.2f) * 2.8f + noise(0.5f)).coerceIn(-25f, 25f)

        // Charging voltage: slightly below the 13.0 V threshold to simulate a weak alternator.
        // The interpreter requires 15 s of continuous low readings (pending period) before the
        // finding becomes active.
        val obdVoltage = (12.2f + noise(0.15f)).coerceIn(10f, 15f)

        return mapOf(
            "rpm"               to rpm,
            "speed"             to speedKmh,
            "coolant"           to coolant,
            "load"              to load,
            "throttle"          to throttlePct.coerceIn(0f, 100f),
            "intake_temp"       to intakeTemp,
            "maf"               to maf,
            "manifold"          to manifold,
            "fuel"              to fuel,
            "monitor_readiness" to monitorReadiness,
            "stft"              to stft,
            "ltft"              to ltft,
            "stft2"             to stft2,
            "ltft2"             to ltft2,
            "obd_voltage"       to obdVoltage,
        )
    }

    private fun noise(magnitude: Float): Float =
        (random.nextFloat() * 2f - 1f) * magnitude
}
