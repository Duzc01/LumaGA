package com.bugenzhao.mnga.ui.screens.topiclist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.HotTopicListRequest
import com.bugenzhao.mnga.protos.service.HotTopicListResponse
import com.bugenzhao.mnga.protos.service.TopicListRequest
import com.bugenzhao.mnga.protos.service.TopicListResponse
import com.bugenzhao.mnga.storage.BlockWordsStorage
import com.bugenzhao.mnga.ui.nav.TopicListMode

/**
 * Entry-scoped holder of the forum's paged topic lists.
 *
 * NavHost scopes [ViewModel]s to the back-stack entry: when the entry is
 * covered, its composition is disposed but the ViewModel (and the loaded
 * data) survives, so popping back reuses it instead of refetching — no
 * manual snapshot needed. The ViewModel is cleared (and data refetched)
 * when the entry leaves the back stack or the process dies.
 */
class TopicListViewModel(
    private val forumId: ForumId,
    private val mode: TopicListMode,
) : ViewModel() {

    /** Block words + forum-shortcut filtering (SS5 maybeFiltered). */
    private fun maybeFiltered(topics: List<Topic>): List<Topic> {
        var result = topics
        if (App.prefs.topicListHideBlocked.value) {
            result = result.filter { !App.blockWords.blocked(BlockWordsStorage.content(it)) }
        }
        if (!App.prefs.topicListShowForumShortcut.value) {
            result = result.filter { !it.hasShortcutForum() }
        }
        return result
    }

    val dataSourceLastPost = PagingDataSource<TopicListResponse, Topic>(
        scope = viewModelScope,
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

    val dataSourcePostDate = PagingDataSource<TopicListResponse, Topic>(
        scope = viewModelScope,
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

    val recommendedDataSource = PagingDataSource<TopicListResponse, Topic>(
        scope = viewModelScope,
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
            Pair(maybeFiltered(response.topicsList), response.pages.toInt().takeIf { it > 0 })
        },
        id = { it.id },
    )

    // The hot list is per date-range; ranges are a small fixed enum, cache them.
    private val hotSources =
        mutableMapOf<HotTopicListRequest.DateRange, PagingDataSource<HotTopicListResponse, Topic>>()

    fun hotDataSource(range: HotTopicListRequest.DateRange): PagingDataSource<HotTopicListResponse, Topic> =
        hotSources.getOrPut(range) {
            PagingDataSource(
                scope = viewModelScope,
                responseParser = { HotTopicListResponse.parser() },
                buildRequest = { _ ->
                    AsyncRequest.newBuilder()
                        .setHotTopicList(
                            HotTopicListRequest.newBuilder()
                                .setId(forumId)
                                .setRange(range)
                                .setFetchPageLimit(5)
                                .build()
                        )
                        .build()
                },
                onResponse = { response -> Pair(response.topicsList, 1) },
                id = { it.id },
            )
        }

    companion object {
        fun factory(forumId: ForumId, mode: TopicListMode) = viewModelFactory {
            initializer { TopicListViewModel(forumId, mode) }
        }
    }
}
