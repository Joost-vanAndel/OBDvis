package com.obdvis.android.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/** Opens an explicit user-initiated web search without adding network access to the app. */
fun Context.searchForDtc(code: String) {
    val query = "OBD-II trouble code ${code.uppercase()}"
    val url = "https://www.google.com/search?q=${Uri.encode(query)}"
    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))

    try {
        startActivity(browserIntent)
    } catch (_: ActivityNotFoundException) {
        // Devices without a browser cannot handle external DTC searches.
    }
}
