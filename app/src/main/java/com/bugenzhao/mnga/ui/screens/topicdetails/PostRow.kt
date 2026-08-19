package com.bugenzhao.mnga.ui.screens.topicdetails

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationSearching
import androidx.compose.material.icons.filled.MapsUgc
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.model.NavigationIdentifier
import com.bugenzhao.mnga.model.PlusFeature
import com.bugenzhao.mnga.model.PlusModel
import com.bugenzhao.mnga.model.QuotedPostResolver
import com.bugenzhao.mnga.model.TopicDetailsActionModel
import com.bugenzhao.mnga.model.VotesModel
import com.bugenzhao.mnga.model.ViewingImageModel
import com.bugenzhao.mnga.model.display
import com.bugenzhao.mnga.protos.datamodel.Device
import com.bugenzhao.mnga.protos.datamodel.Forum
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.protos.datamodel.Post
import com.bugenzhao.mnga.protos.datamodel.PostId
import com.bugenzhao.mnga.protos.datamodel.UserName
import com.bugenzhao.mnga.protos.datamodel.User
import com.bugenzhao.mnga.protos.datamodel.VoteState
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.PostVoteRequest
import com.bugenzhao.mnga.protos.service.PostVoteResponse
import com.bugenzhao.mnga.storage.BlockWordsStorage
import com.bugenzhao.mnga.ui.components.AvatarImage
import com.bugenzhao.mnga.ui.components.DateTimeText
import com.bugenzhao.mnga.ui.post.AttachmentsView
import com.bugenzhao.mnga.ui.post.ContentActions
import com.bugenzhao.mnga.ui.post.ContentEnv
import com.bugenzhao.mnga.ui.post.PostContent
import com.bugenzhao.mnga.ui.post.PostFontSize
import com.bugenzhao.mnga.ui.post.QuoteView
import com.bugenzhao.mnga.util.DateFormatters
import com.bugenzhao.mnga.util.Haptics
import com.bugenzhao.mnga.util.L
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Actions a post row can trigger, forwarded to the host screen. */
object PostRowAction {
    const val REPLY = "reply"
    const val QUOTE = "quote"
    const val COMMENT = "comment"
    const val REPORT = "report"
    const val MODIFY = "modify"
}

/**
 * A single post row, ported from `Shared/Views/PostRowView.swift` (header with
 * floor + user, content, vote footer, comments, signature, long-press context
 * menu and swipe actions).
 */
