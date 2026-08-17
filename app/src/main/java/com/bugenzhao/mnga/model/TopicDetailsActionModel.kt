package com.bugenzhao.mnga.model

import com.bugenzhao.mnga.protos.datamodel.Forum
import com.bugenzhao.mnga.protos.datamodel.Post
import com.bugenzhao.mnga.protos.datamodel.PostId
import com.bugenzhao.mnga.protos.datamodel.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Per-topic-details navigation hub + reply-relation index, ported from
 * `Models/TopicDetailsActionModel.swift`. The view observes the flows and
 * performs the corresponding navigations.
 */
class TopicDetailsActionModel {

    sealed class AuthorOnly {
        data class ByUID(val uid: String) : AuthorOnly()
        data class Anonymous(val postId: PostId?) : AuthorOnly()
    }

    val scrollToPid = MutableStateFlow<String?>(null)
    val scrollToFloor = MutableStateFlow<Int?>(null)
    val showingReplyChain = MutableStateFlow<List<PostId>?>(null)
    val showingQuotedReplies = MutableStateFlow<List<PostId>?>(null)
    val navigateToTid = MutableStateFlow<String?>(null)
    val navigateToPid = MutableStateFlow<String?>(null)
    val showUserProfile = MutableStateFlow<User?>(null)
    val navigateToForum = MutableStateFlow<Forum?>(null)
    val navigateToRemoteUserID = MutableStateFlow<String?>(null)
    val navigateToRemoteUserName = MutableStateFlow<String?>(null)
    val navigateToAuthorOnly = MutableStateFlow<AuthorOnly?>(null)
    val navigateToLocalMode = MutableStateFlow(false)

    private val _quotedTargets = MutableStateFlow<Set<PostId>>(emptySet())
    val quotedTargets: StateFlow<Set<PostId>> = _quotedTargets

    // post -> the post it replies to
    private val replyTo = HashMap<PostId, PostId>()
    // inverse index: target -> those quoting it
    private val quotedBy = HashMap<PostId, MutableSet<PostId>>()
    private val indexedReplyTo = HashMap<PostId, PostId>()

    /** Call whenever a page of posts loads. */
    fun indexReplyRelations(posts: List<Post>) {
        for (post in posts) {
            removeIndexedReplyFrom(post.id)
            val target = PostReplyRelationScanner.target(post.content) ?: continue
            indexedReplyTo[post.id] = target
            quotedBy.getOrPut(target) { mutableSetOf() }.add(post.id)
            replyTo[post.id] = target
        }
        _quotedTargets.value = quotedBy.keys.toSet()
    }

    private fun removeIndexedReplyFrom(from: PostId) {
        val old = indexedReplyTo.remove(from) ?: return
        quotedBy[old]?.remove(from)
        if (quotedBy[old]?.isEmpty() == true) quotedBy.remove(old)
        if (replyTo[from] == old) replyTo.remove(from)
    }

    /** Chain of posts from the oldest ancestor to [from] itself. */
    fun replyChain(from: PostId): List<PostId> {
        val chain = mutableListOf<PostId>()
        var current: PostId? = from
        while (current != null && current !in chain) {
            chain.add(current)
            current = replyTo[current]
        }
        return chain.reversed()
    }

    fun showReplyChain(from: PostId) {
        showingReplyChain.value = replyChain(from)
    }

    fun quotedReplies(postId: PostId): List<PostId> =
        (quotedBy[postId] ?: emptySet()).sortedWith(
            compareBy({ it.tid }, { it.pid.toIntOrNull() ?: it.pid.hashCode() })
        )

    fun hasQuotedReplies(postId: PostId): Boolean = postId in _quotedTargets.value

    fun showQuotedReplies(postId: PostId) {
        val replies = quotedReplies(postId)
        if (replies.isEmpty()) return
        showingQuotedReplies.value = listOf(postId) + replies.filter { it != postId }
    }
}
