package com.bugenzhao.mnga.ui.screens.forumlist

import androidx.compose.foundation.combinedClickable

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SubdirectoryArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.protos.datamodel.Forum
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.ui.components.AvatarImage
import com.bugenzhao.mnga.ui.components.InitialAvatar
import com.bugenzhao.mnga.ui.components.isDefaultAvatarUrl
import com.bugenzhao.mnga.ui.screens.topiclist.DefaultForumIconAsset
import com.bugenzhao.mnga.ui.screens.topiclist.TopicSubjectView
import com.bugenzhao.mnga.ui.screens.topiclist.forumIconModel

/** Stable string key for a forum id ("f<fid>" or "st<stid>"). */
fun forumIdKey(id: ForumId): String =
    if (id.hasFid()) "f${id.fid}" else "st${id.stid}"

/** Debug description like "#650" / "st#42", mirroring `Forum.idDescription`. */
fun ForumId.idDescription(): String =
    if (hasFid()) "#$fid" else "st#$stid"

/**
 * 28dp forum icon, a port of `ForumIconView`. Forums NGA gives no icon for (or
 * whose icon fails to load) get a generated [InitialAvatar] from [name]
 * instead of the bundled placeholder every such forum used to share; the
 * bundled icon is still the last resort for nameless forums.
 */
@Composable
fun ForumIcon(iconUrl: String, name: String, modifier: Modifier = Modifier) {
    val model = forumIconModel(iconUrl)
    val generated: (@Composable () -> Unit)? = if (name.isBlank()) {
        null
    } else {
        { InitialAvatar(name = name, size = 28.dp, modifier = modifier) }
    }
    if (generated != null && isDefaultAvatarUrl(model)) {
        generated()
        return
    }
    AvatarImage(
        url = model ?: DefaultForumIconAsset,
        name = name,
        size = 28.dp,
        // Real icons keep their squarish frame; the generated one stays a disc.
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
        fallback = generated,
    )
}

/**
 * Single-line forum row, a port of `ForumRowView`: icon, localized name, ST or
 * shortcut indicator, trailing info and favorite star.
 */
@Composable
fun ForumRow(
    forum: Forum,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    asTopicShortcut: Topic? = null,
) {
    if (asTopicShortcut != null) {
        // Topic-shortcut variant: subject replaces name and info.
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            TopicSubjectView(
                topic = asTopicShortcut,
                modifier = Modifier.weight(1f),
                maxLines = Int.MAX_VALUE,
                showIndicators = false,
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.SubdirectoryArrowRight,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            ForumIcon(iconUrl = forum.iconUrl, name = forum.name)
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            ForumIcon(iconUrl = forum.iconUrl, name = forum.name)
            Spacer(Modifier.width(12.dp))
            Text(
                forum.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (forum.id.hasStid()) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Outlined.Layers,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            if (isFavorite) {
                Icon(
                    Icons.Outlined.Star,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
            }
            if (forum.info.isNotEmpty()) {
                Text(
                    forum.info,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(120.dp),
                )
            }
        }
    }
}

/** Card container wrapping [ForumRow] for inset-grouped lists. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ForumRowCard(
    forum: Forum,
    modifier: Modifier = Modifier,
    isFavorite: Boolean = false,
    asTopicShortcut: Topic? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier
            .combinedClickable(
                enabled = true,
                onClick = { onClick?.invoke() },
                onLongClick = { onLongClick?.invoke() },
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        ForumRow(
            forum = forum,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            isFavorite = isFavorite,
            asTopicShortcut = asTopicShortcut,
        )
    }
}
