package com.bugenzhao.mnga.ui.screens.topiclist

import android.util.Base64
import androidx.activity.compose.BackHandler

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.model.NavigationIdentifier
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.model.PlusFeature
import com.bugenzhao.mnga.model.PlusModel
import com.bugenzhao.mnga.model.ToastModel
import com.bugenzhao.mnga.protos.datamodel.Forum
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.protos.datamodel.PostReplyAction
import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.HotTopicListRequest
import com.bugenzhao.mnga.protos.service.HotTopicListResponse
import com.bugenzhao.mnga.protos.service.TopicFavorRequest
import com.bugenzhao.mnga.protos.service.TopicFavorResponse
import com.bugenzhao.mnga.protos.service.TopicListRequest
import com.bugenzhao.mnga.protos.service.TopicListResponse
import com.bugenzhao.mnga.storage.BlockWordsStorage
import com.bugenzhao.mnga.storage.TopicListOrder
import com.bugenzhao.mnga.ui.components.PagedList
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.ui.nav.Route
import com.bugenzhao.mnga.ui.nav.TopicListMode
import com.bugenzhao.mnga.util.Haptics
import com.bugenzhao.mnga.util.L
import kotlinx.coroutines.launch

private const val IdleAutoRefreshMillis = 60L * 60 * 1000 // 1 hour

