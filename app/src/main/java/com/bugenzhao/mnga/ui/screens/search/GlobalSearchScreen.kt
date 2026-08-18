package com.bugenzhao.mnga.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Article
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.protos.datamodel.Forum
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.protos.datamodel.Subject
import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.ForumSearchRequest
import com.bugenzhao.mnga.protos.service.ForumSearchResponse
import com.bugenzhao.mnga.protos.service.TopicSearchRequest
import com.bugenzhao.mnga.protos.service.TopicSearchResponse
import com.bugenzhao.mnga.ui.screens.forumlist.ForumIcon
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.ui.nav.Route
import com.bugenzhao.mnga.util.DateFormatters
import com.bugenzhao.mnga.util.L
import com.bugenzhao.mnga.util.errorLocalized
import com.bugenzhao.mnga.storage.DateTimeStrategy
import java.util.Date
import kotlinx.coroutines.CoroutineScope

/**
 * Global search, a consolidation of `GlobalSearchView.swift` + `ForumSearchView`
 * into a single screen with two modes: forum search (`forumSearch` request,
 * rows push `TopicList`) and topic search across all forums (`topicSearch`).
 * The query commits on the search IME action, mirroring `SearchModel.commit()`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(navigator: Navigator) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var text by remember { mutableStateOf("") }
    var commitedText by remember { mutableStateOf<String?>(null) }
    var mode by remember { mutableStateOf(GlobalSearchMode.FORUMS) }

    BackHandler(enabled = navigator.size > 1) { navigator.pop() }

    // Data sources are rebuilt whenever the committed text changes, exactly
    // like `SearchModel`'s `commitedText -> dataSource` mapping.
    val forumDataSource = remember(commitedText) {
        commitedText?.let { buildForumSearchDataSource(scope, it) }
    }
    val topicDataSource = remember(commitedText) {
        commitedText?.let { buildTopicSearchDataSource(scope, it, forumId = null) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L.str(context, "Search")) },
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
                prompt = L.str(context, "Search"),
                onTextChange = { value ->
                    text = value
                    // Clearing the field clears the committed query.
                    if (value.isEmpty()) commitedText = null
                },
                onCommit = { commitedText = text.ifEmpty { null } },
            )

            if (commitedText != null) {
                Text(
                    L.str(context, "Search \"%@\" in...", commitedText ?: ""),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )

                SingleChoiceSegmentedButtonRow(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                ) {
                    SegmentedButton(
                        selected = mode == GlobalSearchMode.FORUMS,
                        onClick = { mode = GlobalSearchMode.FORUMS },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = { Icon(Icons.Outlined.Dashboard, contentDescription = null, Modifier.size(18.dp)) },
                        label = { Text(L.str(context, "All Forums")) },
                    )
                    SegmentedButton(
                        selected = mode == GlobalSearchMode.TOPICS,
                        onClick = { mode = GlobalSearchMode.TOPICS },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = { Icon(Icons.Outlined.Article, contentDescription = null, Modifier.size(18.dp)) },
                        label = { Text(L.str(context, "All Topics")) },
                    )
                }
            }

            val activeForumDS = forumDataSource
            val activeTopicDS = topicDataSource
            when {
                commitedText == null -> SearchIdleHint()
                mode == GlobalSearchMode.FORUMS && activeForumDS != null ->
                    ForumResultsList(activeForumDS, navigator)
                mode == GlobalSearchMode.TOPICS && activeTopicDS != null ->
                    TopicResultsList(activeTopicDS, navigator)
            }
        }
    }
}

/** The two global search scopes, mirroring the "All Forums"/"All Topics" rows. */
private enum class GlobalSearchMode { FORUMS, TOPICS }

/** Search field with commit-on-search-IME semantics, shared by both screens. */
@Composable
internal fun SearchField(
    text: String,
    prompt: String,
    onTextChange: (String) -> Unit,
    onCommit: () -> Unit,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(prompt) },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        trailingIcon = {
            if (text.isNotEmpty()) {
                IconButton(onClick = { onTextChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = null)
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onCommit() }),
    )
}

// region Data source builders

/** `forumSearch` with the committed key; a single, non-paged result page. */
internal fun buildForumSearchDataSource(
    scope: CoroutineScope,
    text: String,
): PagingDataSource<ForumSearchResponse, Forum> =
    PagingDataSource(
        scope = scope,
        responseParser = { ForumSearchResponse.parser() },
        buildRequest = { _ ->
            AsyncRequest.newBuilder()
                .setForumSearch(ForumSearchRequest.newBuilder().setKey(text).build())
                .build()
        },
        onResponse = { response -> Pair(response.forumsList, 1) },
        id = { it.id.idDescription },
    )

/**
 * `topicSearch`; paginated, scoped to [forumId] when provided (in-forum
 * search) and across all forums otherwise.
 */
