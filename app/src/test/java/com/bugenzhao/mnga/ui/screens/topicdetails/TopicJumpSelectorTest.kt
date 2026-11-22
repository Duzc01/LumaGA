package com.bugenzhao.mnga.ui.screens.topicdetails

import org.junit.Assert.assertEquals
import org.junit.Test

class TopicJumpSelectorTest {
    @Test
    fun `long topic uses continuous sliders for floors and pages`() {
        val lastFloor = 123_957
        val lastPage = pageForFloor(lastFloor)

        assertEquals(6_198, lastPage)
        assertEquals(0, jumpSliderSteps(0, lastFloor))
        assertEquals(0, jumpSliderSteps(1, lastPage))
    }

    @Test
    fun `small ranges retain one step per interior value`() {
        assertEquals(0, jumpSliderSteps(0, 1))
        assertEquals(18, jumpSliderSteps(1, 20))
        assertEquals(100, jumpSliderSteps(0, 101))
        assertEquals(0, jumpSliderSteps(0, 102))
    }

    @Test
    fun `page calculation covers the full floor range`() {
        assertEquals(1, pageForFloor(19))
        assertEquals(2, pageForFloor(20))
        assertEquals(107_374_183, pageForFloor(Int.MAX_VALUE))
    }
}
