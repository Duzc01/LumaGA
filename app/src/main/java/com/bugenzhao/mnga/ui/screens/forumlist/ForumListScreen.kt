package com.bugenzhao.mnga.ui.screens.forumlist

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPasteGo
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material.icons.outlined.PlaylistAddCheck
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.NavigationIdentifier
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.protos.datamodel.Category
import com.bugenzhao.mnga.protos.datamodel.Forum
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.ForumListRequest
import com.bugenzhao.mnga.protos.service.ForumListResponse
import com.bugenzhao.mnga.ui.components.Avatar
import com.bugenzhao.mnga.ui.components.LoadingRow
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.ui.nav.Route
import com.bugenzhao.mnga.ui.screens.user.nameDisplayCompat
import com.bugenzhao.mnga.ui.screens.topiclist.copyToClipboard
import com.bugenzhao.mnga.ui.screens.topiclist.openInBrowser
import com.bugenzhao.mnga.ui.screens.topiclist.shareText
import com.bugenzhao.mnga.storage.FilterMode
import com.bugenzhao.mnga.util.Haptics
import com.bugenzhao.mnga.util.L
import kotlinx.coroutines.launch

private const val CollapsedCategoriesKey = "collapsedCategories"
private const val FavoritesSectionID = "LumaGA-Favorites"

