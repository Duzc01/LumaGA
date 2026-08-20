package com.bugenzhao.mnga.ui.screens.user

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.FrontHand
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.PlusFeature
import com.bugenzhao.mnga.model.ViewingImageModel
import com.bugenzhao.mnga.model.appScope
import com.bugenzhao.mnga.protos.datamodel.PostContent
import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.protos.datamodel.TopicWithLightPost
import com.bugenzhao.mnga.protos.datamodel.User
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.RemoteUserRequest
import com.bugenzhao.mnga.protos.service.UserPostListRequest
import com.bugenzhao.mnga.protos.service.UserPostListResponse
import com.bugenzhao.mnga.protos.service.UserTopicListRequest
import com.bugenzhao.mnga.protos.service.UserTopicListResponse
import com.bugenzhao.mnga.storage.BlockWordsStorage
import com.bugenzhao.mnga.ui.components.DateTimeText
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.ui.nav.Route
import com.bugenzhao.mnga.ui.screens.topiclist.TopicRow
import com.bugenzhao.mnga.util.L
import com.bugenzhao.mnga.util.URLs
import java.net.URLEncoder
import kotlinx.coroutines.launch

private enum class ProfileTab(val labelKey: String) {
    TOPICS("Topics"),
    POSTS("Posts"),
}

