package com.bugenzhao.mnga.model

import com.bugenzhao.mnga.LogicException
import com.bugenzhao.mnga.logicCallAsync
import com.google.protobuf.Message
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Generic paged list engine, an exhaustive port of
 * `Models/PagingDataSource.swift`: builds per-page protobuf requests, merges
 * pages into a deduplicated item list with per-item page bookkeeping, tracks
 * loading/refreshing/error states and supports prefetch, targeted page reload
 * and generation-based stale-response protection.
 */
class PagingDataSource<Res : Message, Item : Any>(
    private val scope: CoroutineScope,
    private val responseParser: () -> com.google.protobuf.Parser<Res>,
    private val buildRequest: (page: Int) -> com.bugenzhao.mnga.protos.service.AsyncRequest,
    private val onResponse: (response: Res) -> Pair<List<Item>, Int?>,
    private val id: (Item) -> String,
    private val finishOnError: Boolean = false,
    private val initialPage: Int = 1,
    private val neverRemove: Boolean = false,
) {
    data class State<Item : Any>(
        val items: List<Item> = emptyList(),
        val isLoading: Boolean = false,
        val isRefreshing: Boolean = false,
        val latestResponse: Any? = null,
        val latestError: LogicException? = null,
        val lastRefreshTime: Date? = null,
    ) {
        val loadedPage: Int = 0
    }

    private val _state = MutableStateFlow(State<Item>())
    val state: StateFlow<State<Item>> = _state

    val items: List<Item> get() = _state.value.items
    val isLoading: Boolean get() = _state.value.isLoading
    val isRefreshing: Boolean get() = _state.value.isRefreshing

    @Suppress("UNCHECKED_CAST")
    val latestResponse: Res? get() = _state.value.latestResponse as? Res
    val latestError: LogicException? get() = _state.value.latestError

    /** id -> (index in items, owning page) */
    private val itemToIndexAndPage = HashMap<String, Pair<Int, Int>>()
    private var loadedPageInternal = 0
    private var totalPagesInternal = 1
    private val initialPageLocal = initialPage

    // Generation counter; refreshed responses rotate it, in-flight loadMore /
    // reload results that arrive with a stale generation are discarded.
    private var dataFlowId = UUID.randomUUID()

    val hasMore: Boolean get() = loadedPageInternal < totalPagesInternal
    val nextPage: Int? get() = if (hasMore) loadedPageInternal + 1 else null
    val isInitialLoading: Boolean
        get() = _state.value.isLoading && loadedPageInternal == 0
    val firstLoadedPage: Int?
        get() = itemToIndexAndPage.values.minOfOrNull { it.second }
    val notLoaded: Boolean
        get() = items.isEmpty() && _state.value.lastRefreshTime == null && latestError == null

    /** The highest page whose items are present, for snapshot/restore. */
    val loadedPage: Int get() = loadedPageInternal
    /** Total pages reported by the server, for snapshot/restore. */
    val totalPages: Int get() = totalPagesInternal
    val lastRefreshTime: Date? get() = _state.value.lastRefreshTime

    /**
     * Restore a previously snapshotted screen without re-fetching: items,
     * page bookkeeping and the latest response are put back so the list
     * renders immediately in its previous state.
     */
    fun restoreItems(
        items: List<Item>,
        loadedPage: Int,
        totalPages: Int,
        lastRefreshTime: Date?,
        latestResponse: Any? = null,
    ) {
        // 快照可能携带重复 id（保存前一刻的异常数据），去重避免 LazyColumn
        // "Key was already used" 崩溃。
        val deduped = dedupe(items)
        itemToIndexAndPage.clear()
        deduped.forEachIndexed { index, item ->
            itemToIndexAndPage[id(item)] = Pair(index, loadedPage)
        }
        loadedPageInternal = loadedPage
        totalPagesInternal = totalPages
        dataFlowId = UUID.randomUUID()
        _state.value =
            _state.value.copy(
                items = deduped,
                isLoading = false,
                isRefreshing = false,
                latestResponse = latestResponse,
                latestError = null,
                lastRefreshTime = lastRefreshTime,
            )
    }

    /** External trigger: set to refresh from a specific page. */
    private val _loadFromPage = MutableStateFlow<Int?>(null)
    var loadFromPage: Int?
        get() = _loadFromPage.value
        set(value) {
            _loadFromPage.value = value
        }

    init {
        scope.launch {
            _loadFromPage.collect { page ->
                if (page != null) refresh(fromPage = page, silentOnError = false)
            }
        }
    }

    fun itemsAtPage(page: Int): List<Item> =
        items.filter { itemToIndexAndPage[id(it)]?.second == page }

    fun pagedItems(): List<Pair<Int, List<Item>>> =
        itemToIndexAndPage.values.map { it.second }.distinct().sorted()
            .map { page -> page to itemsAtPage(page) }

    private fun upsertItems(new: List<Item>, page: Int) {
        val list = _state.value.items.toMutableList()
        // 防御：map 与列表异常不同步时，按列表现存 id 兜底查重。
        val existingIds = list.mapTo(HashSet(list.size)) { id(it) }
        for (item in new) {
            val key = id(item)
            val existing = itemToIndexAndPage[key]
            if (existing != null) {
                list[existing.first] = item
            } else if (existingIds.add(key)) {
                list.add(item)
                itemToIndexAndPage[key] = Pair(list.size - 1, page)
            }
        }
        reindex()
        _state.value = _state.value.copy(items = list)
    }

    private fun replaceItems(new: List<Item>, page: Int) {
        if (!neverRemove) {
            // 服务端一页内可能出现重复（如置顶帖），去重避免 LazyColumn
            // "Key was already used" 崩溃。
            val deduped = dedupe(new)
            itemToIndexAndPage.clear()
            _state.value = _state.value.copy(items = deduped)
            deduped.forEachIndexed { index, item ->
                itemToIndexAndPage[id(item)] = Pair(index, page)
            }
        } else {
            upsertItems(new, page)
        }
    }

    /** 去掉列表中重复的 id，保留首次出现。 */
    private fun dedupe(items: List<Item>): List<Item> {
        if (items.size <= 1) return items
        val seen = HashSet<String>(items.size)
        return items.filter { seen.add(id(it)) }
    }

    private fun reindex() {
        val items = _state.value.items
        val byId = HashMap<String, Int>(items.size)
        items.forEachIndexed { index, item -> byId[id(item)] = index }
        val entries = itemToIndexAndPage.entries.toList()
        itemToIndexAndPage.clear()
        for ((key, old) in entries) {
            val index = byId[key] ?: continue
            itemToIndexAndPage[key] = Pair(index, old.second)
        }
    }

    /** Remove items no longer present after list mutation (upsert keeps stale ids). */
    private fun pruneIndex() {
        val ids = items.map(id).toHashSet()
        itemToIndexAndPage.keys.retainAll(ids)
    }

    // region refresh

    fun refresh(
        animated: Boolean = false,
        silentOnError: Boolean = false,
        fromPage: Int = 1,
    ): Job =
        scope.launch {
            // Re-entry guard.
            if (_state.value.isRefreshing || _state.value.isLoading) return@launch
            dataFlowId = UUID.randomUUID()
            _state.value = _state.value.copy(
                isLoading = true,
                isRefreshing = true,
                latestError = null,
            )
            loadedPageInternal = fromPage - 1
            totalPagesInternal = fromPage

            val request = buildRequest(fromPage)
            val result = logicCallAsync(request, responseParser())
            result.fold(
                onSuccess = { response ->
                    val (newItems, newTotalPages) = onResponse(response)
                    replaceItems(newItems, fromPage)
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        latestResponse = response,
                        latestError = null,
                        lastRefreshTime = Date(),
                    )
                    totalPagesInternal = newTotalPages ?: totalPagesInternal
                    loadedPageInternal += 1
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isLoading = false, isRefreshing = false)
                    onError(e as? LogicException ?: LogicException(e.message ?: "error"))
                    if (!silentOnError) {
                        ToastModel.showAuto(ToastModel.Message.Error(e.message ?: "error"))
                    }
                },
            )
        }

    /** Pull-to-refresh friendly variant with a minimum spinner duration. */
    fun refreshAsync(
        animated: Boolean = true,
        silentOnError: Boolean = false,
        fromPage: Int = 1,
        sleepMillis: Long = 0,
    ): Job =
        scope.launch {
            if (sleepMillis > 0) delay(sleepMillis)
            val job = refresh(animated = animated, silentOnError = silentOnError, fromPage = fromPage)
            job.join()
        }

    fun initialLoad(): Job? {
        if (loadedPageInternal == 0 && latestError == null) {
            return refresh(animated = true, fromPage = initialPageLocal)
        }
        return null
    }

    // endregion

    // region load more

    fun loadMore(afterMillis: Long = 0, alwaysAnimation: Boolean = true): Job =
        scope.launch {
            if (afterMillis > 0) delay(afterMillis)
            loadMoreInternal(background = false, alwaysAnimation = alwaysAnimation)
        }

    /** Prefetch hook: call per visible item. */
    fun loadMoreIfNeeded(currentIndex: Int) {
        if (currentIndex >= items.size - 3) {
            scope.launch { loadMoreInternal(background = true, alwaysAnimation = false) }
        }
    }

    private suspend fun loadMoreInternal(background: Boolean, alwaysAnimation: Boolean) {
        if (_state.value.isLoading || loadedPageInternal >= totalPagesInternal) return
        _state.value = _state.value.copy(isLoading = true)
        val page = loadedPageInternal + 1
        val request = buildRequest(page)
        val capturedId = dataFlowId
        val result = logicCallAsync(request, responseParser())
        result.fold(
            onSuccess = { response ->
                if (capturedId != dataFlowId) return@fold // stale
                val (newItems, newTotalPages) = onResponse(response)
                upsertItems(newItems, page)
                _state.value = _state.value.copy(
                    isLoading = false,
                    latestResponse = response,
                    latestError = null,
                )
                if (newItems.isEmpty()) {
                    // Server returned an empty page: treat as end.
                    totalPagesInternal = loadedPageInternal
                } else {
                    totalPagesInternal = newTotalPages ?: totalPagesInternal
                    loadedPageInternal += 1
                }
            },
            onFailure = { e ->
                if (items.isEmpty()) {
                    _state.value = _state.value.copy(isLoading = false)
                } else {
                    _state.value = _state.value.copy(isLoading = false)
                }
                onError(e as? LogicException ?: LogicException(e.message ?: "error"))
                if (!background) {
                    ToastModel.showAuto(ToastModel.Message.Error(e.message ?: "error"))
                }
            },
        )
    }

    // endregion

    // region targeted reload

    fun reload(
        page: Int,
        evenIfNotLoaded: Boolean = false,
        animated: Boolean = true,
        after: (() -> Unit)? = null,
    ): Job =
        scope.launch {
            if (!(page <= loadedPageInternal || evenIfNotLoaded)) return@launch
            if (_state.value.isLoading) return@launch
            _state.value = _state.value.copy(isLoading = true)
            val request = buildRequest(page)
            val capturedId = dataFlowId
            val result = logicCallAsync(request, responseParser())
            result.fold(
                onSuccess = { response ->
                    if (capturedId != dataFlowId) return@fold
                    val (newItems, newTotalPages) = onResponse(response)
                    upsertItems(newItems, page) // merge, no removal
                    _state.value = _state.value.copy(
                        isLoading = false,
                        latestResponse = response,
                        latestError = null,
                    )
                    totalPagesInternal = newTotalPages ?: totalPagesInternal
                    after?.invoke()
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isLoading = false)
                    onError(e as? LogicException ?: LogicException(e.message ?: "error"))
                    ToastModel.showAuto(ToastModel.Message.Error(e.message ?: "error"))
                },
            )
        }

    /** Post-send path: reload the last page, then pull in newly appeared pages. */
    fun reloadLastPage(evenIfNotLoaded: Boolean = true, animated: Boolean = true, after: (() -> Unit)? = null) {
        val oldTotalPages = totalPagesInternal
        val oldLoadedPage = loadedPageInternal
        reload(
            page = oldTotalPages,
            evenIfNotLoaded = evenIfNotLoaded,
            animated = animated,
        ) {
            after?.invoke()
            if (totalPagesInternal > oldTotalPages && oldLoadedPage == oldTotalPages) {
                scope.launch { loadMoreInternal(background = false, alwaysAnimation = animated) }
            }
        }
    }

    // endregion

    private fun onError(e: LogicException) {
        if (finishOnError) totalPagesInternal = loadedPageInternal
        _state.value = _state.value.copy(latestError = e)
    }
}
