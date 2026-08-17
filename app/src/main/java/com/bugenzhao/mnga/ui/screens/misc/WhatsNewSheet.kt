package com.bugenzhao.mnga.ui.screens.misc

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.BuildConfig
import com.bugenzhao.mnga.util.L

/** Persistence key of the last "What's New" version shown to the user. */
const val WHATS_NEW_LAST_SHOWN_VERSION_KEY = "whatsNewLastShownVersion"

/** One feature bullet of a What's New page. */
data class WhatsNewFeature(val icon: ImageVector, val title: String, val subtitle: String)

/** One What's New page, keyed by version. */
data class WhatsNewEntry(val version: String, val features: List<WhatsNewFeature>)

/**
 * Localized format helper: like [L.str] but tolerant of resources that keep
 * the iOS-style specifiers (`%@`, `%lld`) in their English fallback values.
 */
internal fun fmtL(context: Context, key: String, vararg args: Any): String =
    try {
        L.str(context, key, *args)
    } catch (e: Exception) {
        var text = L.str(context, key)
        args.forEach { arg ->
            text = text.replaceFirst(Regex("%(\\d+\\$)?(?:ll?d|d|s|@)"), arg.toString())
        }
        text
    }

/**
 * The What's New collection, mirrored verbatim from `Utilities/WhatsNew.swift`.
 * The Chinese marketing copy is data carried over from the iOS app (it has no
 * English fallback there either).
 */
val whatsNewEntries: List<WhatsNewEntry> = listOf(
    WhatsNewEntry(
        version = "2.0",
        features = listOf(
            WhatsNewFeature(
                Icons.Filled.AutoAwesome,
                "Liquid Glass 全新设计",
                "采用 Liquid Glass 设计语言全面重构，带来更直观的操作逻辑与全新的视觉体验。",
            ),
            WhatsNewFeature(
                Icons.Filled.Lan,
                "增强的网络模块",
                "显著提升了 API 的稳定性与抗封锁能力，遇到 XML 解析错误和浏览器跳转的几率大幅降低。",
            ),
            WhatsNewFeature(
                Icons.Filled.Checklist,
                "大量修复与改进",
                "50 余项 Bug 修复与体验改进，采用 iOS 26 最新 API，使用体验更加稳定丝滑。",
            ),
            WhatsNewFeature(
                Icons.Filled.Star,
                "Plus 计划全新上线",
                "MNGA Plus 不仅为您解锁更完整的体验，更是我们持续改进和长期维护 MNGA 的唯一动力。",
            ),
        ),
    ),
    WhatsNewEntry(
        version = "2.1",
        features = listOf(
            WhatsNewFeature(
                Icons.Filled.Update,
                "保存阅读进度",
                "自动记录你读过的楼层，久未返回会贴心刷新，一打开就续上最新进度。",
            ),
            WhatsNewFeature(
                Icons.Filled.Bookmark,
                "多收藏夹支持",
                "创建、管理多个收藏夹，帮助你精确归类喜爱的帖子。",
            ),
            WhatsNewFeature(
                Icons.Filled.Description,
                "帖子内容更生动",
                "贴文现可展示骰子结果、表格排版，图片会按偏好智能缩放，看帖更轻松。",
            ),
            WhatsNewFeature(
                Icons.Filled.Checklist,
                "持续修复与改进",
                "持续修复已知问题，进一步整合 iOS 26 全新 API，使用体验更加稳定丝滑。",
            ),
        ),
    ),
    WhatsNewEntry(
        version = "2.2",
        features = listOf(
            WhatsNewFeature(
                Icons.Filled.PhotoLibrary,
                "多图翻页浏览",
                "图片多也不怕：左右滑动一口气翻完，放大缩小也顺手。",
            ),
            WhatsNewFeature(
                Icons.Filled.NotificationsActive,
                "随处打开通知",
                "不管你在列表还是看帖，工具栏都能一键直达未读通知，重要消息不迷路。",
            ),
            WhatsNewFeature(
                Icons.Filled.Cloud,
                "收藏版块云端同步",
                "开启后，版块收藏会自动随账号同步云端，多台设备间无缝切换。",
            ),
            WhatsNewFeature(
                Icons.Filled.Checklist,
                "阅读体验持续打磨",
                "匿名帖子只看作者、帖子列表跳转版块、全新表情输入面板；阅读体验更加流畅舒适。",
            ),
        ),
    ),
    WhatsNewEntry(
        version = "2.3.2",
        features = listOf(
            WhatsNewFeature(
                Icons.Filled.Photo,
                "截图分享回归",
                "话题、单楼都能一键生成分享图，带上 MNGA 标识和二维码，安利起来更像样。",
            ),
            WhatsNewFeature(
                Icons.AutoMirrored.Filled.Comment,
                "回复关系更清楚",
                "引用内容可直接展开预览，回复链、查看被回复、定位原楼层都更顺手。",
            ),
            WhatsNewFeature(
                Icons.Filled.Bolt,
                "刷帖更跟手",
                "ProMotion 设备可选择高刷新率优先，iPhone 也能锁定竖屏，躺着看帖更安分。",
            ),
            WhatsNewFeature(
                Icons.Filled.Checklist,
                "阅读体验持续打磨",
                "新增加载最新回复，优化列表、时间、附件、表情和引用渲染，并修复多项导航与内容解析问题。",
            ),
        ),
    ),
)

