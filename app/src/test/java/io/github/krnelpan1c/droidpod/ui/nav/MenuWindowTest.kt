package io.github.krnelpan1c.droidpod.ui.nav

import org.junit.Assert.assertEquals
import org.junit.Test

class MenuWindowTest {
    @Test
    fun shortList_neverScrolls() {
        assertEquals(0, MenuWindow.top(selected = 4, currentTop = 0, visible = 6, count = 5))
    }

    @Test
    fun movingPastTheBottom_scrollsByOne() {
        assertEquals(1, MenuWindow.top(selected = 6, currentTop = 0, visible = 6, count = 10))
    }

    @Test
    fun movingWithinTheWindow_keepsIt() {
        assertEquals(3, MenuWindow.top(selected = 5, currentTop = 3, visible = 6, count = 10))
    }

    @Test
    fun movingAboveTheTop_scrollsUp() {
        assertEquals(2, MenuWindow.top(selected = 2, currentTop = 4, visible = 6, count = 10))
    }

    @Test
    fun windowNeverRunsPastTheEnd() {
        assertEquals(4, MenuWindow.top(selected = 9, currentTop = 8, visible = 6, count = 10))
    }
}
