package com.obdvis.android.data.history

import com.obdvis.android.domain.health.DiagnosticFinding
import com.obdvis.android.domain.health.DtcReadResult
import com.obdvis.android.domain.health.FindingConfidence
import com.obdvis.android.domain.health.FindingRecord
import com.obdvis.android.domain.health.FindingSeverity
import com.obdvis.android.domain.health.FindingStatus
import com.obdvis.android.domain.health.OperatingState
import com.obdvis.android.domain.health.PeakGForce
import com.obdvis.android.domain.health.PostDriveData
import com.obdvis.android.domain.health.SavedDrive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class DriveHistoryStoreTest {
    private val directory = Files.createTempDirectory("obdvis-drive-history").toFile()

    @After
    fun tearDown() {
        directory.deleteRecursively()
    }

    @Test
    fun `save and load round trips the complete drive record`() {
        val store = DriveHistoryStore(directory)
        val drive = savedDrive(startMs = 1_000L, endMs = 61_000L)

        store.save(drive)

        val loaded = store.load().single()
        assertEquals(drive, loaded.copy(fileSizeBytes = drive.fileSizeBytes))
        assertTrue(loaded.fileSizeBytes > 0L)
        assertEquals(directory.listFiles()!!.single().length(), loaded.fileSizeBytes)
    }

    @Test
    fun `history is newest first without a retention cap`() {
        val store = DriveHistoryStore(directory)

        repeat(25) { index ->
            val startMs = index * 2_000L + 1_000L
            store.save(savedDrive(startMs = startMs, endMs = startMs + 1_000L))
        }
        val retained = store.load()

        assertEquals(25, retained.size)
        assertEquals(49_000L, retained.first().id)
        assertEquals(1_000L, retained.last().id)
        assertEquals(25, directory.listFiles()?.size)
    }

    @Test
    fun `delete removes only the selected drive`() {
        val store = DriveHistoryStore(directory)
        store.save(savedDrive(startMs = 1_000L, endMs = 2_000L))
        store.save(savedDrive(startMs = 3_000L, endMs = 4_000L))

        val remaining = store.delete(1_000L)

        assertEquals(listOf(3_000L), remaining.map { it.id })
        assertFalse(remaining.any { it.id == 1_000L })
    }

    @Test
    fun `checkpoint stays hidden until it is recovered`() {
        val store = DriveHistoryStore(directory)
        val checkpoint = savedDrive(startMs = 1_000L, endMs = 31_000L)

        store.saveCheckpoint(checkpoint)

        assertTrue(store.load().isEmpty())
        val recovered = store.recoverCheckpoint()
        assertEquals(checkpoint, recovered?.copy(fileSizeBytes = checkpoint.fileSizeBytes))
        assertEquals(listOf(checkpoint.id), store.load().map { it.id })
        assertEquals(1, directory.listFiles()?.size)
    }

    @Test
    fun `new checkpoint replaces the previous snapshot`() {
        val store = DriveHistoryStore(directory)
        val first = savedDrive(startMs = 1_000L, endMs = 31_000L)
        val latest = savedDrive(startMs = 1_000L, endMs = 46_000L)

        store.saveCheckpoint(first)
        store.saveCheckpoint(latest)

        val recovered = store.recoverCheckpoint()
        assertEquals(latest, recovered?.copy(fileSizeBytes = latest.fileSizeBytes))
    }

    @Test
    fun `completed drive wins over a stale checkpoint`() {
        val store = DriveHistoryStore(directory)
        val completed = savedDrive(startMs = 1_000L, endMs = 61_000L)
        val staleCheckpoint = savedDrive(startMs = 1_000L, endMs = 46_000L)
        store.save(completed)
        store.saveCheckpoint(staleCheckpoint)

        val recovered = store.recoverCheckpoint()

        assertEquals(null, recovered)
        val loaded = store.load().single()
        assertEquals(completed, loaded.copy(fileSizeBytes = completed.fileSizeBytes))
        assertEquals(1, directory.listFiles()?.size)
    }

    private fun savedDrive(startMs: Long, endMs: Long): SavedDrive {
        val finding = DiagnosticFinding(
            id = "fuel_trim_lean",
            title = "Lean fuel trim",
            description = "Fuel trim stayed positive.",
            severity = FindingSeverity.MEDIUM,
            confidence = FindingConfidence.HIGH,
            evidence = linkedMapOf("Bank 1" to "+14.2%", "Window" to "30 s"),
        )
        val record = FindingRecord(
            finding = finding,
            firstSeenMs = startMs + 5_000L,
            lastSeenMs = endMs - 2_000L,
            occurrenceCount = 3,
            peakSeverity = FindingSeverity.HIGH,
            status = FindingStatus.FADING,
            fadingSinceMs = endMs - 1_000L,
            eligibleCount = 7,
        )
        return SavedDrive(
            summary = PostDriveData(
                sessionStartMs = startMs,
                sessionEndMs = endMs,
                findingRecords = listOf(record),
                totalHealthChecks = 8,
                operatingStateCounts = mapOf(
                    OperatingState.WARM_IDLE to 3,
                    OperatingState.CRUISE to 5,
                ),
                dtcResult = DtcReadResult(
                    stored = listOf("P0300"),
                    pending = listOf("P0420"),
                ),
                peakRpm = 4_250f,
                peakSpeedKph = 118f,
                peakCoolantTempC = 96f,
                peakGForce = PeakGForce(0.3f, 0.4f, 0.5f, 0.2f),
            ),
            csvContent = "elapsed_s,pid,value\n0.100,rpm,850.0000\n",
        )
    }
}
