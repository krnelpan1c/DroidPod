package io.github.krnelpan1c.droidpod.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueCounterTest {
    private val playlist = (1..100).map { "t$it" }

    /** A YouTube Music-style window: the playing track and the next [size] - 1. */
    private fun window(number: Int, size: Int = 25, tracks: List<String> = playlist) =
        tracks.drop(number - 1).take(size)

    private fun QueueCounter.at(number: Int, size: Int = 25, tracks: List<String> = playlist) =
        update(window(number, size, tracks), 0)

    @Test
    fun firstSight_usesTheWindow() {
        assertEquals(QueuePosition(1, 25), QueueCounter().at(1))
    }

    @Test
    fun skippingForward_countsUpBothNumbers() {
        val counter = QueueCounter()
        counter.at(1)
        assertEquals(QueuePosition(2, 26), counter.at(2))
        assertEquals(QueuePosition(3, 27), counter.at(3))
    }

    @Test
    fun goingBack_countsDownWithoutShrinkingTheTotal() {
        val counter = QueueCounter()
        (1..5).forEach { counter.at(it) }
        assertEquals(QueuePosition(4, 29), counter.at(4))
        assertEquals(QueuePosition(1, 29), (3 downTo 1).map { counter.at(it) }.last())
        assertEquals(QueuePosition(2, 29), counter.at(2))
    }

    @Test
    fun nearTheEnd_totalStopsGrowing() {
        val counter = QueueCounter()
        (1..100).forEach { counter.at(it) }
        assertEquals(QueuePosition(100, 100), counter.at(100))
    }

    @Test
    fun windowThatKeepsHistory_isCountedFromItsStart() {
        val counter = QueueCounter()
        assertEquals(QueuePosition(3, 10), counter.update(playlist.take(10), 2))
        assertEquals(QueuePosition(4, 11), counter.update(playlist.drop(1).take(10), 2))
    }

    @Test
    fun jumpingAheadInUpNext_countsTheSkippedTracks() {
        val counter = QueueCounter()
        counter.at(1)
        assertEquals(QueuePosition(8, 32), counter.at(8))
    }

    @Test
    fun activeTrackMovingBeforeTheQueueUpdates_countsOnce() {
        val counter = QueueCounter()
        counter.at(1)
        assertEquals(QueuePosition(2, 25), counter.update(window(1), 1))
        assertEquals(QueuePosition(2, 26), counter.at(2))
    }

    @Test
    fun repeatedUpdatesForTheSameTrack_changeNothing() {
        val counter = QueueCounter()
        counter.at(3)
        assertEquals(QueuePosition(1, 25), counter.at(3))
    }

    @Test
    fun newPlaylist_startsOver() {
        val counter = QueueCounter()
        (1..10).forEach { counter.at(it) }
        val other = (1..40).map { "other$it" }
        assertEquals(QueuePosition(1, 25), counter.at(1, tracks = other))
        assertEquals(QueuePosition(2, 26), counter.at(2, tracks = other))
    }

    @Test
    fun newPlaylistStartingWithAPlayedSong_startsOver() {
        val counter = QueueCounter()
        (1..10).forEach { counter.at(it) }
        val other = listOf("t3") + (1..40).map { "other$it" }
        assertEquals(QueuePosition(1, 25), counter.at(1, tracks = other))
    }

    @Test
    fun unknownActiveTrack_isHidden() {
        assertNull(QueueCounter().update(playlist.take(5), -1))
    }
}
