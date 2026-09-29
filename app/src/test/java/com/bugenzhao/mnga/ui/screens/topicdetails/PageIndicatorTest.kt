package com.bugenzhao.mnga.ui.screens.topicdetails

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Local JVM tests for the page-indicator pure helpers.
 */
class PageIndicatorTest {

    @Test
    fun `progress fraction spans 0 to 1 across pages`() {
        assertEquals(0f, pageProgressFraction(1, 10), 0.0001f)
        assertEquals(1f, pageProgressFraction(10, 10), 0.0001f)
        assertEquals(4f / 9f, pageProgressFraction(5, 10), 0.0001f)
    }

    @Test
    fun `progress fraction is 0 for single page`() {
        assertEquals(0f, pageProgressFraction(1, 1), 0.0001f)
    }

    @Test
    fun `list width fits all pages when few`() {
        // 3 pages * 48dp = 144dp, under the 400 - 64 cap.
        assertEquals(144, pageListWidthDp(totalPages = 3, screenWidthDp = 400))
    }

    @Test
    fun `list width is capped by screen width`() {
        // 100 pages would need 4800dp; capped at 400 - 64.
        assertEquals(336, pageListWidthDp(totalPages = 100, screenWidthDp = 400))
    }

    @Test
    fun `list width has a 3-item minimum`() {
        assertEquals(144, pageListWidthDp(totalPages = 1, screenWidthDp = 400))
        assertEquals(96, pageListWidthDp(totalPages = 2, screenWidthDp = 400, itemWidthDp = 32))
    }
}
