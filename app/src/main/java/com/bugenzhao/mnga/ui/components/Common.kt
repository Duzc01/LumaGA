package com.bugenzhao.mnga.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bugenzhao.mnga.LogicException
import com.bugenzhao.mnga.util.DateFormatters
import com.bugenzhao.mnga.util.L
import java.util.Date

/** A full-width row with an centered spinner, like `LoadingRowView`. */
@Composable
fun LoadingRow() {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Footer that shows loading or "no more" states, like `AdaptiveFooterView`. */
@Composable
fun AdaptiveFooter(loading: Boolean, noMore: Boolean) {
    when {
        loading -> LoadingRow()
        noMore -> Box(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                L.str(LocalContext.current, "No More Topics"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        else -> Spacer(Modifier.height(8.dp))
    }
}

/** Grouped-list style container, approximating iOS inset grouped lists. */
@Composable
fun GroupedList(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column { content() }
    }
}

/**
 * One inset-grouped settings row: the title carries an optional subtitle
 * underneath showing the current value, and the trailing slot holds at most
 * one accessory — a [RowChevron] or a switch. Deliberately has no leading
 * icon slot; the settings pages read as plain text lists.
 */
@Composable
fun GroupedRow(
    onClick: (() -> Unit)? = null,
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    // 56dp keeps single-line rows compact while still clearing the 48dp
    // minimum interactive size a Material switch brings along; rows with a
    // subtitle grow past it on their own.
    var rowModifier = Modifier.fillMaxWidth()
    if (onClick != null) {
        rowModifier = rowModifier.then(Modifier.clickable { onClick() })
    }
    Row(
        rowModifier
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp))
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        trailing?.invoke()
    }
}

/** The disclosure chevron of a [GroupedRow] that opens something. */
@Composable
fun RowChevron() {
    Icon(
        Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.outline,
        modifier = Modifier.size(18.dp),
    )
}

/**
 * Circular avatar image, degrading to a generated [InitialAvatar] built from
 * [name] when there is no usable avatar.
 */
@Composable
fun Avatar(url: String?, name: String? = null, size: Int = 40, onClick: (() -> Unit)? = null) {
    val image: @Composable () -> Unit = {
        AvatarImage(url = url, name = name.orEmpty(), size = size.dp)
    }
    if (onClick != null) {
        Box(Modifier.clip(CircleShape).clickable { onClick() }) { image() }
    } else {
        image()
    }
}

/** Timestamp text honoring the user's date-time strategy preference. */
@Composable
fun DateTimeText(
    timestampSeconds: Long,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val context = LocalContext.current
    val strategy = com.bugenzhao.mnga.App.prefs.postRowDateTimeStrategy
    val text = when (strategy) {
        com.bugenzhao.mnga.storage.DateTimeStrategy.DETAILED ->
            DateFormatters.detailed(context, Date(timestampSeconds * 1000))
        com.bugenzhao.mnga.storage.DateTimeStrategy.TIME_AGO ->
            DateFormatters.timeAgo(context, Date(timestampSeconds * 1000))
        else ->
            DateFormatters.automatic(context, Date(timestampSeconds * 1000))
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
    )
}

/**
 * "Hot topic" highlight of a [RepliesBadge]: a fixed warm orange rather than
 * the theme accent, so heat still reads as heat next to any user-chosen tint.
 * The ramp deepens towards dark red as the reply count climbs.
 */
private val HotRepliesLight = Color(0xFFFD7A19)
private val HotRepliesDark = Color(0xFFFF9A4D)

/** Deeper "very hot" orange (Material Deep Orange 800 / 400). */
private val HotDeepLight = Color(0xFFD84315)
private val HotDeepDark = Color(0xFFFF7043)

/** Deepest "on fire" dark red (Material Red 900 / A200). */
private val HotHottestLight = Color(0xFFB71C1C)
private val HotHottestDark = Color(0xFFFF5252)

/** Replies at or above this many are drawn highlighted. */
private const val HotRepliesThreshold = 100

/** Replies at or above this many move to the deeper orange tier. */
private const val HotDeepRepliesThreshold = 500

/** Replies at or above this many move to the deepest dark-red tier. */
private const val HotHottestRepliesThreshold = 1000

/** The muted gray shared by a topic row's metadata line. */
@Composable
fun topicMetaColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)

/**
 * The app's "heat" accent — the same warm orange a hot [RepliesBadge] uses,
 * fixed rather than themed so heat reads as heat beside any user tint. Shared
 * so every hot marker in the app burns the same color.
 */
@Composable
fun hotAccentColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) HotRepliesDark else HotRepliesLight

/**
 * Replies count with a leading bubble icon, highlighted once the topic gets
 * hot and deepening through orange to dark red as the count climbs. [delta]
 * is the number of replies added since the last visit, appended in the accent
 * color when known.
 */
@Composable
fun RepliesBadge(replies: Int, delta: Int? = null) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val hot = replies >= HotRepliesThreshold
    val color = when {
        replies >= HotHottestRepliesThreshold -> if (dark) HotHottestDark else HotHottestLight
        replies >= HotDeepRepliesThreshold -> if (dark) HotDeepDark else HotDeepLight
        hot -> hotAccentColor()
        else -> topicMetaColor()
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Outlined.ChatBubbleOutline,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color,
        )
        Text(
            replies.toString(),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = when {
                    replies >= HotHottestRepliesThreshold -> FontWeight.ExtraBold
                    hot -> FontWeight.Bold
                    else -> FontWeight.Medium
                },
            ),
            color = color,
            // The digit glyphs sit ~1dp below the glyph center in the line
            // box, which reads as off-center against the bubble icon.
            modifier = Modifier.offset(y = (-1).dp),
        )
        if (delta != null) {
            Text(
                "+$delta",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                // 与主数字一致：小号字形的视觉中心偏低约 1dp。
                modifier = Modifier.offset(y = (-1).dp),
            )
        }
    }
}

/** Placeholder for never-loaded/empty/error list states. */
@Composable
fun ListPlaceholder(text: String) {
    Box(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Error placeholder row with retry. */
@Composable
fun ErrorPlaceholder(error: LogicException, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            error.error,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.primary,
            onClick = onRetry,
        ) {
            Text(
                L.str(LocalContext.current, "Retry"),
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}
