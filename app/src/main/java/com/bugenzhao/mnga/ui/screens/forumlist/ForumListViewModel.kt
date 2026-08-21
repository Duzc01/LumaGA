package com.bugenzhao.mnga.ui.screens.forumlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.protos.datamodel.Category
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.ForumListRequest
import com.bugenzhao.mnga.protos.service.ForumListResponse
import kotlinx.coroutines.launch

/**
 * Entry-scoped holder of the root forum-list data.
 *
 * NavHost scopes [ViewModel]s to the back-stack entry: when the entry is
 * covered by a pushed screen its composition is disposed but the ViewModel
 * (and the loaded categories) survives, so popping back to the home screen
 * does not refetch or re-sync the favorite forums.
 */
class ForumListViewModel : ViewModel() {

    val dataSource = PagingDataSource<ForumListResponse, Category>(
        scope = viewModelScope,
        responseParser = { ForumListResponse.parser() },
        buildRequest = {
            AsyncRequest.newBuilder()
                .setForumList(ForumListRequest.getDefaultInstance())
                .build()
        },
        onResponse = { response -> Pair(response.categoriesList, 1) },
        id = { it.id },
    )

    init {
        // 登录状态变化时同步收藏版块。放在 ViewModel 里观察一次：组合重建
        // （返回本页）不会重新触发，而原来的 LaunchedEffect(authInfo) 会。
        viewModelScope.launch {
            App.authStorage.authInfo.collect { App.favoriteForums.sync() }
        }
    }
}
