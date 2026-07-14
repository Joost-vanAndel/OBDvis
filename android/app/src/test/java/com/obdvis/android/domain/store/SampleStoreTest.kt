package com.obdvis.android.domain.store

import com.obdvis.android.domain.model.SensorSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleStoreTest {

    private fun sample(pidId: String, value: Float, ageMs: Long = 0L) = SensorSample(
        pidId = pidId,
        elapsedSeconds = 0f,
        value = value,
        timestampMs = System.currentTimeMillis() - ageMs,
    )

    @Test
    fun `add then latest returns the sample`() {
        val store = SampleStore()
        val s = sample("rpm", 800f)
        store.add(s)
        assertEquals(s, store.latest("rpm"))
    }

    @Test
    fun `latest returns null for unknown pid`() {
        assertNull(SampleStore().latest("unknown"))
    }

    @Test
    fun `latest returns the most recent of multiple samples`() {
        val store = SampleStore()
        store.add(sample("rpm", 700f, ageMs = 500))
        store.add(sample("rpm", 800f, ageMs = 100))
        assertEquals(800f, store.latest("rpm")!!.value)
    }

    @Test
    fun `latestTimestamp returns timestamp of most recent sample`() {
        val store = SampleStore()
        val s = sample("rpm", 800f)
        store.add(s)
        assertEquals(s.timestampMs, store.latestTimestamp("rpm"))
    }

    @Test
    fun `latestTimestamp returns null for unknown pid`() {
        assertNull(SampleStore().latestTimestamp("unknown"))
    }

    @Test
    fun `latestFresh returns sample when within maxAgeMs`() {
        val store = SampleStore()
        store.add(sample("rpm", 800f, ageMs = 100))
        assertNotNull(store.latestFresh("rpm", 1_000L))
    }

    @Test
    fun `latestFresh returns null when sample is older than maxAgeMs`() {
        val store = SampleStore()
        store.add(sample("rpm", 800f, ageMs = 5_000))
        assertNull(store.latestFresh("rpm", 1_000L))
    }

    @Test
    fun `latestFreshValue returns value when fresh`() {
        val store = SampleStore()
        store.add(sample("speed", 60f, ageMs = 50))
        assertEquals(60f, store.latestFreshValue("speed", 1_000L))
    }

    @Test
    fun `window returns only samples within the time window`() {
        val store = SampleStore()
        store.add(sample("rpm", 700f, ageMs = 500))
        store.add(sample("rpm", 800f, ageMs = 200))
        store.add(sample("rpm", 500f, ageMs = 50_000)) // outside 10 s window
        val result = store.window("rpm", 10_000L)
        assertEquals(2, result.size)
        assertTrue(result.none { it.value == 500f })
    }

    @Test
    fun `window returns empty list for unknown pid`() {
        assertTrue(SampleStore().window("rpm", 10_000L).isEmpty())
    }

    @Test
    fun `stats computes correct avg min max and count`() {
        val store = SampleStore()
        store.add(sample("rpm", 1000f, ageMs = 300))
        store.add(sample("rpm", 2000f, ageMs = 200))
        store.add(sample("rpm", 3000f, ageMs = 100))
        val stats = store.stats("rpm", 10_000L)!!
        assertEquals(2000f, stats.avg, 1f)
        assertEquals(1000f, stats.min, 1f)
        assertEquals(3000f, stats.max, 1f)
        assertEquals(3, stats.count)
    }

    @Test
    fun `stats stdDev is zero for single identical value`() {
        val store = SampleStore()
        store.add(sample("rpm", 1500f, ageMs = 100))
        val stats = store.stats("rpm", 10_000L)!!
        assertEquals(0f, stats.stdDev, 0.001f)
    }

    @Test
    fun `stats returns null when no samples exist`() {
        assertNull(SampleStore().stats("rpm", 10_000L))
    }

    @Test
    fun `stats returns null when all samples are outside window`() {
        val store = SampleStore()
        store.add(sample("rpm", 800f, ageMs = 60_000)) // older than 10 s window
        assertNull(store.stats("rpm", 10_000L))
    }

    @Test
    fun `add trims samples older than retention period`() {
        val store = SampleStore()
        val oldTs = System.currentTimeMillis() - SampleStore.RETENTION_MS - 1_000
        store.add(SensorSample("rpm", 0f, 400f, timestampMs = oldTs))
        // Adding a fresh sample triggers trimming of anything before (now - RETENTION_MS)
        store.add(sample("rpm", 800f))
        // Window larger than retention: old sample is gone, only fresh one remains
        val all = store.window("rpm", SampleStore.RETENTION_MS + 60_000)
        assertEquals(1, all.size)
        assertEquals(800f, all[0].value)
    }

    @Test
    fun `clear empties all pid buffers`() {
        val store = SampleStore()
        store.add(sample("rpm", 800f))
        store.add(sample("speed", 60f))
        store.clear()
        assertNull(store.latest("rpm"))
        assertNull(store.latest("speed"))
    }

    @Test
    fun `samples from different pids are stored independently`() {
        val store = SampleStore()
        store.add(sample("rpm", 800f))
        store.add(sample("speed", 60f))
        assertEquals(800f, store.latest("rpm")!!.value)
        assertEquals(60f, store.latest("speed")!!.value)
    }
}