/**
 * The root forum sidebar, a port of `ForumListView`: favorite forums on top,
 * collapsible per-category sections below, plus the root toolbar (user menu,
 * notifications, paywall, search, filter, settings) and the clipboard
 * deep-link "Navigate" affordance.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForumListScreen(
    navigator: Navigator,
    onShowUserMenu: () -> Unit = {},
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // -- Data: all forum categories.
    val dataSource = remember {
        PagingDataSource<ForumListResponse, Category>(
            scope = scope,
            responseParser = { ForumListResponse.parser() },
            buildRequest = {
                AsyncRequest.newBuilder()
                    .setForumList(ForumListRequest.getDefaultInstance())
                    .build()
            },
            onResponse = { response -> Pair(response.categoriesList, 1) },
            id = { it.id },
        )
    }
    val state by dataSource.state.collectAsState()
    val categories = state.items

    // -- Favorites & filter mode.
    val favoriteForums by App.favoriteForums.forumsFlow().collectAsState()
    val showAllRaw by App.favoriteForums.showAll.flow.collectAsState()
    val filterMode = FilterMode.fromRaw(showAllRaw)
    val useRemote by App.favoriteForums.useRemote.flow.collectAsState()
    val synced by App.favoriteForums.synced.collectAsState()

    // -- Collapsed category ids, persisted as a string set.
    var collapsedCategories by remember {
        mutableStateOf(
            App.sharedPreferences.getStringSet(CollapsedCategoriesKey, emptySet())?.toSet()
                ?: emptySet()
        )
    }

    fun toggleCollapsed(id: String) {
        collapsedCategories =
            if (id in collapsedCategories) collapsedCategories - id else collapsedCategories + id
        App.sharedPreferences.edit()
            .putStringSet(CollapsedCategoriesKey, collapsedCategories)
            .apply()
    }

    fun toggleFavorite(forum: Forum) {
        App.favoriteForums.toggle(forum) { Haptics.lightImpact(view) }
    }

    // -- Initial load, favorites sync on auth change.
    LaunchedEffect(Unit) {
        dataSource.initialLoad()
        App.favoriteForums.initialSync()
    }
    val authInfo by App.authStorage.authInfo.collectAsState()
    LaunchedEffect(authInfo) { App.favoriteForums.sync() }

    // -- Toolbar state.
    val currentUser by App.currentUser.user.collectAsState()
    val unreadCount by App.notis.unreadCountAnimated.collectAsState()
    val debugBadge by App.prefs.debugAlwaysShowNotificationBadge.flow.collectAsState()
    val canPaste by App.schemes.canTryNavigateToPasteboardURL.collectAsState()
    var editMode by remember { mutableStateOf(false) }
    var moreMenuExpanded by remember { mutableStateOf(false) }
    var filterSubmenuExpanded by remember { mutableStateOf(false) }
    var contextMenuForum by remember { mutableStateOf<Forum?>(null) }

    val refresh: () -> Unit = {
        scope.launch {
            dataSource.refreshAsync(sleepMillis = 500)
            App.favoriteForums.sync()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "LumaGA",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    // User menu avatar button with a 1dp accent ring. The start
                    // padding mirrors the visual gap of the trailing MoreVert
                    // icon so the avatar and the "More" icon sit symmetrically.
                    IconButton(
                        onClick = onShowUserMenu,
                        modifier = Modifier.padding(start = 14.dp),
                    ) {
                        Box(
                            Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surface),
                                contentAlignment = Alignment.Center,
                            ) {
                                Avatar(
                                    url = currentUser?.avatarUrl,
                                    name = currentUser?.nameDisplayCompat,
                                    size = 26,
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (editMode) {
                        // Edit-favorites mode: hide every other action and show
                        // only "Done" on the far right.
                        TextButton(onClick = { editMode = false }) {
                            Text("完成")
                        }
                        return@CenterAlignedTopAppBar
                    }
                    val showBell = unreadCount > 0 || debugBadge
                    if (showBell) {
                        IconButton(onClick = { App.notis.showingSheet.value = true }) {
                            BadgedBox(badge = { Badge { Text(unreadCount.toString()) } }) {
                                Icon(
                                    Icons.Outlined.Notifications,
                                    contentDescription = L.str(context, "Notifications"),
                                )
                            }
                        }
                    }
                    IconButton(onClick = { navigator.push(Route.GlobalSearch) }) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = L.str(context, "Search"),
                        )
                    }
                    // "More" menu on the far right, holding the forum filters.
                    Box {
                        IconButton(onClick = { moreMenuExpanded = true }) {
                            Icon(
                                Icons.Outlined.MoreVert,
                                contentDescription = L.str(context, "More"),
                            )
                        }
                        MoreMenu(
                            expanded = moreMenuExpanded,
                            onDismiss = { moreMenuExpanded = false },
                            submenuExpanded = filterSubmenuExpanded,
                            onSubmenuDismiss = { filterSubmenuExpanded = false },
                            onSubmenuShow = { filterSubmenuExpanded = true },
                            filterMode = filterMode,
                            onFilterModeChange = { mode ->
                                App.favoriteForums.showAll.value = mode.raw
                            },
                            onEditFavorites = { editMode = true },
                            anyCollapsed = collapsedCategories.isNotEmpty(),
                            onCollapseAll = {
                                collapsedCategories = categories.map { it.id }.toSet()
                                App.sharedPreferences.edit()
                                    .putStringSet(CollapsedCategoriesKey, collapsedCategories)
                                    .apply()
                            },
                            onExpandAll = {
                                collapsedCategories = emptySet()
                                App.sharedPreferences.edit()
                                    .putStringSet(CollapsedCategoriesKey, collapsedCategories)
                                    .apply()
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (canPaste) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Button(onClick = { App.schemes.navigateToPasteboardURL() }) {
                            Icon(
                                Icons.Outlined.ContentPasteGo,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(L.str(context, "Navigate"))
                        }
                    }
                }
            }
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // ---- Favorites section.
                item(
                    key = "favorites-header",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    SectionHeader(
                        title = L.str(context, "Favorites"),
                        expanded = FavoritesSectionID !in collapsedCategories,
                        onToggle = { toggleCollapsed(FavoritesSectionID) },
                        trailing = {
                            if (useRemote) {
                                Icon(
                                    if (synced) Icons.Outlined.CloudDone else Icons.Outlined.CloudQueue,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
                if (FavoritesSectionID !in collapsedCategories) {
                    if (favoriteForums.isEmpty()) {
                        item(key = "favorites-empty", span = { GridItemSpan(maxLineSpan) }) {
                            FavoritesEmptyHint()
                        }
                    } else {
                        items(
                            favoriteForums,
                            key = { forum -> "fav-${forumIdKey(forum.id)}" },
                        ) { forum ->
                            ForumGridCellWithMenu(
                                forum = forum,
                                isFavorite = true,
                                editMode = editMode,
                                contextMenuForum = contextMenuForum,
                                onContextMenu = { contextMenuForum = it },
                                onDismissMenu = { contextMenuForum = null },
                                onClick = { navigator.push(Route.TopicList(forumId = forum.id)) },
                                onToggleFavorite = { toggleFavorite(forum) },
                                onRemove = { toggleFavorite(forum) },
                            )
                        }
                    }
                }

                // ---- All forums, per collapsible category.
                if (filterMode == FilterMode.ALL) {
                    if (categories.isEmpty()) {
                        item(key = "categories-loading", span = { GridItemSpan(maxLineSpan) }) {
                            LoadingRow()
                        }
                    } else {
                        // The "mnga" meta category is the app's own board; it
                        // never belongs on the forum home.
                        val visibleCategories = categories.filterNot { it.id == "mnga" }
                        visibleCategories.forEach { category ->
                            item(
                                key = "cat-header-${category.id}",
                                span = { GridItemSpan(maxLineSpan) },
                            ) {
                                SectionHeader(
                                    title = category.name,
                                    expanded = category.id !in collapsedCategories,
                                    onToggle = { toggleCollapsed(category.id) },
                                )
                            }
                            if (category.id !in collapsedCategories) {
                                items(
                                    category.forumsList,
                                    key = { forum ->
                                        "cat-${category.id}-${forumIdKey(forum.id)}"
                                    },
                                ) { forum ->
                                    ForumGridCellWithMenu(
                                        forum = forum,
                                        isFavorite = App.favoriteForums.isFavorite(forum.id),
                                        editMode = false,
                                        contextMenuForum = contextMenuForum,
                                        onContextMenu = { contextMenuForum = it },
                                        onDismissMenu = { contextMenuForum = null },
                                        onClick = {
                                            navigator.push(Route.TopicList(forumId = forum.id))
                                        },
                                        onToggleFavorite = { toggleFavorite(forum) },
                                        onRemove = {},
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Collapsible section header with a rotating disclosure chevron (SS19). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(300),
        label = "chevron",
    )
    Surface(
        onClick = onToggle,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp).rotate(rotation),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Empty favorites hint: "No Favorites" + swipe hint (SS1). */