internal fun buildTopicSearchDataSource(
    scope: CoroutineScope,
    text: String,
    forumId: ForumId?,
    searchContent: Boolean = true,
    recommendedOnly: Boolean = false,
): PagingDataSource<TopicSearchResponse, Topic> =
    PagingDataSource(
        scope = scope,
        responseParser = { TopicSearchResponse.parser() },
        buildRequest = { page ->
            val request = TopicSearchRequest.newBuilder()
                .setKey(text)
                .setPage(page)
                .setSearchContent(searchContent)
                .setRecommendedOnly(recommendedOnly)
            forumId?.let { request.setId(it) }
            AsyncRequest.newBuilder().setTopicSearch(request.build()).build()
        },
        onResponse = { response ->
            Pair(response.topicsList, response.pages.takeIf { it > 0 })
        },
        id = { it.id },
    )

// endregion

// region Result lists

/** Forum search results, like `ForumSearchView`. */
@Composable
internal fun ForumResultsList(
    dataSource: PagingDataSource<ForumSearchResponse, Forum>,
    navigator: Navigator,
) {
    val context = LocalContext.current
    val state by dataSource.state.collectAsState()

    when {
        dataSource.notLoaded -> {
            LaunchedEffect(dataSource) { dataSource.initialLoad() }
            CenteredSpinner()
        }
        state.items.isEmpty() && state.latestError != null ->
            ErrorState(context.errorLocalized(state.latestError?.error ?: "error"))
        state.items.isEmpty() -> EmptyResultsState()
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") { SectionHeader(L.str(context, "Search Results")) }
            itemsIndexed(state.items, key = { _, forum -> forum.id.idDescription }) { _, forum ->
                ForumRowLite(forum) {
                    navigator.push(Route.TopicList(forumId = forum.id))
                }
            }
        }
    }
}

/** Topic search results, like `TopicSearchView`: paged with prefetch. */
@Composable
internal fun TopicResultsList(
    dataSource: PagingDataSource<TopicSearchResponse, Topic>,
    navigator: Navigator,
) {
    val context = LocalContext.current
    val state by dataSource.state.collectAsState()

    when {
        dataSource.notLoaded -> {
            LaunchedEffect(dataSource) { dataSource.initialLoad() }
            CenteredSpinner()
        }
        state.items.isEmpty() && state.latestError != null ->
            ErrorState(context.errorLocalized(state.latestError?.error ?: "error"))
        state.items.isEmpty() -> EmptyResultsState()
        else -> LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "header") { SectionHeader(L.str(context, "Search Results")) }
            itemsIndexed(state.items, key = { _, topic -> topic.id }) { index, topic ->
                LaunchedEffect(index, state.items.size) {
                    dataSource.loadMoreIfNeeded(index)
                }
                TopicRowLite(topic) {
                    navigator.push(topicDetailsRoute(topic))
                }
            }
            item(key = "footer") { ListFooter(loading = state.isLoading) }
        }
    }
}

/** `TopicDetailsView.build(topic:)` destination for a searched topic. */
internal fun topicDetailsRoute(topic: Topic): Route.TopicDetails =
    Route.TopicDetails(
        topicId = topic.id,
        fav = if (topic.hasFav()) topic.fav else null,
    )

// endregion

// region Rows

