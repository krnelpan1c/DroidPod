package io.github.krnelpan1c.droidpod.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Action ids taken from real diagnostics reports (Spotify and YouTube Music, Android 17). */
class CustomActionInfoTest {
    @Test
    fun youTubeMusic() {
        assertTrue(CustomActionInfo("shuffle_action", "Shuffle off").isShuffle)
        assertTrue(CustomActionInfo("loop_mode_action", "Repeat off").isRepeat)
        assertFalse(CustomActionInfo("thumbs_up_action", "Undo like").isRepeat)
        assertFalse(CustomActionInfo("thumbs_down_action", "Dislike").isShuffle)
    }

    @Test
    fun spotify() {
        assertTrue(CustomActionInfo("TURN_REPEAT_ALL_ON", "Start repeating all tracks").isRepeat)
        assertFalse(CustomActionInfo("ADD_TO", "Add to collection").isRepeat)
        assertFalse(CustomActionInfo("START_RADIO", "Start radio").isShuffle)
    }
}