/**
 * The workhorse topic list, a port of `TopicListView` plus the
 * `HotTopicListView` and `RecommendedTopicListView` variants selected by
 * [mode]. Handles order switching (two independent page caches), block-word
 * and forum-shortcut filtering, pull-to-refresh with a 1-hour idle
 * auto-refresh, toolbar More menu and the bottom action bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicListScreen(
    navigator: Navigator,
    forumId: ForumId,
    mode: TopicListMode = TopicListMode.NORMAL,
    dateRange: Int = 0,
    editor: com.bugenzhao.mnga.ui.editor.EditorController? = null,
    /** The stack route rendering this screen, used to tell a pop from a push. */
    route: Route? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = navigator.size > 1) { navigator.pop() }

    val mock = forumId.hasFid() && forumId.fid.startsWith("mnga_")

    // -- Order state, following the default-order preference (SS5).
    var order by remember(forumId) { mutableStateOf<TopicListOrder?>(null) }
    val defaultOrderRaw by App.prefs.defaultTopicListOrderRaw.flow.collectAsState()
    LaunchedEffect(forumId) {
        if (order == null) order = TopicListOrder.fromRaw(defaultOrderRaw)
    }
    LaunchedEffect(defaultOrderRaw) {
        val latest = TopicListOrder.fromRaw(defaultOrderRaw)
        if (latest != order) order = latest
    }
    val orderOrDefault = order ?: TopicListOrder.fromRaw(defaultOrderRaw)

    // -- Block words + forum-shortcut filtering (SS5 maybeFiltered).
    fun maybeFiltered(topics: List<Topic>): List<Topic> {
        var result = topics
        if (App.prefs.topicListHideBlocked.value) {
            result = result.filter { !App.blockWords.blocked(BlockWordsStorage.content(it)) }
        }
        if (!App.prefs.topicListShowForumShortcut.value) {
            result = result.filter { !it.hasShortcutForum() }
        }
        return result
    }

    // -- Data sources.
    val (dataSourceLastPost, dataSourcePostDate) = remember(forumId) {
        val lastPost = PagingDataSource<TopicListResponse, Topic>(
            scope = scope,
            responseParser = { TopicListResponse.parser() },
            buildRequest = { page ->
                AsyncRequest.newBuilder()
                    .setTopicList(
                        TopicListRequest.newBuilder()
                            .setId(forumId)
                            .setPage(page)
                            .setOrder(TopicListRequest.Order.LAST_POST)
                            .build()
                    )
                    .build()
            },
            onResponse = { response ->
                Pair(maybeFiltered(response.topicsList), response.pages.toInt().takeIf { it > 0 })
            },
            id = { it.id },
        )
        val postDate = PagingDataSource<TopicListResponse, Topic>(
            scope = scope,
            responseParser = { TopicListResponse.parser() },
            buildRequest = { page ->
                AsyncRequest.newBuilder()
                    .setTopicList(
                        TopicListRequest.newBuilder()
                            .setId(forumId)
                            .setPage(page)
                            .setOrder(TopicListRequest.Order.POST_DATE)
                            .build()
                    )
                    .build()
            },
            onResponse = { response ->
                Pair(maybeFiltered(response.topicsList), response.pages.toInt().takeIf { it > 0 })
            },
            id = { it.id },
        )
        lastPost to postDate
    }

    var hotRange by remember(forumId) {
        mutableStateOf(
            HotTopicListRequest.DateRange.forNumber(dateRange)
                ?: HotTopicListRequest.DateRange.DAY
        )
    }
    val hotDataSource = remember(forumId, hotRange) {
        PagingDataSource<HotTopicListResponse, Topic>(
            scope = scope,
            responseParser = { HotTopicListResponse.parser() },
            buildRequest = { _ ->
                AsyncRequest.newBuilder()
                    .setHotTopicList(
                        HotTopicListRequest.newBuilder()
                            .setId(forumId)
                            .setRange(hotRange)
                            .setFetchPageLimit(5)
                            .build()
                    )
                    .build()
            },
            onResponse = { response -> Pair(response.topicsList, 1) },
            id = { it.id },
        )
    }
    val recommendedDataSource = remember(forumId) {
        PagingDataSource<TopicListResponse, Topic>(
            scope = scope,
            responseParser = { TopicListResponse.parser() },
            buildRequest = { page ->
                AsyncRequest.newBuilder()
                    .setTopicList(
                        TopicListRequest.newBuilder()
                            .setId(forumId)
                            .setPage(page)
                            .setOrder(TopicListRequest.Order.POST_DATE)
                            .setRecommendedOnly(true)
                            .build()
                    )
                    .build()
            },
            onResponse = { response ->
                Pair(response.topicsList, response.pages.toInt().takeIf { it > 0 })
            },
            id = { it.id },
        )
    }

    val dataSource = when (mode) {
        TopicListMode.HOT -> hotDataSource
        TopicListMode.RECOMMENDED -> recommendedDataSource
        TopicListMode.NORMAL ->
            if (orderOrDefault == TopicListOrder.POST_DATE) dataSourcePostDate
            else dataSourceLastPost
    }
    val state by dataSource.state.collectAsState()

    // -- Keep the loaded topic list across navigation: when this screen is
    // pushed away (e.g. into a topic details page) and popped back, restore
    // the previous items/page/scroll instead of refetching and jumping to top.
    var savedItemsB64 by rememberSaveable(forumId, mode) { mutableStateOf<List<String>>(emptyList()) }
    var savedLoadedPage by rememberSaveable(forumId, mode) { mutableStateOf(0) }
    var savedTotalPages by rememberSaveable(forumId, mode) { mutableStateOf(1) }
    var savedLastRefresh by rememberSaveable(forumId, mode) { mutableStateOf(0L) }
    var savedResponseB64 by rememberSaveable(forumId, mode) { mutableStateOf<String?>(null) }

    LaunchedEffect(dataSource) {
        if (savedItemsB64.isNotEmpty()) {
            val parser =
                when (dataSource) {
                    hotDataSource -> HotTopicListResponse.parser()
                    else -> TopicListResponse.parser()
                }
            dataSource.restoreItems(
                items = savedItemsB64.map { Topic.parseFrom(Base64.decode(it, Base64.NO_WRAP)) },
                loadedPage = savedLoadedPage,
                totalPages = savedTotalPages,
                lastRefreshTime = savedLastRefresh.takeIf { it > 0 }?.let { java.util.Date(it) },
                latestResponse = savedResponseB64?.let {
                    parser.parseFrom(Base64.decode(it, Base64.NO_WRAP))
                },
            )
        } else {
            dataSource.initialLoad()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Only keep the snapshot when this screen was pushed away by a
            // deeper route (so popping back restores it). When the route was
            // popped off the stack for good, drop the snapshot so the next
            // entry into this forum starts fresh instead of showing the
            // stale list.
            val stillInStack = route != null && navigator.stack.value.any { it === route }
            if (stillInStack && dataSource.items.isNotEmpty()) {
                savedItemsB64 = dataSource.items.map {
                    Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP)
                }
                savedLoadedPage = dataSource.loadedPage
                savedTotalPages = dataSource.totalPages
                savedLastRefresh = dataSource.lastRefreshTime?.time ?: 0L
                savedResponseB64 =
                    (dataSource.latestResponse as? com.google.protobuf.Message)
                        ?.toByteArray()
                        ?.let { Base64.encodeToString(it, Base64.NO_WRAP) }
            } else if (!stillInStack) {
                savedItemsB64 = emptyList()
                savedLoadedPage = 0
                savedTotalPages = 1
                savedLastRefresh = 0L
                savedResponseB64 = null
            }
        }
    }

    // -- Forum meta enrichment (SS5 updateForumMeta).
    var forum by remember(forumId) {
        mutableStateOf(Forum.newBuilder().setId(forumId).build())
    }
    var parentForumName by remember(forumId) { mutableStateOf<String?>(null) }
    val responseForum: Forum? = when (val latest = state.latestResponse) {
        is TopicListResponse -> latest.forum
        is HotTopicListResponse -> latest.forum
        else -> null
    }
    LaunchedEffect(responseForum) {
        val response = responseForum ?: return@LaunchedEffect
        if (forum.id == response.id) {
            if (forum != response) forum = response
        } else if (forum.name != response.name && response.name.isNotEmpty()) {
            parentForumName = response.name
        }
    }

    val subforums = (state.latestResponse as? TopicListResponse)?.subforumsList.orEmpty()
    val toppedTopicID =
        (responseForum?.toppedTopicId ?: forum.toppedTopicId).takeIf { it.isNotEmpty() }

    // -- Favorites of forums.
    val favoriteForums by App.favoriteForums.forumsFlow().collectAsState()
    val isForumFavorite = favoriteForums.any { it.id == forum.id }
    val authInfo by App.authStorage.authInfo.collectAsState()
    val signedIn = authInfo.token.isNotEmpty()

    // -- Local favorite-topic overrides (kept after the swipe action).
    val favoredOverrides = remember(forumId) { mutableStateMapOf<String, Boolean>() }

    fun toggleTopicFavor(topic: Topic) {
        val favored = favoredOverrides[topic.id] ?: topic.isFavored
        val operation = if (favored) {
            TopicFavorRequest.Operation.DELETE
        } else {
            TopicFavorRequest.Operation.ADD
        }
        scope.launch {
            val result = logicCallAsync(
                AsyncRequest.newBuilder()
                    .setTopicFavor(
                        TopicFavorRequest.newBuilder()
                            .setTopicId(topic.id)
                            .setOperation(operation)
                            .build()
                    )
                    .build(),
                TopicFavorResponse.parser(),
            )
            result.onSuccess { response ->
                favoredOverrides[topic.id] = response.isFavored
                Haptics.play(view, Haptics.NotificationType.SUCCESS)
            }
        }
    }

    // -- Toolbar-triggered refresh (SS0.6): refresh then success haptic. The
    // refresh also resets the scroll to the top (via scrollToTopSignal) so
    // the newest items are visible.
    var refreshScrollEpoch by remember { mutableIntStateOf(0) }
    fun triggerRefresh() {
        refreshScrollEpoch++
        scope.launch {
            dataSource.refreshAsync(sleepMillis = 0).join()
            Haptics.play(view, Haptics.NotificationType.SUCCESS)
        }
    }

    // -- Auto refresh after 1h idle (SS0.6 / SS22.9).
    var lastSeenAt by remember(forumId, mode) { mutableStateOf(System.currentTimeMillis()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, dataSource) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> lastSeenAt = System.currentTimeMillis()
                Lifecycle.Event.ON_RESUME -> {
                    val lastRefresh = dataSource.state.value.lastRefreshTime?.time ?: 0L
                    val reference = maxOf(lastSeenAt, lastRefresh)
                    if (reference > 0 &&
                        System.currentTimeMillis() - reference > IdleAutoRefreshMillis
                    ) {
                        scope.launch {
                            dataSource.refreshAsync(sleepMillis = 0).join()
                            ToastModel.showAuto(ToastModel.Message.AutoRefreshed)
                        }
                    }
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // -- New topic flow (Plus-gated, via the global editor controller).
    fun newTopic() {
        if (!PlusModel.checkPlus(PlusFeature.NEW_TOPIC)) return
        editor?.newTopic(forum)
    }

    var moreMenuExpanded by remember { mutableStateOf(false) }
    var orderMenuExpanded by remember { mutableStateOf(false) }
    var shareMenuExpanded by remember { mutableStateOf(false) }
    var rangeMenuExpanded by remember { mutableStateOf(false) }
    var contextMenuTopic by remember { mutableStateOf<Topic?>(null) }

    val showRefreshButton by App.prefs.topicListShowRefreshButton.flow.collectAsState()

    val navID = NavigationIdentifier.ForumID(forum.id)

    val screenTitle = when (mode) {
        TopicListMode.HOT -> L.str(context, "Hot Topics")
        TopicListMode.RECOMMENDED -> L.str(context, "Recommended Topics")
        TopicListMode.NORMAL -> forum.name
    }
    val subtitle = when (mode) {
        TopicListMode.HOT, TopicListMode.RECOMMENDED -> forum.name
        TopicListMode.NORMAL -> parentForumName
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            screenTitle.ifEmpty { forumId.idDisplay() },
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                        if (!subtitle.isNullOrEmpty()) {
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                navigationIcon = {
                    // 与详情页一致的返回按钮。
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    if (mode == TopicListMode.HOT) {
                        Box {
                            IconButton(onClick = { rangeMenuExpanded = true }) {
                                Icon(
                                    Icons.Outlined.DateRange,
                                    contentDescription = L.str(context, "Range"),
                                )
                            }
                            DropdownMenu(
                                expanded = rangeMenuExpanded,
                                onDismissRequest = { rangeMenuExpanded = false },
                            ) {
                                listOf(
                                    HotTopicListRequest.DateRange.DAY to "Last 24 hours",
                                    HotTopicListRequest.DateRange.WEEK to "Last week",
                                    HotTopicListRequest.DateRange.MONTH to "Last month",
                                ).forEach { (range, label) ->
                                    DropdownMenuItem(
                                        text = { Text(L.str(context, label)) },
                                        trailingIcon = {
                                            if (range == hotRange) {
                                                Icon(
                                                    Icons.Outlined.Check,
                                                    contentDescription = null,
                                                )
                                            }
                                        },
                                        onClick = {
                                            hotRange = range
                                            rangeMenuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    } else {
                        if (!mock && signedIn && editor != null) {
                            IconButton(onClick = { newTopic() }) {
                                Icon(
                                    Icons.Outlined.Edit,
                                    contentDescription = L.str(context, "New Topic"),
                                )
                            }
                        }
                        Box {
                            IconButton(onClick = { moreMenuExpanded = true }) {
                                Icon(
                                    Icons.Outlined.MoreVert,
                                    contentDescription = L.str(context, "More"),
                                )
                            }
                            TopicListMoreMenu(
                                expanded = moreMenuExpanded,
                                onDismiss = { moreMenuExpanded = false },
                                orderMenuExpanded = orderMenuExpanded,
                                onOrderMenuShow = { orderMenuExpanded = true },
                                onOrderMenuDismiss = { orderMenuExpanded = false },
                                shareMenuExpanded = shareMenuExpanded,
                                onShareMenuShow = { shareMenuExpanded = true },
                                onShareMenuDismiss = { shareMenuExpanded = false },
                                mock = mock,
                                debugName = forumId.idDisplay() +
                                    " " + (responseForum?.name ?: ""),
                                orderOrDefault = orderOrDefault,
                                onOrderChange = { order = it },
                                onHotTopics = {
                                    navigator.push(
                                        Route.TopicList(forumId = forumId, mode = TopicListMode.HOT)
                                    )
                                },
                                onRecommended = {
                                    navigator.push(
                                        Route.TopicList(
                                            forumId = forumId,
                                            mode = TopicListMode.RECOMMENDED,
                                        )
                                    )
                                },
                                toppedTopicID = toppedTopicID,
                                onToppedTopic = { id ->
                                    navigator.push(Route.TopicDetails(topicId = id))
                                },
                                hasSubforums = subforums.isNotEmpty(),
                                onSubforums = {
                                    navigator.push(Route.SubforumList(forumId = forumId))
                                },
                                onTopicSearch = {
                                    navigator.push(Route.TopicSearch(forumId = forumId))
                                },
                                onRefresh = { triggerRefresh() },
                                signedIn = signedIn,
                                isFavorite = isForumFavorite,
                                onToggleFavorite = {
                                    App.favoriteForums.toggle(forum) {
                                        Haptics.lightImpact(view)
                                    }
                                },
                                shareTitle = forum.name,
                                mngaURL = navID.mngaURL,
                                webpageURL = navID.webpageURL,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        val headerLabel = when (mode) {
            TopicListMode.HOT -> L.str(context, hotRangeLabel(hotRange))
            TopicListMode.RECOMMENDED -> null
            TopicListMode.NORMAL -> L.str(
                context,
                if (orderOrDefault == TopicListOrder.POST_DATE) "Latest Topics" else "Latest Replies",
            )
        }
        val header: (@Composable () -> Unit)? = headerLabel?.let { label ->
            {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        Box(Modifier.fillMaxSize().padding(padding)) {
            PagedList(
                dataSource = dataSource,
                key = { it.id },
                // The toolbar already shows the loading spinner while the
                // first page loads; skip the centered one.
                showInitialLoading = false,
                emptyPlaceholder = L.str(context, "No Results"),
                header = header,
                // A fresh entry (no restored snapshot) must start at the top:
                // the saveable scroll position would otherwise be restored
                // from the route registry even though the data was reloaded.
                freshEntry = savedItemsB64.isEmpty(),
                scrollToTopSignal = refreshScrollEpoch,
                itemContent = { _, topic ->
                    TopicListItem(
                        topic = topic,
                        useTopicPostDate = mode == TopicListMode.RECOMMENDED ||
                            (mode == TopicListMode.NORMAL && orderOrDefault == TopicListOrder.POST_DATE),
                        // Topics of this very forum name no parent of their
                        // own; the forum being browsed is the answer for them.
                        fallbackForumName = forum.name.takeIf { !mock },
                        favoredOverride = favoredOverrides[topic.id],
                        contextMenuTopic = contextMenuTopic,
                        onContextMenu = { contextMenuTopic = it },
                        onDismissMenu = { contextMenuTopic = null },
                        onClick = {
                            navigator.push(
                                Route.TopicDetails(
                                    topicId = topic.id,
                                    fav = topic.fav.takeIf { it.isNotEmpty() },
                                )
                            )
                        },
                        onToggleFavor = { toggleTopicFavor(topic) },
                        onNavigateToForum = { id ->
                            navigator.push(Route.TopicList(forumId = id))
                        },
                    )
                },
            )
        }
    }
}

private fun hotRangeLabel(range: HotTopicListRequest.DateRange): String = when (range) {
    HotTopicListRequest.DateRange.DAY -> "Last 24 hours"
    HotTopicListRequest.DateRange.WEEK -> "Last week"
    else -> "Last month"
}

private fun ForumId.idDisplay(): String =
    if (hasFid()) "#$fid" else "st#$stid"

/**
 * One topic entry: swipe-to-favorite wrapper around either a forum-shortcut
 * row or the standard topic row with its long-press context menu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopicListItem(
    topic: Topic,
    useTopicPostDate: Boolean,
    fallbackForumName: String?,
    favoredOverride: Boolean?,
    contextMenuTopic: Topic?,
    onContextMenu: (Topic?) -> Unit,
    onDismissMenu: () -> Unit,
    onClick: () -> Unit,
    onToggleFavor: () -> Unit,
    onNavigateToForum: (ForumId) -> Unit,
) {
    val context = LocalContext.current
    var shareMenuExpanded by remember { mutableStateOf(false) }
    val boxState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd) {
                onToggleFavor()
            }
            false // Never actually dismiss: favoriting keeps the row.
        }
    )

    SwipeToDismissBox(
        state = boxState,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(
                    Icons.Outlined.Star,
                    contentDescription = L.str(context, "Mark as Favorite"),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 32.dp),
                )
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = false,
    ) {
        Box {
            if (topic.hasShortcutForum()) {
                ForumShortcutRow(
                    topic = topic,
                    onClick = { onNavigateToForum(topic.shortcutForum.id) },
                    onLongClick = { onContextMenu(topic) },
                )
            } else {
                TopicRow(
                    topic = topic,
                    useTopicPostDate = useTopicPostDate,
                    dimmedSubject = true,
                    showIndicators = true,
                    isFavored = favoredOverride,
                    fallbackForumName = fallbackForumName,
                    onClick = onClick,
                    onLongClick = { onContextMenu(topic) },
                )
            }

            DropdownMenu(
                expanded = contextMenuTopic == topic,
                onDismissRequest = {
                    shareMenuExpanded = false
                    onDismissMenu()
                },
            ) {
                DropdownMenuItem(
                    text = { Text(L.str(context, "Goto Topic")) },
                    leadingIcon = { Icon(Icons.Outlined.ArrowForward, contentDescription = null) },
                    onClick = {
                        onClick()
                        onDismissMenu()
                    },
                )
                val title = topicSubjectContent(topic).trim()
                if (title.isNotEmpty()) {
                    DropdownMenuItem(
                        text = { Text(L.str(context, "Copy Title")) },
                        leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                        onClick = {
                            copyToClipboard(context, title)
                            onDismissMenu()
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(L.str(context, "Share")) },
                    leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
                    onClick = { shareMenuExpanded = true },
                )
                DropdownMenu(
                    expanded = shareMenuExpanded,
                    onDismissRequest = { shareMenuExpanded = false },
                ) {
                    val navID = topicNavID(topic)
                    ShareLinksMenuItems(
                        shareTitle = topicSubjectFull(topic),
                        mngaURL = navID.mngaURL,
                        webpageURL = navID.webpageURL,
                        onDone = {
                            shareMenuExpanded = false
                            onDismissMenu()
                        },
                    )
                }
            }
        }
    }
}

/** The three share entries (LumaGA link / NGA link / open in browser). */
@Composable
private fun ShareLinksMenuItems(
    shareTitle: String,
    mngaURL: String?,
    webpageURL: String?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    mngaURL?.let { url ->
        DropdownMenuItem(
            text = { Text(L.str(context, "LumaGA Link")) },
            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
            onClick = {
                shareText(context, "$shareTitle $url".trim())
                onDone()
            },
        )
    }
    webpageURL?.let { url ->
        DropdownMenuItem(
            text = { Text(L.str(context, "NGA Link")) },
            leadingIcon = { Icon(Icons.Outlined.Language, contentDescription = null) },
            onClick = {
                shareText(context, "$shareTitle $url".trim())
                onDone()
            },
        )
        DropdownMenuItem(
            text = { Text(L.str(context, "Open in Browser")) },
            leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
            onClick = {
                openInBrowser(context, url)
                onDone()
            },
        )
    }
}

/** Forum-shortcut topic row (SS2 topic-shortcut variant). */
@Composable
private fun ForumShortcutRow(
    topic: Topic,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    com.bugenzhao.mnga.ui.screens.forumlist.ForumRowCard(
        forum = topic.shortcutForum,
        isFavorite = false,
        asTopicShortcut = topic,
        onClick = onClick,
        onLongClick = onLongClick,
    )
}

/** The toolbar More menu (SS5). */
@Composable
private fun TopicListMoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    orderMenuExpanded: Boolean,
    onOrderMenuShow: () -> Unit,
    onOrderMenuDismiss: () -> Unit,
    shareMenuExpanded: Boolean,
    onShareMenuShow: () -> Unit,
    onShareMenuDismiss: () -> Unit,
    mode: TopicListMode = TopicListMode.NORMAL,
    mock: Boolean,
    debugName: String,
    orderOrDefault: TopicListOrder,
    onOrderChange: (TopicListOrder) -> Unit,
    onHotTopics: () -> Unit,
    onRecommended: () -> Unit,
    toppedTopicID: String?,
    onToppedTopic: (String) -> Unit,
    hasSubforums: Boolean,
    onSubforums: () -> Unit,
    onTopicSearch: () -> Unit,
    onRefresh: () -> Unit,
    signedIn: Boolean,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    shareTitle: String,
    mngaURL: String?,
    webpageURL: String?,
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (!mock) {
            DropdownMenuItem(
                text = {
                    Text(
                        debugName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                onClick = {},
                enabled = false,
            )
            DropdownMenuItem(
                text = { Text(L.str(context, "Order by")) },
                trailingIcon = {
                    Text(
                        L.str(
                            context,
                            if (orderOrDefault == TopicListOrder.POST_DATE) {
                                "Latest Topics"
                            } else {
                                "Latest Replies"
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                onClick = onOrderMenuShow,
            )
            DropdownMenu(
                expanded = orderMenuExpanded,
                onDismissRequest = onOrderMenuDismiss,
            ) {
                DropdownMenuItem(
                    text = { Text(L.str(context, "Latest Replies")) },
                    trailingIcon = {
                        if (orderOrDefault == TopicListOrder.LAST_POST) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                        }
                    },
                    onClick = { onOrderChange(TopicListOrder.LAST_POST) },
                )
                DropdownMenuItem(
                    text = { Text(L.str(context, "Latest Topics")) },
                    trailingIcon = {
                        if (orderOrDefault == TopicListOrder.POST_DATE) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                        }
                    },
                    onClick = { onOrderChange(TopicListOrder.POST_DATE) },
                )
            }
            DropdownMenuItem(
                text = { Text(L.str(context, "Hot Topics")) },
                leadingIcon = {
                    Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null)
                },
                onClick = {
                    if (PlusModel.checkPlus(PlusFeature.HOT_TOPIC)) onHotTopics()
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(L.str(context, "Recommended Topics")) },
                leadingIcon = { Icon(Icons.Outlined.ThumbUp, contentDescription = null) },
                onClick = {
                    onRecommended()
                    onDismiss()
                },
            )
            if (toppedTopicID != null) {
                DropdownMenuItem(
                    text = { Text(L.str(context, "Topped Topic")) },
                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                    onClick = {
                        onToppedTopic(toppedTopicID)
                        onDismiss()
                    },
                )
            }
        }

        // Share submenu.
        DropdownMenuItem(
            text = { Text(L.str(context, "Share")) },
            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
            onClick = onShareMenuShow,
        )
        DropdownMenu(
            expanded = shareMenuExpanded,
            onDismissRequest = onShareMenuDismiss,
        ) {
            ShareLinksMenuItems(
                shareTitle = shareTitle,
                mngaURL = mngaURL,
                webpageURL = webpageURL,
                onDone = {
                    onShareMenuDismiss()
                    onDismiss()
                },
            )
        }

        if (hasSubforums) {
            DropdownMenuItem(
                text = { Text(L.str(context, "Subforums")) },
                leadingIcon = { Icon(Icons.Outlined.Category, contentDescription = null) },
                onClick = {
                    onSubforums()
                    onDismiss()
                },
            )
        }
        if (!mock) {
            DropdownMenuItem(
                text = { Text(L.str(context, "Search Topics")) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                onClick = {
                    onTopicSearch()
                    onDismiss()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(L.str(context, "Refresh")) },
            leadingIcon = { Icon(Icons.Outlined.Refresh, contentDescription = null) },
            onClick = {
                onRefresh()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    L.str(
                        context,
                        if (isFavorite) "Remove from Favorites" else "Mark as Favorite",
                    )
                )
            },
            leadingIcon = {
                Icon(
                    if (isFavorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                    contentDescription = null,
                )
            },
            onClick = {
                onToggleFavorite()
                onDismiss()
            },
        )
    }
}
