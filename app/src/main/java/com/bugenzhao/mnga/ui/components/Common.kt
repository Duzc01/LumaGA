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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun DateTimeText(timestampSeconds: Long) {
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Replies count with the tiered font styling of `RepliesNumView`. */
@Composable
fun RepliesNumText(replies: Int) {
    val style = when {
        replies >= 1000 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
        replies >= 300 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        replies >= 100 -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
        replies >= 40 -> MaterialTheme.typography.bodyMedium
        else -> MaterialTheme.typography.labelMedium
    }
    val color = when {
        replies >= 1000 -> MaterialTheme.colorScheme.primary
        replies >= 100 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(replies.toString(), style = style, color = color)
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
