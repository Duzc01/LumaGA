package com.bugenzhao.mnga.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.protos.datamodel.TopicSnapshot
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.TopicHistoryRequest
import com.bugenzhao.mnga.protos.service.TopicHistoryResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Number of history entries requested from the server. */
private const val HistoryLimit = 1000L

/** SharedPreferences key holding the per-topic deletion tombstones. */
private const val DeletedTopicsKey = "history_deleted_topics"

/**
 * Entry-scoped holder of the browsing-history list. Survives being covered
 * by a pushed screen, so popping back reuses the loaded data instead of
 * refetching.
 *
 * Single-entry deletion is tracked as tombstones (topic id -> deletion
 * timestamp): the Rust cache has no per-entry removal API and its .so ships
 * prebuilt, so deleted ids are filtered client-side. A topic viewed again
 * gets a newer snapshot timestamp than the tombstone and reappears, which
 * matches the "it is history again" expectation.
 */
class HistoryViewModel : ViewModel() {

    val dataSource = PagingDataSource<TopicHistoryResponse, TopicSnapshot>(
        scope = viewModelScope,
        responseParser = { TopicHistoryResponse.parser() },
        buildRequest = {
            AsyncRequest.newBuilder()
                .setTopicHistory(
                    TopicHistoryRequest.newBuilder().setLimit(HistoryLimit).build()
                )
                .build()
        },
        onResponse = { response -> Pair(response.topicsList, 1) },
        id = { it.topicSnapshot.id },
    )

    private val _deletedAt = MutableStateFlow(loadDeleted())
    val deletedAt: StateFlow<Map<String, Long>> = _deletedAt.asStateFlow()

    fun deleteTopic(id: String) {
        val updated = _deletedAt.value.toMutableMap()
        updated[id] = System.currentTimeMillis()
        saveDeleted(updated)
        _deletedAt.value = updated
    }

    fun clearDeleted() {
        App.sharedPreferences.edit().remove(DeletedTopicsKey).apply()
        _deletedAt.value = emptyMap()
    }

    private fun loadDeleted(): Map<String, Long> {
        val raw = App.sharedPreferences.getString(DeletedTopicsKey, null) ?: return emptyMap()
        return try {
            val json = JSONObject(raw)
            buildMap {
                json.keys().forEach { key ->
                    put(key, json.optLong(key, 0L))
                }
            }
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun saveDeleted(map: Map<String, Long>) {
        val json = JSONObject()
        map.forEach { (id, at) -> json.put(id, at) }
        App.sharedPreferences.edit().putString(DeletedTopicsKey, json.toString()).apply()
    }
}

/**
 * Pure helper: whether a history snapshot is hidden by the tombstone map.
 * A snapshot newer than the tombstone (i.e. the topic was viewed again
 * after deletion) is NOT hidden.
 */
internal fun isTombstoned(snapshot: TopicSnapshot, tombstones: Map<String, Long>): Boolean {
    val deleted = tombstones[snapshot.topicSnapshot.id] ?: return false
    return snapshot.timestamp <= deleted
}
