package com.bugenzhao.mnga.ui.screens.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.storage.SearchHistoryScope
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.util.L
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** The two search scopes, one per tab, each with its own history list. */
private enum class SearchTab(val titleKey: String, val history: SearchHistoryScope) {
    TOPICS("Topic Search", SearchHistoryScope.TOPICS),
    FORUMS("Forum Search", SearchHistoryScope.FORUMS),
}

/**
 * Search, borrowing FluxDo's layout: a capsule field in the app bar over a two
 * tab pager (topics / forums). Each tab shows its own search history until a
 * query is committed, then the shared result lists of [SearchResults].
 *
 * The chrome is one continuous light "nav zone" — app bar plus the segmented
 * tab strip — over the grouped gray page the rest of the app uses, so the
 * options card and the history chips read as content rather than as more
 * toolbar.
 *
 * [forumId] is the forum being browsed, if any; only then can the topic search
 * be narrowed to the current forum.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(navigator: Navigator, forumId: ForumId? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val fieldFocus = remember { FocusRequester() }

    var text by remember { mutableStateOf("") }
    var committedText by remember { mutableStateOf<String?>(null) }
    // Narrowed to the browsed forum by default, where there is one to narrow to.
    var currentForumOnly by remember { mutableStateOf(forumId != null) }
    var searchContent by remember { mutableStateOf(true) }

    val pagerState = rememberPagerState(pageCount = { SearchTab.entries.size })

    BackHandler(enabled = navigator.size > 1) { navigator.pop() }

    // Open with the keyboard up: the screen exists only to be typed into. The
    // field may attach a frame later than this effect, hence the guard.
    LaunchedEffect(Unit) {
        runCatching { fieldFocus.requestFocus() }
    }

    /** Commit [query], remembering it in the history of the visible tab. */
    fun commit(query: String) {
        val term = query.trim()
        // Hide the keyboard: while it is open the window is resized, and a
        // results list swapped in at that moment can measure with a zero-size
        // viewport.
        keyboard?.hide()
        committedText = term.ifEmpty { null }
        if (term.isEmpty()) return
        App.searchHistory.remember(SearchTab.entries[pagerState.currentPage].history, term)
    }

    // Data sources are rebuilt whenever the committed text or an option
    // changes, like `SearchModel`'s `commitedText -> dataSource` mapping.
    val topicDataSource = remember(committedText, currentForumOnly, searchContent) {
        committedText?.let {
            buildTopicSearchDataSource(
                scope = scope,
                text = it,
                forumId = forumId.takeIf { currentForumOnly },
                searchContent = searchContent,
            )
        }
    }
    val forumDataSource = remember(committedText) {
        committedText?.let { buildForumSearchDataSource(scope, it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    SearchCapsule(
                        text = text,
                        hint = L.str(context, "Search"),
                        focusRequester = fieldFocus,
                        onTextChange = { value ->
                            text = value
                            // Clearing the field clears the committed query.
                            if (value.isEmpty()) committedText = null
                        },
                        onCommit = { commit(text) },
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
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
                .padding(padding),
        ) {
            // Continues the app bar's surface so the strip belongs to the
            // chrome, not to the page.
            Surface(color = MaterialTheme.colorScheme.surface) {
                SegmentedTabs(
                    titles = SearchTab.entries.map { L.str(context, it.titleKey) },
                    // Follows the swipe itself, not just the settled page.
                    position = pagerState.currentPage + pagerState.currentPageOffsetFraction,
                    onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                )
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                // Both pages hold their own scrollable list; let a page settle
                // before the next one is asked to compose one.
                beyondViewportPageCount = 0,
            ) { page ->
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val tab = SearchTab.entries[page]
                    if (tab == SearchTab.TOPICS) {
                        TopicSearchOptions(
                            hasCurrentForum = forumId != null,
                            currentForumOnly = currentForumOnly,
                            onCurrentForumOnlyChange = { currentForumOnly = it },
                            searchContent = searchContent,
                            onSearchContentChange = { searchContent = it },
                            modifier = Modifier.padding(top = 14.dp),
                        )
                    }
                    val topicDS = topicDataSource
                    val forumDS = forumDataSource
                    when {
                        tab == SearchTab.TOPICS && topicDS != null ->
                            TopicResultsList(topicDS, navigator)
                        tab == SearchTab.FORUMS && forumDS != null ->
                            ForumResultsList(forumDS, navigator)
                        else -> SearchHistorySection(
                            historyScope = tab.history,
                            onPick = { query ->
                                text = query
                                commit(query)
                            },
                            modifier = Modifier.padding(
                                top = if (tab == SearchTab.TOPICS) 0.dp else 14.dp,
                            ),
                        )
                    }
                }
            }
        }
    }
}

/** True while the light-on-dark scheme is in effect. */
@Composable
private fun isDarkScheme(): Boolean =
    MaterialTheme.colorScheme.surface.luminance() < 0.5f

// region App bar field

/**
 * FluxDo's app bar field: a rounded capsule holding the query, drawing its own
 * hint (a `TextField` placeholder cannot be aligned inside a fixed height).
 */
@Composable
private fun SearchCapsule(
    text: String,
    hint: String,
    focusRequester: FocusRequester,
    onTextChange: (String) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 10.dp, end = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            modifier = Modifier.size(17.dp),
            tint = muted.copy(alpha = 0.75f),
        )
        Spacer(Modifier.width(7.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (text.isEmpty()) {
                Text(
                    hint,
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted.copy(alpha = 0.7f),
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                singleLine = true,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onCommit() }),
            )
        }
        if (text.isNotEmpty()) {
            // The filled glyph, the way a platform search field clears itself.
            Icon(
                Icons.Filled.Cancel,
                contentDescription = L.str(context, "Clear"),
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable { onTextChange("") }
                    .padding(4.dp),
                tint = muted.copy(alpha = 0.55f),
            )
        }
    }
}

