package com.bugenzhao.mnga.ui.post

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.bugenzhao.mnga.protos.datamodel.Span
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NgaRichTextTest {

    @Test
    fun parseScoreCardStyleAttributes() {
        val attrs = listOf(
            "float", "left",
            "margin", "1", "0", "1", "1",
            "width", "9",
            "height", "7",
            "background", "#b22222",
            "align", "center",
            "border-radius", "0.3",
            "font", "0", "#fff",
        )
        val style = parseNgaStyle(attrs)
        assertTrue(style.floatLeft)
        assertFalse(style.floatRight)
        assertTrue(style.hasExplicitWidth)
        assertEquals(Color(0xFFB22222), style.background)
        assertEquals(Color(0xFFFFFFFF), style.color)
        assertEquals(0, style.fontLevel)
        assertEquals(HAlign.CENTER, style.align)
        assertEquals(6f, style.borderRadiusDp!!, 0.01f)
    }

    @Test
    fun parseHeadingAndVotePlaceholderStyles() {
        val heading = parseNgaStyle(listOf("font", "3", "#444", "line-height", "1", "width", "100%"))
        assertEquals(3, heading.fontLevel)
        assertEquals(19.sp, heading.resolvedFontSize())
        assertEquals(32.sp, heading.resolvedFontSize(inRatingCard = true))
        assertTrue(heading.isHeadingFont)
        assertEquals(Color(0xFF444444), heading.color)
        assertTrue(isLowContrastNeutral(heading.color!!))
        assertTrue(heading.widthPercent)
        assertFalse(heading.hasExplicitWidth)

        val vote = parseNgaStyle(listOf("font", "4", "line-height", "1.3", "innerHTML", "\$votedata_voteavgvalue"))
        assertEquals(4, vote.fontLevel)
        assertEquals(44.sp, vote.resolvedFontSize(inRatingCard = true))
        assertEquals("\$votedata_voteavgvalue", vote.innerHtmlVar)
    }

    @Test
    fun parseDybgBannerStyle() {
        val banner = parseNgaStyle(
            listOf(
                "height", "100%", "width", "100%",
                "dybg", "110%;100%;50%;0%;0%;./mon_202110/31/-10yuu8Q9vk-68x7K12T3cS1ls-a0.png",
            ),
        )
        assertEquals("./mon_202110/31/-10yuu8Q9vk-68x7K12T3cS1ls-a0.png", banner.dybgUrl)
    }

    @Test
    fun parseColorsAndContrastCheck() {
        assertEquals(Color(0xFFB22222), parseNgaColor("#b22222"))
        assertEquals(Color(0xFFFFFFFF), parseNgaColor("#fff"))
        assertEquals(Color(0xFF444444), parseNgaColor("#444"))
        assertEquals(ContentPalette["crimson"], parseNgaColor("crimson"))
        assertNull(parseNgaColor("not-a-color"))

        assertTrue(isLowContrastNeutral(Color(0xFF444444)))
        assertTrue(isLowContrastNeutral(Color(0xFFFFFFFF)))
        assertFalse(isLowContrastNeutral(Color(0xFFB22222)))
        assertFalse(isLowContrastNeutral(Color(0xFF0066BB)))
    }

    @Test
    fun findsActualScoreSpanInsideBadge() {
        val score = Span.Tagged.newBuilder()
            .setTag("style")
            .addAllComplexAttributes(listOf("font", "4", "innerHTML", "\$votedata_voteavgvalue"))
            .addSpans(Span.newBuilder().setPlain(Span.Plain.newBuilder().setText("8.8")))
            .build()
        val voteBadge = Span.Tagged.newBuilder()
            .setTag("style")
            .addAllComplexAttributes(listOf("background", "#b22222"))
            .addSpans(Span.newBuilder().setTagged(score))
            .build()
        assertEquals(score, findNgaScorePlaceholder(voteBadge))
        assertNull(findNgaScorePlaceholder(voteBadge.toBuilder().clearSpans().build()))
    }
}
