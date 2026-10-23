package io.github.krnelpan1c.droidpod.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SessionSelectorTest {
    private fun c(key: String, playing: Boolean, at: Long) = SessionCandidate(key, playing, at)

    @Test
    fun empty_selectsNothing() {
        assertNull(SessionSelector.select(emptyList(), null))
    }

    @Test
    fun prefersTheMostRecentlyStartedPlayingSession() {
        val sessions = listOf(c("spotify", true, 100), c("ytm", true, 200), c("podcast", false, 300))
        assertEquals("ytm", SessionSelector.select(sessions, null))
    }

    @Test
    fun withNothingPlaying_picksTheLastPlayed() {
        val sessions = listOf(c("spotify", false, 100), c("ytm", false, 200))
        assertEquals("ytm", SessionSelector.select(sessions, null))
    }

    @Test
    fun withNoHistory_keepsSystemOrder() {
        val sessions = listOf(c("first", false, 0), c("second", false, 0))
        assertEquals("first", SessionSelector.select(sessions, null))
    }

    @Test
    fun pin_beatsSessionsThatWerePlayingBeforeIt() {
        val sessions = listOf(c("spotify", true, 100), c("ytm", false, 50))
        assertEquals("ytm", SessionSelector.select(sessions, Pin("ytm", pinnedAt = 150)))
    }

    @Test
    fun pin_isOverriddenWhenAnotherAppStartsPlayingLater() {
        val sessions = listOf(c("spotify", true, 300), c("ytm", false, 50))
        assertEquals("spotify", SessionSelector.select(sessions, Pin("ytm", pinnedAt = 150)))
    }

    @Test
    fun pin_forAGoneSessionIsIgnored() {
        val sessions = listOf(c("spotify", false, 100))
        assertEquals("spotify", SessionSelector.select(sessions, Pin("ytm", pinnedAt = 150)))
    }
}
