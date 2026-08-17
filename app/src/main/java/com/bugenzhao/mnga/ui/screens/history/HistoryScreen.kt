package com.bugenzhao.mnga.ui.screens.history

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.protos.datamodel.CacheOperation
import com.bugenzhao.mnga.protos.datamodel.CacheType
import com.bugenzhao.mnga.protos.datamodel.TopicSnapshot
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.CacheRequest
import com.bugenzhao.mnga.protos.service.CacheResponse
import com.bugenzhao.mnga.protos.service.TopicHistoryRequest
import com.bugenzhao.mnga.protos.service.TopicHistoryResponse
import com.bugenzhao.mnga.ui.components.AdaptiveFooter
import com.bugenzhao.mnga.ui.components.ErrorPlaceholder
import com.bugenzhao.mnga.ui.components.ListPlaceholder
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.ui.nav.Route
import com.bugenzhao.mnga.ui.screens.topiclist.TopicRow
import com.bugenzhao.mnga.ui.screens.topiclist.topicSubjectFull
import com.bugenzhao.mnga.util.L
import kotlinx.coroutines.launch

private const val HistoryLimit = 1000L

/**
 * Local reading history, a port of `TopicHistoryListView`: snapshots served
 * from cache, searched by committed text, rows show the last-visit timestamp,
 * and a clear-history action wipes the TOPIC_HISTORY cache.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navigator: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = navigator.size > 1) { navigator.pop() }

    val dataSource = remember {
        PagingDataSource<TopicHistoryResponse, TopicSnapshot>(
            scope = scope,
            responseParser = { TopicHistoryResponse.parser() },
            buildRequest = {
                AsyncRequest.newBuilder()
                    .setTopicHistory(
                        TopicHistoryRequest.newBuilder().setLimit(HistoryLimit).build()
                    )
                    .build()
            },
            onResponse = { response -> Pair(response.topicsList, 1) },
            id = { it.topicSnapshot.id },
        )
    }
    val state by dataSource.state.collectAsState()
    LaunchedEffect(Unit) { dataSource.initialLoad() }

    // BasicSearchModel semantics: committed on submit, nil when cleared.
    var searchText by remember { mutableStateOf("") }
    var committedText by remember { mutableStateOf<String?>(null) }
    var searchActive by remember { mutableStateOf(false) }
    var showingClearConfirmation by remember { mutableStateOf(false) }

    fun clearHistory() {
        scope.launch {
            val result = logicCallAsync(
                AsyncRequest.newBuilder()
                    .setCache(
                        CacheRequest.newBuilder()
                            .setType(CacheType.TOPIC_HISTORY)
                            .setOperation(CacheOperation.CLEAR)
                            .build()
                    )
                    .build(),
                CacheResponse.parser(),
            )
            result.onSuccess { dataSource.refresh() }
        }
    }

    // Snapshot display topic: dates replaced by the visit timestamp (ms -> s).
    val displayTopics = state.items.mapNotNull { snapshot ->
        val topic = snapshot.topicSnapshot
        if (topic.id.isEmpty()) return@mapNotNull null
        val visitDate = snapshot.timestamp / 1000
        topic.toBuilder()
            .setPostDate(visitDate)
            .setLastPostDate(visitDate)
            .build()
    }
    val filteredTopics = committedText?.let { text ->
        displayTopics.filter { topicSubjectFull(it).contains(text) }
    } ?: displayTopics

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L.str(context, "History"), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = { searchActive = !searchActive }) {
                        Icon(
                            Icons.Outlined.Search,
                            contentDescription = L.str(context, "Search History"),
                        )
                    }
                    IconButton(onClick = { showingClearConfirmation = true }) {
                        Icon(Icons.Outlined.Delete, contentDescription = L.str(context, "Delete"))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (searchActive) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { value ->
                        searchText = value
                        if (value.isEmpty()) committedText = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text(L.str(context, "Search History")) },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            searchText = ""
                            committedText = null
                            searchActive = false
                        }) {
                            Icon(Icons.Outlined.Close, contentDescription = null)
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { committedText = searchText.takeIf { it.isNotEmpty() } },
                    ),
                )
            }

            val listState = rememberLazyListState()
            LaunchedEffect(listState, filteredTopics.size) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
                    last >= filteredTopics.size - 3
                }.collect { nearEnd ->
                    if (nearEnd && filteredTopics.isNotEmpty()) {
                        dataSource.loadMoreIfNeeded(filteredTopics.size - 1)
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = { dataSource.refreshAsync(sleepMillis = 500) },
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    dataSource.isInitialLoading ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    displayTopics.isEmpty() && state.latestError != null ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ErrorPlaceholder(state.latestError!!) { dataSource.refresh() }
                        }
                    displayTopics.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ListPlaceholder(L.str(context, "No History"))
                        }
                    committedText != null && filteredTopics.isEmpty() ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            ListPlaceholder(L.str(context, "No Results"))
                        }
                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(filteredTopics, key = { _, topic -> topic.id }) { _, topic ->
                            TopicRow(
                                topic = topic,
                                dimmedSubject = false,
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
                        item(key = "footer") {
                            AdaptiveFooter(loading = state.isLoading, noMore = !dataSource.hasMore)
                        }
                    }
                }
            }
        }
    }

    if (showingClearConfirmation) {
        AlertDialog(
            onDismissRequest = { showingClearConfirmation = false },
            title = { Text(L.str(context, "Are you sure to clear the cache?")) },
            text = { Text(L.str(context, "Topic Histories")) },
            confirmButton = {
                TextButton(onClick = {
                    showingClearConfirmation = false
                    clearHistory()
                }) { Text(L.str(context, "Clear")) }
            },
            dismissButton = {
                TextButton(onClick = { showingClearConfirmation = false }) {
                    Text(L.str(context, "Cancel"))
                }
            },
        )
    }
}
