package com.bugenzhao.mnga.ui.screens.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.protos.datamodel.ShortMessagePost
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.ShortMessageDetailsRequest
import com.bugenzhao.mnga.protos.service.ShortMessageDetailsResponse

/**
 * Entry-scoped holder of one short-message conversation. Survives being
 * covered by a pushed screen, so popping back reuses the loaded posts
 * instead of refetching.
 */
class ShortMessageDetailsViewModel(id: String) : ViewModel() {

    val dataSource = PagingDataSource<ShortMessageDetailsResponse, ShortMessagePost>(
        scope = viewModelScope,
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

    companion object {
        fun factory(id: String) = viewModelFactory {
            initializer { ShortMessageDetailsViewModel(id) }
        }
    }
}
