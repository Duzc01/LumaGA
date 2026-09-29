package com.bugenzhao.mnga.ui.screens.topicdetails

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bugenzhao.mnga.util.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CapsuleHeight = 40.dp
private val CapsuleWidth = 120.dp
private val ExpandedHeight = 56.dp
private val PageItemWidth = 48.dp
private const val LongPressTimeoutMs = 300L

/**
 * 胶囊背景进度条填充比例：当前页在总页数中的位置。
 * 与 fluxdo 的 TopicProgress 保持一致：(current - 1) / (total - 1)。
 */
internal fun pageProgressFraction(currentPage: Int, totalPages: Int): Float =
    if (totalPages > 1) {
        (currentPage - 1).toFloat() / (totalPages - 1).toFloat()
    } else {
        0f
    }

/**
 * 展开态横向页码列表的宽度：容纳所有页码，至少 3 个页码宽，
 * 至多屏幕宽度减去两侧边距。
 */
internal fun pageListWidthDp(
    totalPages: Int,
    screenWidthDp: Int,
    itemWidthDp: Int = 48,
): Int = (totalPages * itemWidthDp)
    .coerceAtLeast(itemWidthDp * 3)
    .coerceAtMost(screenWidthDp - 64)

/**
 * 帖子详情页底部的胶囊页码指示器（复刻自 fluxdo 的 TopicProgress）。
 *
 * - 胶囊形态：显示 `当前页 / 总页数`，背景有阅读进度填充。
 * - 长按 300ms：形变为横向页码列表，可左右滑动，点击页码跳转。
 * - 点击胶囊外部或滑动帖子列表：收起回胶囊形态。
 * - 点击胶囊：预留，暂无行为。
 */
@Composable
fun PageIndicatorOverlay(
    currentPage: Int,
    totalPages: Int,
    isListScrolling: Boolean,
    onJumpToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val view = LocalView.current

    // 滑动帖子列表时收起页码列表。
    LaunchedEffect(isListScrolling) {
        if (isListScrolling) expanded = false
    }

    Box(modifier.fillMaxSize()) {
        if (expanded) {
            // 点击外部收起。clickable 只在纯 tap 时触发，不拦截列表滚动。
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { expanded = false },
                    ),
            )
        }
        PageCapsule(
            currentPage = currentPage,
            totalPages = totalPages,
            expanded = expanded,
            onLongPress = {
                Haptics.lightImpact(view)
                expanded = true
            },
            onTap = {
                // 预留：点击胶囊的交互待定。
            },
            onJumpToPage = { page ->
                expanded = false
                onJumpToPage(page)
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
        )
    }
}

