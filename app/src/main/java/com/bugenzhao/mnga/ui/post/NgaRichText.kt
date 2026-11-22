package com.bugenzhao.mnga.ui.post

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.bugenzhao.mnga.protos.datamodel.Span
import kotlin.math.max
import kotlin.math.min

/**
 * Parsing and adaptation helpers for NGA's extended BBCode layout/card tags
 * (`[style ...]`, `[fixsize ...]`, `[comment ...]`, `[randomblock]`, etc.).
 */

internal data class NgaStyle(
    val background: Color? = null,
    val color: Color? = null,
    val fontLevel: Int? = null,
    val align: HAlign? = null,
    val floatLeft: Boolean = false,
    val floatRight: Boolean = false,
    val borderRadiusDp: Float? = null,
    val innerHtmlVar: String? = null,
    val dybgUrl: String? = null,
    val isBlockLayout: Boolean = false,
    val hasExplicitWidth: Boolean = false,
    val widthPercent: Boolean = false,
) {
    /** Approximate Compose font size for NGA's `[style font N]` scale. */
    fun resolvedFontSize(inRatingCard: Boolean = false): TextUnit = when (fontLevel) {
        null, 0 -> TextUnit.Unspecified
        1 -> 12.sp
        2 -> 14.sp
        3 -> if (inRatingCard) 32.sp else 19.sp
        4 -> if (inRatingCard) 44.sp else 24.sp
        else -> 26.sp
    }

    val isHeadingFont: Boolean
        get() = (fontLevel ?: 0) >= 3
}

internal fun findNgaScorePlaceholder(tagged: Span.Tagged): Span.Tagged? {
    if (tagged.tag == "style" &&
        tagged.complexAttributesList.windowed(2).any { (key, value) ->
            key.equals("innerHTML", ignoreCase = true) && value == "\$votedata_voteavgvalue"
        }
    ) {
        return tagged
    }
    return tagged.spansList.firstNotNullOfOrNull { span ->
        if (span.hasTagged()) findNgaScorePlaceholder(span.tagged) else null
    }
}

/** Parses a named NGA color or `#rgb` / `#rrggbb` / `#aarrggbb` hex color. */
internal fun parseNgaColor(raw: String): Color? {
    val token = raw.trim()
    if (token.isEmpty()) return null
    ContentPalette[token.lowercase()]?.let { return it }
    if (!token.startsWith('#')) return null
    val hex = token.substring(1)
    return when (hex.length) {
        3 -> {
            val r = hex[0].digitToIntOrNull(16) ?: return null
            val g = hex[1].digitToIntOrNull(16) ?: return null
            val b = hex[2].digitToIntOrNull(16) ?: return null
            Color(red = (r * 17) / 255f, green = (g * 17) / 255f, blue = (b * 17) / 255f)
        }
        6 -> {
            val rgb = hex.toLongOrNull(16) ?: return null
            Color(0xFF000000L or rgb)
        }
        8 -> {
            val argb = hex.toLongOrNull(16) ?: return null
            Color(argb)
        }
        else -> null
    }
}

/**
 * NGA web templates hardcode dark grays (`#444`, `#333`, `#000`) or `#fff`
 * assuming a light beige forum background. When rendered without an explicit
 * container background, those neutral colors become illegible in dark (or light)
 * mode — drop them so the active Material theme's `onSurface` takes over.
 */
internal fun isLowContrastNeutral(color: Color): Boolean {
    val chroma = max(color.red, max(color.green, color.blue)) -
        min(color.red, min(color.green, color.blue))
    if (chroma >= 0.08f) return false
    val lum = color.luminance()
    return lum < 0.35f || lum > 0.85f
}

/**
 * Parses NGA's space-delimited `[style ...]` / `[fixsize ...]` attribute list
 * (e.g. `float left margin 1 0 1 1 width 9 height 7 background #b22222 align center
 * border-radius 0.3 font 0 #fff`).
 */
