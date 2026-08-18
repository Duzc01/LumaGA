package com.bugenzhao.mnga.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import java.util.Locale

/**
 * Generated fallback avatars: a colored disc carrying the first character of
 * the user/forum name, used wherever NGA gives us no avatar, a placeholder
 * avatar, or an image that fails to load.
 */

/**
 * Substrings that mark a remote image as a stock placeholder rather than a
 * real avatar. NGA serves its defaults as ordinary, successfully-loading
 * images, so they can only be recognised by path — extend this list when a new
 * placeholder path shows up. Matched case-insensitively against the full URL.
 */
private val DefaultAvatarMarkers = listOf(
    "avatar_default",
    "default_avatar",
    "nga_avatar",
    "/avatars/default",
    "default_forum_icon",
    "ficon/default",
)

/**
 * True when [url] cannot yield a real avatar: blank, or a known placeholder
 * path. Load failures are handled separately by [AvatarImage], which also
 * degrades to a generated avatar.
 */
fun isDefaultAvatarUrl(url: String?): Boolean {
    val trimmed = url?.trim().orEmpty()
    if (trimmed.isEmpty()) return true
    val lower = trimmed.lowercase(Locale.ROOT)
    return DefaultAvatarMarkers.any { it in lower }
}

/**
 * The character to draw for [name]: the first letter or digit, so decorated
 * names like `【喵】` or `_bugen` give `喵` / `B` instead of the bracket or
 * underscore. Falls back to the first character of any kind, then to `?`.
 */
fun avatarInitial(name: String): String {
    val trimmed = name.trim()
    if (trimmed.isEmpty()) return "?"
    val first = trimmed.firstCodePointWhere { Character.isLetterOrDigit(it) }
        ?: trimmed.firstCodePointWhere { !Character.isWhitespace(it) }
        ?: return "?"
    val upper = first.uppercase(Locale.ROOT)
    // Guard against expanding uppercase forms ("ß" -> "SS") — keep one glyph.
    return if (upper.codePointCount(0, upper.length) == 1) upper else first
}

private inline fun String.firstCodePointWhere(predicate: (Int) -> Boolean): String? {
    var i = 0
    while (i < length) {
        val cp = codePointAt(i)
        if (predicate(cp)) return String(Character.toChars(cp))
        i += Character.charCount(cp)
    }
    return null
}

/** Disc fill plus the rim drawn around it, both derived from one name. */
data class AvatarPalette(val fill: Color, val ring: Color)

/**
 * FNV-1a over the UTF-16 units. Deliberately not [String.hashCode]: an
 * explicit hash keeps a name's color pinned across releases.
 */
private fun stableHash(seed: String): Int {
    var hash = -0x7ee3623b // 2166136261, the FNV-1a 32-bit offset basis
    for (char in seed) {
        hash = hash xor char.code
        hash *= 16777619
    }
    return hash
}

/** WCAG contrast ratio of white text on [background]. */
private fun contrastWithWhite(background: Color): Float =
    1.05f / (background.luminance() + 0.05f)

/**
 * Stable color for [seed]: same name, same color, every launch. The hue comes
 * straight from the hash; the lightness starts theme-appropriate and is walked
 * down until white text clears the 3:1 WCAG bar for large text, because bright
 * hues (yellow, cyan) render far lighter than blues at equal HSL lightness.
 */
fun avatarPalette(seed: String, dark: Boolean): AvatarPalette {
    val hash = stableHash(seed)
    val hue = (hash and 0x7FFFFFFF) % 360
    // Some saturation spread so same-hue neighbours stay distinguishable.
    val saturation = 0.54f + ((hash ushr 16) and 0xFF) / 255f * 0.22f
    var lightness = if (dark) 0.46f else 0.42f
    var fill = Color.hsl(hue.toFloat(), saturation, lightness)
    while (lightness > 0.16f && contrastWithWhite(fill) < 3.2f) {
        lightness -= 0.02f
        fill = Color.hsl(hue.toFloat(), saturation, lightness)
    }
    // Brighter same-hue rim: reads as a colored border on light and dark
    // surfaces alike, where a darker rim would vanish against the dark theme.
    val ring = Color.hsl(hue.toFloat(), saturation, (lightness + 0.20f).coerceAtMost(0.78f))
    return AvatarPalette(fill = fill, ring = ring)
}

/**
 * CJK and full-width glyphs already fill their em box; latin capitals need to
 * be a touch larger to look optically the same size.
 */
private fun initialFontScale(initial: String): Float =
    if (initial.codePointAt(0) >= 0x2E80) 0.44f else 0.50f

/**
 * A generated avatar for [name]: a [size] disc filled with the name's stable
 * color, rimmed in a brighter shade of it, with the name's first character
 * centered in white.
 */
@Composable
fun InitialAvatar(
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentDescription: String? = null,
) {
    val initial = remember(name) { avatarInitial(name) }
    // Derive darkness from the scheme rather than the system: the app has its
    // own light/dark override in preferences.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val palette = remember(name, dark) { avatarPalette(name, dark) }
    val fontSize = with(LocalDensity.current) { (size * initialFontScale(initial)).toSp() }
    val ringWidth = (size.value * 0.055f).coerceIn(1f, 2.5f).dp
    val label = contentDescription

    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(palette.fill)
            .border(ringWidth, palette.ring, shape)
            .then(
                // `this.` is required: the bare name would bind to the parameter.
                if (label != null) Modifier.semantics { this.contentDescription = label } else Modifier
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initial,
            style = TextStyle(
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * Remote avatar for [name] that degrades to [InitialAvatar] (or [fallback],
 * when the caller wants something else) whenever [url] is blank, points at a
 * known placeholder, or fails to load.
 */
@Composable
fun AvatarImage(
    url: String?,
    name: String,
    size: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    contentDescription: String? = null,
    fallback: (@Composable () -> Unit)? = null,
) {
    val degraded: @Composable () -> Unit = fallback ?: {
        InitialAvatar(
            name = name,
            size = size,
            modifier = modifier,
            shape = shape,
            contentDescription = contentDescription,
        )
    }

    var failed by remember(url) { mutableStateOf(false) }
    if (failed || isDefaultAvatarUrl(url)) {
        degraded()
        return
    }
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.size(size).clip(shape),
        onState = { state -> if (state is AsyncImagePainter.State.Error) failed = true },
    )
}