/** Simplified `ForumRowView`: icon, localized name/info, stid and favorite marks. */
@Composable
internal fun ForumRowLite(forum: Forum, onClick: () -> Unit) {
    val context = LocalContext.current
    val isFavorite = App.favoriteForums.isFavorite(forum.id)

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ForumIcon(iconUrl = forum.iconUrl, name = L.str(context, forum.name))
            Column(Modifier.weight(1f)) {
                Text(
                    L.str(context, forum.name),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (forum.info.isNotEmpty()) {
                    Text(
                        L.str(context, forum.info),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (forum.id.hasStid()) {
                Icon(
                    Icons.Outlined.Layers,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isFavorite) {
                Icon(
                    Icons.Outlined.Star,
                    contentDescription = null,
                    modifier = Modifier.size(15.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/**
 * Simplified `TopicRowView`: subject (tags bar + multicolored content, dimmed
 * when read since last visit), tiered replies count, author and date footer.
 */
@Suppress("DEPRECATION")
@Composable
internal fun TopicRowLite(
    topic: Topic,
    useTopicPostDate: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val multicolor = App.prefs.topicListSubjectMulticolor.value
    val dimmed = !topic.id.startsWith("mnga_") && topic.hasRepliesNumLastVisit()
    val date = if (useTopicPostDate) topic.postDate else topic.lastPostDate

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    TopicTagsBarLite(topic)
                    TopicSubjectTextLite(topic, dimmed, multicolor)
                }
                RepliesNumLite(
                    num = topic.repliesNum,
                    lastNum = if (topic.hasRepliesNumLastVisit()) topic.repliesNumLastVisit else null,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val authorName = topic.authorName
                val anonymous = authorName.anonymous.isNotEmpty()
                Icon(
                    if (anonymous) Icons.Outlined.TheaterComedy else Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    if (anonymous) authorName.anonymous else authorName.normal,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.weight(1f))
                DateTimeTextLite(date)
            }
        }
    }
}

/** Leading accent-colored tag bar: bookmark mark, parent forum name, tags. */
@Suppress("DEPRECATION")
@Composable
private fun TopicTagsBarLite(topic: Topic) {
    val tags = topic.subject.tagsList.ifEmpty { topic.tagsList }
    val parentName = if (topic.hasParentForum()) topic.parentForum.name else null
    if (tags.isEmpty() && parentName == null && !topic.isFavored) return

    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (topic.isFavored) {
            Icon(
                Icons.Outlined.Bookmark,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (parentName != null) {
            Text(
                parentName,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        tags.forEach { tag ->
            Text(
                tag,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/** Subject content with the `fontModifiers` palette of `TopicSubjectView`. */
@Suppress("DEPRECATION")
@Composable
private fun TopicSubjectTextLite(topic: Topic, dimmed: Boolean, multicolor: Boolean) {
    val context = LocalContext.current
    val content = topic.subject.content.ifEmpty { topic.subjectContent }

    if (content.isEmpty()) {
        Text(
            L.str(context, "Untitled"),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Medium,
                fontStyle = FontStyle.Italic,
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        return
    }

    val modifiers = if (multicolor) topic.subject.fontModifiersList else emptyList()
    val paletteColor = modifiers.firstNotNullOfOrNull { subjectPalette[it] }
    val color = when {
        paletteColor != null -> paletteColor.copy(alpha = if (dimmed) 0.6f else 1f)
        dimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        else -> Color.Unspecified
    }
    var style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
    when {
        modifiers.contains(Subject.FontModifier.BOLD) ->
            style = style.copy(fontWeight = FontWeight.Bold)
        modifiers.contains(Subject.FontModifier.SEMIBOLD) ->
            style = style.copy(fontWeight = FontWeight.SemiBold)
    }
    if (modifiers.contains(Subject.FontModifier.ITALIC)) {
        style = style.copy(fontStyle = FontStyle.Italic)
    }
    val decoration =
        if (modifiers.contains(Subject.FontModifier.UNDERLINE)) TextDecoration.Underline else null

    Text(
        content,
        style = style,
        color = color,
        textDecoration = decoration,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Subject palette from `ContentCombiner.palette`. */
private val subjectPalette: Map<Subject.FontModifier, Color> =
    mapOf(
        Subject.FontModifier.RED to Color(0xFFDD0000),
        Subject.FontModifier.BLUE to Color(0xFF0066BB),
        Subject.FontModifier.GREEN to Color(0xFF3D9F0E),
        Subject.FontModifier.ORANGE to Color(0xFFA06700),
        Subject.FontModifier.SILVER to Color(0xFF888888),
    )

/** Replies count with the tiered styling of `RepliesNumView`, plus the (+N) delta. */
@Composable
private fun RepliesNumLite(num: Int, lastNum: Int?) {
    val accent = MaterialTheme.colorScheme.primary
    val (style, color) = when {
        num <= 0 -> MaterialTheme.typography.bodyMedium to Color.Transparent
        num < 40 ->
            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium) to
                accent.copy(alpha = 0.8f)
        num < 100 ->
            MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold) to
                accent.copy(alpha = 0.9f)
        num < 200 ->
            MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold) to accent
        num < 500 ->
            MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold) to accent
        else ->
            MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.ExtraBold) to accent
    }

    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(num.toString(), style = style, color = color)
        val delta = lastNum?.let { num - it }?.takeIf { it > 0 }
        if (delta != null) {
            Text(
                "(+$delta)",
                style = MaterialTheme.typography.labelMedium,
                color = accent.copy(alpha = 0.8f),
            )
        }
    }
}

/** Timestamp honoring the `postRowDateTimeStrategy` preference. */
@Composable
internal fun DateTimeTextLite(timestampSeconds: Long) {
    val context = LocalContext.current
    val date = Date(timestampSeconds * 1000)
    val text = when (App.prefs.postRowDateTimeStrategy) {
        DateTimeStrategy.DETAILED -> DateFormatters.detailed(context, date)
        DateTimeStrategy.TIME_AGO -> DateFormatters.timeAgo(context, date)
        DateTimeStrategy.AUTOMATIC -> DateFormatters.automatic(context, date)
    }
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

// endregion

// region Shared list states

@Composable
internal fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
internal fun CenteredSpinner() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(24.dp),
            strokeWidth = 2.5.dp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** `ContentUnavailableView("No Results", systemImage: "magnifyingglass")`. */
@Composable
internal fun EmptyResultsState() {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            L.str(context, "No Results"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Idle hint shown before any query is committed. */
@Composable
internal fun SearchIdleHint() {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(40.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            L.str(context, "Search"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Shown for failed searches (the engine already banners the error toast). */
@Composable
internal fun ErrorState(message: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** Loading footer for paged results. */
@Composable
internal fun ListFooter(loading: Boolean) {
    if (loading) {
        CenteredSpinner()
    } else {
        Spacer(Modifier.height(8.dp))
    }
}

/** `ForumId.idDescription`: "#fid" or "##stid". */
internal val ForumId.idDescription: String
    get() = if (hasStid()) "##$stid" else "#$fid"

// endregion