@Composable
private fun PageCapsule(
    currentPage: Int,
    totalPages: Int,
    expanded: Boolean,
    onLongPress: () -> Unit,
    onTap: () -> Unit,
    onJumpToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val screenWidthDp = LocalConfiguration.current.screenWidthDp
    val pressScope = rememberCoroutineScope()
    // Hoisted so the press gesture can drive the list directly: the list is
    // composed on expansion (mid-gesture) and can never pick up the in-flight
    // pointer itself, so drag deltas are forwarded manually.
    val listState = rememberLazyListState()
    // 展开时滚动到当前页（列表组合完成后再滚，避免 timer 里直接滚未组合的 state）。
    LaunchedEffect(expanded) {
        if (expanded) {
            listState.scrollToItem(
                (currentPage - 1).coerceIn(0, (totalPages - 1).coerceAtLeast(0)),
            )
        }
    }
    // The pointerInput block is a restricted scope and cannot call
    // listState.scrollBy directly; drag deltas go through this channel to a
    // LaunchedEffect that performs the scroll.
    val dragDeltas = remember { Channel<Float>(Channel.UNLIMITED) }
    LaunchedEffect(listState, dragDeltas) {
        for (dx in dragDeltas) {
            listState.scrollBy(-dx)
        }
    }
    val targetWidth =
        if (expanded) pageListWidthDp(totalPages, screenWidthDp).dp else CapsuleWidth
    val targetHeight = if (expanded) ExpandedHeight else CapsuleHeight
    val width by animateDpAsState(
        targetWidth,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "page-indicator-width",
    )
    val height by animateDpAsState(
        targetHeight,
        animationSpec = tween(260, easing = FastOutSlowInEasing),
        label = "page-indicator-height",
    )

    Surface(
        modifier = modifier
            .width(width)
            .height(height)
            .capsulePressDrag(
                scope = pressScope,
                timeoutMs = LongPressTimeoutMs,
                enabled = !expanded,
                onLongPress = onLongPress,
                onTap = onTap,
                onDrag = { dragDeltas.trySend(it) },
            ),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 4.dp,
    ) {
        Box {
            // 胶囊形态的进度条背景。
            if (!expanded) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(pageProgressFraction(currentPage, totalPages))
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ),
                )
            }
            AnimatedContent(
                targetState = expanded,
                transitionSpec = {
                    (fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.92f))
                        .togetherWith(fadeOut(tween(150)))
                },
                label = "page-indicator-morph",
            ) { isExpanded ->
                if (isExpanded) {
                    PageNumberList(
                        currentPage = currentPage,
                        totalPages = totalPages,
                        listState = listState,
                        onJumpToPage = onJumpToPage,
                    )
                } else {
                    Row(
                        Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "$currentPage",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                            ),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = " / ",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        )
                        Text(
                            text = "$totalPages",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 15.sp,
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** 横向页码列表：可左右滑动，点击页码跳转。触摸滚动由胶囊手势手动驱动。 */
@Composable
private fun PageNumberList(
    currentPage: Int,
    totalPages: Int,
    listState: LazyListState,
    onJumpToPage: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = PaddingValues(horizontal = 8.dp),
        userScrollEnabled = false,
    ) {
        items(totalPages, key = { it }) { index ->
            val page = index + 1
            val selected = page == currentPage
            Box(
                modifier = Modifier
                    .width(PageItemWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primaryContainer
                        else Color.Transparent,
                    )
                    .clickable { onJumpToPage(page) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "$page",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * 胶囊按压手势：按住 [timeoutMs] 触发 [onLongPress] 展开为页码列表；
 * 展开后手指不松开继续滑动，直接驱动页码列表滚动；
 * 超时前松开则视为点击触发 [onTap]。
 *
 * 用于自定义长按时长（Compose 默认长按阈值不可配置），以及长按后
 * 无缝衔接滑动：页码列表在展开瞬间才被组合，无法接管进行中的
 * 手势流，故由本手势手动把拖动增量转发给列表（列表自身的触摸滚动
 * 已关闭，避免双重处理）。
 *
 * 注意：[enabled] 不作为 pointerInput 的 key——展开时若重启手势块，
 * 进行中的按压会被取消，导致"长按后必须松手才能滑动"。改为常驻手势，
 * 内部用 [enabled] 的最新值区分"胶囊态按压检测"与"展开态拖动转发"。
 */
@Composable
private fun Modifier.capsulePressDrag(
    scope: CoroutineScope,
    timeoutMs: Long,
    enabled: Boolean = true,
    onLongPress: () -> Unit,
    onTap: () -> Unit,
    onDrag: (Float) -> Unit,
): Modifier {
    val enabledState = rememberUpdatedState(enabled)
    val onLongPressState = rememberUpdatedState(onLongPress)
    val onTapState = rememberUpdatedState(onTap)
    val onDragState = rememberUpdatedState(onDrag)
    return pointerInput(scope, timeoutMs) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!enabledState.value) {
                // 展开态：所有横向拖动都手动转发给页码列表。
                var lastX = down.position.x
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val dx = change.position.x - lastX
                    lastX = change.position.x
                    if (dx != 0f) {
                        // 手指向右拖内容跟随向右：反向滚动。
                        onDragState.value(dx)
                        change.consume()
                    }
                }
                return@awaitEachGesture
            }
            // 胶囊态：按压检测。
            var longPressed = false
            val job = scope.launch {
                delay(timeoutMs)
                if (!enabledState.value) return@launch
                longPressed = true
                onLongPressState.value()
            }
            try {
                // 阶段一：touch slop，区分点击与拖动。
                val slopChange = awaitTouchSlopOrCancellation(down.id) { change, _ ->
                    change.consume()
                }
                if (slopChange == null) {
                    // 未拖动即松开：纯按压。
                    job.cancel()
                    if (!longPressed && enabledState.value) onTapState.value()
                    return@awaitEachGesture
                }
                if (!longPressed) {
                    // 长按触发前就拖动了：不是长按手势，吃掉剩余事件。
                    job.cancel()
                    var event = awaitPointerEvent()
                    while (event.changes.any { it.pressed }) {
                        event = awaitPointerEvent()
                    }
                    return@awaitEachGesture
                }
                // 阶段二：已展开且手指未松开：手动转发拖动给页码列表。
                // 注意此时 enabled 已变为 false，但本手势块常驻不重启，
                // 继续在此分支内完成本次拖动。
                var lastX = slopChange.position.x
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    val dx = change.position.x - lastX
                    lastX = change.position.x
                    if (dx != 0f) {
                        onDragState.value(dx)
                        change.consume()
                    }
                }
            } finally {
                job.cancel()
            }
        }
    }
}
