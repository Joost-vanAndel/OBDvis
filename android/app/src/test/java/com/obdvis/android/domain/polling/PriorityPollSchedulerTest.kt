package com.obdvis.android.domain.polling

import androidx.compose.ui.graphics.Color
import com.obdvis.android.domain.model.PidDefinition
import com.obdvis.android.domain.model.SensorSample
import com.obdvis.android.domain.store.SampleStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriorityPollSchedulerTest {

    private fun pid(id: String) = PidDefinition(
        id = id,
        hex = "0C",
        name = id,
        unit = "",
        min = 0f,
        max = 100f,
        color = Color(0xFF000000.toInt()),
        formula = { 0f },
    )

    private fun SampleStore.addAt(pidId: String, ageMs: Long) {
        add(SensorSample(
            pidId = pidId,
            elapsedSeconds = 0f,
            value = 0f,
            timestampMs = System.currentTimeMillis() - ageMs,
        ))
    }

    @Test
    fun `nextPid returns null for empty candidate list`() {
        val store = SampleStore()
        val scheduler = PriorityPollScheduler(store)
        assertNull(scheduler.nextPid(emptyList()) { 1f })
    }

    @Test
    fun `nextPid returns the only candidate when list has one element`() {
        val store = SampleStore()
        val scheduler = PriorityPollScheduler(store)
        val p = pid("rpm")
        assertEquals(p, scheduler.nextPid(listOf(p)) { 1f })
    }

    @Test
    fun `nextPid never-polled pid wins over recently polled same-weight pid`() {
        val store = SampleStore()
        store.addAt("speed", ageMs = 100)
        val scheduler = PriorityPollScheduler(store)

        val rpm = pid("rpm")    // never polled → age = now (very large score)
        val speed = pid("speed") // polled 100ms ago
        val weight = { _: String -> 1f }

        assertEquals(rpm, scheduler.nextPid(listOf(rpm, speed), weight))
    }

    @Test
    fun `nextPid higher weight wins over lower weight with same age`() {
        val store = SampleStore()
        store.addAt("rpm", ageMs = 1_000)
        store.addAt("baro", ageMs = 1_000)
        val scheduler = PriorityPollScheduler(store)

        val rpm = pid("rpm")   // weight 4 → score = 1000 * 4 = 4000
        val baro = pid("baro") // weight 1 → score = 1000 * 1 = 1000

        val weight = PidPriorityGroups::overviewWeightOf
        // rpm is in OVERVIEW_HIGH (weight 4), baro is default (weight 1)
        assertEquals(rpm, scheduler.nextPid(listOf(rpm, baro), weight))
    }

    @Test
    fun `nextPid older age wins when weights are equal`() {
        val store = SampleStore()
        store.addAt("baro", ageMs = 500)
        store.addAt("ambient_temp", ageMs = 2_000)
        val scheduler = PriorityPollScheduler(store)

        val baro = pid("baro")         // weight 1, age 500ms → score 500
        val ambient = pid("ambient_temp") // weight 1, age 2000ms → score 2000
        val weight = { _: String -> 1f }

        assertEquals(ambient, scheduler.nextPid(listOf(baro, ambient), weight))
    }

    @Test
    fun `nextPid high-weight recent pid beats low-weight old pid when score is higher`() {
        val store = SampleStore()
        store.addAt("rpm", ageMs = 500)    // weight 4, score = 2000
        store.addAt("baro", ageMs = 1_500) // weight 1, score = 1500
        val scheduler = PriorityPollScheduler(store)

        val rpm = pid("rpm")
        val baro = pid("baro")
        val weight = PidPriorityGroups::overviewWeightOf

        assertEquals(rpm, scheduler.nextPid(listOf(rpm, baro), weight))
    }

    @Test
    fun `nextPid low-weight old pid beats high-weight recent pid when score is higher`() {
        val store = SampleStore()
        store.addAt("rpm", ageMs = 100)     // weight 4, score = 400
        store.addAt("baro", ageMs = 5_000)  // weight 1, score = 5000
        val scheduler = PriorityPollScheduler(store)

        val rpm = pid("rpm")
        val baro = pid("baro")
        val weight = PidPriorityGroups::overviewWeightOf

        assertEquals(baro, scheduler.nextPid(listOf(rpm, baro), weight))
    }
}