@Composable
fun PostRow(
    post: Post,
    isAuthor: Boolean = false,
    action: TopicDetailsActionModel? = null,
    votes: VotesModel,
    quotedResolver: QuotedPostResolver? = null,
    viewingImage: ViewingImageModel? = null,
    enableAuthorOnly: Boolean = true,
    locateFloor: ((Post) -> Unit)? = null,
    showMenu: Boolean = true,
    shouldHighlight: Boolean = false,
    onHighlightConsumed: () -> Unit = {},
    onPostAction: ((action: String, post: Post) -> Unit)? = null,
    onNavigateAuthorOnly: ((post: Post) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val showSignature = App.prefs.showSignature.flow.collectAsState().value

    val mock = post.id.tid.startsWith("mnga_")
    val dummy = post.id.tid == "dummy" && post.id.pid == "0"

    // Author info (local user lookup is a sync JNI call: off the main thread).
    var user by remember(post.authorId) { mutableStateOf<User?>(null) }
    LaunchedEffect(post.authorId) {
        user = withContext(Dispatchers.IO) { App.users.localUser(post.authorId) }
    }

    // Current vote overlay state.
    val voteMap by votes.votes.collectAsState()
    val vote = voteMap[post.id] ?: VotesModel.Vote(post.voteState, 0)

    // Highlight-on-jump: 3 s accent flash.
    var highlighted by remember { mutableStateOf(false) }
    LaunchedEffect(shouldHighlight) {
        if (shouldHighlight) {
            onHighlightConsumed()
            highlighted = true
            kotlinx.coroutines.delay(3000)
            highlighted = false
        }
    }
    val rowColor by animateColorAsState(
        targetValue = if (highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        } else {
            Color.Transparent
        },
        label = "rowColor",
    )

    fun doVote(operation: PostVoteRequest.Operation) {
        if (mock || dummy) {
            Haptics.play(view, Haptics.NotificationType.WARNING)
            return
        }
        scope.launch {
            val result = logicCallAsync(
                AsyncRequest.newBuilder()
                    .setPostVote(
                        PostVoteRequest.newBuilder()
                            .setPostId(post.id)
                            .setOperation(operation)
                            .build(),
                    )
                    .build(),
                PostVoteResponse.parser(),
            )
            result.onSuccess { response ->
                if (!response.hasError()) {
                    votes.apply(post.id, response.state, response.delta)
                    if (response.state != VoteState.NONE) {
                        Haptics.lightImpact(view)
                    }
                }
            }
        }
    }

    // Long-press context menu state (also opened from the header ellipsis).
    var menuOpen by remember { mutableStateOf(false) }
    var showAttachments by remember { mutableStateOf(false) }

    // Bridges rendered content taps to the screen's action model / viewer.
    val contentActions = remember(action, viewingImage) {
        object : ContentActions {
            override fun navigateToPost(id: PostId) {
                action?.navigateToPid?.value = id.pid
            }

            override fun navigateToTopic(tid: String) {
                action?.navigateToTid?.value = tid
            }

            override fun navigateToForum(id: ForumId) {
                action?.navigateToForum?.value = Forum.newBuilder().setId(id).build()
            }

            override fun navigateToUser(uid: String) {
                action?.navigateToRemoteUserID?.value = uid
            }

            override fun navigateToUserName(name: String) {
                action?.navigateToRemoteUserName?.value = name
            }

            override fun openURL(url: String) {
                val uri = runCatching { Uri.parse(url.trim()) }.getOrNull() ?: return
                val resolved = runCatching {
                    Uri.parse(
                        com.bugenzhao.mnga.util.URLs.resourceURL(
                            url.trim(),
                            com.bugenzhao.mnga.util.URLs.base,
                        )
                    )
                }.getOrNull()
                when (val navID = NavigationIdentifier.parse(resolved ?: uri)) {
                    is NavigationIdentifier.TopicID ->
                        action?.navigateToTid?.value = navID.tid
                    is NavigationIdentifier.PostID ->
                        action?.navigateToPid?.value = navID.pid
                    is NavigationIdentifier.ForumID ->
                        action?.navigateToForum?.value =
                            Forum.newBuilder().setId(navID.id).build()
                    is NavigationIdentifier.UserID ->
                        action?.navigateToRemoteUserID?.value = navID.uid
                    is NavigationIdentifier.UserNameID ->
                        action?.navigateToRemoteUserName?.value = navID.name
                    null -> App.openURL.open(resolved ?: uri, prefs = App.prefs)
                }
            }

            override fun showReplyChain(from: PostId) {
                action?.showReplyChain(from)
            }

            override fun viewImages(urls: List<String>, current: String) {
                viewingImage?.show(urls, current)
            }
        }
    }

    val content = @Composable {
        Column(
            Modifier
                .fillMaxWidth()
                .background(rowColor)
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            PostRowHeader(
                post = post,
                user = user,
                isAuthor = isAuthor,
                action = action,
            )

            // Content (block-words overlay with tap-to-reveal).
            BlockedContent(post = post, user = user, contentActions = contentActions)

            PostRowFooter(
                post = post,
                vote = vote,
                showMenu = showMenu && !dummy,
                onUpvote = { doVote(PostVoteRequest.Operation.UPVOTE) },
                onDownvote = { doVote(PostVoteRequest.Operation.DOWNVOTE) },
                onQuote = { onPostAction?.invoke(PostRowAction.QUOTE, post) },
                onMenuClick = { menuOpen = true },
            )

            if (post.commentsList.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row {
                    Spacer(Modifier.width(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (comment in post.commentsList) {
                            PostCommentRow(comment = comment)
                        }
                    }
                }
            }

            if (showSignature) {
                val sig = user?.signature
                if (sig != null && sig.spansList.isNotEmpty()) {
                    // 签名分隔线：与帖子分割线不同色、不满屏。
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        modifier = Modifier.width(96.dp),
                    )
                    PostContent(
                        content = sig,
                        env = ContentEnv(actions = contentActions),
                        fontSize = PostFontSize.SMALL,
                        defaultColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // Quote / vote quick actions were folded into the footer (up/down vote and
    // quote buttons), so the swipe-reveal row is kept only as a plain wrapper.
    SwipeActionsRow(
        enabled = false,
        leading = false,
        actions = emptyList(),
        modifier = modifier,
    ) {
        Box {
            Column(
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { menuOpen = true },
                ),
            ) {
                content()
            }
            PostRowContextMenu(
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                post = post,
                action = action,
                user = user,
                onShowAttachments = { showAttachments = true },
                enableAuthorOnly = enableAuthorOnly,
                locateFloor = locateFloor,
                mock = mock,
                dummy = dummy,
                onPostAction = onPostAction,
                onNavigateAuthorOnly = onNavigateAuthorOnly,
            )
        }
    }

    if (showAttachments && post.attachmentsList.isNotEmpty()) {
        PostAttachmentsSheet(
            post = post,
            viewingImage = viewingImage,
            onDismiss = { showAttachments = false },
        )
    }
}

/** Attachments list sheet, ported from `AttachmentsView` presentation. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PostAttachmentsSheet(
    post: Post,
    viewingImage: ViewingImageModel?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    ModalBottomSheet(onDismissRequest = onDismiss) {
        AttachmentsView(
            attachments = post.attachmentsList,
            onViewImage = { urls, current ->
                onDismiss()
                viewingImage?.show(urls, current)
            },
            onOpenURL = { url ->
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            },
        )
    }
}

// region Header

/**
 * Two-line post header: the avatar spans a name row (name + trailing post
 * date) and a metadata row (labelled user stats + trailing device / floor).
 */
@Composable
private fun PostRowHeader(
    post: Post,
    user: User?,
    isAuthor: Boolean,
    action: TopicDetailsActionModel?,
) {
    val showRegDate = App.prefs.postRowShowUserRegDate.flow.collectAsState().value

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PostRowAvatar(post = post, user = user, action = action)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PostRowUserName(
                    post = post,
                    user = user,
                    isAuthor = isAuthor,
                    action = action,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                DateTimeText(timestampSeconds = post.postDate)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (user != null && user.name.anonymous.isEmpty()) {
                    UserDetailsLine(user = user, showRegDate = showRegDate)
                }
                Spacer(Modifier.weight(1f))
                PostRowFloor(post = post)
            }
        }
    }
}

/** Device icon + `[N 楼]`, trailing the header's metadata row. */
@Composable
private fun PostRowFloor(post: Post) {
    val context = LocalContext.current
    if (post.floor == 0) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            deviceIcon(post.device),
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            L.str(context, "[Floor %lld]", post.floor),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Circular author avatar for the post header. Tapping opens the profile. */
@Composable
private fun PostRowAvatar(
    post: Post,
    user: User?,
    action: TopicDetailsActionModel?,
    size: Dp = 36.dp,
) {
    AvatarImage(
        url = user?.avatarUrl,
        name = user?.name?.display()?.ifEmpty { null } ?: post.authorId,
        size = size,
        modifier = Modifier
            .clip(CircleShape)
            .combinedClickable(
                onClick = { user?.let { action?.showUserProfile?.value = it } },
                onLongClick = {},
            ),
    )
}

/**
 * Author name plus the mute / thread-author indicators. Tapping toggles the
 * raw id, long-pressing opens the profile.
 */
@Composable
private fun PostRowUserName(
    post: Post,
    user: User?,
    isAuthor: Boolean,
    action: TopicDetailsActionModel?,
    style: TextStyle,
    modifier: Modifier = Modifier,
) {
    val showAuthorIndicator = App.prefs.postRowShowAuthorIndicator.flow.collectAsState().value
    var showId by remember(post.id) { mutableStateOf(false) }

    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val name = user?.name
        val displayed = when {
            name == null -> post.authorId
            showId -> name.normal.ifEmpty { post.authorId }
            else -> name.display()
        }
        Text(
            displayed,
            style = style,
            fontWeight = if (isAuthor) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (user?.mute == true) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .weight(1f, fill = false)
                .combinedClickable(
                    onClick = { showId = !showId },
                    onLongClick = { user?.let { action?.showUserProfile?.value = it } },
                ),
        )

        if (user?.mute == true) {
            Icon(
                Icons.Filled.MicOff,
                contentDescription = "Muted",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error,
            )
        }
        if (isAuthor && showAuthorIndicator) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Compact avatar + name, used by the inline comment rows. */
@Composable
fun PostRowUserView(
    post: Post,
    user: User?,
    isAuthor: Boolean,
    action: TopicDetailsActionModel? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PostRowAvatar(post = post, user = user, action = action)
        PostRowUserName(
            post = post,
            user = user,
            isAuthor = isAuthor,
            action = action,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun UserDetailsLine(user: User, showRegDate: Boolean) {
    val context = LocalContext.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LabelledStat(
            label = L.str(context, "Fame:"),
            // Fame is stored x10 signed; render with one decimal.
            value = String.format("%.1f", user.fame / 10.0),
            color = if (user.fame < 0) MaterialTheme.colorScheme.error else secondary,
        )
        LabelledStat(
            label = L.str(context, "Posts:"),
            value = user.postNum.toString(),
            color = if (user.postNum in 1 until 50) {
                MaterialTheme.colorScheme.error
            } else {
                secondary
            },
        )
        if (showRegDate && user.regDate > 0) {
            Text(
                DateFormatters.detailed(context, Date(user.regDate * 1000)).substringBefore(' '),
                style = MaterialTheme.typography.labelMedium,
                color = secondary,
            )
        }
        if (user.ipLocation.isNotEmpty()) {
            Text(
                user.ipLocation,
                style = MaterialTheme.typography.labelMedium,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One `label: value` metadata item of the header's second line. */
@Composable
private fun LabelledStat(label: String, value: String, color: Color) {
    Text(
        label + value,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        maxLines = 1,
    )
}

// endregion

// region Footer

/**
 * Footer action row. The reference layout keeps every control right-aligned,
 * with the overflow menu as its last item; the informational attachment marker
 * stays on the left.
 */
@Composable
private fun PostRowFooter(
    post: Post,
    vote: VotesModel.Vote,
    showMenu: Boolean,
    onUpvote: () -> Unit,
    onDownvote: () -> Unit,
    onQuote: () -> Unit,
    onMenuClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Leading indicators.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (post.attachmentsList.isNotEmpty()) {
                Icon(
                    Icons.Filled.AttachFile,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                if (vote.state == VoteState.UP) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                contentDescription = "Upvote",
                modifier = Modifier
                    .size(18.dp)
                    .combinedClickable(onClick = onUpvote, onLongClick = {}),
                tint = if (vote.state == VoteState.UP) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
            val score = maxOf(post.score + vote.delta, 0)
            Text(
                score.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (vote.state == VoteState.UP) FontWeight.Bold else FontWeight.Normal,
                color = if (vote.state == VoteState.UP) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
            Icon(
                if (vote.state == VoteState.DOWN) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                contentDescription = "Downvote",
                modifier = Modifier
                    .size(18.dp)
                    .combinedClickable(onClick = onDownvote, onLongClick = {}),
                tint = if (vote.state == VoteState.DOWN) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
            )
            Icon(
                Icons.Outlined.FormatQuote,
                contentDescription = "Quote",
                modifier = Modifier
                    .size(18.dp)
                    .combinedClickable(onClick = onQuote, onLongClick = {}),
                tint = MaterialTheme.colorScheme.outline,
            )
            if (showMenu) {
                Icon(
                    Icons.Filled.MoreVert,
                    contentDescription = null,
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onMenuClick),
                    tint = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

private fun deviceIcon(device: Device): ImageVector = when (device) {
    Device.APPLE -> Icons.Filled.PhoneIphone
    Device.ANDROID -> Icons.Filled.PhoneAndroid
    Device.DESKTOP -> Icons.Filled.Computer
    Device.WINDOWS_PHONE -> Icons.Filled.DevicesOther
    else -> Icons.Filled.HelpOutline
}

// endregion

// region Comments

/** Inline reply (comment) row: drops the first 3 metadata spans. */
@Composable
fun PostCommentRow(comment: Post) {
    val spans = comment.content.spansList
    val realSpans = if (spans.size > 3) spans.drop(3) else spans
    val content = if (realSpans !== spans) {
        comment.content.toBuilder().clearSpans().addAllSpans(realSpans).build()
    } else {
        comment.content
    }

    Column(
        Modifier.padding(vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PostRowUserView(post = comment, user = null, isAuthor = false)
            Spacer(Modifier.weight(1f))
            DateTimeText(timestampSeconds = comment.postDate)
        }
        QuoteView(fullWidth = false) {
            PostContent(
                content = content,
                env = ContentEnv(inQuote = true),
                fontSize = PostFontSize.SMALL,
            )
        }
    }
}

// endregion

// region Blocked content

/** Block-words gate with tap-to-reveal, ported from `BlockedView`. */
@Composable
private fun BlockedContent(
    post: Post,
    user: User?,
    contentActions: ContentActions,
) {
    val blockContent = BlockWordsStorage.content(
        user = user?.name ?: UserName.getDefaultInstance(),
        content = post.content.raw,
        tags = emptyList(),
    )
    val blocked = App.blockWords.blocked(blockContent)
    var revealed by remember(post.id) { mutableStateOf(false) }

    Box(
        Modifier.combinedClickable(
            onClick = { if (blocked && !revealed) revealed = true },
            onLongClick = {},
        ),
    ) {
        PostContent(post = post, env = ContentEnv(actions = contentActions))
        if (blocked && !revealed) {
            // Dimming overlay hides the blocked content until revealed.
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
            )
        }
    }
}

// endregion

// region Context menu

@Composable
private fun PostRowContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    post: Post,
    action: TopicDetailsActionModel?,
    user: User?,
    onShowAttachments: () -> Unit,
    enableAuthorOnly: Boolean,
    locateFloor: ((Post) -> Unit)?,
    mock: Boolean,
    dummy: Boolean,
    onPostAction: ((action: String, post: Post) -> Unit)?,
    onNavigateAuthorOnly: ((Post) -> Unit)?,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val authUid = App.authStorage.authInfo.collectAsState().value.uid

    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (onPostAction != null && !mock && !dummy) {
            DropdownMenuItem(
                text = { Text(L.str(context, "Reply")) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, null) },
                onClick = { onPostAction(PostRowAction.REPLY, post); onDismiss() },
            )
            DropdownMenuItem(
                text = { Text(L.str(context, "Quote")) },
                leadingIcon = { Icon(Icons.Filled.FormatQuote, null) },
                onClick = { onPostAction(PostRowAction.QUOTE, post); onDismiss() },
            )
            DropdownMenuItem(
                text = { Text(L.str(context, "Comment")) },
                leadingIcon = { Icon(Icons.Filled.MapsUgc, null) },
                onClick = { onPostAction(PostRowAction.COMMENT, post); onDismiss() },
            )
            if (authUid == post.authorId) {
                DropdownMenuItem(
                    text = { Text(L.str(context, "Edit")) },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { onPostAction(PostRowAction.MODIFY, post); onDismiss() },
                )
            }
            HorizontalDivider()
        }

        if (post.attachmentsList.isNotEmpty()) {            DropdownMenuItem(
                text = { Text(L.str(context, "Attachments (%lld)", post.attachmentsList.size)) },
                leadingIcon = { Icon(Icons.Filled.AttachFile, null) },
                onClick = {
                    onDismiss()
                    onShowAttachments()
                },
            )
        }

        if (action != null && enableAuthorOnly && user != null) {
            DropdownMenuItem(
                text = { Text(L.str(context, "This Author Only")) },
                leadingIcon = { Icon(Icons.Outlined.Person, null) },
                onClick = {
                    if (PlusModel.checkPlus(PlusFeature.AUTHOR_ONLY)) {
                        onNavigateAuthorOnly?.invoke(post)
                    }
                    onDismiss()
                },
            )
        }

        if (action != null && action.hasQuotedReplies(post.id)) {
            DropdownMenuItem(
                text = { Text(L.str(context, "View Replies")) },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                onClick = {
                    action.showQuotedReplies(post.id)
                    onDismiss()
                },
            )
        }

        if (locateFloor != null) {
            DropdownMenuItem(
                text = { Text(L.str(context, "Locate This Floor")) },
                leadingIcon = { Icon(Icons.Filled.LocationSearching, null) },
                onClick = {
                    locateFloor(post)
                    onDismiss()
                },
            )
        }

        if (onPostAction != null && !mock && !dummy && authUid != post.authorId) {
            DropdownMenuItem(
                text = { Text(L.str(context, "Report")) },
                leadingIcon = { Icon(Icons.Filled.HelpOutline, null) },
                onClick = { onPostAction(PostRowAction.REPORT, post); onDismiss() },
            )
        }

        HorizontalDivider()

        // `pid == "0"` is the main floor: share a topic link instead of a post one.
        val navID: NavigationIdentifier =
            if (post.id.pid == "0") {
                NavigationIdentifier.TopicID(post.id.tid, null)
            } else {
                NavigationIdentifier.PostID(post.id.pid)
            }
        DropdownMenuItem(
            text = { Text(L.str(context, "LumaGA Link")) },
            leadingIcon = { Icon(Icons.Filled.Link, null) },
            onClick = {
                navID.mngaURL?.let { clipboard.setText(AnnotatedString(it)) }
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(L.str(context, "NGA Link")) },
            leadingIcon = { Icon(Icons.Filled.Link, null) },
            onClick = {
                navID.webpageURL?.let { clipboard.setText(AnnotatedString(it)) }
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(L.str(context, "Share")) },
            leadingIcon = { Icon(Icons.Filled.Share, null) },
            onClick = {
                val text = navID.webpageURL ?: navID.mngaURL ?: ""
                runCatching {
                    context.startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            },
                            null,
                        ),
                    )
                }
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(L.str(context, "Open in Browser")) },
            leadingIcon = { Icon(Icons.Filled.OpenInBrowser, null) },
            onClick = {
                navID.webpageURL?.let {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, android.net.Uri.parse(it)),
                        )
                    }
                }
                onDismiss()
            },
        )
    }
}

// endregion

// region Swipe actions

private data class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)

/**
 * Swipe-to-reveal action row (vote/quote), replacing SwiftUI `swipeActions`.
 * Kept as a plain wrapper now that the vote/quote actions live in the footer;
 * the content shifts horizontally while the actions sit behind it; releasing
 * snaps back after firing the tapped action.
 */
@Composable
private fun SwipeActionsRow(
    enabled: Boolean,
    leading: Boolean,
    actions: List<SwipeAction>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var offsetX by remember { mutableStateOf(0f) }
    val maxOffset = 96f * actions.size
    val density = LocalDensity.current
    val context = LocalContext.current

    Box(modifier) {
        // Actions revealed behind the content.
        Row(
            Modifier
                .matchParentSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (leading) Arrangement.Start else Arrangement.End,
        ) {
            actions.forEach { act ->
                IconButton(onClick = {
                    act.onClick()
                    offsetX = 0f
                }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            act.icon,
                            contentDescription = act.label,
                            tint = if (act.tint == Color.Unspecified) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                act.tint
                            },
                        )
                        Text(
                            L.str(context, act.label),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Box(
            Modifier
                .offset(x = with(density) { offsetX.toDp() })
                .pointerInput(enabled, actions.size) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = { offsetX = 0f },
                        onDragCancel = { offsetX = 0f },
                    ) { _, dragAmount ->
                        val target = if (leading) {
                            (offsetX + dragAmount).coerceIn(0f, maxOffset)
                        } else {
                            (offsetX + dragAmount).coerceIn(-maxOffset, 0f)
                        }
                        offsetX = target
                    }
                },
        ) {
            content()
        }
    }
}

// endregion
