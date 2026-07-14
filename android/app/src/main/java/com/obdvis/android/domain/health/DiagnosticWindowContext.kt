package com.obdvis.android.domain.health

import com.obdvis.android.domain.store.SampleStore

/**
 * Aggregated rolling-window statistics captured at diagnostic time.
 *
 * Rather than a single-point snapshot, each field is a statistical summary
 * over the last [windowSeconds] seconds. Diagnostics use this context to
 * explain findings without exposing raw streaming history.
 *
 * Null stats mean no fresh data was available for that signal within the window.
 */
data class DiagnosticWindowContext(
    val windowSeconds: Int,
    val rpm: SampleStore.WindowStats?,
    val speed: SampleStore.WindowStats?,
    val coolant: SampleStore.WindowStats?,
    val stftBank1: SampleStore.WindowStats?,
    val ltftBank1: SampleStore.WindowStats?,
    val stftBank2: SampleStore.WindowStats?,
    val ltftBank2: SampleStore.WindowStats?,
    val mafGps: SampleStore.WindowStats?,
    val o2Upstream: SampleStore.WindowStats?,
    val afrUpstream: SampleStore.WindowStats?,
    val o2Downstream: SampleStore.WindowStats?,
    val throttle: SampleStore.WindowStats?,
    val engineLoad: SampleStore.WindowStats?,
    /** Operating states that appeared in [recentStates]. */
    val operatingStatesObserved: Set<String>,
)
