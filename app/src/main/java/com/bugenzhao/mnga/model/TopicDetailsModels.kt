package com.bugenzhao.mnga.model

import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.LogicException
import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.protos.datamodel.Post
import com.bugenzhao.mnga.protos.datamodel.PostId
import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.protos.datamodel.VoteState
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.TopicDetailsRequest
import com.bugenzhao.mnga.protos.service.TopicDetailsResponse
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Maps a post id to its (floor, page), scanning topic pages when needed,
 * ported from `Models/TopicPostLocator.swift`.
 */
class TopicPostLocator(
    private val scope: CoroutineScope,
    private val topic: Topic,
) {
    data class Location(val floor: Int, val page: Int)

    private val locations = ConcurrentHashMap<PostId, Location>()
    private val inFlight = ConcurrentHashMap<PostId, CompletableDeferred<Result<Location>>>()

    /** Call whenever replies load. */
    fun seed(posts: List<Post>) {
        for (post in posts) {
            if (post.id.tid == topic.id) {
                locations[post.id] =
                    Location(post.floor.toInt(), maxOf(post.atPage.toInt(), 1))
            }
        }
    }

    fun cachedLocation(postId: PostId): Location? =
        if (postId.pid == "0") Location(0, 1) else locations[postId]

    fun locate(postId: PostId): CompletableDeferred<Result<Location>> {
        cachedLocation(postId)?.let {
            return CompletableDeferred(Result.success(it))
        }
        inFlight[postId]?.let { return it }
        val deferred = CompletableDeferred<Result<Location>>()
        inFlight[postId] = deferred
        scope.launch {
            val result = scanLocation(postId)
            result.onSuccess { locations[postId] = it }
            inFlight.remove(postId)
            deferred.complete(result)
        }
        return deferred
    }

    private suspend fun scanLocation(postId: PostId): Result<Location> {
        var page = 1
        var totalPages: Int? = null
        val fav = topic.fav.ifEmpty { null }
        while (totalPages == null || page <= totalPages!!) {
            val builder = TopicDetailsRequest.newBuilder()
                .setTopicId(topic.id)
                .setPage(page)
                .setWebApiStrategyValue(App.prefs.topicDetailsWebApiStrategy.raw)
            fav?.let { builder.fav = it }
            val result = logicCallAsync(
                AsyncRequest.newBuilder().setTopicDetails(builder).build(),
                TopicDetailsResponse.parser(),
            )
            result.fold(
                onSuccess = { response ->
                    seed(response.repliesList)
                    locations[postId]?.let { return Result.success(it) }
                    totalPages = maxOf(response.pages.toInt(), 1)
                    if (response.repliesList.isEmpty()) {
                        return Result.failure(
                            LogicException("Unable to locate this post in the full topic.")
                        )
                    }
                    page += 1
                },
                onFailure = { e -> return Result.failure(e) },
            )
        }
        return Result.failure(LogicException("Unable to locate this post in the full topic."))
    }
}

/**
 * Resolves full `Post` objects for quoted/replied-to posts, ported from
 * `Models/QuotedPostResolver.swift`.
 */
class QuotedPostResolver(private val scope: CoroutineScope) {

    private val _posts = MutableStateFlow<Map<PostId, Post>>(emptyMap())
    val posts: StateFlow<Map<PostId, Post>> = _posts

    private val _failed = MutableStateFlow<Set<PostId>>(emptySet())
    val failed: StateFlow<Set<PostId>> = _failed

    private val inFlight = mutableSetOf<PostId>()
    private val inFlightMutex = Mutex()

    /** Typically looks up the visible data source's items. */
    var localPostProvider: ((PostId) -> Post?)? = null

    fun seed(posts: List<Post>) {
        val map = _posts.value.toMutableMap()
        var failedChanged = false
        val failedSet = _failed.value.toMutableSet()
        for (post in posts) {
            map[post.id] = post
            if (failedSet.remove(post.id)) failedChanged = true
        }
        _posts.value = map
        if (failedChanged) _failed.value = failedSet
    }

    fun post(id: PostId): Post? {
        _posts.value[id]?.let { return it }
        localPostProvider?.invoke(id)?.let {
            val map = _posts.value.toMutableMap()
            map[id] = it
            _posts.value = map
            _failed.value = _failed.value - id
            return it
        }
        return null
    }

    fun load(id: PostId) {
        if (_posts.value.containsKey(id) || id in _failed.value) return
        scope.launch {
            inFlightMutex.withLock {
                if (id in inFlight) return@launch
                inFlight.add(id)
            }
            try {
                localPostProvider?.invoke(id)?.let {
                    val map = _posts.value.toMutableMap()
                    map[id] = it
                    _posts.value = map
                    return@launch
                }
                val result = logicCallAsync(
                    AsyncRequest.newBuilder()
                        .setTopicDetails(
                            TopicDetailsRequest.newBuilder()
                                .setWebApiStrategyValue(App.prefs.topicDetailsWebApiStrategy.raw)
                                .setTopicId(id.tid)
                                .setPostId(id.pid)
                        )
                        .build(),
                    TopicDetailsResponse.parser(),
                )
                result.fold(
                    onSuccess = { response ->
                        val map = _posts.value.toMutableMap()
                        val failedSet = _failed.value.toMutableSet()
                        val first = response.repliesList.firstOrNull()
                        if (first != null) map[id] = first else failedSet.add(id)
                        _posts.value = map
                        _failed.value = failedSet
                    },
                    onFailure = {
                        _failed.value = _failed.value + id
                    },
                )
            } finally {
                inFlightMutex.withLock { inFlight.remove(id) }
            }
        }
    }

    fun resetFailures() {
        _failed.value = emptySet()
    }
}

/** Per-topic overlay of the user's votes on posts. */
class VotesModel {
    data class Vote(val state: VoteState, val delta: Int)

    private val _votes = MutableStateFlow<Map<PostId, Vote>>(emptyMap())
    val votes: StateFlow<Map<PostId, Vote>> = _votes

    fun voteFor(post: Post): Vote =
        _votes.value[post.id] ?: Vote(post.voteState, 0)

    fun apply(postId: PostId, state: VoteState, delta: Int) {
        val current = _votes.value[postId] ?: Vote(VoteState.NONE, 0)
        _votes.value =
            _votes.value + (postId to current.copy(state = state, delta = current.delta + delta))
    }
}

/** Full-screen image viewer state, ported from `Models/ViewingImageModel.swift`. */
class ViewingImageModel {
    private val _urls = MutableStateFlow<List<String>>(emptyList())
    val urls: StateFlow<List<String>> = _urls

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex

    private val _showing = MutableStateFlow(false)
    val showing: StateFlow<Boolean> = _showing

    fun show(url: String) = show(listOf(url), url)

    fun show(urls: List<String>, current: String) {
        if (urls.isEmpty()) return
        _urls.value = urls
        _currentIndex.value = urls.indexOf(current).takeIf { it >= 0 } ?: 0
        _showing.value = true
    }

    fun dismiss() {
        _showing.value = false
    }
}
