package com.bugenzhao.mnga.ui.editor

import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.ContentEditorModel
import com.bugenzhao.mnga.ui.post.StickerImages
import com.bugenzhao.mnga.util.L
import com.bugenzhao.mnga.util.Stickers
import org.json.JSONArray

/** Panel tab: the persisted recents list, or one sticker family. */
private sealed class StickerCategory {
    data object Recent : StickerCategory()
    data class Prefix(val prefix: String) : StickerCategory()
}

private const val RECENTS_KEY = "recentStickers"
private const val RECENTS_LIMIT = 40

private fun loadRecents(prefs: SharedPreferences): List<String> =
    try {
        val array = JSONArray(prefs.getString(RECENTS_KEY, "[]") ?: "[]")
        List(array.length()) { i -> array.getString(i) }
    } catch (e: Exception) {
        emptyList()
    }

private fun saveRecents(prefs: SharedPreferences, names: List<String>) {
    prefs.edit().putString(RECENTS_KEY, JSONArray(names).toString()).apply()
}

/**
 * Sticker picker panel, ported from `StickerInputView`: segmented category
 * picker (recents first, then every family) over a 4-column grid. Tapping a
 * sticker inserts its BBCode into [model] and records it as recent.
 */
@Composable
fun StickerInputPanel(model: ContentEditorModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = App.sharedPreferences
    var recents by remember { mutableStateOf(loadRecents(prefs)) }
    var category by remember {
        mutableStateOf<StickerCategory>(
            if (recents.isEmpty())
                StickerCategory.Prefix(Stickers.prefixes.firstOrNull() ?: "ac")
            else StickerCategory.Recent
        )
    }

    val stickers =
        when (val cat = category) {
            StickerCategory.Recent -> recents
            is StickerCategory.Prefix -> Stickers.all.filter { it.startsWith(cat.prefix) }
        }

    Column(modifier = modifier.fillMaxWidth()) {
        // Category picker: clock icon for recents, upper-cased family names.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = category == StickerCategory.Recent,
                onClick = { category = StickerCategory.Recent },
                label = {
                    Icon(
                        Icons.Outlined.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
            )
            Stickers.prefixes.forEach { prefix ->
                FilterChip(
                    selected = category == StickerCategory.Prefix(prefix),
                    onClick = { category = StickerCategory.Prefix(prefix) },
                    label = { Text(prefix.uppercase()) },
                )
            }
        }

        Box(
            Modifier.fillMaxWidth().height(240.dp).padding(horizontal = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (stickers.isEmpty()) {
                Text(
                    L.str(context, "Empty"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.fillMaxWidth().height(240.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(stickers, key = { it }) { name ->
                        StickerCell(name) {
                            model.insertSticker(name)
                            // Record recents: dedupe, move to front, cap at 40.
                            val next = mutableListOf<String>()
                            next.add(name)
                            next.addAll(recents.filter { it != name })
                            while (next.size > RECENTS_LIMIT) next.removeAt(next.size - 1)
                            recents = next
                            saveRecents(prefs, next)
                        }
                    }
                }
            }
        }
    }
}

/** One sticker tile: template families are tinted, `dt` gets a white plate. */
@Composable
private fun StickerCell(name: String, onClick: () -> Unit) {
    val isTemplate = StickerImages.isTemplate(name)
    val isDt = name.startsWith("dt")
    Box(
        Modifier.size(52.dp)
            .clip(CircleShape)
            .background(if (isDt) Color.White.copy(alpha = 0.9f) else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = StickerImages.model(name),
            contentDescription = name,
            colorFilter = if (isTemplate) StickerImages.templateColorFilter() else null,
            modifier = Modifier.size(44.dp),
        )
    }
}
