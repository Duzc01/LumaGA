package com.bugenzhao.mnga.ui.components.imageviewer

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import coil.imageLoader
import coil.request.ImageRequest
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.BuildConfig
import com.bugenzhao.mnga.model.ViewingImageModel
import com.bugenzhao.mnga.util.URLs
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-screen image viewer, ported from `iOS/Views/NewImageViewer.swift`:
 * horizontal pager, per-page pinch zoom (1x-5x), double-tap toggle, drag to
 * dismiss with fading background, page indicator and share (JPEG-0.9 re-encode
 * or the original file, per the `alwaysShareImageAsFile` preference).
 */
@Composable
fun ImageViewerDialog(model: ViewingImageModel) {
    val showing by model.showing.collectAsState()
    if (!showing) return
    val urls by model.urls.collectAsState()
    val current by model.currentIndex.collectAsState()
    if (urls.isEmpty()) return

    val context = LocalContext.current

    // Whole-viewer opacity driven by the drag-to-dismiss gesture.
    var dragAlpha by remember { mutableFloatStateOf(1f) }
    val alpha by animateFloatAsState(targetValue = dragAlpha, animationSpec = tween(120), label = "alpha")

    // Prepared share payload for the currently settled page.
    var shareIntent by remember { mutableStateOf<Intent?>(null) }
    var preparingShare by remember { mutableStateOf(true) }

    val pagerState = rememberPagerState(initialPage = current, pageCount = { urls.size })

    // The model is the source of truth; keep the pager in sync.
    LaunchedEffect(current) {
        if (pagerState.settledPage != current && current in urls.indices) {
            pagerState.animateScrollToPage(current)
        }
    }
    LaunchedEffect(pagerState) {
        var last = pagerState.settledPage
        androidx.compose.runtime.snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != last) {
                last = page
                preparingShare = true
                shareIntent = null
            }
        }
    }

    // Prepare the share intent for the visible page.
    LaunchedEffect(pagerState.settledPage, urls) {
        val url = urls.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
        preparingShare = true
        shareIntent = null
        val intent = withContext(Dispatchers.IO) { buildImageShareIntent(context, url) }
        shareIntent = intent
        preparingShare = false
    }

    Dialog(
        onDismissRequest = { model.dismiss() },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            dismissOnBackPress = true,
        ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha }
                .background(color = Color.Black.copy(alpha = 0.96f * alpha)),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                pageSpacing = 40.dp,
                key = { urls[it] },
            ) { page ->
                ZoomableImagePage(
                    url = urls[page],
                    isCurrent = page == pagerState.settledPage,
                    onDismissAlpha = { dragAlpha = it },
                    onDismiss = { model.dismiss() },
                )
            }

            // Top bar: page indicator trailing, share + close.
            Row(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 24.dp, start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (urls.size > 1) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${current + 1}",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            " / ${urls.size}",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 13.sp,
                        )
                    }
                }
                Box(Modifier.weight(1f))
                IconButton(onClick = { model.dismiss() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            // Bottom share button.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
            ) {
                if (preparingShare || shareIntent == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    IconButton(onClick = {
                        shareIntent?.let { runCatching { context.startActivity(it) } }
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White)
                    }
                }
            }

            // Bottom-right download button (saved to 下载/LumaGA, same as videos).
            val scope = rememberCoroutineScope()
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { granted ->
                val url = urls.getOrNull(pagerState.settledPage) ?: return@rememberLauncherForActivityResult
                if (granted) {
                    saveImage(scope, context, url) { ok -> toastImageResult(context, ok) }
                } else {
                    android.widget.Toast.makeText(
                        context,
                        "需要存储权限才能下载",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 32.dp, end = 24.dp),
            ) {
                IconButton(onClick = {
                    val url = urls.getOrNull(pagerState.settledPage) ?: return@IconButton
                    if (hasStoragePermission(context)) {
                        saveImage(scope, context, url) { ok -> toastImageResult(context, ok) }
                    } else {
                        permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                }) {
                    Icon(Icons.Filled.Download, contentDescription = "Download", tint = Color.White)
                }
            }
        }
    }

    // Reset dismiss opacity when reopened.
    LaunchedEffect(showing) {
        if (showing) dragAlpha = 1f
    }
}

/**
 * One pager page: pinch zoom 1x-5x, double-tap toggle, one-finger pan while
 * zoomed, and vertical drag-to-dismiss while at 1x.
 */
