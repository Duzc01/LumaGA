package com.bugenzhao.mnga.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.protos.datamodel.Forum
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.ForumSearchRequest
import com.bugenzhao.mnga.protos.service.ForumSearchResponse
import com.bugenzhao.mnga.protos.service.TopicSearchRequest
import com.bugenzhao.mnga.protos.service.TopicSearchResponse
import com.bugenzhao.mnga.ui.screens.forumlist.ForumIcon
import com.bugenzhao.mnga.ui.screens.topiclist.TopicRow
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.ui.nav.Route
import com.bugenzhao.mnga.util.L
import com.bugenzhao.mnga.util.errorLocalized
import kotlinx.coroutines.CoroutineScope

/**
 * Shared search plumbing: the two `AsyncRequest` data sources (`forumSearch`,
 * `topicSearch`) and the result lists they feed, used by both tabs of
 * [SearchScreen]. Ported from `GlobalSearchView.swift` + `ForumSearchView` +
 * `TopicSearchView`.
 */

// region Data source builders

/** `forumSearch` with the committed key; a single, non-paged result page. */
internal fun buildForumSearchDataSource(
    scope: CoroutineScope,
    text: String,
): PagingDataSource<ForumSearchResponse, Forum> =
    PagingDataSource(
        scope = scope,
        responseParser = { ForumSearchResponse.parser() },
        buildRequest = { _ ->
            AsyncRequest.newBuilder()
                .setForumSearch(ForumSearchRequest.newBuilder().setKey(text).build())
                .build()
        },
        onResponse = { response -> Pair(response.forumsList, 1) },
        id = { it.id.idDescription },
    )

/**
 * `topicSearch`; paginated, scoped to [forumId] when provided (in-forum
 * search) and across all forums otherwise.
 */
internal fun buildTopicSearchDataSource(
    scope: CoroutineScope,
    text: String,
    forumId: ForumId?,
    searchContent: Boolean = true,
): PagingDataSource<TopicSearchResponse, Topic> =
    PagingDataSource(
        scope = scope,
        responseParser = { TopicSearchResponse.parser() },
        buildRequest = { page ->
            val request = TopicSearchRequest.newBuilder()
                .setKey(text)
                .setPage(page)
                .setSearchContent(searchContent)
            forumId?.let { request.setId(it) }
            AsyncRequest.newBuilder().setTopicSearch(request.build()).build()
        },
        onResponse = { response ->
            Pair(response.topicsList, response.pages.takeIf { it > 0 })
        },
        id = { it.id },
    )

// endregion

// region Result lists

/** Forum search results, like `ForumSearchView`. */
@Composable
internal fun ForumResultsList(
    dataSource: PagingDataSource<ForumSearchResponse, Forum>,
    navigator: Navigator,
) {
    val context = LocalContext.current
    val state by dataSource.state.collectAsState()

    // Keep observing the loading state here, before the branch selection: the
    // results list is swapped in right when loading finishes (while the
    // window/insets may still be changing), and its first measure can land in
    // a zero-size viewport. The read below keeps the composition wired to the
    // loading->loaded transition so the list re-measures once the viewport
    // settles. The value itself is not used; the observation is the point.
    @Suppress("UNUSED_VARIABLE")
    val loadingProbe = state.isLoading || state.lastRefreshTime != null

    // The swapped-in content is recreated whenever its branch changes. A bare
    // LazyColumn entering this slot can otherwise keep measuring the stale
    // (zero-item) content and render nothing until a later recomposition
    // forces a relayout.
    val branch = when {
        dataSource.notLoaded -> 0
        state.items.isEmpty() && state.latestError != null -> 1
        state.items.isEmpty() -> 2
        else -> 3
    }
    key(branch) {
        when {
            dataSource.notLoaded -> {
                LaunchedEffect(dataSource) { dataSource.initialLoad() }
                CenteredSpinner()
            }
            state.items.isEmpty() && state.latestError != null ->
                ErrorState(context.errorLocalized(state.latestError?.error ?: "error"))
            state.items.isEmpty() -> EmptyResultsState()
            else -> {
                val lstate = rememberLazyListState()
                LazyColumn(
                    state = lstate,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "header") { SectionHeader(L.str(context, "Search Results")) }
                    itemsIndexed(state.items, key = { _, forum -> forum.id.idDescription }) { _, forum ->
                        ForumRowLite(forum) {
                            navigator.push(Route.TopicList(forumId = forum.id))
                        }
                    }
                }
            }
        }
    }
}

