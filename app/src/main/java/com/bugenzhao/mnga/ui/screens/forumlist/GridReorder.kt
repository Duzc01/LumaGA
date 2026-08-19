package com.bugenzhao.mnga.ui.screens.forumlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.toOffset
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** How much the held cell grows while it is being dragged. */
private const val DragLiftScale = 1.1f

/** Peak auto-scroll speed while a cell is held against a viewport edge, px/ms. */
private const val MaxAutoScrollPerMs = 1f

/**
 * Free-form (horizontal, vertical *and* diagonal) drag reorder for a
 * `LazyVerticalGrid`.
 *
 * Two properties make it feel direct:
 *
 * - Cells are tracked by lazy-grid item **key**, never by index, so the lifted
 *   cell is always the one the finger picked up — even after the list under it
 *   has been reordered several times.
 * - The lifted cell's translation is recomputed from its *live* layout offset
 *   every frame ([translationFor]), so it stays glued to the finger instead of
 *   snapping away when a reorder moves it into a new slot.
 *
 * The drop target is hit-tested against the real measured cell bounds, which is
 * what allows dragging in any direction without assuming a cell size or a fixed
 * column count.
 */
@Stable
class GridReorderState internal constructor(
    private val gridState: LazyGridState,
    private val scope: CoroutineScope,
    /** The reorderable item keys, in their current order. */
    private val keys: () -> List<Any>,
    private val onMove: (from: Int, to: Int) -> Unit,
    private val onSwap: () -> Unit,
) {
    /** Key of the cell currently held by the finger, if any. */
    var draggingKey: Any? by mutableStateOf(null)
        private set

    /** Key of the lifted cell: dragged, or springing back into its slot. */
    private var activeKey: Any? by mutableStateOf(null)

    /** Layout offset of the held cell when the drag started. */
    private var startOffset = Offset.Zero

    /** Finger travel since the drag started, on both axes. */
    private var dragDelta by mutableStateOf(Offset.Zero)

    /**
     * Layout offset of the held cell when the last reorder was applied. While
     * it is unchanged the reorder has not been laid out yet, so hit-testing is
     * paused — this keeps a burst of touch events from stacking up several
     * moves against a stale layout.
     */
    private var offsetAtLastSwap: IntOffset? = null

    private val lift = Animatable(1f)
    private val settle = Animatable(Offset.Zero, Offset.VectorConverter)
    private var autoScrollJob: Job? = null

    /** True while [key] is lifted, i.e. dragged or springing back. */
    fun isActive(key: Any): Boolean = key == activeKey

    private fun itemInfo(key: Any): LazyGridItemInfo? =
        gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == key }

    /** Translation that keeps the held cell under the finger. */
    fun translationFor(key: Any): Offset = when {
        key != activeKey -> Offset.Zero
        key != draggingKey -> settle.value
        else -> itemInfo(key)?.let { startOffset + dragDelta - it.offset.toOffset() } ?: Offset.Zero
    }

    fun scaleFor(key: Any): Float = if (key == activeKey) lift.value else 1f

    fun onDragStart(key: Any) {
        val item = itemInfo(key) ?: return
        draggingKey = key
        activeKey = key
        startOffset = item.offset.toOffset()
        dragDelta = Offset.Zero
        offsetAtLastSwap = null
        scope.launch { lift.animateTo(DragLiftScale, spring(stiffness = Spring.StiffnessMedium)) }
        autoScrollJob?.cancel()
        autoScrollJob = scope.launch { autoScroll() }
    }

    fun onDrag(amount: Offset) {
        if (draggingKey == null) return
        dragDelta += amount
        reorderIfNeeded()
    }

    fun onDragEnd() {
        val key = draggingKey ?: return
        val dropped = translationFor(key)
        draggingKey = null
        dragDelta = Offset.Zero
        offsetAtLastSwap = null
        autoScrollJob?.cancel()
        autoScrollJob = null
        scope.launch {
            launch { lift.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
            settle.snapTo(dropped)
            settle.animateTo(
                Offset.Zero,
                spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMedium),
            )
            if (activeKey == key) activeKey = null
        }
    }

    /** The held cell's current rect, in viewport coordinates. */
    private fun draggedRect(item: LazyGridItemInfo) =
        Rect(startOffset + dragDelta, item.size.toSize())

    /**
     * Moves the held item to whichever reorderable cell now sits under the
     * centre of the dragged one. Items the grid does not own (headers, other
     * sections) are skipped because they are absent from [keys].
     */
    private fun reorderIfNeeded() {
        val key = draggingKey ?: return
        val item = itemInfo(key) ?: return
        if (item.offset == offsetAtLastSwap) return
        val order = keys()
        val from = order.indexOf(key)
        if (from < 0) return
        val centre = draggedRect(item).center
        val target = gridState.layoutInfo.visibleItemsInfo.firstOrNull { other ->
            other.key != key && other.bounds().contains(centre)
        } ?: return
        val to = order.indexOf(target.key)
        if (to < 0 || to == from) return
        offsetAtLastSwap = item.offset
        onMove(from, to)
        onSwap()
    }

    /** Scrolls the grid while the held cell rests against a viewport edge. */
    private suspend fun autoScroll() {
        var lastFrameNanos = 0L
        while (true) {
            val nanos = withFrameNanos { it }
            val elapsedMs =
                if (lastFrameNanos == 0L) 0f else (nanos - lastFrameNanos) / 1_000_000f
            lastFrameNanos = nanos
            val speed = autoScrollSpeed()
            if (speed != 0f && elapsedMs > 0f) {
                gridState.scrollBy(speed * elapsedMs.coerceAtMost(32f))
                reorderIfNeeded()
            }
        }
    }

    private fun autoScrollSpeed(): Float {
        val key = draggingKey ?: return 0f
        val rect = draggedRect(itemInfo(key) ?: return 0f)
        val info = gridState.layoutInfo
        // A half-cell "hot" margin at either end of the viewport, with the
        // speed ramping up the deeper the cell is pushed into it. Keeping the
        // margin small stops a cell that merely sits near an edge from
        // dragging the whole list along with it.
        val band = (rect.height / 2f).coerceAtLeast(1f)
        val fromTop = rect.top - info.viewportStartOffset
        val fromBottom = info.viewportEndOffset - rect.bottom
        return when {
            fromTop < band && fromTop <= fromBottom ->
                -MaxAutoScrollPerMs * ((band - fromTop) / band).coerceIn(0f, 1f)
            fromBottom < band ->
                MaxAutoScrollPerMs * ((band - fromBottom) / band).coerceIn(0f, 1f)
            else -> 0f
        }
    }
}

