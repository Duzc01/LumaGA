package com.bugenzhao.mnga.ui.screens.topiclist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.AvatarUrls
import com.bugenzhao.mnga.model.NavigationIdentifier
import com.bugenzhao.mnga.model.display
import com.bugenzhao.mnga.protos.datamodel.Subject
import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.protos.datamodel.UserName
import com.bugenzhao.mnga.storage.BlockWordsStorage
import com.bugenzhao.mnga.ui.components.AvatarImage
import com.bugenzhao.mnga.ui.components.DateTimeText
import com.bugenzhao.mnga.ui.components.RepliesBadge
import com.bugenzhao.mnga.ui.components.avatarPalette
import com.bugenzhao.mnga.ui.components.topicMetaColor
import com.bugenzhao.mnga.util.L
import com.bugenzhao.mnga.util.URLs

/** Subject color palette, mirroring `ContentCombiner.palette`. */
private val SubjectRed = Color(0xFFDD0000)
private val SubjectBlue = Color(0xFF0066BB)
private val SubjectGreen = Color(0xFF3D9F0E)
private val SubjectOrange = Color(0xFFA06700)
private val SubjectSilver = Color(0xFF888888)

/** Default 28dp forum icon bundled as an asset. */
const val DefaultForumIconAsset = "file:///android_asset/misc/default_forum_icon.png"

// region topic field compat helpers (Extensions.swift)

/** `subject.content` falling back to the legacy `subjectContent` field. */
fun topicSubjectContent(topic: Topic): String =
    topic.subject.content.ifEmpty { topic.subjectContent }

/** `subject.tags` falling back to the legacy `tags` field. */
fun topicTags(topic: Topic): List<String> =
    topic.subject.tagsList.ifEmpty { topic.tagsList }

/** The full subject string: joined tags plus content. */
fun topicSubjectFull(topic: Topic): String =
    topicTags(topic).joinToString("") { "[$it] " } + topicSubjectContent(topic)

/** `authorNameCompat`: display name falling back to the legacy raw field. */
fun topicAuthorName(topic: Topic): UserName =
    if (topic.authorName.display().isEmpty()) {
        UserName.newBuilder().setNormal(topic.authorNameRaw).build()
    } else {
        topic.authorName
    }

/** Deep-link identity of a topic, `fav` code included when present. */
fun topicNavID(topic: Topic): NavigationIdentifier.TopicID =
    NavigationIdentifier.TopicID(topic.id, topic.fav.takeIf { it.isNotEmpty() })

/** True for built-in meta topics ("mnga_..." ids). */
fun Topic.isMNGAMockID(): Boolean = id.startsWith("mnga_")

// endregion

/**
 * Subject line (tags + colored content), a port of `TopicSubjectView` and
 * `TopicSubjectContentInnerView`.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopicSubjectView(
    topic: Topic,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    showIndicators: Boolean = false,
    dimmed: Boolean = false,
    /** False where the caller shows the forum and tags itself, as [TopicRow]
     * does on its metadata line. */
    tagBar: Boolean = true,
) {
    val context = LocalContext.current
    val tags = topicTags(topic)
    val content = topicSubjectContent(topic)
    val showTagBar = tagBar && (
        tags.isNotEmpty() || topic.hasParentForum() ||
            (showIndicators && topic.isFavored)
        )

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (showTagBar) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (showIndicators && topic.isFavored) {
                    Icon(
                        Icons.Outlined.Bookmark,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (topic.hasParentForum()) {
                    Text(
                        topic.parentForum.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
                tags.forEach { tag ->
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                    )
                }
            }
        }

        if (content.isEmpty()) {
            Text(
                L.str(context, "Untitled"),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            SubjectContentText(
                content = content,
                modifiers = topic.subject.fontModifiersList,
                dimmed = dimmed,
                maxLines = maxLines,
            )
        }
    }
}

@Composable
private fun SubjectContentText(
    content: String,
    modifiers: List<Subject.FontModifier>,
    dimmed: Boolean,
    maxLines: Int,
) {
    val multicolor = App.prefs.topicListSubjectMulticolor.value
    var color: Color? = null
    var fontWeight: FontWeight? = null
    var italic = false
    var underline = false
    if (multicolor) {
        val alpha = if (dimmed) 0.6f else 1f
        for (modifier in modifiers) {
            when (modifier) {
                Subject.FontModifier.RED -> color = SubjectRed.copy(alpha = alpha)
                Subject.FontModifier.BLUE -> color = SubjectBlue.copy(alpha = alpha)
                Subject.FontModifier.GREEN -> color = SubjectGreen.copy(alpha = alpha)
                Subject.FontModifier.ORANGE -> color = SubjectOrange.copy(alpha = alpha)
                Subject.FontModifier.SILVER -> color = SubjectSilver.copy(alpha = alpha)
                Subject.FontModifier.SEMIBOLD -> fontWeight = FontWeight.SemiBold
                Subject.FontModifier.BOLD -> fontWeight = FontWeight.Bold
                Subject.FontModifier.ITALIC -> italic = true
                Subject.FontModifier.UNDERLINE -> underline = true
                else -> {}
            }
        }
    }
    Text(
        content,
        style = MaterialTheme.typography.bodyLarge.copy(
            // One step below `bodyLarge`'s 17sp: a bold two-line headline at
            // the body size reads too heavy above the 12sp metadata lines.
            fontSize = 16.sp,
            lineHeight = 21.sp,
            fontWeight = fontWeight ?: FontWeight.Bold,
            fontStyle = if (italic) FontStyle.Italic else null,
            textDecoration = if (underline) TextDecoration.Underline else null,
        ),
        color = color
            ?: if (dimmed) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Redacted placeholder bars shown for subjects matched by block words. */
@Composable
fun BlockedSubjectView(modifier: Modifier = Modifier) {
    val barColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.fillMaxWidth(0.8f).height(14.dp)
                .background(barColor, RoundedCornerShape(4.dp))
        )
        Box(
            Modifier.fillMaxWidth(0.5f).height(14.dp)
                .background(barColor, RoundedCornerShape(4.dp))
        )
    }
}

/**
 * The standard topic row: a bold two-line subject headline over one metadata
 * block — the author's avatar and name with the timestamp opposite, the forum
 * and tags with the replies badge opposite.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TopicRow(
    topic: Topic,
    modifier: Modifier = Modifier,
    useTopicPostDate: Boolean = false,
    dimmedSubject: Boolean = true,
    showIndicators: Boolean = true,
    isFavored: Boolean? = null,
    /** Forum shown on the metadata line for topics that name no parent forum
     * of their own — the forum currently being browsed, where there is one. */
    fallbackForumName: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val blocked = App.blockWords.blocked(BlockWordsStorage.content(topic))
    val favored = isFavored ?: topic.isFavored
    val shouldDim = dimmedSubject && !topic.isMNGAMockID() && topic.hasRepliesNumLastVisit()
    val date = if (useTopicPostDate) topic.postDate else topic.lastPostDate
    val num = topic.repliesNum
    val delta = if (topic.hasRepliesNumLastVisit() && num > topic.repliesNumLastVisit) {
        num - topic.repliesNumLastVisit
    } else {
        null
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                enabled = true,
                onClick = { onClick?.invoke() },
                onLongClick = { onLongClick?.invoke() },
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (blocked) {
                BlockedSubjectView()
            } else {
                TopicSubjectView(
                    topic = topic,
                    maxLines = 2,
                    showIndicators = showIndicators,
                    dimmed = shouldDim,
                    // The forum and tags live on the metadata line below.
                    tagBar = false,
                )
            }

            TopicMetaBlock(
                topic = topic,
                date = date,
                replies = num,
                delta = delta,
                showFavored = showIndicators && favored,
                fallbackForumName = fallbackForumName,
            )
        }
    }
}

