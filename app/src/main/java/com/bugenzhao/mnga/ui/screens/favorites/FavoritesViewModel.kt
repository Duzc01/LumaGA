package com.bugenzhao.mnga.ui.screens.favorites

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.protos.datamodel.FavoriteTopicFolder
import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.FavoriteFolderCreateRequest
import com.bugenzhao.mnga.protos.service.FavoriteFolderCreateResponse
import com.bugenzhao.mnga.protos.service.FavoriteFolderListRequest
import com.bugenzhao.mnga.protos.service.FavoriteFolderListResponse
import com.bugenzhao.mnga.protos.service.FavoriteFolderModifyRequest
import com.bugenzhao.mnga.protos.service.FavoriteFolderModifyResponse
import com.bugenzhao.mnga.protos.service.FavoriteTopicListRequest
import com.bugenzhao.mnga.protos.service.FavoriteTopicListResponse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

/** Favorite folders of the logged-in user (SS15). */
class FavoriteFoldersModel(private val scope: CoroutineScope) {
    val folders = MutableStateFlow<List<FavoriteTopicFolder>>(emptyList())

    suspend fun load(force: Boolean = false) {
        if (folders.value.isEmpty() || force) {
            val result = logicCallAsync(
                AsyncRequest.newBuilder()
                    .setFavoriteFolderList(FavoriteFolderListRequest.getDefaultInstance())
                    .build(),
                FavoriteFolderListResponse.parser(),
            )
            result.onSuccess { response -> folders.value = response.foldersList }
        }
    }

    suspend fun modify(request: FavoriteFolderModifyRequest): Boolean {
        val result = logicCallAsync(
            AsyncRequest.newBuilder().setFavoriteFolderModify(request).build(),
            FavoriteFolderModifyResponse.parser(),
        )
        return if (result.isSuccess) {
            load(force = true)
            true
        } else {
            false
        }
    }

    /** Creates a folder and returns its id, or null on failure. */
    suspend fun create(name: String): String? {
        val result = logicCallAsync(
            AsyncRequest.newBuilder()
                .setFavoriteFolderCreate(
                    FavoriteFolderCreateRequest.newBuilder().setName(name).build()
                )
                .build(),
            FavoriteFolderCreateResponse.parser(),
        )
        return result.getOrNull()?.folderId?.also { load(force = true) }
    }
}

/**
 * Entry-scoped holder of the favorites screen state.
 *
 * NavHost scopes [ViewModel]s to the back-stack entry: when the entry is
 * covered, its composition is disposed but the ViewModel (folders, the
 * selected folder id, loaded topic lists) survives, so popping back reuses
 * it instead of refetching — no manual snapshot needed. The selected folder
 * id lives in [SavedStateHandle] so it also survives process death.
 */
class FavoritesViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    val foldersModel = FavoriteFoldersModel(viewModelScope)

    /** The folder the user last selected; kept across pop-backs and process death. */
    var currentFolderId: String?
        get() = savedStateHandle["currentFolderId"]
        set(value) {
            savedStateHandle["currentFolderId"] = value
        }

    // One paged topic list per folder, cached so switching folders back and
    // forth does not refetch. Folders are few, so the cache is small.
    private val topicSources =
        mutableMapOf<String, PagingDataSource<FavoriteTopicListResponse, Topic>>()

    fun topicDataSource(folderId: String): PagingDataSource<FavoriteTopicListResponse, Topic> =
        topicSources.getOrPut(folderId) {
            PagingDataSource(
                scope = viewModelScope,
                responseParser = { FavoriteTopicListResponse.parser() },
                buildRequest = { page ->
                    AsyncRequest.newBuilder()
                        .setFavoriteTopicList(
                            FavoriteTopicListRequest.newBuilder()
                                .setFolderId(folderId)
                                .setPage(page)
                                .build()
                        )
                        .build()
                },
                onResponse = { response ->
                    Pair(response.topicsList, response.pages.toInt().takeIf { it > 0 })
                },
                id = { it.id },
            )
        }
}