private fun versionParts(version: String): List<Int> =
    version.split(".").map { it.toIntOrNull() ?: 0 }

/** Entries sorted by version descending, like `AllWhatsNewView`. */
val whatsNewEntriesDescending: List<WhatsNewEntry> =
    whatsNewEntries.sortedByDescending { versionParts(it.version).joinToString(".") }

/** `major.minor` display rule when the patch component is zero. */
fun WhatsNewEntry.betterDescription(): String {
    val parts = version.split(".")
    return if (parts.size == 3 && parts[2].toIntOrNull() == 0) parts.take(2).joinToString(".")
    else version
}

fun lastShownWhatsNewVersion(): String? =
    App.sharedPreferences.getString(WHATS_NEW_LAST_SHOWN_VERSION_KEY, null)

fun markWhatsNewShown(version: String = BuildConfig.VERSION_NAME) {
    App.sharedPreferences.edit()
        .putString(WHATS_NEW_LAST_SHOWN_VERSION_KEY, version)
        .apply()
}

fun resetWhatsNewShown() {
    App.sharedPreferences.edit()
        .remove(WHATS_NEW_LAST_SHOWN_VERSION_KEY)
        .apply()
}

/** True when the current version's What's New page has not been shown yet. */
fun shouldShowWhatsNew(): Boolean =
    lastShownWhatsNewVersion() != BuildConfig.VERSION_NAME

/**
 * Honors the `debugResetWhatsNew` preference at launch: clears the store and
 * flips the preference back off.
 */
fun maybeDebugResetWhatsNew() {
    if (App.prefs.debugResetWhatsNew.value) {
        resetWhatsNewShown()
        App.prefs.debugResetWhatsNew.value = false
    }
}

/**
 * The version-gated "What's New" sheet: shown by the root host while
 * [shouldShowWhatsNew] is true.
 */
@Composable
fun WhatsNewSheet(onDismiss: () -> Unit) {
    val entry = remember {
        whatsNewEntriesDescending.firstOrNull { BuildConfig.VERSION_NAME.startsWith(it.version) }
            ?: whatsNewEntriesDescending.first()
    }
    WhatsNewPage(
        entry = entry,
        primaryLabel = "Continue",
        onPrimary = {
            markWhatsNewShown()
            onDismiss()
        },
        onSecondary = { App.plus.showPaywall() },
    )
}

/** One full What's New page, reusable by the sheet and the "all versions" list. */
@Composable
fun WhatsNewPage(
    entry: WhatsNewEntry,
    primaryLabel: String,
    onPrimary: () -> Unit,
    onSecondary: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onPrimary, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().padding(24.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                Modifier.padding(28.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                WhatsNewTitle(entry)
                Column(
                    Modifier.height(320.dp).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    entry.features.forEach { feature -> WhatsNewFeatureRow(feature) }
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Button(onClick = onPrimary, modifier = Modifier.fillMaxWidth()) {
                        Text(L.str(context, primaryLabel))
                    }
                    if (onSecondary != null) {
                        TextButton(onClick = onSecondary) {
                            Text(L.str(context, "Check out Plus"))
                        }
                    }
                }
            }
        }
    }
}

/** "What's new in MNGA x.y.z" with the "MNGA x.y.z" substring tinted accent. */
@Composable
private fun WhatsNewTitle(entry: WhatsNewEntry) {
    val context = LocalContext.current
    val version = entry.betterDescription()
    val title = fmtL(context, "What's new in MNGA %@", version)
    val highlighted = "MNGA $version"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Filled.Article,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp),
        )
        if (title.contains(highlighted)) {
            val prefix = title.substringBefore(highlighted)
            val suffix = title.substringAfter(highlighted)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(prefix, style = MaterialTheme.typography.headlineSmall)
                Text(
                    highlighted,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(suffix, style = MaterialTheme.typography.headlineSmall)
            }
        } else {
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun WhatsNewFeatureRow(feature: WhatsNewFeature) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).background(
                MaterialTheme.colorScheme.primaryContainer,
                CircleShape,
            ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                feature.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(feature.title, style = MaterialTheme.typography.titleMedium)
            Text(
                feature.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The "What's New" settings page: every version entry, the current one badged,
 * each row opening the corresponding page with a "Back" primary action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllWhatsNewContent(onBack: () -> Unit) {
    var selected by remember { mutableStateOf<WhatsNewEntry?>(null) }
    val current = selected
    if (current == null) {
        Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            whatsNewEntriesDescending.forEach { entry ->
                val isCurrent = BuildConfig.VERSION_NAME.startsWith(entry.betterDescription())
                val context = LocalContext.current
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    onClick = { selected = entry },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(entry.betterDescription(), style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.weight(1f))
                        if (isCurrent) {
                            Text(
                                L.str(context, "Current Version"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    } else {
        WhatsNewPage(entry = current, primaryLabel = "Back", onPrimary = { selected = null })
    }
}