/**
 * The two metadata lines under a subject, sharing one avatar: author name and
 * timestamp on top, forum and tags with the replies badge below.
 */
@Composable
private fun TopicMetaBlock(
    topic: Topic,
    date: Long,
    replies: Int,
    delta: Int?,
    showFavored: Boolean,
    fallbackForumName: String?,
) {
    val name = topicAuthorName(topic)
    val display = name.display()
    val anonymous = name.anonymous.isNotEmpty()
    val meta = topicMetaColor()

    Row(verticalAlignment = Alignment.CenterVertically) {
        AvatarImage(
            url = authorAvatarUrl(topic.authorId, anonymous),
            name = display,
            size = 32.dp,
        )
        Spacer(Modifier.width(8.dp))
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (display.isEmpty()) {
                    Spacer(Modifier.weight(1f))
                } else {
                    Text(
                        display,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.width(8.dp))
                DateTimeText(timestampSeconds = date, color = meta)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TopicOriginLine(
                    topic = topic,
                    showFavored = showFavored,
                    color = meta,
                    fallbackForumName = fallbackForumName,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                RepliesBadge(replies = replies, delta = delta)
            }
        }
    }
}

/** Favorite marker, parent forum (color-chipped) and subject tags, one line. */
@Composable
private fun TopicOriginLine(
    topic: Topic,
    showFavored: Boolean,
    color: Color,
    fallbackForumName: String?,
    modifier: Modifier = Modifier,
) {
    val tags = topicTags(topic)
    val forumName = if (topic.hasParentForum()) {
        topic.parentForum.name
    } else {
        fallbackForumName.orEmpty()
    }
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showFavored) {
            Icon(
                Icons.Outlined.Bookmark,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (forumName.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                ForumColorChip(seed = forumName)
                Text(
                    forumName,
                    style = MaterialTheme.typography.labelMedium,
                    color = color,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (tags.isNotEmpty()) {
            Text(
                tags.joinToString(" ") { "#$it" },
                style = MaterialTheme.typography.labelMedium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
    }
}

/** Small rounded chip in the forum's own stable color, keyed by its name. */
@Composable
private fun ForumColorChip(seed: String) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val color = remember(seed, dark) { avatarPalette(seed, dark).fill }
    Box(Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
}

/**
 * The author's avatar URL, or null until [AvatarUrls] answers (and forever for
 * anonymous authors and authors who have none), leaving the caller to draw a
 * generated disc in the meantime.
 */
@Composable
private fun authorAvatarUrl(authorId: String, anonymous: Boolean): String? {
    val lookupId = if (anonymous) "" else authorId
    var url by remember(lookupId) { mutableStateOf<String?>(null) }
    LaunchedEffect(lookupId) {
        url = AvatarUrls.resolve(lookupId)
    }
    return url
}

/** Copy [text] onto the system clipboard. */
fun copyToClipboard(context: Context, text: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("LumaGA", text))
}

/** Share plain text through the Android share sheet. */
fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

/** Open [url] with the default browser. */
fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    context.startActivity(intent)
}

/** Resolve a forum icon URL against the NGA resource host. */
fun forumIconModel(iconUrl: String): String? =
    iconUrl.takeIf { it.isNotEmpty() }?.let { URLs.resourceURL(it) }
