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
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Small file-backed repository for completed drives.
 *
 * Each drive is an independent, compressed record. This keeps individual deletion cheap and
 * avoids rewriting all history whenever a drive is saved.
 */
class DriveHistoryStore(
    private val directory: File,
) {
    @Synchronized
    fun load(): List<SavedDrive> =
        historyFiles()
            .mapNotNull { file -> runCatching { read(file) }.getOrNull() }
            .sortedByDescending { it.summary.sessionEndMs }

    @Synchronized
    fun save(drive: SavedDrive): List<SavedDrive> {
        ensureDirectory()
        val target = fileFor(drive.id)
        val temporary = File(directory, "${target.name}.tmp")
        try {
            DataOutputStream(
                GZIPOutputStream(
                    BufferedOutputStream(FileOutputStream(temporary))
                )
            ).use { output -> DriveRecordCodec.write(output, drive) }

            if (target.exists() && !target.delete()) {
                throw IOException("Unable to replace saved drive ${drive.id}")
            }
            if (!temporary.renameTo(target)) {
                throw IOException("Unable to finish saving drive ${drive.id}")
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }

        return load()
    }

    @Synchronized
    fun delete(id: Long): List<SavedDrive> {
        val target = fileFor(id)
        if (target.exists() && !target.delete()) {
            throw IOException("Unable to delete saved drive $id")
        }
        return load()
    }

    private fun read(file: File): SavedDrive {
        val drive = DataInputStream(
            GZIPInputStream(
                BufferedInputStream(FileInputStream(file))
            )
        ).use(DriveRecordCodec::read)
        return drive.copy(fileSizeBytes = file.length())
    }

    private fun historyFiles(): List<File> =
        directory.listFiles { file -> file.isFile && file.extension == FILE_EXTENSION }
            ?.toList()
            ?: emptyList()

    private fun fileFor(id: Long): File = File(directory, "$id.$FILE_EXTENSION")

    private fun ensureDirectory() {
        if (!directory.exists() && !directory.mkdirs()) {
            throw IOException("Unable to create drive history directory")
        }
    }

    companion object {
        private const val FILE_EXTENSION = "obd-drive"
    }
}

private object DriveRecordCodec {
    private const val MAGIC = 0x4F424448 // "OBDH"
    private const val VERSION = 1
    private const val MAX_STRING_BYTES = 10 * 1024 * 1024
    private const val MAX_COLLECTION_SIZE = 10_000

    fun write(output: DataOutputStream, drive: SavedDrive) {
        output.writeInt(MAGIC)
        output.writeInt(VERSION)

        with(drive.summary) {
            output.writeLong(sessionStartMs)
            output.writeLong(sessionEndMs)
            output.writeInt(totalHealthChecks)

            output.writeList(findingRecords) { record ->
                writeFinding(record.finding)
                writeLong(record.firstSeenMs)
                writeLong(record.lastSeenMs)
                writeInt(record.occurrenceCount)
                writeString(record.peakSeverity.name)
                writeString(record.status.name)
                writeNullableLong(record.fadingSinceMs)
                writeInt(record.eligibleCount)
            }

            output.writeInt(operatingStateCounts.size)
            operatingStateCounts.forEach { (state, count) ->
                output.writeString(state.name)
                output.writeInt(count)
            }

            output.writeStringList(dtcResult.stored)
            output.writeStringList(dtcResult.pending)
            output.writeNullableFloat(peakRpm)
            output.writeNullableFloat(peakSpeedKph)
            output.writeNullableFloat(peakCoolantTempC)
            output.writeBoolean(peakGForce != null)
            peakGForce?.let { peak ->
                output.writeFloat(peak.left)
                output.writeFloat(peak.right)
                output.writeFloat(peak.forward)
                output.writeFloat(peak.backward)
            }
        }

        output.writeString(drive.csvContent)
    }

    fun read(input: DataInputStream): SavedDrive {
        require(input.readInt() == MAGIC) { "Invalid drive history record" }
        require(input.readInt() == VERSION) { "Unsupported drive history version" }

        val sessionStartMs = input.readLong()
        val sessionEndMs = input.readLong()
        val totalHealthChecks = input.readInt()
        val findingRecords = input.readList {
            FindingRecord(
                finding = readFinding(),
                firstSeenMs = readLong(),
                lastSeenMs = readLong(),
                occurrenceCount = readInt(),
                peakSeverity = enumValueOf(readString()),
                status = enumValueOf(readString()),
                fadingSinceMs = readNullableLong(),
                eligibleCount = readInt(),
            )
        }

        val operatingStateCounts = buildMap {
            repeat(input.readCollectionSize()) {
                put(enumValueOf<OperatingState>(input.readString()), input.readInt())
            }
        }
        val dtcResult = DtcReadResult(
            stored = input.readStringList(),
            pending = input.readStringList(),
        )
        val peakRpm = input.readNullableFloat()
        val peakSpeedKph = input.readNullableFloat()
        val peakCoolantTempC = input.readNullableFloat()
        val peakGForce = if (input.readBoolean()) {
            PeakGForce(
                left = input.readFloat(),
                right = input.readFloat(),
                forward = input.readFloat(),
                backward = input.readFloat(),
            )
        } else {
            null
        }
        val csvContent = input.readString()

        return SavedDrive(
            summary = PostDriveData(
                sessionStartMs = sessionStartMs,
                sessionEndMs = sessionEndMs,
                findingRecords = findingRecords,
                totalHealthChecks = totalHealthChecks,
                operatingStateCounts = operatingStateCounts,
                dtcResult = dtcResult,
                peakRpm = peakRpm,
                peakSpeedKph = peakSpeedKph,
                peakCoolantTempC = peakCoolantTempC,
                peakGForce = peakGForce,
            ),
            csvContent = csvContent,
        )
    }

    private fun DataOutputStream.writeFinding(finding: DiagnosticFinding) {
        writeString(finding.id)
        writeString(finding.title)
        writeString(finding.description)
        writeString(finding.severity.name)
        writeString(finding.confidence.name)
        writeInt(finding.evidence.size)
        finding.evidence.forEach { (label, value) ->
            writeString(label)
            writeString(value)
        }
    }

    private fun DataInputStream.readFinding(): DiagnosticFinding {
        val id = readString()
        val title = readString()
        val description = readString()
        val severity = enumValueOf<FindingSeverity>(readString())
        val confidence = enumValueOf<FindingConfidence>(readString())
        val evidence = buildMap {
            repeat(readCollectionSize()) {
                put(readString(), readString())
            }
        }
        return DiagnosticFinding(id, title, description, severity, confidence, evidence)
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun DataInputStream.readString(): String {
        val size = readInt()
        require(size in 0..MAX_STRING_BYTES) { "Invalid string size" }
        val bytes = ByteArray(size)
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun <T> DataOutputStream.writeList(values: List<T>, writeItem: DataOutputStream.(T) -> Unit) {
        writeInt(values.size)
        values.forEach { writeItem(it) }
    }

    private fun <T> DataInputStream.readList(readItem: DataInputStream.() -> T): List<T> =
        List(readCollectionSize()) { readItem() }

    private fun DataOutputStream.writeStringList(values: List<String>) =
        writeList(values) { writeString(it) }

    private fun DataInputStream.readStringList(): List<String> = readList { readString() }

    private fun DataInputStream.readCollectionSize(): Int =
        readInt().also { require(it in 0..MAX_COLLECTION_SIZE) { "Invalid collection size" } }

    private fun DataOutputStream.writeNullableFloat(value: Float?) {
        writeBoolean(value != null)
        if (value != null) writeFloat(value)
    }

    private fun DataInputStream.readNullableFloat(): Float? =
        if (readBoolean()) readFloat() else null

    private fun DataOutputStream.writeNullableLong(value: Long?) {
        writeBoolean(value != null)
        if (value != null) writeLong(value)
    }

    private fun DataInputStream.readNullableLong(): Long? =
        if (readBoolean()) readLong() else null
}
