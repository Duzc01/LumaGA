package com.bugenzhao.mnga.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.model.PagingDataSource

/**
 * Standard paged list scaffolding: pull-to-refresh, initial loading spinner,
 * empty/error placeholders, footer and last-3-items prefetch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <Item : Any> PagedList(
    dataSource: PagingDataSource<*, Item>,
    key: ((Item) -> Any)? = null,
    showFooter: Boolean = true,
    showInitialLoading: Boolean = true,
    emptyPlaceholder: String = "No Results",
    header: (@Composable () -> Unit)? = null,
    itemContent: @Composable (Int, Item) -> Unit,
) {
    val state by dataSource.state.collectAsState()
    // Save the scroll position so returning to this screen restores it.
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    // Prefetch when approaching the end.
    LaunchedEffect(listState, state.items.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= state.items.size - 3
        }.collect { nearEnd ->
            if (nearEnd && state.items.isNotEmpty()) {
                dataSource.loadMoreIfNeeded(state.items.size - 1)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { dataSource.refreshAsync(sleepMillis = 500) },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            dataSource.isInitialLoading && showInitialLoading ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            dataSource.isInitialLoading ->
                // The host screen shows its own (e.g. toolbar) loading
                // indicator; keep the content area blank until data arrives.
                Box(Modifier.fillMaxSize())
            state.items.isEmpty() && state.latestError != null ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val err = state.latestError
                    if (err != null) ErrorPlaceholder(err) { dataSource.refresh() }
                }
            state.items.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ListPlaceholder(emptyPlaceholder)
                }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (header != null) {
                    item(key = "header") { header() }
                }
                itemsIndexed(state.items, key = if (key != null) { _, item -> key(item) } else null) { index, item ->
                    itemContent(index, item)
                }
                if (showFooter) {
                    item(key = "footer") {
                        AdaptiveFooter(loading = state.isLoading, noMore = !dataSource.hasMore)
                    }
                }
            }
        }
    }
}
