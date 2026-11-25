package com.bugenzhao.mnga.ui.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FontScaleTest {

    @Test
    fun normalizePercentClampsAndSnapsToStepGrid() {
        assertEquals(FontScale.MIN_PERCENT, FontScale.normalizePercent(50))
        assertEquals(FontScale.MAX_PERCENT, FontScale.normalizePercent(200))
        assertEquals(FontScale.DEFAULT_PERCENT, FontScale.normalizePercent(100))
        assertEquals(105, FontScale.normalizePercent(103))
        assertEquals(100, FontScale.normalizePercent(102))
        assertEquals(115, FontScale.normalizePercent(117))
    }

    @Test
    fun topicTypographyScalesTitleAndDampensMetadata() {
        val defaultTitle = FontScale.topicTitleFontSize(1f)
        val largeTitle = FontScale.topicTitleFontSize(1.5f)
        assertEquals(16f, defaultTitle.value, 0.01f)
        assertEquals(24f, largeTitle.value, 0.01f)
        assertTrue(FontScale.topicTitleLineHeight(1.5f).value > largeTitle.value * 1.25f)

        val defaultMeta = FontScale.topicMetaFontSize(1f)
        val largeMeta = FontScale.topicMetaFontSize(1.5f)
        val smallMeta = FontScale.topicMetaFontSize(0.8f)
        assertEquals(12f, defaultMeta.value, 0.01f)
        // Dampened metadata stays capped below full 1.5x (18sp) so single-line rows stay clean.
        assertEquals(14.4f, largeMeta.value, 0.01f)
        assertEquals(10.8f, smallMeta.value, 0.01f)
    }

    @Test
    fun quotedFontSizeScalesWithPostFontScale() {
        val scale = 1.25f
        val bodySize = FontScale.postBaseFontSize(isSmall = false, scale = scale)
        assertEquals(20f, bodySize.value, 0.01f)

        // Normal body steps down one notch inside quotes instead of snapping to hardcoded 16sp.
        val quotedFromNormal = FontScale.postQuotedFontSize(bodySize, scale)
        assertEquals(18.75f, quotedFromNormal.value, 0.01f)

        // Nested quotes keep their stepped-down size rather than jumping back up.
        val nestedQuoted = FontScale.postQuotedFontSize(quotedFromNormal, scale)
        assertEquals(18.75f, nestedQuoted.value, 0.01f)

        // Unspecified falls back to scaled subheadline.
        assertEquals(18.75f, FontScale.postQuotedFontSize(TextUnit.Unspecified, scale).value, 0.01f)
    }

    @Test
    fun paragraphLineHeightAdaptsToScaleAndTallRuns() {
        // Uniform enlarged body text gets proportional line height rather than hardcoded 20sp.
        val largeUniform = FontScale.paragraphLineHeight(
            pieceFontSizes = listOf(24.sp, 24.sp),
            hasSticker = false,
            fallbackBaseFontSize = 24.sp,
        )
        assertEquals(32.4f, largeUniform.value, 0.01f)

        // Stickers release line height so the tall inline box doesn't clip.
        val withSticker = FontScale.paragraphLineHeight(
            pieceFontSizes = listOf(16.sp),
            hasSticker = true,
            fallbackBaseFontSize = 16.sp,
        )
        assertEquals(TextUnit.Unspecified, withSticker)

        // Mixed [size=...] inline runs release line height so only the enlarged line grows.
        val mixedSizes = FontScale.paragraphLineHeight(
            pieceFontSizes = listOf(16.sp, 24.sp),
            hasSticker = false,
            fallbackBaseFontSize = 16.sp,
        )
        assertEquals(TextUnit.Unspecified, mixedSizes)

        // Display numerals (>= 28sp, e.g. rating card score) release line height.
        val displayScore = FontScale.paragraphLineHeight(
            pieceFontSizes = listOf(44.sp),
            hasSticker = false,
            fallbackBaseFontSize = 16.sp,
        )
        assertEquals(TextUnit.Unspecified, displayScore)
    }
}