/** Topic search results, like `TopicSearchView`: paged with prefetch. */
@Composable
internal fun TopicResultsList(
    dataSource: PagingDataSource<TopicSearchResponse, Topic>,
    navigator: Navigator,
) {
    val context = LocalContext.current
    val state by dataSource.state.collectAsState()

    // Observe the loading state: see ForumResultsList.
    @Suppress("UNUSED_VARIABLE")
    val loadingProbe = state.isLoading || state.lastRefreshTime != null

    // See ForumResultsList: recreate the swapped-in content per branch.
    val branch = when {
        dataSource.notLoaded -> 0
        state.items.isEmpty() && state.latestError != null -> 1
        state.items.isEmpty() -> 2
        else -> 3
    }
    key(branch) {
        when {
            dataSource.notLoaded -> {
                LaunchedEffect(dataSource) { dataSource.initialLoad() }
                CenteredSpinner()
            }
            state.items.isEmpty() && state.latestError != null ->
                ErrorState(context.errorLocalized(state.latestError?.error ?: "error"))
            state.items.isEmpty() -> EmptyResultsState()
            else -> {
                val lstate = rememberLazyListState()
                LazyColumn(
                    state = lstate,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item(key = "header") { SectionHeader(L.str(context, "Search Results")) }
                    itemsIndexed(state.items, key = { _, topic -> topic.id }) { index, topic ->
                        LaunchedEffect(index, state.items.size) {
                            dataSource.loadMoreIfNeeded(index)
                        }
                        TopicRow(
                            topic = topic,
                            onClick = { navigator.push(topicDetailsRoute(topic)) },
                        )
                    }
                    item(key = "footer") { ListFooter(loading = state.isLoading) }
                }
            }
        }
    }
}

/** `TopicDetailsView.build(topic:)` destination for a searched topic. */
internal fun topicDetailsRoute(topic: Topic): Route.TopicDetails =
    Route.TopicDetails(
        topicId = topic.id,
        fav = if (topic.hasFav()) topic.fav else null,
    )

// endregion

// region Rows

/** Simplified `ForumRowView`: icon, localized name/info, stid and favorite marks. */
@Composable
internal fun ForumRowLite(forum: Forum, onClick: () -> Unit) {
    val context = LocalContext.current
    val isFavorite = App.favoriteForums.isFavorite(forum.id)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ForumIcon(iconUrl = forum.iconUrl, name = L.str(context, forum.name))
            Column(Modifier.weight(1f)) {
                Text(
                    L.str(context, forum.name),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (forum.info.isNotEmpty()) {
                    Text(
                        L.str(context, forum.info),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (forum.id.hasStid()) {
                Icon(
                    Icons.Outlined.Layers,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isFavorite) {
                Icon(
                    Icons.Outlined.Star,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

// endregion

// region Shared list states

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
internal fun CenteredSpinner() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** `ContentUnavailableView("No Results", systemImage: "magnifyingglass")`. */
@Composable
internal fun EmptyResultsState() {
    val context = LocalContext.current
    PlaceholderState(L.str(context, "No Results"))
}

/** Idle hint shown before any query is committed. */
@Composable
internal fun SearchIdleHint() {
    val context = LocalContext.current
    PlaceholderState(L.str(context, "Search"))
}

/**
 * The shared empty state: the magnifier set in a soft disc so the placeholder
 * reads as a deliberate mark rather than as a stray glyph on the page.
 */
@Composable
private fun PlaceholderState(message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 64.dp, bottom = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Search,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
        )
    }
}

/** Shown for failed searches (the engine already banners the error toast). */
@Composable
internal fun ErrorState(message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Loading footer for paged results. */
@Composable
internal fun ListFooter(loading: Boolean) {
    if (loading) {
        CenteredSpinner()
    } else {
        Spacer(Modifier.height(8.dp))
    }
}

/** `ForumId.idDescription`: "#fid" or "##stid". */
internal val ForumId.idDescription: String
    get() = if (hasStid()) "##$stid" else "#$fid"

// endregion
