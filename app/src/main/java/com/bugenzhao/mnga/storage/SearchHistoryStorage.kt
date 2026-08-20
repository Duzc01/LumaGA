package com.bugenzhao.mnga.storage

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Which search tab a history list belongs to; each keeps its own list. */
enum class SearchHistoryScope(internal val prefsKey: String) {
    TOPICS("searchHistoryTopics"),
    FORUMS("searchHistoryForums"),
}

/**
 * Recent search terms, one list per [SearchHistoryScope], persisted in the
 * shared preferences as newline-joined strings (queries are single-line by
 * construction, so no escaping is needed).
 */
class SearchHistoryStorage(private val prefs: SharedPreferences) {

    companion object {
        var shared: SearchHistoryStorage? = null

        /** Kept small: the chip area shows at most three lines anyway. */
        const val capacity = 20
    }

    private val flows: Map<SearchHistoryScope, MutableStateFlow<List<String>>> =
        SearchHistoryScope.entries.associateWith { MutableStateFlow(load(it)) }

    fun queries(scope: SearchHistoryScope): StateFlow<List<String>> = flows.getValue(scope)

    /** Record [query] as the most recent search of [scope], de-duplicated. */
    fun remember(scope: SearchHistoryScope, query: String) {
        val term = query.trim()
        if (term.isEmpty()) return
        update(scope) { old -> listOf(term) + old.filterNot { it == term } }
    }

    fun remove(scope: SearchHistoryScope, query: String) {
        update(scope) { old -> old.filterNot { it == query } }
    }

    fun clear(scope: SearchHistoryScope) {
        update(scope) { emptyList() }
    }

    private fun update(scope: SearchHistoryScope, transform: (List<String>) -> List<String>) {
        val flow = flows.getValue(scope)
        val next = transform(flow.value).take(capacity)
        flow.value = next
        prefs.edit().putString(scope.prefsKey, next.joinToString("\n")).apply()
    }

    private fun load(scope: SearchHistoryScope): List<String> =
        prefs.getString(scope.prefsKey, null)
            ?.split("\n")
            ?.filter { it.isNotEmpty() }
            ?.take(capacity)
            .orEmpty()
}