private fun LazyGridItemInfo.bounds() = Rect(offset.toOffset(), size.toSize())

/**
 * Remembers the drag-reorder state for a grid. [keys] must list the item keys
 * of the reorderable range in their current order; [onMove] receives indices
 * into that list.
 */
@Composable
fun rememberGridReorderState(
    gridState: LazyGridState,
    keys: List<Any>,
    onMove: (from: Int, to: Int) -> Unit,
    onSwap: () -> Unit = {},
): GridReorderState {
    val scope = rememberCoroutineScope()
    val currentKeys = rememberUpdatedState(keys)
    val currentOnMove = rememberUpdatedState(onMove)
    val currentOnSwap = rememberUpdatedState(onSwap)
    return remember(gridState, scope) {
        GridReorderState(
            gridState = gridState,
            scope = scope,
            keys = { currentKeys.value },
            onMove = { from, to -> currentOnMove.value(from, to) },
            onSwap = { currentOnSwap.value() },
        )
    }
}

/**
 * Makes one grid cell draggable: a long press lifts it, after which it follows
 * the finger in any direction and the cells it crosses shuffle out of the way.
 *
 * The lift transform lives in a graphics layer, so following the finger costs a
 * redraw rather than a recomposition of the cell.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.reorderableGridCell(state: GridReorderState, key: Any): Modifier = this
    .zIndex(if (state.isActive(key)) 1f else 0f)
    .graphicsLayer {
        val translation = state.translationFor(key)
        translationX = translation.x
        translationY = translation.y
        val scale = state.scaleFor(key)
        scaleX = scale
        scaleY = scale
    }
    .pointerInput(key) {
        detectDragGesturesAfterLongPress(
            onDragStart = { state.onDragStart(key) },
            onDrag = { change, amount ->
                change.consume()
                state.onDrag(amount)
            },
            onDragEnd = { state.onDragEnd() },
            onDragCancel = { state.onDragEnd() },
        )
    }
