package com.bugenzhao.mnga.ui.screens.misc

import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.Coil
import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.protos.datamodel.CacheOperation
import com.bugenzhao.mnga.protos.datamodel.CacheType
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.CacheRequest
import com.bugenzhao.mnga.protos.service.CacheResponse
import com.bugenzhao.mnga.ui.components.GroupedList
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.util.Haptics
import com.bugenzhao.mnga.util.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What a pending clear confirmation refers to. */
private sealed interface PendingClear {
    data object Image : PendingClear
    data class Data(val type: CacheType) : PendingClear
}

private fun cacheTypeLabel(type: CacheType): String = when (type) {
    CacheType.ALL -> "All"
    CacheType.TOPIC_HISTORY -> "Topic Histories"
    CacheType.TOPIC_DETAILS -> "Topic Cache"
    CacheType.NOTIFICATION -> "Notifications"
    else -> "Unknown"
}

/**
 * Cache management settings page, ported from `Views/CacheView.swift`.
 * The image cache is the Coil disk cache; data caches live in the logic layer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CacheScreen(navigator: Navigator? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var imageStatus by remember { mutableStateOf<String?>(null) }
    var cacheStatus by remember { mutableStateOf<Map<CacheType, String>>(emptyMap()) }
    var pendingClear by remember { mutableStateOf<PendingClear?>(null) }

    fun manipulateCache(type: CacheType, operation: CacheOperation) {
        scope.launch {
            val request = AsyncRequest.newBuilder()
                .setCache(
                    CacheRequest.newBuilder()
                        .setType(type)
                        .setOperation(operation)
                        .build()
                )
                .build()
            logicCallAsync(request, CacheResponse.parser()).onSuccess { response ->
                if (operation == CacheOperation.CLEAR) {
                    Haptics.play(view, Haptics.NotificationType.SUCCESS)
                    manipulateCache(type, CacheOperation.CHECK)
                } else {
                    val text =
                        if (type == CacheType.ALL) {
                            Formatter.formatShortFileSize(context, response.totalSize)
                        } else {
                            fmtL(context, "%llu items", response.items)
                        }
                    cacheStatus = cacheStatus + (type to text)
                }
            }
        }
    }

    fun loadImageCacheSize() {
        scope.launch {
            val size = withContext(Dispatchers.IO) {
                Coil.imageLoader(context).diskCache?.size ?: 0L
            }
            imageStatus = Formatter.formatShortFileSize(context, size)
        }
    }

    fun clearImageCache() {
        imageStatus = null
        scope.launch {
            withContext(Dispatchers.IO) { Coil.imageLoader(context).diskCache?.clear() }
            Haptics.play(view, Haptics.NotificationType.SUCCESS)
            loadImageCacheSize()
        }
    }

    LaunchedEffect(Unit) {
        if (imageStatus == null) loadImageCacheSize()
        CacheType.entries.forEach { manipulateCache(it, CacheOperation.CHECK) }
    }

    BackHandler(enabled = navigator != null && navigator.size > 1) { navigator?.pop() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L.str(context, "Cache Management")) },
                navigationIcon = {
                    IconButton(onClick = { navigator?.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item(key = "image") {
                SectionCard(header = L.str(context, "Image")) {
                    CacheRow(
                        label = L.str(context, "Image Cache"),
                        leading = {
                            Icon(
                                Icons.Filled.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        status = imageStatus,
                        clearable = true,
                        onClick = { pendingClear = PendingClear.Image },
                    )
                }
            }

            item(key = "data") {
                SectionCard(
                    header = L.str(context, "Data"),
                    footer = L.str(context, "Tap an item to clear it."),
                ) {
                    CacheType.entries.forEach { type ->
                        val clearable = type != CacheType.ALL
                        CacheRow(
                            label = L.str(context, cacheTypeLabel(type)),
                            leading = {
                                Icon(
                                    Icons.Filled.CleaningServices,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            status = cacheStatus[type],
                            clearable = clearable,
                            onClick = if (clearable) {
                                { pendingClear = PendingClear.Data(type) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
    }

    when (val pending = pendingClear) {
        null -> {}
        is PendingClear.Image ->
            ClearCacheDialog(
                onConfirm = {
                    pendingClear = null
                    clearImageCache()
                },
                onDismiss = { pendingClear = null },
            )
        is PendingClear.Data ->
            ClearCacheDialog(
                onConfirm = {
                    val type = pending.type
                    pendingClear = null
                    cacheStatus = cacheStatus - type
                    manipulateCache(type, CacheOperation.CLEAR)
                },
                onDismiss = { pendingClear = null },
            )
    }
}

@Composable
private fun SectionCard(
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                header,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, bottom = 6.dp),
            )
        }
        GroupedList { Column { content() } }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp, top = 6.dp, end = 16.dp),
            )
        }
    }
}

/** `CacheRowView`: label + trailing status text or spinner. */
@Composable
private fun CacheRow(
    label: String,
    leading: (@Composable () -> Unit)? = null,
    status: String?,
    clearable: Boolean,
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        leading?.invoke()
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color =
                if (clearable) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        if (status != null) {
            Text(
                status,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
        }
    }
}

/** The exact confirmation dialog copy from `CacheView.swift`. */
@Composable
private fun ClearCacheDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(L.str(context, "Are you sure to clear the cache?")) },
        text = { Text(L.str(context, "This will take a while.")) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(L.str(context, "Clear"), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(L.str(context, "Cancel")) }
        },
    )
}
