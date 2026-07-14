package com.obdvis.android.domain

import com.obdvis.android.data.bluetooth.BluetoothSocketStream
import com.obdvis.android.data.obd.DtcParser
import com.obdvis.android.data.obd.ElmCommandSender
import com.obdvis.android.domain.health.DtcReadResult
import com.obdvis.android.data.obd.ElmInitializer
import com.obdvis.android.data.obd.ObdResponseParser
import com.obdvis.android.domain.model.PidDefinition
import com.obdvis.android.domain.model.SensorSample
import com.obdvis.android.domain.polling.PriorityPollScheduler
import com.obdvis.android.domain.store.SampleStore
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages an ELM327 OBD session on [stream].
 *
 * [enabledPids] and [weightFn] are called each polling cycle so tab switches
 * take effect immediately without restarting the session.
 *
 * [sampleStore] is shared with the ViewModel so the priority scheduler can make
 * freshness-aware decisions without a separate internal store.
 */
class OBDSession(
    private val stream: BluetoothSocketStream,
    private val enabledPids: () -> List<PidDefinition>,
    private val weightFn: () -> (String) -> Float,
    private val sampleStore: SampleStore,
) : Session {
    private val sender = ElmCommandSender(stream)
    private val parser = ObdResponseParser()
    // Serializes all ELM327 send/receive calls so the polling loop and one-shot
    // commands (readDtcs, clearDtcs, pollSnapshot) never interleave on the same socket.
    private val mutex = Mutex()
    private val scheduler = PriorityPollScheduler(sampleStore)
    // Set once during readings() initialisation; used by pollSnapshot().
    @Volatile private var supportedPids: List<PidDefinition> = emptyList()

    /**
     * Initializes the ELM327 then continuously polls enabled PIDs,
     * emitting a [SensorSample] for each successful reading.
     *
     * Must be collected on Dispatchers.IO (the ELM327 reads are blocking).
     * Cancel the collecting coroutine to stop polling.
     */
    override fun readings(): Flow<SensorSample> = flow {
        val supportedHexes = ElmInitializer(sender).initialize()
        // Exclude null-hex (AT-command) PIDs from OBD discovery; all OBD PIDs allowed if discovery failed.
        val obdPids = PidRegistry.all.filter { it.hex != null }
        val allSupported = obdPids
            .let { all -> if (supportedHexes.isEmpty()) all else all.filter { it.hex!!.uppercase() in supportedHexes } }
        supportedPids = allSupported   // expose for pollSnapshot()
        val supportedIds = allSupported.map { it.id }.toSet()

        val sessionStartMs = System.currentTimeMillis()
        var lastVoltagePollMs = 0L
        var consecutiveFailures = 0

        while (currentCoroutineContext().isActive) {
            // Re-evaluate each iteration so tab switches take effect immediately.
            val pids = enabledPids().filter { it.id in supportedIds }

            // Interleave ATRV voltage reads every VOLTAGE_POLL_INTERVAL_MS.
            // Track the last *attempt* time so a failed read doesn't cause a retry spin.
            val voltageAge = System.currentTimeMillis() - lastVoltagePollMs
            if (voltageAge > VOLTAGE_POLL_INTERVAL_MS) {
                lastVoltagePollMs = System.currentTimeMillis()
                if (!currentCoroutineContext().isActive) break
                try {
                    val readStart = System.currentTimeMillis()
                    val (response, elapsed) = mutex.withLock {
                        val r = sender.send("ATRV\r", timeoutMs = 600)
                        val e = (System.currentTimeMillis() - sessionStartMs) / 1000f
                        r to e
                    }
                    consecutiveFailures = 0
                    val latency = System.currentTimeMillis() - readStart
                    val voltage = parseAtrvVoltage(response)
                    if (voltage != null) {
                        emit(SensorSample(pidId = "obd_voltage", elapsedSeconds = elapsed, value = voltage,
                            timestampMs = System.currentTimeMillis(), latencyMs = latency))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        stream.close()
                        throw IOException("ELM327 unresponsive: $MAX_CONSECUTIVE_FAILURES consecutive command failures")
                    }
                }
                continue
            }

            if (pids.isEmpty()) { delay(200); continue }

            val pid = scheduler.nextPid(pids, weightFn())
            if (pid == null) { delay(200); continue }
            try {
                val readStart = System.currentTimeMillis()
                val (response, elapsed) = mutex.withLock {
                    val r = sender.send("01${pid.hex}\r", timeoutMs = 600)
                    val e = (System.currentTimeMillis() - sessionStartMs) / 1000f
                    r to e
                }
                consecutiveFailures = 0
                val latency = System.currentTimeMillis() - readStart
                val value = parser.parse(pid, response) ?: continue
                emit(SensorSample(
                    pidId = pid.id,
                    elapsedSeconds = elapsed,
                    value = value,
                    timestampMs = System.currentTimeMillis(),
                    latencyMs = latency,
                ))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (++consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    stream.close()
                    throw IOException("ELM327 unresponsive: $MAX_CONSECUTIVE_FAILURES consecutive command failures")
                }
            }
        }
    }

    companion object {
        private const val VOLTAGE_POLL_INTERVAL_MS = 5_000L
        // After this many consecutive send failures the socket is closed and the session
        // ends with an error. Closing the socket is the only reliable way to unblock a
        // stuck BluetoothSocket.getInputStream().read() on Android — Thread.interrupt()
        // is ignored by the native Bluetooth stack.
        private const val MAX_CONSECUTIVE_FAILURES = 5
    }

    private fun parseAtrvVoltage(response: String): Float? =
        response.replace(Regex("[^0-9.]"), "").toFloatOrNull()?.takeIf { it > 0f }

    /**
     * Polls every ECU-supported PID once, acquiring the mutex per command so it
     * interleaves with the continuous polling loop rather than blocking it.
     * Returns an empty map if [readings] has not yet initialised.
     */
    override suspend fun pollSnapshot(): Map<String, Float> {
        val pids = supportedPids
        if (pids.isEmpty()) return emptyMap()
        val values = mutableMapOf<String, Float>()
        for (pid in pids) {
            try {
                val response = mutex.withLock { sender.send("01${pid.hex}\r", timeoutMs = 600) }
                parser.parse(pid, response)?.let { values[pid.id] = it }
            } catch (_: Exception) {}
        }
        return values
    }

    /**
     * Queries stored DTCs (mode 03) and pending DTCs (mode 07).
     * Uses a 3 s timeout per command — some ECUs are slow on these modes.
     * Returns empty lists on any error or if the mode is unsupported.
     */
    override suspend fun readDtcs(): DtcReadResult = mutex.withLock {
        try {
            val stored  = DtcParser.parse(sender.send("03\r", timeoutMs = 3000), responseMode = "43")
            val pending = DtcParser.parse(sender.send("07\r", timeoutMs = 3000), responseMode = "47")
            DtcReadResult(stored = stored, pending = pending)
        } catch (_: Exception) {
            DtcReadResult()
        }
    }

    /**
     * Sends OBD mode 04 to clear all stored DTCs and reset readiness monitors.
     * Returns true if the ECU acknowledged with a mode 44 response.
     */
    override suspend fun clearDtcs(): Boolean = mutex.withLock {
        try {
            val response = sender.send("04\r", timeoutMs = 3000)
                .replace("\\s".toRegex(), "").uppercase()
            response.contains("44") || response.contains("OK")
        } catch (_: Exception) {
            false
        }
    }
}
