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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.NavigationIdentifier
import com.bugenzhao.mnga.model.display
import com.bugenzhao.mnga.protos.datamodel.Subject
import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.protos.datamodel.UserName
import com.bugenzhao.mnga.storage.BlockWordsStorage
import com.bugenzhao.mnga.ui.components.DateTimeText
import com.bugenzhao.mnga.ui.components.RepliesNumText
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
) {
    val context = LocalContext.current
    val tags = topicTags(topic)
    val content = topicSubjectContent(topic)
    val showTagBar = tags.isNotEmpty() || topic.hasParentForum() ||
        (showIndicators && topic.isFavored)

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
            fontWeight = fontWeight ?: FontWeight.Medium,
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
 * The standard topic row, a port of `TopicRowView` + `TopicLikeRowInnerView`:
 * subject with tag bar and replies badge on the first line, authors and
 * timestamp on the adaptive footer line.
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
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.weight(1f)) {
                    if (blocked) {
                        BlockedSubjectView()
                    } else {
                        TopicSubjectView(
                            topic = topic,
                            maxLines = 2,
                            showIndicators = showIndicators,
                            dimmed = shouldDim,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Row(verticalAlignment = Alignment.Top) {
                    RepliesNumText(replies = num)
                    if (delta != null) {
                        Text(
                            "(+$delta)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Adaptive footer: leading authors, trailing timestamp.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AuthorNames(topic = topic)
                }
                Spacer(Modifier.width(8.dp))
                DateTimeText(timestampSeconds = date)
            }
        }
    }
}

@Composable
private fun AuthorNames(topic: Topic) {
    val name = topicAuthorName(topic)
    val display = name.display()
    if (display.isEmpty()) return
    val anonymous = name.anonymous.isNotEmpty()
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(
            if (anonymous) Icons.Outlined.TheaterComedy else Icons.Outlined.Person,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            display,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
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
