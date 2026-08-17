package com.bugenzhao.mnga.storage

import android.content.Context
import android.content.SharedPreferences
import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.protos.datamodel.Forum
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.protos.service.FavoriteForumListRequest
import com.bugenzhao.mnga.protos.service.FavoriteForumListResponse
import com.bugenzhao.mnga.protos.service.FavoriteForumModifyRequest
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.util.Constants
import com.bugenzhao.mnga.util.Haptics
import com.bugenzhao.mnga.util.PbJson
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Filter mode persisted as an English sentence, matching the iOS format. */
enum class FilterMode(val raw: String) {
    ALL("All Forums"),
    FAVORITES_ONLY("Favorites Only");

    companion object {
        fun fromRaw(raw: String): FilterMode = entries.firstOrNull { it.raw == raw } ?: ALL
    }
}

interface Backend {
    val forums: StateFlow<List<Forum>>
    suspend fun sync()
    suspend fun remove(id: ForumId)
    suspend fun add(forum: Forum)
    fun move(fromIndex: Int, toIndex: Int)
}

/** Purely local backend, persisted in the group store. */
class LocalBackend(private val prefs: SharedPreferences) : Backend {
    private val _forums = MutableStateFlow<List<Forum>>(emptyList())
    override val forums: StateFlow<List<Forum>> = _forums

    init {
        _forums.value =
            PbJson.listFromJson(prefs.getString(Constants.Key.favoriteForums, null)) {
                Forum.newBuilder()
            }
    }

    private fun persist() {
        prefs.edit()
            .putString(Constants.Key.favoriteForums, PbJson.listToJson(_forums.value))
            .apply()
    }

    override suspend fun sync() {}

    override suspend fun remove(id: ForumId) {
        _forums.value = _forums.value.filterNot { it.id == id }
        persist()
    }

    override suspend fun add(forum: Forum) {
        if (_forums.value.none { it.id == forum.id }) {
            _forums.value = _forums.value + forum
            persist()
        }
    }

    override fun move(fromIndex: Int, toIndex: Int) {
        val list = _forums.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _forums.value = list
        persist()
    }
}

/** Server-synced backend (`forum_favor2`) with a local offline mirror. */
class RemoteBackend(private val prefs: SharedPreferences) : Backend {
    private val _forums = MutableStateFlow<List<Forum>>(emptyList())
    override val forums: StateFlow<List<Forum>> = _forums

    init {
        _forums.value =
            PbJson.listFromJson(prefs.getString("remoteFavoriteForums", null)) {
                Forum.newBuilder()
            }
    }

    private fun persist() {
        prefs.edit()
            .putString("remoteFavoriteForums", PbJson.listToJson(_forums.value))
            .apply()
    }

    /** Merge-not-overwrite to preserve manual ordering. */
    private fun merge(remote: List<Forum>) {
        val remoteIds = remote.map { it.id }.toSet()
        val kept = _forums.value.filter { it.id in remoteIds }
        val existingIds = kept.map { it.id }.toSet()
        val added = remote.filter { it.id !in existingIds }
        _forums.value = kept + added
        persist()
    }

    override suspend fun sync() {
        logicCallAsync(
            AsyncRequest.newBuilder()
                .setFavoriteForumList(FavoriteForumListRequest.getDefaultInstance())
                .build(),
            FavoriteForumListResponse.parser(),
        ).onSuccess { merge(it.forumsList) }
    }

    override suspend fun remove(id: ForumId) {
        _forums.value = _forums.value.filterNot { it.id == id }
        persist()
        logicCallAsync(
            AsyncRequest.newBuilder()
                .setFavoriteForumModify(
                    FavoriteForumModifyRequest.newBuilder()
                        .setOperation(FavoriteForumModifyRequest.Operation.DEL)
                        .setId(id)
                )
                .build(),
            com.bugenzhao.mnga.protos.service.FavoriteForumModifyResponse.parser(),
        )
        sync()
    }

    override suspend fun add(forum: Forum) {
        if (_forums.value.none { it.id == forum.id }) {
            _forums.value = _forums.value + forum
            persist()
        }
        logicCallAsync(
            AsyncRequest.newBuilder()
                .setFavoriteForumModify(
                    FavoriteForumModifyRequest.newBuilder()
                        .setOperation(FavoriteForumModifyRequest.Operation.ADD)
                        .setId(forum.id)
                )
                .build(),
            com.bugenzhao.mnga.protos.service.FavoriteForumModifyResponse.parser(),
        )
        sync()
    }

    /** The server has no ordering; moving is local-only. */
    override fun move(fromIndex: Int, toIndex: Int) {
        val list = _forums.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _forums.value = list
        persist()
    }
}

/**
 * Façade over the local and remote favorite-forum backends, ported from
 * `Storage/FavoriteForumsStorage.swift`.
 */
class FavoriteForumsStorage(context: Context, private val standardPrefs: SharedPreferences) {

    companion object {
        var shared: FavoriteForumsStorage? = null
    }

    private val groupPrefs: SharedPreferences =
        context.getSharedPreferences(Constants.Key.groupStore, Context.MODE_PRIVATE)

    val local = LocalBackend(groupPrefs)
    val remote = RemoteBackend(standardPrefs)

    val useRemote = Pref(
        standardPrefs, "useRemoteFavoriteForums", false,
        { p, k, d -> p.getBoolean(k, d) },
        { e, k, v -> e.putBoolean(k, v) },
    )

    val synced = MutableStateFlow(false)

    val showAll = Pref(
        standardPrefs, "showAll", FilterMode.ALL.raw,
        { p, k, d -> p.getString(k, d) ?: d },
        { e, k, v -> e.putString(k, v) },
    )

    val filterMode: FilterMode
        get() = FilterMode.fromRaw(showAll.value)

    private val inner: Backend
        get() = if (useRemote.value) remote else local

    val favoriteForums: List<Forum>
        get() = inner.forums.value

    fun forumsFlow(): StateFlow<List<Forum>> = inner.forums

    suspend fun sync() {
        synced.value = false
        inner.sync()
        synced.value = true
    }

    suspend fun initialSync() {
        if (!synced.value) sync()
    }

    fun isFavorite(id: ForumId): Boolean = favoriteForums.any { it.id == id }

    fun toggle(forum: Forum, onHaptic: () -> Unit) {
        scope.launch {
            if (isFavorite(forum.id)) {
                inner.remove(forum.id)
            } else {
                inner.add(forum)
                onHaptic()
            }
        }
    }

    fun remove(ids: List<ForumId>) {
        scope.launch { ids.forEach { inner.remove(it) } }
    }

    fun move(fromIndex: Int, toIndex: Int) = inner.move(fromIndex, toIndex)

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main)
}
