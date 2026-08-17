package com.bugenzhao.mnga.ui.screens.messages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.model.PlusFeature
import com.bugenzhao.mnga.model.appScope
import com.bugenzhao.mnga.protos.datamodel.PostContent
import com.bugenzhao.mnga.protos.datamodel.ShortMessage
import com.bugenzhao.mnga.protos.datamodel.ShortMessagePost
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.ShortMessageDetailsRequest
import com.bugenzhao.mnga.protos.service.ShortMessageDetailsResponse
import com.bugenzhao.mnga.ui.components.AdaptiveFooter
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.ui.screens.user.RawPostContent
import com.bugenzhao.mnga.ui.screens.user.UserView
import com.bugenzhao.mnga.ui.screens.user.UserViewStyle
import com.bugenzhao.mnga.ui.screens.user.checkPlusFeature
import com.bugenzhao.mnga.util.DateFormatters
import com.bugenzhao.mnga.util.L
import java.util.Date

/**
 * One short message conversation, ported from `ShortMessageDetailsView`:
 * participants strip plus paged post rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortMessageDetailsScreen(
    navigator: Navigator,
    id: String,
    onReply: (ShortMessage) -> Unit = {},
    contentRenderer: (@Composable (PostContent) -> Unit)? = null,
) {
    val context = LocalContext.current

    val dataSource = remember(id) {
        PagingDataSource<ShortMessageDetailsResponse, ShortMessagePost>(
            scope = appScope,
            responseParser = { ShortMessageDetailsResponse.parser() },
            buildRequest = { page ->
                AsyncRequest.newBuilder()
                    .setShortMessageDetails(
                        ShortMessageDetailsRequest.newBuilder()
                            .setId(id)
                            .setPage(page)
                    )
                    .build()
            },
            onResponse = { response ->
                Pair(response.postsList, response.pages.toInt().takeIf { it > 0 })
            },
            id = { it.id },
        )
    }

    LaunchedEffect(id) {
        if (dataSource.notLoaded) dataSource.initialLoad()
    }

    fun replyTarget(): ShortMessage =
        ShortMessage.newBuilder().setId(id).build()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            L.str(context, "Short Message Details"),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "#$id",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (checkPlusFeature(PlusFeature.SHORT_MESSAGE)) {
                                onReply(replyTarget())
                            }
                        },
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = L.str(context, "Reply"))
                    }
                },
            )
        },
    ) { padding ->
        val state by dataSource.state.collectAsState()
        val listState = rememberLazyListState()

        // Prefetch when approaching the end.
        LaunchedEffect(listState, state.items.size) {
            snapshotFlow {
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
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
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when {
                dataSource.isInitialLoading || dataSource.notLoaded ->
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator()
                    }
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val participants = dataSource.latestResponse?.usersList.orEmpty()
                    if (participants.isNotEmpty()) {
                        item(key = "participants-label") {
                            Text(
                                L.str(context, "Participants"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        item(key = "participants") {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surface,
                            ) {
                                LazyRow(
                                    Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                ) {
                                    itemsIndexed(participants, key = { _, u -> u.id }) { _, user ->
                                        UserView(
                                            user = user,
                                            style = UserViewStyle.VERTICAL,
                                            onShowUserProfile = { u ->
                                                navigator.push(
                                                    com.bugenzhao.mnga.ui.nav.Route.UserProfile(
                                                        user = u,
                                                    )
                                                )
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }

                    itemsIndexed(state.items, key = { _, post -> post.id }) { _, post ->
                        ShortMessagePostRow(post = post, contentRenderer = contentRenderer)
                    }

                    item(key = "footer") {
                        AdaptiveFooter(loading = state.isLoading, noMore = !dataSource.hasMore)
                    }
                }
            }
        }
    }

    BackHandler(enabled = navigator.size > 1) { navigator.pop() }
}

/**
 * One message post row, ported from `ShortMessagePostRowView`: author header,
 * subject + rich content and a switchable date footer.
 */
@Composable
internal fun ShortMessagePostRow(
    post: ShortMessagePost,
    modifier: Modifier = Modifier,
    contentRenderer: (@Composable (PostContent) -> Unit)? = null,
) {
    val context = LocalContext.current
    var detailedDate by rememberSaveable(post.id) { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.weight(1f)) {
                    UserView(
                        id = post.authorId,
                        style = UserViewStyle.NORMAL,
                    )
                }
            }
            if (post.subject.isNotEmpty()) {
                Text(
                    post.subject,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val renderer = contentRenderer
            if (renderer != null) renderer(post.content)
            else RawPostContent(
                post.content,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                Modifier.fillMaxWidth().clickable { detailedDate = !detailedDate },
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    if (detailedDate) {
                        DateFormatters.detailed(context, Date(post.postDate * 1000))
                    } else {
                        DateFormatters.automatic(context, Date(post.postDate * 1000))
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
