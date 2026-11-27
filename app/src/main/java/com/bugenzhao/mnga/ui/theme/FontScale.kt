package com.bugenzhao.mnga.ui.theme

import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Shared font-size scaling rules for topic lists and post content.
 *
 * Stored as integer percentages (80..150, step 5, default 100) so preferences
 * stay exact across persistence and stack cleanly on top of Android's system
 * `LocalDensity.fontScale`.
 */
object FontScale {
    const val MIN_PERCENT = 80
    const val MAX_PERCENT = 150
    const val DEFAULT_PERCENT = 100
    const val STEP_PERCENT = 5

    /** Intermediate tick count for Compose [androidx.compose.material3.Slider]. */
    const val SLIDER_STEPS = (MAX_PERCENT - MIN_PERCENT) / STEP_PERCENT - 1

    /** Exact normalized slider position of [DEFAULT_PERCENT] within `[MIN_PERCENT, MAX_PERCENT]`. */
    const val DEFAULT_FRACTION =
        (DEFAULT_PERCENT - MIN_PERCENT).toFloat() / (MAX_PERCENT - MIN_PERCENT).toFloat()

    /**
     * Compute the left X coordinate for an anchor label (e.g. "Standard" at 100%)
     * so its center lines up with the slider thumb at [fraction] while never
     * colliding with the leading or trailing edge labels.
     */
    fun sliderAnchorLabelX(
        totalWidthPx: Int,
        thumbWidthPx: Int,
        labelWidthPx: Int,
        leftLabelWidthPx: Int,
        rightLabelWidthPx: Int,
        minGapPx: Int,
        fraction: Float = DEFAULT_FRACTION,
    ): Int {
        val trackStart = thumbWidthPx / 2f
        val trackSpan = (totalWidthPx - thumbWidthPx).coerceAtLeast(0).toFloat()
        val anchorCenter = trackStart + trackSpan * fraction.coerceIn(0f, 1f)
        val rawX = (anchorCenter - labelWidthPx / 2f).roundToInt()
        val minX = leftLabelWidthPx + minGapPx
        val maxX = (totalWidthPx - rightLabelWidthPx - minGapPx - labelWidthPx).coerceAtLeast(minX)
        return rawX.coerceIn(minX, maxX)
    }

    const val TOPIC_TITLE_BASE_SP = 16f
    const val TOPIC_META_BASE_SP = 12f
    const val TOPIC_HEADER_BASE_SP = 22f
    const val POST_NORMAL_BASE_SP = 16f
    const val POST_SMALL_BASE_SP = 15f

    /** Clamp and snap a persisted/raw percentage onto the valid 5% grid. */
    fun normalizePercent(raw: Int): Int {
        val clamped = raw.coerceIn(MIN_PERCENT, MAX_PERCENT)
        val offset = ((clamped - MIN_PERCENT).toFloat() / STEP_PERCENT).roundToInt() * STEP_PERCENT
        return (MIN_PERCENT + offset).coerceIn(MIN_PERCENT, MAX_PERCENT)
    }

    fun scaleFactor(percent: Int): Float = normalizePercent(percent) / 100f

    /**
     * Dampened scale for secondary metadata (author/date/tags/replies) so
     * enlarging titles does not crowd single-line metadata bars.
     */
    fun gentleScale(
        scale: Float,
        weight: Float = 0.5f,
        min: Float = 0.9f,
        max: Float = 1.2f,
    ): Float = (1f + (scale - 1f) * weight).coerceIn(min, max)

    fun topicTitleFontSize(scale: Float): TextUnit = (TOPIC_TITLE_BASE_SP * scale).sp

    fun topicTitleLineHeight(scale: Float): TextUnit = (TOPIC_TITLE_BASE_SP * scale * 1.32f).sp

    fun topicMetaFontSize(scale: Float): TextUnit =
        (TOPIC_META_BASE_SP * gentleScale(scale)).sp

    fun topicMetaLineHeight(scale: Float): TextUnit =
        (TOPIC_META_BASE_SP * gentleScale(scale) * 1.33f).sp

    fun topicHeaderTitleFontSize(scale: Float): TextUnit =
        (TOPIC_HEADER_BASE_SP * gentleScale(scale, weight = 0.7f, min = 0.85f, max = 1.35f)).sp

    fun topicHeaderTitleLineHeight(scale: Float): TextUnit =
        (TOPIC_HEADER_BASE_SP * gentleScale(scale, weight = 0.7f, min = 0.85f, max = 1.35f) * 1.45f).sp

    fun postBaseFontSize(isSmall: Boolean, scale: Float): TextUnit =
        ((if (isSmall) POST_SMALL_BASE_SP else POST_NORMAL_BASE_SP) * scale).sp

    /**
     * One step smaller than normal body size for `[quote]` and inline reply
     * previews, preserving smaller inherited sizes (such as footnotes or nested
     * quotes) instead of snapping back to a hardcoded 16sp constant.
     */
    fun postQuotedFontSize(current: TextUnit, scale: Float): TextUnit {
        val normalSp = POST_NORMAL_BASE_SP * scale
        val subSp = POST_SMALL_BASE_SP * scale
        return when {
            current == TextUnit.Unspecified || !current.isSp -> subSp.sp
            current.value >= normalSp - 0.05f -> subSp.sp
            else -> current
        }
    }

    /** Tighter cap for fixed-width rating card numbers so badges never clip. */
    fun ratingCardScale(scale: Float): Float = scale.coerceIn(0.85f, 1.15f)

    /**
     * Compute a paragraph's line height from its resolved inline font sizes.
     *
     * Leaves line height unspecified when stickers, display-sized numerals
     * (>= 28sp), or mixed enlarged inline spans (`[size=...]`) are present so
     * only the tall line grows without overlapping adjacent lines.
     */
    fun paragraphLineHeight(
        pieceFontSizes: List<TextUnit>,
        hasSticker: Boolean,
        fallbackBaseFontSize: TextUnit,
    ): TextUnit {
        if (hasSticker) return TextUnit.Unspecified
        val explicitSp = pieceFontSizes.mapNotNull { unit ->
            if (unit != TextUnit.Unspecified && unit.isSp) unit.value else null
        }
        val fallbackSp = if (fallbackBaseFontSize != TextUnit.Unspecified && fallbackBaseFontSize.isSp) {
            fallbackBaseFontSize.value
        } else {
            POST_NORMAL_BASE_SP
        }
        val maxSp = explicitSp.maxOrNull() ?: fallbackSp
        val minSp = explicitSp.minOrNull() ?: fallbackSp
        if (maxSp >= 28f || maxSp > minSp * 1.15f) {
            return TextUnit.Unspecified
        }
        return (maxSp * 1.35f).sp
    }
}
