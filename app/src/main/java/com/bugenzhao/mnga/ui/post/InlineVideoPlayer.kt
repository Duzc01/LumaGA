package com.bugenzhao.mnga.ui.post

import android.media.MediaPlayer
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.MediaController
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bugenzhao.mnga.BuildConfig
import com.bugenzhao.mnga.util.URLs

/**
 * 内嵌帖子视频播放器：未播放时显示 16:9 深色占位 + 中央播放按钮；
 * 点击后挂载 SurfaceView + MediaPlayer 并自动开始播放，宽撑满、高度
 * 按视频实际宽高比自适应（与内嵌图片的展示逻辑一致）。带系统
 * MediaController。视频请求携带 UA/Referer（NGA 直链对裸请求断流）。
 *
 * 独立成组件以便后续替换为 ExoPlayer 等更完整的播放器。
 */
@Composable
fun InlineVideoPlayer(
    url: String,
    modifier: Modifier = Modifier,
) {
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

    val context = LocalContext.current
    val player = remember(url) {
        MediaPlayer().apply {
            setDataSource(
                context,
                Uri.parse(url),
                mapOf(
                    "User-Agent" to "LumaGA/${BuildConfig.VERSION_NAME}",
                    "Referer" to URLs.base,
                ),
            )
        }
    }
    val controller = remember(url) {
        MediaController(context).apply {
            // 部分 ROM 的 MediaPlayer 未实现 MediaController.MediaPlayerControl，
            // 用显式包装避免 ClassCastException。
            setMediaPlayer(PlayerControlAdapter(player))
        }
    }

    AndroidView(
        factory = { viewContext ->
            SurfaceView(viewContext).also { surface ->
                controller.setAnchorView(surface)
                surface.holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        runCatching {
                            player.setDisplay(holder)
                            player.setOnPreparedListener { it.start() }
                            player.setOnVideoSizeChangedListener { _, w, h ->
                                if (w > 0 && h > 0) aspect = w.toFloat() / h
                            }
                            player.prepareAsync()
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
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .clip(shape),
        onRelease = { player.release() },
    )
}

/** 适配 [MediaPlayer] 到 MediaController 控制接口。 */
private class PlayerControlAdapter(private val mp: MediaPlayer) :
    MediaController.MediaPlayerControl {
    override fun start() = mp.start()
    override fun pause() = mp.pause()
    override fun getDuration(): Int = mp.duration
    override fun getCurrentPosition(): Int = mp.currentPosition
    override fun seekTo(pos: Int) = mp.seekTo(pos)
    override fun isPlaying(): Boolean = mp.isPlaying
    override fun getBufferPercentage(): Int = 0
    override fun canPause(): Boolean = true
    override fun canSeekBackward(): Boolean = true
    override fun canSeekForward(): Boolean = true
    override fun getAudioSessionId(): Int = mp.audioSessionId
}