@Composable
private fun ZoomableImagePage(
    url: String,
    isCurrent: Boolean,
    onDismissAlpha: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val context = LocalContext.current

    // coerceIn 对 NaN 会原样返回 NaN（比较全为 false），导致缩放卡死；
    // 第二指落下的瞬间 calculateZoom 可能产生异常值，这里统一兜底。
    fun clampScale(value: Float): Float =
        if (value.isNaN() || value.isInfinite() || value <= 0f) 1f
        else value.coerceIn(1f, 5f)

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(url) {
                awaitEachGesture {
                    awaitFirstDown(pass = PointerEventPass.Main)
                    var pastSlop = false
                    var zooming = false
                    val touchSlop = viewConfiguration.touchSlop
                    var totalPan = Offset.Zero
                    var dragDown = 0f

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (event.changes.size > 1) zooming = true

                        if (!pastSlop) {
                            totalPan += panChange
                            val zoomed = zoomChange != 1f
                            if (zoomed || abs(totalPan) > touchSlop ||
                                event.changes.size > 1
                            ) {
                                pastSlop = true
                            }
                        }

                        if (pastSlop) {
                            if (zooming || scale > 1f) {
                                // Pinch zoom / pan of the zoomed image.
                                // 只应用有效的 zoom（第二指落下的瞬间可能是 NaN/0）。
                                if (zoomChange.isFinite() && zoomChange > 0f) {
                                    scale = clampScale(scale * zoomChange)
                                }
                                if (scale > 1f) {
                                    offset = (offset + panChange).let { o ->
                                        val bound = 2000f * (scale - 1f)
                                        Offset(
                                            o.x.coerceIn(-bound, bound),
                                            o.y.coerceIn(-bound, bound),
                                        )
                                    }
                                } else {
                                    offset = Offset.Zero
                                }
                                // Consume while interacting with the image.
                                event.changes.forEach {
                                    if (it.positionChanged()) it.consume()
                                }
                            } else {
                                // Not zoomed: only a downward drag dismisses.
                                dragDown += panChange.y
                                if (dragDown > 0) {
                                    onDismissAlpha(1f - (dragDown / 700f).coerceIn(0f, 0.9f))
                                    event.changes.forEach {
                                        if (it.positionChanged()) it.consume()
                                    }
                                }
                            }
                        }

                        if (event.changes.none { it.pressed }) {
                            // Released.
                            if (pastSlop && !zooming && scale <= 1f) {
                                if (dragDown > 160f) onDismiss() else onDismissAlpha(1f)
                                scale = 1f
                                offset = Offset.Zero
                            }
                            break
                        }
                    }
                }
            }
            // Double-tap toggles between 1x and 2.5x.
            .pointerInput(url) {
                detectTapGestures(
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            scale = 2.5f
                            offset = Offset((tap.x - size.width / 2f) * -1f * 0.75f, 0f)
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        coil.compose.AsyncImage(
            model = ImageRequest.Builder(context)
                .data(url)
                .crossfade(false)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
    LaunchedEffect(isCurrent) { if (!isCurrent) onDismissAlpha(1f) }
}

private fun abs(offset: Offset): Float = maxOf(kotlin.math.abs(offset.x), kotlin.math.abs(offset.y))

// region Share helpers

private fun md5Hex(string: String): String {
    val digest = MessageDigest.getInstance("MD5").digest(string.toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

/** Last path component, safe for a filename. */
private fun lastPathComponent(url: String): String =
    Uri.parse(url).lastPathSegment?.takeIf { it.isNotEmpty() } ?: "image"

private fun shareNameFor(url: String): String =
    "LumaGA_${md5Hex(url)}_${lastPathComponent(url)}"

/**
 * Builds an `ACTION_SEND` intent for the image at [url], mirroring the iOS
 * `TransferableImage` rules:
 *  - static JPEG/PNG/WebP and `alwaysShareImageAsFile` off: re-encode as JPEG
 *    at quality 90 into the cache dir;
 *  - anything else (animated GIF, HEIC, ... or the force-file preference):
 *    download the original bytes and share them as a file.
 * Falls back to a plain text share when no `FileProvider` is declared.
 */
internal suspend fun buildImageShareIntent(context: Context, url: String): Intent? {
    val forceFile = App.prefs.alwaysShareImageAsFile.value

    var mimeType: String? = null
    var drawable: Drawable? = null
    return try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .allowHardware(false)
            .build()
        val success = context.imageLoader.execute(request) as? coil.request.SuccessResult
        if (success != null) {
            drawable = success.drawable
            mimeType = when {
                url.endsWith(".png", ignoreCase = true) -> "image/png"
                url.endsWith(".webp", ignoreCase = true) -> "image/webp"
                url.endsWith(".gif", ignoreCase = true) -> "image/gif"
                else -> "image/jpeg"
            }
        }

        val isStaticBitmap = drawable is BitmapDrawable && mimeType != null &&
            mimeType in listOf("image/jpeg", "image/png", "image/webp")

        val (file, type) = if (isStaticBitmap && !forceFile) {
            val bitmap = (drawable as BitmapDrawable).bitmap
            val name = shareNameFor(url).substringBeforeLast(".") + ".jpg"
            val out = File(context.cacheDir, "share/$name").apply {
                parentFile?.mkdirs()
                if (!exists()) {
                    outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                }
            }
            out to "image/jpeg"
        } else {
            val name = shareNameFor(url)
            val out = File(context.cacheDir, "share/$name").apply {
                parentFile?.mkdirs()
                if (!exists()) {
                    java.net.URL(url).openStream().use { input ->
                        outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
            out to (mimeType ?: "application/octet-stream")
        }

        val contentUri = try {
            androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
        } catch (e: Exception) {
            null
        }

        val intent = if (contentUri != null) {
            Intent(Intent.ACTION_SEND).apply {
                this.type = type
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            // No file provider available: share the source URL as text.
            Intent(Intent.ACTION_SEND).apply {
                this.type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, url)
            }
        }
        Intent.createChooser(intent, null)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
}

// endregion

// region Download helpers

/** Extension from the URL path, defaulting to jpg. */
private fun imageExtFor(url: String): String {
    val path = Uri.parse(url).path?.lowercase() ?: return "jpg"
    return when {
        path.endsWith(".png") -> "png"
        path.endsWith(".gif") -> "gif"
        path.endsWith(".webp") -> "webp"
        path.endsWith(".bmp") -> "bmp"
        path.endsWith(".heic") -> "heic"
        else -> "jpg"
    }
}

private fun mimeFor(ext: String): String = when (ext) {
    "png" -> "image/png"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "bmp" -> "image/bmp"
    "heic" -> "image/heic"
    else -> "image/jpeg"
}

/** API 29+ 走 MediaStore 无需权限；旧版本需要存储权限。 */
private fun hasStoragePermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= 29 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED

private fun toastImageResult(context: Context, ok: Boolean) {
    android.widget.Toast.makeText(
        context,
        if (ok) "已保存到 下载/LumaGA/" else "下载失败",
        android.widget.Toast.LENGTH_SHORT,
    ).show()
}

/** 下载图片到公共「下载/LumaGA」目录（API 29+ 用 MediaStore），与视频一致。 */
private fun saveImage(
    scope: kotlinx.coroutines.CoroutineScope,
    context: Context,
    url: String,
    onResult: (Boolean) -> Unit,
) {
    scope.launch {
        val ok = withContext(Dispatchers.IO) {
            runCatching {
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
                    val ext = imageExtFor(url)
                    val mime = mimeFor(ext)
                    if (Build.VERSION.SDK_INT >= 29) {
                        val resolver = context.contentResolver
                        val values = ContentValues().apply {
                            put(
                                MediaStore.MediaColumns.DISPLAY_NAME,
                                "LumaGA-${System.currentTimeMillis()}.$ext",
                            )
                            put(MediaStore.MediaColumns.MIME_TYPE, mime)
                            put(
                                MediaStore.MediaColumns.RELATIVE_PATH,
                                Environment.DIRECTORY_DOWNLOADS + "/LumaGA",
                            )
                            put(MediaStore.MediaColumns.IS_PENDING, 1)
                        }
                        val uri = resolver.insert(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            values,
                        ) ?: return@runCatching false
                        val written = resolver.openOutputStream(uri)?.use { output ->
                            conn.inputStream.use { input -> input.copyTo(output) }
                            true
                        } ?: false
                        values.clear()
                        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(uri, values, null, null)
                        written
                    } else {
                        val dir = File(
                            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                            "LumaGA",
                        ).apply { mkdirs() }
                        val target = File(dir, "LumaGA-${System.currentTimeMillis()}.$ext")
                        conn.inputStream.use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                        target.length() > 0
                    }
                } finally {
                    conn.disconnect()
                }
            }.getOrDefault(false)
        }
        onResult(ok)
    }
}

// endregion
