package com.bugenzhao.mnga.ui.nav

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.navigation.NavBackStackEntry
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.protos.datamodel.User
import org.json.JSONObject

/**
 * Maps [Route] objects to/from NavHost route strings.
 *
 * Every route type has its own route pattern; routes carrying arguments pack
 * them into a single URL-encoded JSON `{payload}` path argument. NavController
 * percent-decodes path segments when it parses a route string, so [encode]
 * must URL-encode the payload and [decode] receives it already decoded.
 * Decoding is defensive (runCatching): a broken payload yields null rather
 * than crashing the stack derivation.
 */
object RouteCodec {

    const val ROUTE_FORUM_LIST = "forum-list"
    const val ROUTE_TOPIC_LIST = "topic-list/{payload}"
    const val ROUTE_TOPIC_DETAILS = "topic-details/{payload}"
    const val ROUTE_USER_PROFILE = "user-profile/{payload}"
    const val ROUTE_GLOBAL_SEARCH = "global-search"
    const val ROUTE_TOPIC_SEARCH = "topic-search/{payload}"
    const val ROUTE_HOT_TOPICS = "hot-topics"
    const val ROUTE_FAVORITES = "favorites"
    const val ROUTE_HISTORY = "history"
    const val ROUTE_SHORT_MESSAGES = "short-messages"
    const val ROUTE_SHORT_MESSAGE_DETAILS = "short-message-details/{payload}"
    const val ROUTE_SUBFORUMS = "subforums"
    const val ROUTE_SUBFORUM_LIST = "subforum-list/{payload}"
    const val ROUTE_UNKNOWN_FORUM = "unknown-forum/{payload}"
    const val ROUTE_CACHE_SETTINGS = "cache-settings"
    const val ROUTE_BLOCK_WORDS = "block-words"
    const val ROUTE_ABOUT = "about"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_NOTIFICATIONS = "notifications"
    const val ROUTE_CLOCK_IN = "clock-in"

