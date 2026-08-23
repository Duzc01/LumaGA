package com.bugenzhao.mnga.ui.screens.subforums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.protos.datamodel.Subforum
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.TopicListRequest
import com.bugenzhao.mnga.protos.service.TopicListResponse
import com.bugenzhao.mnga.ui.screens.forumlist.forumIdKey

/**
 * Entry-scoped holder of the subforum list of one forum. Survives being
 * covered by a pushed screen, so popping back reuses the loaded data
 * instead of refetching.
 */
class SubforumListViewModel(forumId: ForumId) : ViewModel() {

    val dataSource = PagingDataSource<TopicListResponse, Subforum>(
        scope = viewModelScope,
        responseParser = { TopicListResponse.parser() },
        buildRequest = {
            AsyncRequest.newBuilder()
                .setTopicList(
                    TopicListRequest.newBuilder()
                        .setId(forumId)
                        .setPage(1)
                        .build()
                )
                .build()
        },
        onResponse = { response -> Pair(response.subforumsList, 1) },
        id = { "${forumIdKey(it.forum.id)}:${it.filterId}" },
    )

    companion object {
        fun factory(forumId: ForumId) = viewModelFactory {
            initializer { SubforumListViewModel(forumId) }
        }
    }
}
