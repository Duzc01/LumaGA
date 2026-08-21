package com.bugenzhao.mnga.ui.post

import android.media.MediaPlayer
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bugenzhao.mnga.BuildConfig
import com.bugenzhao.mnga.util.URLs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 内嵌帖子视频播放器：未播放时显示 16:9 深色占位 + 中央播放按钮；点击后
 * 挂载 SurfaceView + MediaPlayer 播放，宽撑满、高度按视频真实宽高比自适应。
 *
 * 底部自定义控制条：播放/暂停、2dp 进度条（可拖动 seek）、静音切换、下载。
 * 默认静音播放。视频请求携带 UA/Referer（NGA 直链对裸请求断流）。
 *
 * 独立成组件以便后续替换为 ExoPlayer 等更完整的播放器。
 */
@Composable
fun InlineVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var playing by remember(url) { mutableStateOf(false) }
    // 视频比例：拿到真实宽高前先用 16:9 占位。
    var aspect by remember(url) { mutableFloatStateOf(16f / 9f) }
    val shape = RoundedCornerShape(8.dp)

    if (!playing) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(shape)
                .background(Color(0xFF141416))
                .clickable { playing = true },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        return
    }

    // 播放器在点击播放后才创建；数据源在 surface 就绪时设置，避免组合时
    // 同步网络连接阻塞/失败导致后续视频无法播放。
    val player = remember(url) { MediaPlayer() }
    var prepared by remember(url) { mutableStateOf(false) }

    var isPlaying by remember(url) { mutableStateOf(false) }
    var positionMs by remember(url) { mutableLongStateOf(0L) }
    var durationMs by remember(url) { mutableLongStateOf(0L) }
    var muted by remember(url) { mutableStateOf(true) }

    // 进度轮询。
    LaunchedEffect(player) {
        while (true) {
            runCatching {
                positionMs = player.currentPosition.toLong()
                val d = player.duration.toLong()
                if (d > 0) durationMs = d
            }
            delay(500)
        }
    }

    DisposableEffect(player) {
        onDispose { runCatching { player.release() } }
    }

    fun togglePlay() {
        runCatching {
            if (player.isPlaying) {
                player.pause()
                isPlaying = false
            } else {
                player.start()
                isPlaying = true
            }
        }
    }

    fun toggleMute() {
        muted = !muted
        runCatching { player.setVolume(if (muted) 0f else 1f, if (muted) 0f else 1f) }
    }

    fun download() {
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(context.cacheDir, "videos").apply { mkdirs() }
                    val target = File(dir, "LumaGA-${System.currentTimeMillis()}.mp4")
                    val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 15_000
                        readTimeout = 30_000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "LumaGA/${BuildConfig.VERSION_NAME}")
                        setRequestProperty("Referer", URLs.base)
                    }
                    try {
                        if (conn.responseCode !in 200..299) return@runCatching false
                        conn.inputStream.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        target.length() > 0
                    } finally {
                        conn.disconnect()
                    }
                }.getOrDefault(false)
            }
            android.widget.Toast.makeText(
                context,
                if (ok) "已下载到 ${context.cacheDir}/videos/" else "下载失败",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(shape),
    ) {
        AndroidView(
            factory = { viewContext ->
                SurfaceView(viewContext).also { surface ->
                    surface.holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            runCatching {
                                if (!prepared) {
                                    player.setDataSource(context, Uri.parse(url), mapOf(
                                        "User-Agent" to "LumaGA/${BuildConfig.VERSION_NAME}",
                                        "Referer" to URLs.base,
                                    ))
                                    player.setDisplay(holder)
                                    player.setOnPreparedListener { mp ->
                                        // 默认静音播放。
                                        mp.setVolume(0f, 0f)
                                        prepared = true
                                        mp.start()
                                        isPlaying = true
                                    }
                                    player.setOnVideoSizeChangedListener { _, w, h ->
                                        if (w > 0 && h > 0) aspect = w.toFloat() / h
                                    }
                                    player.prepareAsync()
                                } else {
                                    player.setDisplay(holder)
                                }
                            }
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) = Unit

                        override fun surfaceDestroyed(holder: SurfaceHolder) = Unit
                    })
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // 底部控制条。
        VideoControls(
            isPlaying = isPlaying,
            positionMs = positionMs,
            durationMs = durationMs,
            muted = muted,
            onPlayPause = ::togglePlay,
            onSeek = { ms -> runCatching { player.seekTo(ms.toInt()) } },
            onToggleMute = ::toggleMute,
            onDownload = ::download,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/** 底部控制条：播放/暂停 + 进度条 + 静音 + 下载。 */
@Composable
private fun VideoControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    muted: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleMute: () -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 拖动中的本地进度（避免被轮询打断）。
    var dragFraction by remember { mutableFloatStateOf(-1f) }
    val duration = durationMs.coerceAtLeast(1L)
    val fraction = if (dragFraction >= 0f) dragFraction else {
        (positionMs.toFloat() / duration).coerceIn(0f, 1f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            // 半透明黑底：0.5 透明度在保证按钮可读的同时不过度遮挡画面。
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        IconButton(onClick = onPlayPause, modifier = Modifier.size(36.dp)) {
            Icon(
                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            formatTime(if (dragFraction >= 0f) (fraction * duration).toLong() else positionMs),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        VideoSeekBar(
            fraction = fraction,
            onSeek = { f ->
                dragFraction = f
                onSeek((f * duration).toLong())
                dragFraction = -1f
            },
            onDrag = { dragFraction = it },
            modifier = Modifier.weight(1f),
        )
        Text(
            formatTime(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        IconButton(onClick = onToggleMute, modifier = Modifier.size(36.dp)) {
            Icon(
                if (muted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                contentDescription = if (muted) "Unmute" else "Mute",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onDownload, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Filled.Download,
                contentDescription = "Download",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * 2dp 进度条 + 小圆点指示器，支持点击与拖动 seek。
 */
@Composable
private fun VideoSeekBar(
    fraction: Float,
    onSeek: (Float) -> Unit,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .height(24.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onDrag((offset.x / size.width).coerceIn(0f, 1f))
                    },
                    onHorizontalDrag = { change, _ ->
                        onDrag((change.position.x / size.width).coerceIn(0f, 1f))
                    },
                    onDragEnd = {
                        // 拖完不额外触发 seek（拖动中已实时 seek 由调用方处理）
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        val thumbSize = 10.dp
        val trackWidth = maxWidth - thumbSize

        // 轨道 2dp。
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.3f)),
        )
        // 已播放进度 2dp。
        Box(
            Modifier
                .fillMaxWidth(fraction)
                .height(2.dp)
                .clip(CircleShape)
                .background(Color.White),
        )
        // 小圆点指示器。
        Box(
            Modifier
                .offset(x = trackWidth * fraction)
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}

private fun formatTime(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0L)
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}