/**
 * Full user profile page, ported from `UserProfileView` / `RemoteUserProfileView`:
 * huge user header, signature, and a Topics/Posts segmented paged list.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    navigator: Navigator,
    userId: String? = null,
    userName: String? = null,
    user: User? = null,
    signatureContent: (@Composable (PostContent) -> Unit)? = null,
    onNewMessage: (User) -> Unit = { },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var currentUser by remember { mutableStateOf(user) }
    var loading by remember {
        mutableStateOf(user == null && (userId != null || userName != null))
    }

    // Resolve by id/name like `RemoteUserProfileView` (with error toast).
    LaunchedEffect(userId, userName, user) {
        if (currentUser != null) return@LaunchedEffect
        val request = RemoteUserRequest.newBuilder()
        if (!userId.isNullOrEmpty()) request.userId = userId
        if (!userName.isNullOrEmpty()) request.userName = userName
        val resolved = App.users.remoteUser(request.build(), showError = true)
        loading = false
        if (resolved != null) currentUser = resolved
    }

    val authInfo by App.authStorage.authInfo.collectAsState()
    val blockWords by App.blockWords.words.collectAsState()

    val resolvedUser = currentUser
    val viewingImage = remember { ViewingImageModel() }

    val signaturePostModel = remember { SignaturePostModel(appScope) }
    val sentSignature by signaturePostModel.sent.collectAsState()
    var showSignatureEditor by remember { mutableStateOf(false) }

    val tab = remember { mutableStateOf(ProfileTab.TOPICS) }

    if (resolvedUser == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (loading) CircularProgressIndicator()
            else Text(
                L.str(context, "Empty"),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BackHandler(enabled = navigator.size > 1) { navigator.pop() }
        return
    }

    val isMyself = resolvedUser.id.isNotEmpty() &&
        resolvedUser.id == authInfo.uid
    val blocked = resolvedUser.id.isNotEmpty() &&
        blockWords.contains(BlockWordsStorage.fromUser(resolvedUser.name))
    val shouldShowList =
        resolvedUser.id.isNotEmpty() && !resolvedUser.isAnonymousUser && !blocked

    val topicDataSource = remember(resolvedUser.id) {
        com.bugenzhao.mnga.model.PagingDataSource<UserTopicListResponse, Topic>(
            scope = appScope,
            responseParser = { UserTopicListResponse.parser() },
            buildRequest = { page ->
                AsyncRequest.newBuilder()
                    .setUserTopicList(
                        UserTopicListRequest.newBuilder()
                            .setAuthorId(resolvedUser.id)
                            .setPage(page)
                    )
                    .build()
            },
            onResponse = { response ->
                Pair(response.topicsList, response.pages.toInt().takeIf { it > 0 })
            },
            id = { it.id },
        )
    }
    val postDataSource = remember(resolvedUser.id) {
        com.bugenzhao.mnga.model.PagingDataSource<UserPostListResponse, TopicWithLightPost>(
            scope = appScope,
            responseParser = { UserPostListResponse.parser() },
            buildRequest = { page ->
                AsyncRequest.newBuilder()
                    .setUserPostList(
                        UserPostListRequest.newBuilder()
                            .setAuthorId(resolvedUser.id)
                            .setPage(page)
                    )
                    .build()
            },
            // Page count unknown: load until empty/error.
            onResponse = { response -> Pair(response.tpsList, Int.MAX_VALUE) },
            id = { "${it.post.id.tid}/${it.post.id.pid}" },
            finishOnError = true,
        )
    }

    LaunchedEffect(topicDataSource) {
        if (topicDataSource.notLoaded) topicDataSource.initialLoad()
    }
    LaunchedEffect(postDataSource, tab.value) {
        if (tab.value == ProfileTab.POSTS && postDataSource.notLoaded) {
            postDataSource.initialLoad()
        }
    }

    fun reloadUser() {
        scope.launch {
            App.users.remoteUser(resolvedUser.id, showError = false, ignoreCache = true)
                ?.let { currentUser = it }
        }
    }
    fun refresh() {
        scope.launch {
            App.users.remoteUser(resolvedUser.id, showError = false, ignoreCache = true)
                ?.let { currentUser = it }
            if (shouldShowList) {
                topicDataSource.refreshAsync()
                postDataSource.refreshAsync()
            }
        }
    }
    // Reload after the signature update round-trips (own profile only).
    LaunchedEffect(sentSignature) {
        if (sentSignature != null && isMyself) reloadUser()
    }

    val displayName = resolvedUser.name.displayString
    val title =
        if (resolvedUser.isAnonymousUser) L.str(context, "Anonymous User")
        else displayName.ifEmpty { L.str(context, "User Profile") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (shouldShowList) {
                            Text(
                                if (tab.value == ProfileTab.TOPICS)
                                    L.str(context, "%@'s Topics", displayName)
                                else L.str(context, "%@'s Posts", displayName),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    ProfileOverflowMenu(
                        isMyself = isMyself,
                        anonymous = resolvedUser.isAnonymousUser,
                        blocked = blocked,
                        user = resolvedUser,
                        onEditSignature = { showSignatureEditor = true },
                        onNewMessage = {
                            if (checkPlusFeature(PlusFeature.SHORT_MESSAGE)) onNewMessage(resolvedUser)
                        },
                        onBlockUser = { App.blockWords.toggleUser(resolvedUser.name) },
                    )
                },
            )
        },
    ) { padding ->
        val topicState by topicDataSource.state.collectAsState()
        val postState by postDataSource.state.collectAsState()
        val listState = rememberLazyListState()

        // Force a fresh measure of the list whenever the active tab's content
        // transitions between loading/empty/loaded. Without this, the
        // LazyColumn occasionally keeps measuring the stale item set (the
        // item-provider update can land after the layout pass and no new
        // pass is scheduled), leaving the rows invisible until the next
        // recomposition (e.g. a tab switch) forces a relayout.
        val activeDS = if (tab.value == ProfileTab.TOPICS) topicDataSource else postDataSource
        val activeIsEmpty =
            if (tab.value == ProfileTab.TOPICS) topicState.items.isEmpty()
            else postState.items.isEmpty()

        // Prefetch when approaching the end of the active list.
        val activeItems = if (tab.value == ProfileTab.TOPICS) topicState.items
        else postState.items
        LaunchedEffect(listState, activeItems.size) {
            snapshotFlow {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                last >= activeItems.size - 3
            }.collect { nearEnd ->
                if (nearEnd && activeItems.isNotEmpty()) {
                    if (tab.value == ProfileTab.TOPICS) topicDataSource.loadMoreIfNeeded(activeItems.size - 1)
                    else postDataSource.loadMoreIfNeeded(activeItems.size - 1)
                }
            }
        }

        PullToRefreshBox(
            isRefreshing = topicState.isRefreshing || postState.isRefreshing,
            onRefresh = { refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            key(activeDS.isInitialLoading to activeIsEmpty) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                // 顶部留 4dp：标题上移贴 AppBar，卡片位置保持不变。
                contentPadding = PaddingValues(start = 16.dp, top = 4.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "header") {
                    Column {
                        Text(
                            L.str(context, "User Profile"),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        UserProfileHeader(
                            user = resolvedUser,
                            blocked = blocked,
                            isMyself = isMyself,
                            onAvatarClick = { url -> viewingImage.show(url) },
                            onEditSignature = { showSignatureEditor = true },
                            onNewMessage = {
                                if (checkPlusFeature(PlusFeature.SHORT_MESSAGE)) {
                                    onNewMessage(resolvedUser)
                                }
                            },
                            onBlockUser = { App.blockWords.toggleUser(resolvedUser.name) },
                            onShare = { shareUser(context, resolvedUser) },
                            signatureContent = signatureContent,
                        )
                    }
                }

                if (!shouldShowList) {
                    if (blocked) {
                        item(key = "blocked") {
                            SectionCard {
                                Box(
                                    Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                    contentAlignment = Alignment.Center,
                                ) { Text(L.str(context, "Blocked")) }
                            }
                        }
                    }
                } else {
                    item(key = "tabs") {
                        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                            ProfileTab.entries.forEachIndexed { index, entry ->
                                SegmentedButton(
                                    selected = tab.value == entry,
                                    onClick = { tab.value = entry },
                                    shape = SegmentedButtonDefaults.itemShape(
                                        index = index,
                                        count = ProfileTab.entries.size,
                                    ),
                                ) { Text(L.str(context, entry.labelKey)) }
                            }
                        }
                    }

                    if (tab.value == ProfileTab.TOPICS) {
                        item(key = "topics-header") {
                            SectionHeader(L.str(context, "%@'s Topics", displayName))
                        }
                        if (topicDataSource.isInitialLoading) {
                            item(key = "topics-loading") { CenteredLoading() }
                        } else if (topicState.items.isEmpty()) {
                            item(key = "topics-empty") {
                                SectionCard {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(L.str(context, "Empty")) }
                                }
                            }
                        } else {
                            itemsIndexed(topicState.items, key = { _, t -> "t-${t.id}" }) { _, topic ->
                                // The shared list row, not a `SectionCard`
                                // wrapper: it brings its own card surface.
                                TopicRow(
                                    topic = topic,
                                    // Every topic here is this user's own, so
                                    // the posting date is what distinguishes
                                    // them, not the last reply.
                                    useTopicPostDate = true,
                                    onClick = {
                                        navigator.push(
                                            Route.TopicDetails(
                                                topicId = topic.id,
                                                fav = topic.fav.takeIf { it.isNotEmpty() },
                                            )
                                        )
                                    },
                                )
                            }
                        }
                    } else {
                        item(key = "posts-header") {
                            SectionHeader(L.str(context, "%@'s Posts", displayName))
                        }
                        if (postDataSource.isInitialLoading) {
                            item(key = "posts-loading") { CenteredLoading() }
                        } else if (postState.items.isEmpty()) {
                            item(key = "posts-empty") {
                                SectionCard {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                        contentAlignment = Alignment.Center,
                                    ) { Text(L.str(context, "Empty")) }
                                }
                            }
                        } else {
                            itemsIndexed(postState.items, key = { _, tp -> "p-${tp.post.id.pid}" }) { _, tp ->
                                SectionCard {
                                    UserPostRow(tp) {
                                        // The original page is unknown (iOS FIXME).
                                        navigator.push(
                                            Route.TopicDetails(
                                                topicId = tp.topic.id,
                                                postId = tp.post.id.pid.takeIf { it.isNotEmpty() },
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item(key = "footer") {
                        val active = if (tab.value == ProfileTab.TOPICS) topicState else postState
                        val ds = if (tab.value == ProfileTab.TOPICS) topicDataSource else postDataSource
                        com.bugenzhao.mnga.ui.components.AdaptiveFooter(
                            loading = active.isLoading,
                            noMore = !ds.hasMore,
                        )
                    }
                }
            }
            }
        }
    }

    val editorShowing by signaturePostModel.showEditor.collectAsState()
    LaunchedEffect(showSignatureEditor, editorShowing) {
        if (showSignatureEditor && !editorShowing) {
            signaturePostModel.show(
                UserSignatureEditTask(
                    UserSignatureEditAction(
                        userID = resolvedUser.id,
                        initialSignature = resolvedUser.signature.rawReplacingBr,
                    )
                )
            )
        }
    }
    LaunchedEffect(editorShowing) {
        if (!editorShowing) showSignatureEditor = false
    }
    if (editorShowing) {
        SignatureEditorSheet(model = signaturePostModel) { showSignatureEditor = false }
    }

    ImageViewerOverlay(viewingImage)

    BackHandler(enabled = navigator.size > 1) { navigator.pop() }
}

@Composable
private fun ProfileOverflowMenu(
    isMyself: Boolean,
    anonymous: Boolean,
    blocked: Boolean,
    user: User,
    onEditSignature: () -> Unit,
    onNewMessage: () -> Unit,
    onBlockUser: () -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val webURL = userWebURL(user)

    IconButton(onClick = { expanded = true }) {
        Icon(Icons.Filled.MoreHoriz, contentDescription = L.str(context, "More"))
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (isMyself) {
            DropdownMenuItem(
                text = { Text(L.str(context, "Edit Signature")) },
                leadingIcon = { Icon(Icons.Outlined.EditNote, null) },
                onClick = { expanded = false; onEditSignature() },
            )
        }
        if (!isMyself) {
            if (!anonymous) {
                DropdownMenuItem(
                    text = { Text(L.str(context, "New Short Message")) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Chat, null) },
                    onClick = { expanded = false; onNewMessage() },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        L.str(context, if (blocked) "Unblock This User" else "Block This User"),
                        color = if (blocked) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.error,
                    )
                },
                leadingIcon = { Icon(Icons.Outlined.FrontHand, null) },
                onClick = { expanded = false; onBlockUser() },
            )
        }
        if (!anonymous) {
            DropdownMenuItem(
                text = { Text(L.str(context, "LumaGA Link")) },
                leadingIcon = { Icon(Icons.Filled.Share, null) },
                onClick = {
                    expanded = false
                    shareText(context, "mnga://user/${user.id}", user.name.displayString)
                },
            )
            if (webURL != null) {
                DropdownMenuItem(
                    text = { Text(L.str(context, "NGA Link")) },
                    leadingIcon = { Icon(Icons.Filled.Share, null) },
                    onClick = {
                        expanded = false
                        shareText(context, webURL, user.name.displayString)
                    },
                )
                DropdownMenuItem(
                    text = { Text(L.str(context, "Open in Browser")) },
                    leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, null) },
                    onClick = {
                        expanded = false
                        App.openURL.open(
                            android.net.Uri.parse(webURL),
                            prefs = App.prefs,
                        )
                    },
                )
            }
        }
    }
}

/** Light post row: topic subject + content preview + date. */
@Composable
private fun UserPostRow(tp: TopicWithLightPost, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            tp.topic.subject.content.ifEmpty { tp.topic.subjectContent },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        RawPostContent(
            tp.post.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        DateTimeText(tp.post.postDate)
    }
}

