package com.bugenzhao.mnga.ui.screens.history

import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.protos.datamodel.TopicSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Local JVM tests for the history tombstone filtering in [isTombstoned]. */
class HistoryTombstoneTest {

    private fun snapshot(id: String, timestampMs: Long): TopicSnapshot =
        TopicSnapshot.newBuilder()
            .setTimestamp(timestampMs)
            .setTopicSnapshot(Topic.newBuilder().setId(id))
            .build()

    @Test
    fun `snapshot without tombstone is visible`() {
        assertFalse(isTombstoned(snapshot("1", 1000L), emptyMap()))
    }

    @Test
    fun `snapshot older than tombstone is hidden`() {
        val tombstones = mapOf("1" to 2000L)
        assertTrue(isTombstoned(snapshot("1", 1000L), tombstones))
        assertTrue(isTombstoned(snapshot("1", 2000L), tombstones))
    }

    @Test
    fun `snapshot newer than tombstone reappears`() {
        // The topic was viewed again after deletion: the new snapshot wins.
        val tombstones = mapOf("1" to 2000L)
        assertFalse(isTombstoned(snapshot("1", 2001L), tombstones))
    }

    @Test
    fun `tombstone for another topic does not hide`() {
        val tombstones = mapOf("2" to 2000L)
        assertFalse(isTombstoned(snapshot("1", 1000L), tombstones))
    }
}
