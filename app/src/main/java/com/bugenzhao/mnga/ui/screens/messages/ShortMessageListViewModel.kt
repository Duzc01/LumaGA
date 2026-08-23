package com.bugenzhao.mnga.ui.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.protos.datamodel.ShortMessage
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.ShortMessageListRequest
import com.bugenzhao.mnga.protos.service.ShortMessageListResponse

/**
 * Entry-scoped holder of the short-message conversation list. Survives being
 * covered by a pushed screen, so popping back reuses the loaded data instead
 * of refetching.
 */
class ShortMessageListViewModel : ViewModel() {

    val dataSource = PagingDataSource<ShortMessageListResponse, ShortMessage>(
        scope = viewModelScope,
        responseParser = { ShortMessageListResponse.parser() },
        buildRequest = { page ->
            AsyncRequest.newBuilder()
                .setShortMessageList(
                    ShortMessageListRequest.newBuilder().setPage(page)
                )
                .build()
        },
        onResponse = { response ->
            Pair(response.messagesList, response.pages.toInt().takeIf { it > 0 })
        },
        id = { it.id },
    )
}
