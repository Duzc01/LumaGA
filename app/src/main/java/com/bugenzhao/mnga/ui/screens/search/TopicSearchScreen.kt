package com.bugenzhao.mnga.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.util.L

/**
 * Topic search, a port of `TopicSearchView.swift` + `TopicSearchModel`: commit
 * on the search IME action, then run a paged `topicSearch` (scoped to
 * [forumId] when provided, across all forums otherwise). Toggling the search
 * options rebuilds the data source, exactly like re-committing the query.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopicSearchScreen(navigator: Navigator, forumId: ForumId? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("") }
    var commitedText by remember { mutableStateOf<String?>(null) }
    var searchContent by remember { mutableStateOf(true) }
    var recommendedOnly by remember { mutableStateOf(false) }

    BackHandler(enabled = navigator.size > 1) { navigator.pop() }

    // Rebuilt whenever the committed text or any option changes.
    val dataSource = remember(commitedText, forumId, searchContent, recommendedOnly) {
        commitedText?.let {
            buildTopicSearchDataSource(
                scope = scope,
                text = it,
                forumId = forumId,
                searchContent = searchContent,
                recommendedOnly = recommendedOnly,
            )
        }
    }
    LaunchedEffect(dataSource) { dataSource?.initialLoad() }

    val title = if (forumId == null) "Topic Search" else "Search Topics"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L.str(context, title)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            SearchField(
                text = text,
                prompt = L.str(context, "Search Topics"),
                onTextChange = { value ->
                    text = value
                    // Clearing the field clears the committed query.
                    if (value.isEmpty()) commitedText = null
                },
                onCommit = { commitedText = text.ifEmpty { null } },
            )

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = searchContent,
                    onClick = { searchContent = !searchContent },
                    label = { Text(L.str(context, "Search Content")) },
                    leadingIcon = {
                        Icon(Icons.Outlined.Article, contentDescription = null)
                    },
                )
                FilterChip(
                    selected = recommendedOnly,
                    onClick = { recommendedOnly = !recommendedOnly },
                    label = { Text(L.str(context, "Recommended Only")) },
                    leadingIcon = {
                        Icon(Icons.Outlined.ThumbUp, contentDescription = null)
                    },
                )
            }

            val active = dataSource
            if (active == null) {
                SearchIdleHint()
            } else {
                TopicResultsList(active, navigator)
            }
        }
    }
}