    /** The route string a [Route] maps to, navigable via NavController. */
    fun encode(route: Route): String = when (route) {
        Route.ForumList -> ROUTE_FORUM_LIST
        is Route.TopicList -> withPayload(ROUTE_TOPIC_LIST) {
            putForumId("forum", route.forumId)
            route.categoryName?.let { put("categoryName", it) }
            if (route.mode != TopicListMode.NORMAL) put("mode", route.mode.name)
            if (route.dateRange != 0) put("dateRange", route.dateRange)
        }
        is Route.TopicDetails -> withPayload(ROUTE_TOPIC_DETAILS) {
            put("topicId", route.topicId)
            route.fav?.let { put("fav", it) }
            route.postId?.let { put("postId", it) }
            route.authorId?.let { put("authorId", it) }
            if (route.anonymousAuthorOnly) put("anon", true)
            if (route.localCache) put("cache", true)
            route.startPage?.let { put("startPage", it) }
        }
        is Route.UserProfile -> withPayload(ROUTE_USER_PROFILE) {
            route.userId?.let { put("userId", it) }
            route.userName?.let { put("userName", it) }
            route.user?.let {
                put("userB64", Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP))
            }
        }
        Route.GlobalSearch -> ROUTE_GLOBAL_SEARCH
        is Route.TopicSearch -> withPayload(ROUTE_TOPIC_SEARCH) {
            route.forumId?.let { putForumId("forum", it) }
        }
        Route.HotTopics -> ROUTE_HOT_TOPICS
        Route.Favorites -> ROUTE_FAVORITES
        Route.History -> ROUTE_HISTORY
        Route.ShortMessages -> ROUTE_SHORT_MESSAGES
        is Route.ShortMessageDetails -> withPayload(ROUTE_SHORT_MESSAGE_DETAILS) {
            put("id", route.id)
        }
        Route.Subforums -> ROUTE_SUBFORUMS
        is Route.SubforumList -> withPayload(ROUTE_SUBFORUM_LIST) {
            putForumId("forum", route.forumId)
        }
        is Route.UnknownForum -> withPayload(ROUTE_UNKNOWN_FORUM) {
            route.name?.let { put("name", it) }
        }
        Route.CacheSettings -> ROUTE_CACHE_SETTINGS
        Route.BlockWords -> ROUTE_BLOCK_WORDS
        Route.About -> ROUTE_ABOUT
        Route.Settings -> ROUTE_SETTINGS
        Route.Notifications -> ROUTE_NOTIFICATIONS
        Route.ClockIn -> ROUTE_CLOCK_IN
    }

    /** Decodes the route carried by a back-stack entry; null when unparseable. */
    fun decode(entry: NavBackStackEntry): Route? {
        val args = entry.arguments ?: return null
        return when (entry.destination.route) {
            ROUTE_FORUM_LIST -> Route.ForumList
            ROUTE_TOPIC_LIST -> decodePayload(args) { decodeTopicList(it) }
            ROUTE_TOPIC_DETAILS -> decodePayload(args) { decodeTopicDetails(it) }
            ROUTE_USER_PROFILE -> decodePayload(args) { decodeUserProfile(it) }
            ROUTE_GLOBAL_SEARCH -> Route.GlobalSearch
            ROUTE_TOPIC_SEARCH -> decodePayload(args) { decodeTopicSearch(it) }
            ROUTE_HOT_TOPICS -> Route.HotTopics
            ROUTE_FAVORITES -> Route.Favorites
            ROUTE_HISTORY -> Route.History
            ROUTE_SHORT_MESSAGES -> Route.ShortMessages
            ROUTE_SHORT_MESSAGE_DETAILS -> decodePayload(args) { decodeShortMessageDetails(it) }
            ROUTE_SUBFORUMS -> Route.Subforums
            ROUTE_SUBFORUM_LIST -> decodePayload(args) { decodeSubforumList(it) }
            ROUTE_UNKNOWN_FORUM -> decodePayload(args) { decodeUnknownForum(it) }
            ROUTE_CACHE_SETTINGS -> Route.CacheSettings
            ROUTE_BLOCK_WORDS -> Route.BlockWords
            ROUTE_ABOUT -> Route.About
            ROUTE_SETTINGS -> Route.Settings
            ROUTE_NOTIFICATIONS -> Route.Notifications
            ROUTE_CLOCK_IN -> Route.ClockIn
            else -> null
        }
    }

    // -- encoding helpers -----------------------------------------------------

    private fun withPayload(pattern: String, fill: JSONObject.() -> Unit): String {
        val json = JSONObject()
        json.fill()
        return pattern.replace("{payload}", Uri.encode(json.toString()))
    }

    private fun JSONObject.putForumId(key: String, id: ForumId) {
        when {
            id.hasFid() -> put("${key}Fid", id.fid)
            id.hasStid() -> put("${key}Stid", id.stid)
        }
    }

    // -- decoding helpers -----------------------------------------------------

    private inline fun decodePayload(
        args: Bundle,
        decode: (JSONObject) -> Route,
    ): Route? {
        val payload = args.getString("payload") ?: return null
        return runCatching { decode(JSONObject(payload)) }.getOrNull()
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key) else null

    private fun JSONObject.forumId(key: String): ForumId? = when {
        has("${key}Fid") -> ForumId.newBuilder().setFid(getString("${key}Fid")).build()
        has("${key}Stid") -> ForumId.newBuilder().setStid(getString("${key}Stid")).build()
        else -> null
    }

    private fun decodeTopicList(json: JSONObject): Route.TopicList {
        val forumId = json.forumId("forum") ?: error("missing forum id")
        return Route.TopicList(
            forumId = forumId,
            categoryName = json.optStringOrNull("categoryName"),
            mode = runCatching { TopicListMode.valueOf(json.optString("mode", TopicListMode.NORMAL.name)) }
                .getOrDefault(TopicListMode.NORMAL),
            dateRange = json.optInt("dateRange", 0),
        )
    }

    private fun decodeTopicDetails(json: JSONObject): Route.TopicDetails =
        Route.TopicDetails(
            topicId = json.optString("topicId", ""),
            fav = json.optStringOrNull("fav"),
            postId = json.optStringOrNull("postId"),
            authorId = json.optStringOrNull("authorId"),
            anonymousAuthorOnly = json.optBoolean("anon", false),
            localCache = json.optBoolean("cache", false),
            startPage = if (json.has("startPage")) json.optInt("startPage") else null,
        )

    private fun decodeUserProfile(json: JSONObject): Route.UserProfile =
        Route.UserProfile(
            userId = json.optStringOrNull("userId"),
            userName = json.optStringOrNull("userName"),
            user = json.optStringOrNull("userB64")?.let { b64 ->
                runCatching { User.parseFrom(Base64.decode(b64, Base64.NO_WRAP)) }.getOrNull()
            },
        )

    private fun decodeTopicSearch(json: JSONObject): Route.TopicSearch =
        Route.TopicSearch(forumId = json.forumId("forum"))

    private fun decodeShortMessageDetails(json: JSONObject): Route.ShortMessageDetails =
        Route.ShortMessageDetails(id = json.optString("id", ""))

    private fun decodeSubforumList(json: JSONObject): Route.SubforumList {
        val forumId = json.forumId("forum") ?: error("missing forum id")
        return Route.SubforumList(forumId = forumId)
    }

    private fun decodeUnknownForum(json: JSONObject): Route.UnknownForum =
        Route.UnknownForum(name = json.optStringOrNull("name"))
}