@Composable
internal fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) { Column { content() } }
}

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CenteredLoading() {
    Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.padding(2.dp), strokeWidth = 2.dp)
    }
}

/** `mnga://user/<uid>`; suppressed for mock ids. */
internal fun userMNGAURL(user: User): String? =
    if (user.id.startsWith("mnga_")) null else "mnga://user/${user.id}"

/** NGA web profile link; suppressed for mock ids. */
internal fun userWebURL(user: User): String? = when {
    user.id.startsWith("mnga_") -> null
    user.id.isNotEmpty() -> URLs.base + "nuke.php?func=ucp&uid=" + user.id
    user.name.normal.isNotEmpty() ->
        URLs.base + "nuke.php?func=ucp&username=" +
            URLEncoder.encode(user.name.normal, "UTF-8")
    else -> null
}

internal fun shareUser(context: android.content.Context, user: User) {
    val url = userMNGAURL(user) ?: userWebURL(user) ?: return
    shareText(context, url, user.name.displayString)
}

internal fun shareText(context: android.content.Context, text: String, title: String?) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
        title?.let { putExtra(android.content.Intent.EXTRA_SUBJECT, it) }
    }
    runCatching {
        context.startActivity(android.content.Intent.createChooser(intent, null))
    }
}

/** Minimal full-screen viewer for the huge avatar tap. */
@Composable
internal fun ImageViewerOverlay(model: ViewingImageModel) {
    val showing by model.showing.collectAsState()
    val urls by model.urls.collectAsState()
    val index by model.currentIndex.collectAsState()
    if (!showing) return
    androidx.compose.ui.window.Dialog(
        onDismissRequest = { model.dismiss() },
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clickable { model.dismiss() },
            contentAlignment = Alignment.Center,
        ) {
            coil.compose.AsyncImage(
                model = urls.getOrNull(index),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
