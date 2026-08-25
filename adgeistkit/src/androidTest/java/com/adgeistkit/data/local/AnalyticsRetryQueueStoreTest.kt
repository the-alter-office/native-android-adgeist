package com.adgeistkit.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnalyticsRetryQueueStoreTest {

    private lateinit var store: AnalyticsRetryQueueStore

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("adgeist_analytics_retry_queue.db")
        store = AnalyticsRetryQueueStore(context)
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().targetContext
            .deleteDatabase("adgeist_analytics_retry_queue.db")
    }

    @Test
    fun insertThenGetAll_returnsRowsInFifoOrder() {
        store.insertAndTrim("https://a", "{}")
        store.insertAndTrim("https://b", "{}")
        store.insertAndTrim("https://c", "{}")

        val urls = store.getAll().map { it.url }

        assertEquals(listOf("https://a", "https://b", "https://c"), urls)
    }

    @Test
    fun count_reflectsInsertsAndDeletes() {
        assertEquals(0, store.count())

        val id = store.insertAndTrim("https://a", "{}")
        assertEquals(1, store.count())

        store.deleteById(id)
        assertEquals(0, store.count())
    }

    @Test
    fun deleteById_removesOnlyThatRow() {
        val keep = store.insertAndTrim("https://keep", "{}")
        val remove = store.insertAndTrim("https://remove", "{}")

        store.deleteById(remove)

        val remaining = store.getAll()
        assertEquals(1, remaining.size)
        assertEquals(keep, remaining[0].id)
        assertEquals("https://keep", remaining[0].url)
    }

    @Test
    fun deleteById_withUnknownId_isANoOp() {
        store.insertAndTrim("https://a", "{}")

        store.deleteById(999_999L)

        assertEquals(1, store.count())
    }

    @Test
    fun insertAndTrim_enforcesTheCapByDroppingTheOldestRows() {
        val overflow = 5
        val ids = (1..AnalyticsRetryQueueStore.MAX_QUEUED + overflow).map { i ->
            store.insertAndTrim("https://item-$i", "{}")
        }

        assertEquals(AnalyticsRetryQueueStore.MAX_QUEUED, store.count())

        val remainingIds = store.getAll().map { it.id }.toSet()
        // The 5 oldest (lowest id) rows were dropped; the newest MAX_QUEUED all survive.
        assertFalse("oldest row should have been dropped", ids[0] in remainingIds)
        assertFalse("5th-oldest row should have been dropped", ids[4] in remainingIds)
        assertTrue("6th-oldest row should have survived", ids[5] in remainingIds)
        assertTrue("newest row should have survived", ids.last() in remainingIds)
    }

    @Test
    fun newRowsStartWithZeroReattempts() {
        store.insertAndTrim("https://a", "{}")

        assertEquals(0, store.getAll().single().reattempts)
    }

    @Test
    fun markRetry_raisesTheReattemptCountOfOnlyThatRow() {
        val target = store.insertAndTrim("https://target", "{}")
        store.insertAndTrim("https://other", "{}")

        store.markRetry(target, nextAttemptAtMillis = 0L)
        store.markRetry(target, nextAttemptAtMillis = 0L)

        val byUrl = store.getAll().associateBy { it.url }

        assertEquals(2, byUrl.getValue("https://target").reattempts)
        assertEquals(0, byUrl.getValue("https://other").reattempts)
    }

    @Test
    fun markRetry_withUnknownId_isANoOp() {
        val id = store.insertAndTrim("https://a", "{}")

        store.markRetry(999_999L, nextAttemptAtMillis = 0L)

        assertEquals(1, store.count())
        assertEquals(0, store.getAll().single().reattempts)
        assertEquals(id, store.getAll().single().id)
    }

    // ---- Due times ----

    @Test
    fun getDue_returnsOnlyRowsWhoseBackoffHasElapsed() {
        store.insertAndTrim("https://due", "{}", nextAttemptAt = 100L)
        store.insertAndTrim("https://waiting", "{}", nextAttemptAt = 5_000L)

        val due = store.getDue(nowMillis = 1_000L).map { it.url }

        assertEquals(listOf("https://due"), due)
    }

    @Test
    fun getDue_isInclusiveOfTheDueInstant() {
        store.insertAndTrim("https://exact", "{}", nextAttemptAt = 1_000L)

        assertEquals(1, store.getDue(nowMillis = 1_000L).size)
        assertTrue(store.getDue(nowMillis = 999L).isEmpty())
    }

    @Test
    fun markRetry_pushesTheRowOutOfTheDueSet() {
        val id = store.insertAndTrim("https://a", "{}")
        assertEquals(1, store.getDue(nowMillis = 1_000L).size)

        store.markRetry(id, nextAttemptAtMillis = 9_000L)

        assertTrue(store.getDue(nowMillis = 1_000L).isEmpty())
        assertEquals(1, store.getDue(nowMillis = 9_000L).size)
    }

    @Test
    fun earliestNextAttempt_isTheSoonestRowOrNullWhenEmpty() {
        assertEquals(null, store.earliestNextAttempt())

        store.insertAndTrim("https://later", "{}", nextAttemptAt = 8_000L)
        store.insertAndTrim("https://sooner", "{}", nextAttemptAt = 2_000L)

        assertEquals(2_000L, store.earliestNextAttempt())
    }

    @Test
    fun dueTimesSurviveReopeningTheDatabase() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val id = store.insertAndTrim("https://a", "{}")
        store.markRetry(id, nextAttemptAtMillis = 7_000L)

        // A fresh store stands in for the next process: the backoff must come back with it.
        val reopened = AnalyticsRetryQueueStore(context)

        assertEquals(7_000L, reopened.earliestNextAttempt())
        assertTrue(reopened.getDue(nowMillis = 1_000L).isEmpty())
        assertEquals(1, reopened.getDue(nowMillis = 7_000L).single().reattempts)
    }

    @Test
    fun insertAndTrim_underTheCap_dropsNothing() {
        repeat(3) { i -> store.insertAndTrim("https://item-$i", "{}") }

        assertEquals(3, store.count())
    }
}
