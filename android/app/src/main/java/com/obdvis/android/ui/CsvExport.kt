package com.obdvis.android.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun rememberCsvExportAction(csvContent: () -> String): (String) -> Unit {
    val context = LocalContext.current
    val currentContent by rememberUpdatedState(csvContent)
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openOutputStream(uri)?.use { output ->
            output.write(currentContent().toByteArray(Charsets.UTF_8))
        }
    }
    return launcher::launch
}