// endregion

// region Tabs

/**
 * A two-segment control instead of the stock underlined tab row: a single
 * track with a thumb that slides under the finger as the pager is dragged,
 * which suits a chooser of two short labels better than a full-width
 * indicator.
 */
@Composable
private fun SegmentedTabs(
    titles: List<String>,
    position: Float,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (titles.isEmpty()) return
    val dark = isDarkScheme()
    val inset = 3.dp
    val height = 34.dp
    val selected = position.roundToInt().coerceIn(titles.indices)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        val segmentWidth: Dp = (maxWidth - inset * 2) / titles.size
        val clamped = position.coerceIn(0f, (titles.size - 1).toFloat())
        Box(
            Modifier
                .padding(inset)
                .offset(x = segmentWidth * clamped)
                .width(segmentWidth)
                .fillMaxHeight()
                .shadow(
                    // Light mode lifts the thumb off the track; in the dark
                    // scheme a shadow is invisible and the fill does the work.
                    elevation = if (dark) 0.dp else 2.dp,
                    shape = RoundedCornerShape(8.dp),
                    clip = false,
                )
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (dark) {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    } else {
                        MaterialTheme.colorScheme.surface
                    }
                ),
        )
        Row(Modifier.fillMaxSize()) {
            titles.forEachIndexed { index, title ->
                val isSelected = index == selected
                val color by animateColorAsState(
                    if (isSelected) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    label = "tabLabel",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // No ripple: the sliding thumb is the feedback.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(index) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        title,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        color = color,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

// endregion

// region Topic options

/**
 * The topic search options as one grouped card: the scope radios (only with a
 * forum to narrow to) over the content toggle, each a full-width row so the
 * label is as tappable as its control.
 */
@Composable
private fun TopicSearchOptions(
    hasCurrentForum: Boolean,
    currentForumOnly: Boolean,
    onCurrentForumOnlyChange: (Boolean) -> Unit,
    searchContent: Boolean,
    onSearchContentChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(horizontal = 12.dp)) {
            if (hasCurrentForum) {
                Row(Modifier.fillMaxWidth()) {
                    OptionRadio(
                        label = L.str(context, "Current Forum"),
                        selected = currentForumOnly,
                        onClick = { onCurrentForumOnlyChange(true) },
                        modifier = Modifier.weight(1f),
                    )
                    OptionRadio(
                        label = L.str(context, "All Forums"),
                        selected = !currentForumOnly,
                        onClick = { onCurrentForumOnlyChange(false) },
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider(
                    // Inset past the controls, the way a grouped list divides.
                    Modifier.padding(start = 28.dp),
                    thickness = Dp.Hairline,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                )
            }
            OptionCheckbox(
                label = L.str(context, "Search Content"),
                checked = searchContent,
                onCheckedChange = onSearchContentChange,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Row height shared by the option rows, a comfortable grouped-list step. */
private val OptionRowHeight = 44.dp

@Composable
private fun OptionRadio(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(OptionRowHeight)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The row owns the click; the control is decoration, sized down from
        // its 40dp touch target so the rows stay compact.
        RadioButton(selected = selected, onClick = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        OptionLabel(label, selected)
    }
}

@Composable
private fun OptionCheckbox(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(OptionRowHeight)
            .toggleable(
                value = checked,
                role = Role.Checkbox,
                onValueChange = onCheckedChange,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        OptionLabel(label, checked)
    }
}

/** Chosen options step up to the full-strength text; the rest stay muted. */
@Composable
private fun OptionLabel(label: String, active: Boolean) {
    Text(
        label,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
        color = if (active) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

// endregion

// region History

/**
 * Recent queries of one tab as removable chips, wrapping over at most three
 * lines — FluxDo's `_RemovableChip`, laid out as its filter panel lays out
 * tags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistorySection(
    historyScope: SearchHistoryScope,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val storage = App.searchHistory
    val queries by storage.queries(historyScope).collectAsState()

    if (queries.isEmpty()) {
        SearchIdleHint()
        return
    }

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // The same header the results lists use, so the two states of a tab
            // share one rhythm.
            SectionHeader(L.str(context, "Search History"))
            Spacer(Modifier.weight(1f))
            // Chips past the third line are clipped, so a clear-all is the only
            // way to reach what the wrap does not show.
            Text(
                L.str(context, "Clear"),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { storage.clear(historyScope) }
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
        }
        FlowRow(
            Modifier
                .fillMaxWidth()
                // Deleting a chip reflows the rest instead of snapping.
                .animateContentSize(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxLines = 3,
        ) {
            queries.forEach { query ->
                key(query) {
                    SearchHistoryChip(
                        query = query,
                        onClick = { onPick(query) },
                        onDelete = { storage.remove(historyScope, query) },
                    )
                }
            }
        }
    }
}

/** One recent query: the term and its own delete button, in a single block. */
@Composable
private fun SearchHistoryChip(query: String, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val outline = MaterialTheme.colorScheme.outline
    Row(
        Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            // A hairline keeps the pill legible where the page is nearly as
            // light as the chip itself.
            .border(Dp.Hairline, outline.copy(alpha = 0.55f), CircleShape)
            .clickable { onClick() }
            .padding(start = 12.dp, end = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            query,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // A long query must not claim more than a line of its own.
            modifier = Modifier.widthIn(max = 180.dp),
        )
        Icon(
            Icons.Outlined.Close,
            contentDescription = L.str(context, "Delete"),
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .clickable { onDelete() }
                .padding(6.dp),
            tint = outline,
        )
    }
}

// endregion
