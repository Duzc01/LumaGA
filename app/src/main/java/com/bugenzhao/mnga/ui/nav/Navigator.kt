package com.bugenzhao.mnga.ui.nav

import androidx.navigation.NavHostController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * A path-based navigation stack mirroring SwiftUI's `NavigationStack`
 * semantics, backed by Navigation Compose's [NavHostController].
 *
 * The public surface (push/pop/popToRoot/popTo/replace/contains/stack/size/
 * lastOp) is unchanged; internally the route list is derived from the
 * NavController back stack, so programmatic navigation (deep links, context
 * menus) and system back presses stay in sync. NavHost keeps every entry's
 * composition and saveable state alive while it is off-screen, so popping back
 * to a screen resumes it instead of rebuilding and refetching.
 */
class Navigator(
    val navController: NavHostController,
    initial: List<Route> = emptyList(),
) {

    /** How the top-most route most recently entered the stack; screens use it
     * to tell a fresh push from a pop-back (resume). */
    enum class Op { PUSH, POP }

    private val _stack = MutableStateFlow(initial)
    val stack: StateFlow<List<Route>> = _stack

    val current: Route? get() = _stack.value.lastOrNull()
    val size: Int get() = _stack.value.size

    /** PUSH after [push], POP after any pop-style operation. */
    var lastOp: Op = Op.PUSH
        private set

    fun push(route: Route) {
        lastOp = Op.PUSH
        navController.navigate(RouteCodec.encode(route))
    }

    fun pop() {
        lastOp = Op.POP
        navController.popBackStack()
    }

    /** Pushes [route], replacing the current top entry — for a sheet that
     * routes onward into a pushed screen. (Push-then-pop would pop the very
     * entry just pushed.) */
    fun pushReplacingTop(route: Route) {
        val top = navController.currentBackStack.value.lastOrNull() ?: return
        lastOp = Op.PUSH
        navController.navigate(RouteCodec.encode(route)) {
            // popUpTo matches the *destination* id, not the entry id.
            popUpTo(top.destination.id) { inclusive = true }
        }
    }

    fun popToRoot() {
        navController.popBackStack(RouteCodec.ROUTE_FORUM_LIST, inclusive = false)
    }

    fun popTo(index: Int) {
        val entry = navController.currentBackStack.value.getOrNull(index) ?: return
        navController.popBackStack(entry, inclusive = false)
    }

    fun replace(route: Route) {
        navController.navigate(RouteCodec.encode(route)) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    fun contains(predicate: (Route) -> Boolean): Boolean = _stack.value.any(predicate)

    /**
     * Keeps [stack] (and [lastOp]) in sync with the NavController back stack.
     * Call once from composition:
     * `LaunchedEffect(navigator) { navigator.observe(this) }`.
     */
    fun observe(scope: CoroutineScope) {
        scope.launch {
            navController.currentBackStack.collect { entries ->
                val routes = entries.mapNotNull(RouteCodec::decode)
                val previous = _stack.value
                lastOp = when {
                    routes.size > previous.size -> Op.PUSH
                    routes.size < previous.size -> Op.POP
                    else -> lastOp
                }
                _stack.value = routes
            }
        }
    }
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
