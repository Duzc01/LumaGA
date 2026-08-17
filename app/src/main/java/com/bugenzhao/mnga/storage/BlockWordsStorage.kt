package com.bugenzhao.mnga.storage

import android.content.SharedPreferences
import com.bugenzhao.mnga.model.PlusFeature
import com.bugenzhao.mnga.model.PlusModel
import com.bugenzhao.mnga.protos.datamodel.BlockWord
import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.protos.datamodel.UserName
import com.bugenzhao.mnga.util.PbJson
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * User-configurable block list of keywords and blocked users, ported from
 * `Storage/BlockWordsStorage.swift`. A Plus-gated feature.
 */
class BlockWordsStorage(private val prefs: SharedPreferences) {

    companion object {
        var shared: BlockWordsStorage? = null

        const val userPrefix = "User: "

        fun displayName(user: UserName): String =
            if (user.anonymous.isNotEmpty()) user.anonymous else user.normal

        fun fromUser(user: UserName): BlockWord =
            BlockWord.newBuilder().setWord(userPrefix + displayName(user)).build()

        fun BlockWord.userNameOrNull(): String? =
            word.takeIf { it.startsWith(userPrefix) }?.removePrefix(userPrefix)

        /** Composite match string for a topic: user|subject|tags. */
        fun content(user: UserName, content: String, tags: List<String>): String =
            displayName(user) + "|" + content + "|" + tags.joinToString("|")

        fun content(topic: Topic): String =
            content(
                user = topic.authorName,
                content = topic.subject.content.ifEmpty { topic.subjectContent },
                tags = topic.subject.tagsList.ifEmpty { topic.tagsList },
            )
    }

    private val _words = MutableStateFlow<List<BlockWord>>(emptyList())
    val words: StateFlow<List<BlockWord>> = _words

    init {
        _words.value =
            PbJson.listFromJson(prefs.getString("blockWords", null)) { BlockWord.newBuilder() }
    }

    private fun persist() {
        prefs.edit().putString("blockWords", PbJson.listToJson(_words.value)).apply()
    }

    /** True if any word is a substring of [content]. */
    fun blocked(content: String): Boolean = _words.value.any { content.contains(it.word) }

    fun blocked(user: UserName): Boolean = _words.value.contains(fromUser(user))

    fun add(word: BlockWord) {
        if (_words.value.contains(word)) return
        _words.value = listOf(word) + _words.value
        persist()
    }

    fun remove(word: BlockWord) {
        _words.value = _words.value.filterNot { it == word }
        persist()
    }

    /** Plus-gated toggle for blocking a user by name. */
    fun toggleUser(user: UserName) {
        PlusModel.shared ?: return
        if (!PlusModel.checkPlus(PlusFeature.BLOCK_CONTENTS)) return
        val word = fromUser(user)
        if (_words.value.contains(word)) remove(word) else add(word)
    }
}
