package com.bugenzhao.mnga.ui.screens.favorites

import androidx.activity.compose.BackHandler

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Create
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bugenzhao.mnga.logicCallAsync
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.model.PlusFeature
import com.bugenzhao.mnga.model.PlusModel
import com.bugenzhao.mnga.protos.datamodel.FavoriteTopicFolder
import com.bugenzhao.mnga.protos.datamodel.Topic
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.FavoriteFolderModifyRequest
import com.bugenzhao.mnga.protos.service.TopicFavorRequest
import com.bugenzhao.mnga.protos.service.TopicFavorResponse
import com.bugenzhao.mnga.ui.components.AdaptiveFooter
import com.bugenzhao.mnga.ui.components.ErrorPlaceholder
import com.bugenzhao.mnga.ui.components.ListPlaceholder
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.ui.nav.Route
import com.bugenzhao.mnga.ui.screens.topiclist.TopicRow
import com.bugenzhao.mnga.util.Haptics
import com.bugenzhao.mnga.util.L
import kotlinx.coroutines.launch

/** The default folder forced first, mirroring `sortedFolders`. */
private fun List<FavoriteTopicFolder>.sortedFolders(): List<FavoriteTopicFolder> =
    sortedByDescending { it.isDefault }

/**
 * Favorite topics grouped by folder, a port of `FavoriteTopicListView`: a
 * paged topic list for the selected folder, folder management menu
 * (rename/delete/make default/create/switch) and swipe-to-delete rows.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(navigator: Navigator, initialFolderId: String? = null) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = navigator.size > 1) { navigator.pop() }

    val favoritesVM: FavoritesViewModel = viewModel()
    val foldersModel = favoritesVM.foldersModel
    val folders by foldersModel.folders.collectAsState()
    var currentFolder by remember { mutableStateOf<FavoriteTopicFolder?>(null) }

    // Load folders on entry, keeping the previous selection when possible.
    // The ViewModel (and the loaded folder list) survives pop-backs, so only
    // a first entry or process death triggers a fetch.
    LaunchedEffect(Unit) {
        if (currentFolder == null && foldersModel.folders.value.isEmpty()) {
            foldersModel.load(force = true)
        }
    }
    LaunchedEffect(folders) {
        if (folders.isNotEmpty()) {
            val restored = folders.firstOrNull { it.id == currentFolder?.id }
                ?: favoritesVM.currentFolderId?.let { id -> folders.firstOrNull { it.id == id } }
                ?: folders.firstOrNull { it.id == initialFolderId }
                ?: folders.firstOrNull { it.isDefault }
                ?: folders.first()
            if (restored.id != currentFolder?.id) {
                currentFolder = restored
                favoritesVM.currentFolderId = restored.id
            }
        } else if (currentFolder != null && folders.none { it.id == currentFolder?.id }) {
            currentFolder = null
        }
    }

    var folderMenuExpanded by remember { mutableStateOf(false) }
    var showingRename by remember { mutableStateOf(false) }
    var showingCreate by remember { mutableStateOf(false) }
    var showingDeleteConfirmation by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    fun modifyCurrent(build: (FavoriteFolderModifyRequest.Builder) -> Unit) {
        val folder = currentFolder ?: return
        val builder = FavoriteFolderModifyRequest.newBuilder().setFolderId(folder.id)
        build(builder)
        scope.launch {
            val ok = foldersModel.modify(builder.build())
            if (ok) Haptics.play(view, Haptics.NotificationType.SUCCESS)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            L.str(context, "Favorite Topics"),
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            currentFolder?.name ?: L.str(context, "Default Folder"),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { folderMenuExpanded = true }) {
                        Icon(
                            if (currentFolder?.isDefault == true) {
                                Icons.Outlined.FolderOpen
                            } else {
                                Icons.Outlined.Folder
                            },
                            contentDescription = L.str(context, "Folder"),
                        )
                    }
                    FolderMenu(
                        expanded = folderMenuExpanded,
                        onDismiss = { folderMenuExpanded = false },
                        folders = folders.sortedFolders(),
                        currentFolder = currentFolder,
                        onSwitchFolder = { folder ->
                            if (folder.id != currentFolder?.id &&
                                PlusModel.checkPlus(PlusFeature.MULTI_FAVORITE)
                            ) {
                                currentFolder = folder
                                favoritesVM.currentFolderId = folder.id
                            }
                        },
                        onMakeDefault = { modifyCurrent { it.setSetDefault(true) } },
                        onRename = {
                            newName = currentFolder?.name.orEmpty()
                            showingRename = true
                        },
                        onDelete = { showingDeleteConfirmation = true },
                        onCreate = {
                            newName = ""
                            showingCreate = true
                        },
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val folder = currentFolder
            if (folder == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                FavoriteTopicList(folder = folder, navigator = navigator)
            }
        }
    }

    // Rename dialog.
    if (showingRename) {
        AlertDialog(
            onDismissRequest = { showingRename = false },
            title = { Text(L.str(context, "Rename Folder")) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text(L.str(context, "Folder Name")) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    modifyCurrent { it.setRename(newName) }
                    showingRename = false
                }) { Text(L.str(context, "Done")) }
            },
            dismissButton = {
                TextButton(onClick = { showingRename = false }) {
                    Text(L.str(context, "Cancel"))
                }
            },
        )
    }
    // Create-folder dialog.
    if (showingCreate) {
        AlertDialog(
            onDismissRequest = { showingCreate = false },
            title = { Text(L.str(context, "New Folder")) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    placeholder = { Text(L.str(context, "Folder Name")) },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = newName.trim()
                    showingCreate = false
                    if (name.isNotEmpty()) {
                        scope.launch {
                            val id = foldersModel.create(name)
                            if (id != null) {
                                Haptics.play(view, Haptics.NotificationType.SUCCESS)
                            }
                        }
                    }
                }) { Text(L.str(context, "Done")) }
            },
            dismissButton = {
                TextButton(onClick = { showingCreate = false }) {
                    Text(L.str(context, "Cancel"))
                }
            },
        )
    }
    // Delete confirmation.
    if (showingDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showingDeleteConfirmation = false },
            title = { Text(L.str(context, "Delete the folder and all its topics?")) },
            confirmButton = {
                TextButton(onClick = {
                    modifyCurrent { it.setDelete(true) }
                    showingDeleteConfirmation = false
                }) { Text(L.str(context, "Delete")) }
            },
            dismissButton = {
                TextButton(onClick = { showingDeleteConfirmation = false }) {
                    Text(L.str(context, "Cancel"))
                }
            },
        )
    }
}

/** The folder picker + management dropdown (SS15). */
@Composable
private fun FolderMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    folders: List<FavoriteTopicFolder>,
    currentFolder: FavoriteTopicFolder?,
    onSwitchFolder: (FavoriteTopicFolder) -> Unit,
    onMakeDefault: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onCreate: () -> Unit,
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (currentFolder != null) {
            DropdownMenuItem(
                text = {
                    Column {
                        Text("#${currentFolder.id} ${currentFolder.name}")
                        if (currentFolder.isDefault) {
                            Text(
                                L.str(context, "Default"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                onClick = {},
                enabled = false,
            )
            if (!currentFolder.isDefault) {
                DropdownMenuItem(
                    text = { Text(L.str(context, "Make Default")) },
                    leadingIcon = { Icon(Icons.Outlined.FolderOpen, contentDescription = null) },
                    onClick = {
                        onMakeDefault()
                        onDismiss()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(L.str(context, "Rename")) },
                leadingIcon = { Icon(Icons.Outlined.Create, contentDescription = null) },
                onClick = {
                    onRename()
                    onDismiss()
                },
            )
            DropdownMenuItem(
                text = { Text(L.str(context, "Delete")) },
                leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                onClick = {
                    onDelete()
                    onDismiss()
                },
            )
        }
        // Folder switcher.
        folders.forEach { folder ->
            DropdownMenuItem(
                text = {
                    Column {
                        Text(folder.name)
                        if (folder.isDefault) {
                            Text(
                                L.str(context, "Default Folder"),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                trailingIcon = {
                    if (folder.id == currentFolder?.id) {
                        Icon(Icons.Outlined.Check, contentDescription = null)
                    }
                },
                onClick = { onSwitchFolder(folder) },
            )
        }
        DropdownMenuItem(
            text = { Text(L.str(context, "New Folder")) },
            leadingIcon = { Icon(Icons.Outlined.Add, contentDescription = null) },
            onClick = {
                onCreate()
                onDismiss()
            },
        )
    }
}

/**
 * The paged favorite-topic list of one folder with swipe-to-delete (SS15).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FavoriteTopicList(folder: FavoriteTopicFolder, navigator: Navigator) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    // The per-folder paged list lives in the entry-scoped ViewModel: it
    // survives pop-backs (composition is disposed, ViewModel is not), so
    // returning here does not refetch.
    val favoritesVM: FavoritesViewModel = viewModel()
    val dataSource = favoritesVM.topicDataSource(folder.id)
    val state by dataSource.state.collectAsState()

    // Load on first entry only: after a pop-back the ViewModel still holds
    // the data, so notLoaded is false and nothing is refetched.
    LaunchedEffect(folder.id) {
        if (dataSource.notLoaded) dataSource.initialLoad()
    }

    // Rows hidden after a successful swipe-delete.
    val hiddenIds = remember(folder.id) { mutableStateListOf<String>() }
    val visibleItems = state.items.filter { it.id !in hiddenIds }

    val listState = rememberLazyListState()
    LaunchedEffect(listState, visibleItems.size) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= visibleItems.size - 3
        }.collect { nearEnd ->
            if (nearEnd && visibleItems.isNotEmpty()) {
                dataSource.loadMoreIfNeeded(visibleItems.size - 1)
            }
        }
    }

    fun deleteFavorite(topic: Topic, boxState: SwipeToDismissBoxState) {
        scope.launch {
            val result = logicCallAsync(
                AsyncRequest.newBuilder()
                    .setTopicFavor(
                        TopicFavorRequest.newBuilder()
                            .setFolderId(folder.id)
                            .setTopicId(topic.id)
                            .setOperation(TopicFavorRequest.Operation.DELETE)
                            .build()
                    )
                    .build(),
                TopicFavorResponse.parser(),
            )
            result.onSuccess { response ->
                Haptics.play(view, Haptics.NotificationType.SUCCESS)
                if (!response.isFavored) {
                    hiddenIds.add(topic.id)
                } else {
                    // Still favored in another folder: snap the row back.
                    boxState.snapTo(SwipeToDismissBoxValue.Settled)
                }
            }.onFailure {
                boxState.snapTo(SwipeToDismissBoxValue.Settled)
            }
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { dataSource.refreshAsync(sleepMillis = 500) },
        modifier = Modifier.fillMaxSize(),
    ) {
        when {
            dataSource.isInitialLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            visibleItems.isEmpty() && state.latestError != null ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ErrorPlaceholder(state.latestError!!) { dataSource.refresh() }
                }
            visibleItems.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    ListPlaceholder(L.str(context, "No Favorites"))
                }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(visibleItems, key = { _, topic -> topic.id }) { _, topic ->
                    val boxState = rememberSwipeToDismissBoxState()
                    LaunchedEffect(boxState.currentValue) {
                        if (boxState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                            deleteFavorite(topic, boxState)
                        }
                    }
                    SwipeToDismissBox(
                        state = boxState,
                        backgroundContent = {
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Row(
                                    Modifier.fillMaxSize().padding(horizontal = 20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = L.str(context, "Delete"),
                                        tint = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        },
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true,
                    ) {
                        TopicRow(
                            topic = topic,
                            dimmedSubject = false,
                            showIndicators = false,
                            onClick = {
                                navigator.push(
                                    Route.TopicDetails(
                                        topicId = topic.id,
                                        fav = topic.fav.takeIf { it.isNotEmpty() },
                                    )
                                )
                            },
                        )
                    }
                }
                item(key = "footer") {
                    AdaptiveFooter(loading = state.isLoading, noMore = !dataSource.hasMore)
                }
            }
        }
    }
}
