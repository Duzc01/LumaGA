package com.bugenzhao.mnga.ui.post

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.protos.datamodel.Attachment
import com.bugenzhao.mnga.util.L
import com.bugenzhao.mnga.util.URLs

private val Attachment.isImage: Boolean get() = type == "img"

/** Human-readable byte size, e.g. `1.2 MB`. */
fun formatSize(bytes: Long): String = when {
    bytes >= 1 shl 20 -> "%.1f MB".format(bytes.toFloat() / (1 shl 20))
    bytes >= 1 shl 10 -> "%.1f KB".format(bytes.toFloat() / (1 shl 10))
    else -> "$bytes B"
}

/**
 * Inline post image, ported from `Views/ContentImageView.swift`.
 *
 * - Open-source-sticker attachments render as fixed 50dp tiles (no viewer).
 * - `onlyThumbs` (inside a reply quote) renders a "View Image" button instead
 *   of loading the picture.
 * - Otherwise the image loads at its natural width, capped at
 * `postRowImageScale × screen width`, dimmed in dark mode per preference, and
 * opens the viewer (with all sibling attachments) on tap.
 */
@Composable
fun ContentImageView(
    url: String,
    onlyThumbs: Boolean = false,
    forceNotThumb: Boolean = false,
    onViewImage: (urls: List<String>, current: String) -> Unit,
    alt: String? = null,
    sizeBytes: Long? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lastPathComponent = url.substringAfterLast('/')
    if (lastPathComponent in OpenSourceStickers.names) {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = null,
            modifier = modifier.size(50.dp),
            loading = { LoadingTile() },
        )
        return
    }

    if (onlyThumbs && !forceNotThumb) {
        ContentButton(
            icon = Icons.Outlined.Image,
            title = { Text(L.str(context, "View Image")) },
            inQuote = true,
        ) { onViewImage(listOf(url), url) }
        return
    }

    val scale = App.prefs.postRowImageScale.scale
    val maxWidth =
        (LocalConfiguration.current.screenWidthDp * scale).dp
    val dark = isSystemInDarkTheme()
    val dim = dark && App.prefs.postRowDimImagesInDarkMode.flow.collectAsState().value
    val filter =
        if (dim) {
            ColorFilter.colorMatrix(ColorMatrix().apply { setToScale(0.7f, 0.7f, 0.7f, 1f) })
        } else null

    Column(modifier) {
        // Load at the original resolution: while the placeholder box is only
        // 48dp tall, Coil would otherwise downsample to that tiny size and the
        // image would look blurry once it expands to its real aspect ratio.
        val painter = coil.compose.rememberAsyncImagePainter(
            model =
                coil.request.ImageRequest.Builder(context)
                    .data(url)
                    .size(coil.size.Size.ORIGINAL)
                    .build()
        )
        // Reading the painter state subscribes to load progress so the box
        // re-measures once the intrinsic size is known.
        val painterState = painter.state
        val intrinsic = painter.intrinsicSize
        val isReady = painterState is AsyncImagePainter.State.Success
        val aspect =
            if (intrinsic.width > 0f && intrinsic.height > 0f) {
                intrinsic.width / intrinsic.height
            } else {
                1f
            }
        Box(
            Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth)
                .then(
                    if (isReady) {
                        Modifier.aspectRatio(aspect)
                    } else {
                        // Before the image is ready (or if it failed) show only
                        // a small icon instead of a full-size placeholder.
                        Modifier.height(48.dp)
                    }
                )
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = isReady) { onViewImage(listOf(url), url) },
            contentAlignment = Alignment.Center,
        ) {
            // The painter must stay composed (and drawn) even before the image
            // is ready: AsyncImagePainter only starts its request while drawing,
            // so skipping Image would leave the state stuck at Loading forever.
            androidx.compose.foundation.Image(
                painter = painter,
                contentDescription = alt,
                contentScale = ContentScale.Fit,
                colorFilter = filter,
                modifier = Modifier.fillMaxSize(),
            )
            if (!isReady) {
                Icon(
                    if (painterState is AsyncImagePainter.State.Error) {
                        Icons.Outlined.BrokenImage
                    } else {
                        Icons.Outlined.Image
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (alt != null || sizeBytes != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                listOfNotNull(alt?.takeIf { it.isNotEmpty() }, sizeBytes?.takeIf { it > 0 }?.let(::formatSize))
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LoadingTile() {
    Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
    }
}

/**
 * Attachments sheet body, ported from `Views/AttachmentsView.swift`: a
 * disclosure header with the count, then one row per attachment (icon by type,
 * middle-truncated URL, size). Images open the multi-page viewer; other files
 * open externally.
 */
@Composable
fun AttachmentsView(
    attachments: List<Attachment>,
    onViewImage: (urls: List<String>, current: String) -> Unit,
    onOpenURL: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageUrls =
        remember(attachments) {
            attachments.filter { it.isImage }.mapNotNull { URLs.attachmentURL(it.url) }
        }

    LazyColumn(modifier) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Outlined.AttachFile,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    L.str(context, "Attachments (%lld)", attachments.size),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            HorizontalDivider()
        }
        items(attachments, key = { it.url }) { attachment ->
            val url = URLs.attachmentURL(attachment.url)
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(enabled = url != null) {
                        if (url != null) {
                            if (attachment.isImage) onViewImage(imageUrls, url)
                            else onOpenURL(url)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    if (attachment.isImage) Icons.Outlined.Image else Icons.Outlined.Link,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        attachment.url,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (attachment.size > 0) {
                        Text(
                            formatSize(attachment.size),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
