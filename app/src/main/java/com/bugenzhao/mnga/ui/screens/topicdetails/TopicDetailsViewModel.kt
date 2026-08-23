package com.bugenzhao.mnga.ui.screens.topicdetails

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.bugenzhao.mnga.ui.nav.Route

/**
 * Entry-scoped holder of the topic-details data source. Survives being
 * covered by a pushed screen (e.g. a user profile), so popping back reuses
 * the loaded floors instead of refetching.
 */
class TopicDetailsViewModel(
    route: Route.TopicDetails,
    initialPage: Int,
) : ViewModel() {

    private val onlyPostId = route.postId?.takeIf { it.isNotEmpty() && !route.anonymousAuthorOnly }
    private val authorOnlyMode = route.authorId != null || route.anonymousAuthorOnly

    val dataSource = buildTopicDetailsDataSource(
        scope = viewModelScope,
        topicId = route.topicId,
        fav = route.fav?.takeIf { it.isNotEmpty() },
        onlyPostId = onlyPostId,
        localCache = route.localCache,
        authorId = route.authorId?.takeIf { it.isNotEmpty() },
        anonymousAuthorOnly = route.anonymousAuthorOnly,
        useDisabledStrategy = onlyPostId != null || authorOnlyMode,
        finishOnError = route.localCache,
        initialPage = initialPage,
    )

    companion object {
        fun factory(route: Route.TopicDetails, initialPage: Int) = viewModelFactory {
            initializer { TopicDetailsViewModel(route, initialPage) }
        }
    }
}