internal fun parseNgaStyle(attrs: List<String>): NgaStyle {
    var background: Color? = null
    var color: Color? = null
    var fontLevel: Int? = null
    var align: HAlign? = null
    var floatLeft = false
    var floatRight = false
    var borderRadiusDp: Float? = null
    var innerHtmlVar: String? = null
    var dybgUrl: String? = null
    var isBlockLayout = false
    var hasExplicitWidth = false
    var widthPercent = false

    var i = 0
    while (i < attrs.size) {
        when (attrs[i].lowercase()) {
            "background", "background-color" -> {
                isBlockLayout = true
                val next = attrs.getOrNull(i + 1)
                val parsed = next?.let(::parseNgaColor)
                if (parsed != null) {
                    background = parsed
                    i += 2
                    // `[fixsize ... background #fff #fff]` sometimes passes a second theme color.
                    if (attrs.getOrNull(i)?.let(::parseNgaColor) != null) i += 1
                    continue
                }
            }
            "color" -> {
                val parsed = attrs.getOrNull(i + 1)?.let(::parseNgaColor)
                if (parsed != null) {
                    color = parsed
                    i += 2
                    continue
                }
            }
            "font" -> {
                var consumed = 1
                val first = attrs.getOrNull(i + 1)
                val level = first?.toIntOrNull()
                if (level != null) {
                    fontLevel = level
                    consumed += 1
                    val secondColor = attrs.getOrNull(i + 2)?.let(::parseNgaColor)
                    if (secondColor != null) {
                        color = secondColor
                        consumed += 1
                    }
                } else {
                    val firstColor = first?.let(::parseNgaColor)
                    if (firstColor != null) {
                        color = firstColor
                        consumed += 1
                    }
                }
                i += consumed
                continue
            }
            "align" -> {
                isBlockLayout = true
                when (attrs.getOrNull(i + 1)?.lowercase()) {
                    "center" -> {
                        align = HAlign.CENTER
                        i += 2
                        continue
                    }
                    "right" -> {
                        align = HAlign.END
                        i += 2
                        continue
                    }
                    "left", "justify", "justify-all" -> {
                        align = HAlign.START
                        i += 2
                        continue
                    }
                }
            }
            "float" -> {
                isBlockLayout = true
                when (attrs.getOrNull(i + 1)?.lowercase()) {
                    "left" -> {
                        floatLeft = true
                        i += 2
                        continue
                    }
                    "right" -> {
                        floatRight = true
                        i += 2
                        continue
                    }
                }
            }
            "border-radius" -> {
                isBlockLayout = true
                val raw = attrs.getOrNull(i + 1)?.trimEnd('p', 'x', 'e', 'm', '%')?.toFloatOrNull()
                if (raw != null && raw.isFinite() && raw > 0f) {
                    borderRadiusDp = (if (raw <= 2f) raw * 20f else raw).coerceIn(4f, 16f)
                    i += 2
                    continue
                }
            }
            "innerhtml" -> {
                val variable = attrs.getOrNull(i + 1)
                if (variable != null) {
                    innerHtmlVar = variable
                    i += 2
                    continue
                }
            }
            "dybg" -> {
                isBlockLayout = true
                val spec = attrs.getOrNull(i + 1)
                if (spec != null) {
                    val candidate = spec.split(';').lastOrNull { it.isNotBlank() }?.trim()
                    if (candidate != null && isLikelyImagePath(candidate)) {
                        dybgUrl = candidate
                    }
                    i += 2
                    continue
                }
            }
            "width" -> {
                isBlockLayout = true
                val next = attrs.getOrNull(i + 1)
                if (next != null) {
                    if (next.endsWith("%")) {
                        widthPercent = true
                    } else if (next.toFloatOrNull() != null) {
                        hasExplicitWidth = true
                    }
                }
            }
            "height", "margin", "padding", "line-height", "left", "top", "right", "bottom",
            "position", "display", "border", "overflow" -> {
                isBlockLayout = true
            }
        }
        i += 1
    }

    return NgaStyle(
        background = background,
        color = color,
        fontLevel = fontLevel,
        align = align,
        floatLeft = floatLeft,
        floatRight = floatRight,
        borderRadiusDp = borderRadiusDp,
        innerHtmlVar = innerHtmlVar,
        dybgUrl = dybgUrl,
        isBlockLayout = isBlockLayout,
        hasExplicitWidth = hasExplicitWidth,
        widthPercent = widthPercent,
    )
}

private fun isLikelyImagePath(s: String): Boolean {
    val lower = s.lowercase()
    return lower.startsWith("./") ||
        lower.startsWith("http://") ||
        lower.startsWith("https://") ||
        lower.endsWith(".png") ||
        lower.endsWith(".jpg") ||
        lower.endsWith(".jpeg") ||
        lower.endsWith(".gif") ||
        lower.endsWith(".webp")
}
