package com.obdvis.android.domain.health

/**
 * A completed drive as stored in local history.
 *
 * [csvContent] is captured at the same time as [summary], so reopening a saved drive does not
 * depend on the current in-memory chart buffer.
 */
data class SavedDrive(
    val summary: PostDriveData,
    val csvContent: String,
    /** Compressed on-device record size. Populated by DriveHistoryStore after persistence. */
    val fileSizeBytes: Long = 0L,
) {
    val id: Long get() = summary.sessionStartMs
}
