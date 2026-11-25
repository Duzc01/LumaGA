package com.bugenzhao.mnga.ui.screens.prefs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.protos.datamodel.PostContent as PostContentProto
import com.bugenzhao.mnga.protos.datamodel.Span
import com.bugenzhao.mnga.protos.datamodel.Subject
import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.protos.datamodel.UserName
import com.bugenzhao.mnga.ui.components.GroupedList
import com.bugenzhao.mnga.ui.post.PostContent
import com.bugenzhao.mnga.ui.screens.topiclist.TopicRow
import com.bugenzhao.mnga.ui.theme.FontScale
import com.bugenzhao.mnga.util.L
import kotlin.math.roundToInt

/**
 * Dedicated font-size preferences screen with live previews for both the topic
 * list and post content, backed by independent percentage sliders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSizeSettingsScreen(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = App.prefs
    val rawTopicPercent by prefs.topicListFontScalePercent.flow.collectAsState()
    val rawPostPercent by prefs.postContentFontScalePercent.flow.collectAsState()
    val topicPercent = FontScale.normalizePercent(rawTopicPercent)
    val postPercent = FontScale.normalizePercent(rawPostPercent)
    val topicScale = FontScale.scaleFactor(topicPercent)
    val postScale = FontScale.scaleFactor(postPercent)

    val isModified = topicPercent != FontScale.DEFAULT_PERCENT ||
        postPercent != FontScale.DEFAULT_PERCENT

    val previewTopic = remember(context) {
        val nowSeconds = System.currentTimeMillis() / 1000L
        Topic.newBuilder()
            .setId("mnga_font_preview")
            .setSubject(
                Subject.newBuilder()
                    .setContent(L.str(context, "Font Preview Topic Subject"))
                    .addTags(L.str(context, "Font Preview Tag"))
                    .build(),
            )
            .setAuthorName(
                UserName.newBuilder()
                    .setNormal("LumaGA")
                    .build(),
            )
            .setPostDate(nowSeconds - 3600)
            .setLastPostDate(nowSeconds - 180)
            .setRepliesNum(128)
            .setRepliesNumLastVisit(116)
            .setIsFavored(true)
            .build()
    }

    val previewPostContent = remember(context) {
        PostContentProto.newBuilder()
            .addSpans(
                Span.newBuilder().setTagged(
                    Span.Tagged.newBuilder()
                        .setTag("quote")
                        .addSpans(plainSpan(L.str(context, "Font Preview Post Quote")))
                        .build(),
                ),
            )
            .addSpans(plainSpan(L.str(context, "Font Preview Post Body")))
            .addSpans(Span.newBuilder().setBreakLine(Span.BreakLine.getDefaultInstance()))
            .addSpans(
                Span.newBuilder().setTagged(
                    Span.Tagged.newBuilder()
                        .setTag("code")
                        .addSpans(plainSpan(L.str(context, "Font Preview Post Code")))
                        .build(),
                ),
            )
            .build()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L.str(context, "Font Size")) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = L.str(context, "Done"),
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            prefs.topicListFontScalePercent.value = FontScale.DEFAULT_PERCENT
                            prefs.postContentFontScalePercent.value = FontScale.DEFAULT_PERCENT
                        },
                        enabled = isModified,
                    ) {
                        Text(L.str(context, "Reset"))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        ) {
            item(key = "topic-list-font") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(L.str(context, "Topic List Font Size"))
                    TopicRow(
                        topic = previewTopic,
                        dimmedSubject = false,
                        showIndicators = true,
                        isFavored = true,
                        fallbackForumName = L.str(context, "Font Preview Forum"),
                        fontScale = topicScale,
                    )
                    FontScaleSliderCard(
                        percent = topicPercent,
                        baseSp = FontScale.TOPIC_TITLE_BASE_SP,
                        onPercentChange = { next ->
                            prefs.topicListFontScalePercent.value = next
                        },
                    )
                }
            }

            item(key = "post-content-font") {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionLabel(L.str(context, "Post Font Size"))
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surface,
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                L.str(context, "Font Preview Topic Header"),
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = FontScale.topicHeaderTitleFontSize(postScale),
                                    lineHeight = FontScale.topicHeaderTitleLineHeight(postScale),
                                    fontWeight = FontWeight.Bold,
                                ),
                            )
                            PostContent(
                                content = previewPostContent,
                                fontScale = postScale,
                            )
                        }
                    }
                    FontScaleSliderCard(
                        percent = postPercent,
                        baseSp = FontScale.POST_NORMAL_BASE_SP,
                        onPercentChange = { next ->
                            prefs.postContentFontScalePercent.value = next
                        },
                    )
                }
            }

            item(key = "font-size-footer") {
                Text(
                    L.str(context, "Font Size Footer"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun FontScaleSliderCard(
    percent: Int,
    baseSp: Float,
    onPercentChange: (Int) -> Unit,
) {
    val context = LocalContext.current
    val resolvedSp = (baseSp * FontScale.scaleFactor(percent) * 10f).roundToInt() / 10f
    val spLabel = if (resolvedSp % 1f == 0f) {
        "${resolvedSp.toInt()}sp"
    } else {
        "${resolvedSp}sp"
    }
    val valueText = if (percent == FontScale.DEFAULT_PERCENT) {
        "${L.str(context, "Standard")} · $percent% ($spLabel)"
    } else {
        "$percent% ($spLabel)"
    }

    GroupedList {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    L.str(context, "Scale"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    valueText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Slider(
                value = percent.toFloat(),
                onValueChange = { raw ->
                    val normalized = FontScale.normalizePercent(raw.roundToInt())
                    if (normalized != percent) {
                        onPercentChange(normalized)
                    }
                },
                valueRange = FontScale.MIN_PERCENT.toFloat()..FontScale.MAX_PERCENT.toFloat(),
                steps = FontScale.SLIDER_STEPS,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${L.str(context, "Small")} ${FontScale.MIN_PERCENT}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    L.str(context, "Standard"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${L.str(context, "Large")} ${FontScale.MAX_PERCENT}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun plainSpan(text: String): Span =
    Span.newBuilder()
        .setPlain(Span.Plain.newBuilder().setText(text).build())
        .build()
