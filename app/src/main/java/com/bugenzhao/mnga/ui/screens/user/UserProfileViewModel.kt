package com.bugenzhao.mnga.ui.screens.user

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.protos.datamodel.TopicWithLightPost
import com.bugenzhao.mnga.protos.datamodel.User
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.RemoteUserRequest
import com.bugenzhao.mnga.protos.service.UserPostListRequest
import com.bugenzhao.mnga.protos.service.UserPostListResponse
import com.bugenzhao.mnga.protos.service.UserTopicListRequest
import com.bugenzhao.mnga.protos.service.UserTopicListResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Entry-scoped holder of the user-profile state: the resolved [User], its
 * loading flag, and the paged topic/post lists keyed by user id. Survives
 * being covered by a pushed screen, so popping back does not re-resolve the
 * user nor refetch the lists.
 */
class UserProfileViewModel(
    private val userId: String?,
    private val userName: String?,
    initialUser: User?,
) : ViewModel() {

    val currentUser = MutableStateFlow(initialUser)
    val loading = MutableStateFlow(initialUser == null && (userId != null || userName != null))

    init {
        // Resolve by id/name like `RemoteUserProfileView` (with error toast).
        if (currentUser.value == null && (userId != null || userName != null)) {
            viewModelScope.launch {
                val request = RemoteUserRequest.newBuilder()
                if (!userId.isNullOrEmpty()) request.userId = userId
                if (!userName.isNullOrEmpty()) request.userName = userName
                val resolved = App.users.remoteUser(request.build(), showError = true)
                loading.value = false
                if (resolved != null) currentUser.value = resolved
            }
        }
    }

    fun reload() {
        val id = currentUser.value?.id ?: return
        viewModelScope.launch {
            App.users.remoteUser(id, showError = false, ignoreCache = true)
                ?.let { currentUser.value = it }
        }
    }

    private val topicSources =
        mutableMapOf<String, PagingDataSource<UserTopicListResponse, Topic>>()

    fun topicDataSource(userId: String): PagingDataSource<UserTopicListResponse, Topic> =
        topicSources.getOrPut(userId) {
            PagingDataSource(
                scope = viewModelScope,
                responseParser = { UserTopicListResponse.parser() },
                buildRequest = { page ->
                    AsyncRequest.newBuilder()
                        .setUserTopicList(
                            UserTopicListRequest.newBuilder()
                                .setAuthorId(userId)
                                .setPage(page)
                        )
                        .build()
                },
                onResponse = { response ->
                    Pair(response.topicsList, response.pages.toInt().takeIf { it > 0 })
                },
                id = { it.id },
            )
        }

    private val postSources =
        mutableMapOf<String, PagingDataSource<UserPostListResponse, TopicWithLightPost>>()

    fun postDataSource(userId: String): PagingDataSource<UserPostListResponse, TopicWithLightPost> =
        postSources.getOrPut(userId) {
            PagingDataSource(
                scope = viewModelScope,
                responseParser = { UserPostListResponse.parser() },
                buildRequest = { page ->
                    AsyncRequest.newBuilder()
                        .setUserPostList(
                            UserPostListRequest.newBuilder()
                                .setAuthorId(userId)
                                .setPage(page)
                        )
                        .build()
                },
                // Page count unknown: load until empty/error.
                onResponse = { response -> Pair(response.tpsList, Int.MAX_VALUE) },
                id = { "${it.post.id.tid}/${it.post.id.pid}" },
                finishOnError = true,
            )
        }

    companion object {
        fun factory(userId: String?, userName: String?, initialUser: User?) =
            viewModelFactory {
                initializer { UserProfileViewModel(userId, userName, initialUser) }
            }
    }
}
