package com.obdvis.android.domain.health

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FindingStateManagerTest {

    private val catalystFinding = DiagnosticFinding(
        id = "o2_downstream_cycling",
        title = "Possible low catalyst oxygen storage",
        description = "screening finding",
        severity = FindingSeverity.LOW,
        confidence = FindingConfidence.LOW,
    )

    @Test
    fun `catalyst screening finding requires persistence before becoming visible`() {
        val manager = FindingStateManager()
        val eligible = setOf(catalystFinding.id)

        val initial = manager.merge(listOf(catalystFinding), eligible, now = 1_000L)
        val stillPending = manager.merge(
            listOf(catalystFinding),
            eligible,
            now = 1_000L + FindingStateManager.PERSISTENCE_THRESHOLD_MS - 1,
        )
        val active = manager.merge(
            listOf(catalystFinding),
            eligible,
            now = 1_000L + FindingStateManager.PERSISTENCE_THRESHOLD_MS,
        )

        assertFalse(initial.containsKey(catalystFinding.id))
        assertFalse(stillPending.containsKey(catalystFinding.id))
        assertTrue(active.containsKey(catalystFinding.id))
    }
}
