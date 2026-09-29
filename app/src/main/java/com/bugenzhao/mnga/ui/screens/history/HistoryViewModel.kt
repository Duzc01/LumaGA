package com.bugenzhao.mnga.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.protos.datamodel.TopicSnapshot
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.DeleteTopicHistoryRequest
import com.bugenzhao.mnga.protos.service.DeleteTopicHistoryResponse
import com.bugenzhao.mnga.protos.service.TopicHistoryRequest
import com.bugenzhao.mnga.protos.service.TopicHistoryResponse
import kotlinx.coroutines.launch

/** Number of history entries requested from the server. */
private const val HistoryLimit = 1000L

/**
 * Entry-scoped holder of the browsing-history list. Survives being covered
 * by a pushed screen, so popping back reuses the loaded data instead of
 * refetching.
 *
 * Single-entry deletion goes straight to the Rust cache
 * (AsyncRequest.delete_topic_history), so the entry is really gone. A topic
 * viewed again is re-recorded and reappears, which matches the "it is history
 * again" expectation.
 */
class HistoryViewModel : ViewModel() {

    val dataSource = PagingDataSource<TopicHistoryResponse, TopicSnapshot>(
        scope = viewModelScope,
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

    fun deleteTopic(id: String) {
        viewModelScope.launch {
            val result = logicCallAsync(
                AsyncRequest.newBuilder()
                    .setDeleteTopicHistory(
                        DeleteTopicHistoryRequest.newBuilder().setTopicId(id).build()
                    )
                    .build(),
                DeleteTopicHistoryResponse.parser(),
            )
            result.onSuccess { dataSource.refresh() }
        }
    }
}