@Composable
private fun FavoritesEmptyHint() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                Icons.Outlined.StarBorder,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                L.str(context, "No Favorites"),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            L.str(context, "Swipe a forum to mark it as favorite"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A grid cell (icon + name) with a long-press context menu (favorite toggle,
 * copy link, share) and an edit-mode remove badge.
 */
@Composable
private fun ForumGridCellWithMenu(
    forum: Forum,
    isFavorite: Boolean,
    editMode: Boolean,
    contextMenuForum: Forum?,
    onContextMenu: (Forum?) -> Unit,
    onDismissMenu: () -> Unit,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRemove: () -> Unit,
) {
    val navID = NavigationIdentifier.ForumID(forum.id)
    Box {
        ForumGridCell(
            forum = forum,
            editMode = editMode,
            onClick = onClick,
            onLongClick = { if (!editMode) onContextMenu(forum) },
            onRemove = onRemove,
        )
        ForumContextMenu(
            expanded = contextMenuForum == forum,
            onDismiss = onDismissMenu,
            isFavorite = isFavorite,
            navID = navID,
            onToggleFavorite = {
                onToggleFavorite()
                onDismissMenu()
            },
        )
    }
}

/** Dense 3-per-row grid cell: forum icon on top, single-line name below. */
@Composable
private fun ForumGridCell(
    forum: Forum,
    editMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val context = LocalContext.current
    Box {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    enabled = true,
                    onClick = { if (!editMode) onClick() },
                    onLongClick = onLongClick,
                )
                .padding(horizontal = 4.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ForumIcon(iconUrl = forum.iconUrl, name = forum.name, size = 36.dp)
            Text(
                forum.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (editMode) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp),
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = L.str(context, "Remove from Favorites"),
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Shared long-press context menu for a forum (favorite / copy / share / open). */
@Composable
private fun ForumContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    isFavorite: Boolean,
    navID: NavigationIdentifier,
    onToggleFavorite: () -> Unit,
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
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
            onClick = onToggleFavorite,
        )
        navID.mngaURL?.let { url ->
            DropdownMenuItem(
                text = { Text(L.str(context, "Copy Link")) },
                leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                onClick = {
                    copyToClipboard(context, url)
                    onDismiss()
                },
            )
        }
        DropdownMenuItem(
            text = { Text(L.str(context, "Share")) },
            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
            onClick = {
                val url = navID.webpageURL ?: navID.mngaURL
                if (url != null) shareText(context, url)
                onDismiss()
            },
        )
        navID.webpageURL?.let { url ->
            DropdownMenuItem(
                text = { Text(L.str(context, "Open in Browser")) },
                leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, contentDescription = null) },
                onClick = {
                    openInBrowser(context, url)
                    onDismiss()
                },
            )
        }
    }
}

/** The trailing "More" menu of the forum list, holding the forum filters. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    submenuExpanded: Boolean,
    onSubmenuShow: () -> Unit,
    onSubmenuDismiss: () -> Unit,
    filterMode: FilterMode,
    onFilterModeChange: (FilterMode) -> Unit,
    onEditFavorites: () -> Unit,
    anyCollapsed: Boolean,
    onCollapseAll: () -> Unit,
    onExpandAll: () -> Unit,
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(L.str(context, "Edit Favorites")) },
            leadingIcon = {
                Icon(Icons.Outlined.PlaylistAddCheck, contentDescription = null)
            },
            onClick = {
                onEditFavorites()
                onDismiss()
            },
        )
        DropdownMenuItem(
            text = { Text(L.str(context, "Filters")) },
            leadingIcon = { Icon(Icons.Outlined.FilterList, contentDescription = null) },
            onClick = onSubmenuShow,
        )
        DropdownMenu(
            expanded = submenuExpanded,
            onDismissRequest = onSubmenuDismiss,
        ) {
            FilterMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = { Text(L.str(context, mode.raw)) },
                    leadingIcon = {
                        Icon(
                            if (mode == FilterMode.FAVORITES_ONLY) {
                                Icons.Outlined.Star
                            } else {
                                Icons.Outlined.StarBorder
                            },
                            contentDescription = null,
                        )
                    },
                    trailingIcon = {
                        if (mode == filterMode) {
                            Icon(Icons.Outlined.Check, contentDescription = null)
                        }
                    },
                    onClick = { onFilterModeChange(mode) },
                )
            }
        }
        if (filterMode == FilterMode.ALL) {
            if (!anyCollapsed) {
                DropdownMenuItem(
                    text = { Text(L.str(context, "Collapse All Categories")) },
                    leadingIcon = { Icon(Icons.Outlined.ExpandLess, contentDescription = null) },
                    onClick = {
                        onCollapseAll()
                        onDismiss()
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(L.str(context, "Expand All Categories")) },
                    leadingIcon = { Icon(Icons.Outlined.ExpandMore, contentDescription = null) },
                    onClick = {
                        onExpandAll()
                        onDismiss()
                    },
                )
            }
        }
    }
}
