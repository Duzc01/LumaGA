package com.bugenzhao.mnga.ui.screens.subforums

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.protos.datamodel.Forum
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.protos.datamodel.Subforum
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.SubforumFilterRequest
import com.bugenzhao.mnga.protos.service.SubforumFilterResponse
import com.bugenzhao.mnga.protos.service.TopicListResponse
import com.bugenzhao.mnga.ui.components.ListPlaceholder
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.ui.nav.Route
import com.bugenzhao.mnga.ui.screens.forumlist.forumIdKey
import com.bugenzhao.mnga.ui.screens.forumlist.ForumRow
import com.bugenzhao.mnga.util.Haptics
import com.bugenzhao.mnga.util.L
import kotlinx.coroutines.launch

private const val SubforumTipSeenKey = "subforumTipSeen"

/**
 * Subforum management, a port of `SubforumListView`: forum rows with a
 * subscribe/unsubscribe switch per subforum (subforumFilter request), tap to
 * navigate, long-press to favorite, and a first-visit tip card.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SubforumListScreen(navigator: Navigator, forumId: ForumId) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = navigator.size > 1) { navigator.pop() }

    // Entry-scoped ViewModel: the loaded subforum list survives pop-backs
    // (composition is disposed, ViewModel is not) — no refetch on return.
    val subforumListVM: SubforumListViewModel = viewModel(
        factory = SubforumListViewModel.factory(forumId),
    )
    val dataSource = subforumListVM.dataSource
    val state by dataSource.state.collectAsState()
    LaunchedEffect(dataSource) {
        if (dataSource.notLoaded) dataSource.initialLoad()
    }

    // Local editable copy, updated optimistically and re-synced on refresh.
    val subforums = remember { mutableStateListOf<Subforum>() }
    LaunchedEffect(state.items, state.lastRefreshTime) {
        subforums.clear()
        subforums.addAll(state.items)
    }

    val forumName = (state.latestResponse as? TopicListResponse)?.forum?.name
        ?.ifEmpty { null }
        ?: (if (forumId.hasFid()) "#${forumId.fid}" else "st#${forumId.stid}")
    val favoriteForums by App.favoriteForums.forumsFlow().collectAsState()

    var tipSeen by remember {
        mutableStateOf(App.sharedPreferences.getBoolean(SubforumTipSeenKey, false))
    }
    var contextMenuForum by remember { mutableStateOf<Forum?>(null) }

    fun setSubforumFilter(show: Boolean, subforum: Subforum) {
        // Optimistic local flip; the server response reconciles it.
        val index = subforums.indexOfFirst { it.forum.id == subforum.forum.id }
        if (index >= 0) {
            subforums[index] = subforums[index].toBuilder().setSelected(show).build()
        }
        val parentFid = if (forumId.hasFid()) forumId.fid else forumId.stid
        scope.launch {
            val result = logicCallAsync(
                AsyncRequest.newBuilder()
                    .setSubforumFilter(
                        SubforumFilterRequest.newBuilder()
                            .setOperation(
                                if (show) {
                                    SubforumFilterRequest.Operation.SHOW
                                } else {
                                    SubforumFilterRequest.Operation.BLOCK
                                }
                            )
                            .setForumId(parentFid)
                            .setSubforumFilterId(subforum.filterId)
                            .build()
                    )
                    .build(),
                SubforumFilterResponse.parser(),
            )
            result.onSuccess {
                Haptics.play(view, Haptics.NotificationType.SUCCESS)
                dataSource.refresh(silentOnError = true)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        L.str(context, "Subforums of %@", forumName),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { dataSource.refreshAsync(sleepMillis = 500) },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (!tipSeen) {
                    item(key = "tip") {
                        SubforumTipCard(onDismiss = {
                            tipSeen = true
                            App.sharedPreferences.edit()
                                .putBoolean(SubforumTipSeenKey, true)
                                .apply()
                        })
                    }
                }
                if (dataSource.isInitialLoading && subforums.isEmpty()) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(vertical = 32.dp)) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(24.dp).align(Alignment.Center),
                                strokeWidth = 2.dp,
                            )
                        }
                    }
                } else if (subforums.isEmpty()) {
                    item(key = "empty") { ListPlaceholder(L.str(context, "Empty")) }
                } else {
                    itemsIndexed(
                        subforums,
                        key = { _, subforum ->
                            "${forumIdKey(subforum.forum.id)}:${subforum.filterId}"
                        },
                    ) { _, subforum ->
                        val forum = subforum.forum
                        val isFavorite = favoriteForums.any { it.id == forum.id }
                        Box {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(
                                        enabled = true,
                                        onClick = { navigator.push(Route.TopicList(forumId = forum.id)) },
                                        onLongClick = { contextMenuForum = forum },
                                    ),
                            ) {
                                Row(
                                    Modifier.padding(
                                        start = 16.dp,
                                        top = 12.dp,
                                        bottom = 12.dp,
                                        end = 8.dp,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        ForumRow(
                                            forum = forum,
                                            isFavorite = isFavorite,
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Switch(
                                        checked = subforum.filterable && subforum.selected ||
                                            !subforum.filterable,
                                        onCheckedChange = { checked ->
                                            if (subforum.filterable) {
                                                setSubforumFilter(checked, subforum)
                                            }
                                        },
                                        enabled = subforum.filterable,
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = contextMenuForum == forum,
                                onDismissRequest = { contextMenuForum = null },
                            ) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            L.str(
                                                context,
                                                if (isFavorite) {
                                                    "Remove from Favorites"
                                                } else {
                                                    "Mark as Favorite"
                                                },
                                            )
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            if (isFavorite) Icons.Outlined.Star
                                            else Icons.Outlined.StarBorder,
                                            contentDescription = null,
                                        )
                                    },
                                    onClick = {
                                        App.favoriteForums.toggle(forum) {
                                            Haptics.lightImpact(view)
                                        }
                                        contextMenuForum = null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** First-visit tip card, the TipKit `SubforumListTip` equivalent. */
@Composable
private fun SubforumTipCard(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                Icons.Outlined.Category,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    L.str(context, "View and Manage Subforums"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    L.str(context, "Subforum Tip"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Outlined.Close, contentDescription = null)
            }
        }
    }
}
