package com.bugenzhao.mnga.ui.nav

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A minimal path-based navigation stack mirroring SwiftUI's `NavigationStack`
 * semantics: screens push/pop on an observable route list, and programmatic
 * navigation (deep links, context menus) manipulates the same list.
 */
class Navigator(initial: List<Route> = emptyList()) {

    private val _stack = MutableStateFlow(initial)
    val stack: StateFlow<List<Route>> = _stack

    val current: Route? get() = _stack.value.lastOrNull()
    val size: Int get() = _stack.value.size

    fun push(route: Route) {
        _stack.value = _stack.value + route
    }

    fun pop() {
        if (_stack.value.isNotEmpty()) {
            _stack.value = _stack.value.dropLast(1)
        }
    }

    fun popToRoot() {
        _stack.value = _stack.value.take(1)
    }

    fun popTo(index: Int) {
        if (index in _stack.value.indices && index < _stack.value.size - 1) {
            _stack.value = _stack.value.take(index + 1)
        }
    }

    fun replace(route: Route) {
        _stack.value = listOf(route)
    }

    fun contains(predicate: (Route) -> Boolean): Boolean = _stack.value.any(predicate)
}

/** One pushed screen. */
sealed class Route {
    /** Root forum list (also the implicit root of the main stack). */
    data object ForumList : Route()

    data class TopicList(
        val forumId: com.bugenzhao.mnga.protos.datamodel.ForumId,
        val categoryName: String? = null,
        /** Extra modes spawned from a topic list screen. */
        val mode: TopicListMode = TopicListMode.NORMAL,
        val dateRange: Int = 0,
    ) : Route()

    data class TopicDetails(
        val topicId: String,
        val fav: String? = null,
        val postId: String? = null,
        val authorId: String? = null,
        val anonymousAuthorOnly: Boolean = false,
        val localCache: Boolean = false,
        val startPage: Int? = null,
    ) : Route()

    data class UserProfile(
        val userId: String? = null,
        val userName: String? = null,
        val user: com.bugenzhao.mnga.protos.datamodel.User? = null,
    ) : Route()

    data object GlobalSearch : Route()
    data class TopicSearch(
        val forumId: com.bugenzhao.mnga.protos.datamodel.ForumId? = null,
    ) : Route()
    data object HotTopics : Route()
    data object Favorites : Route()
    data object History : Route()
    data object ShortMessages : Route()
    data class ShortMessageDetails(val id: String) : Route()
    data object Subforums :
        Route()

    data class SubforumList(
        val forumId: com.bugenzhao.mnga.protos.datamodel.ForumId,
    ) : Route()

    data class UnknownForum(val name: String?) : Route()
    data object CacheSettings : Route()
    data object BlockWords : Route()
    data object About : Route()
    data object Settings : Route()
    data object Notifications : Route()
}

enum class TopicListMode { NORMAL, HOT, RECOMMENDED }
