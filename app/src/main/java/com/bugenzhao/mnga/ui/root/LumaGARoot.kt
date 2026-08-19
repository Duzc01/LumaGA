package com.bugenzhao.mnga.ui.root

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.NavigationIdentifier
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.ui.nav.Route
import com.bugenzhao.mnga.ui.nav.TopicListMode
import com.bugenzhao.mnga.ui.screens.favorites.FavoritesScreen
import com.bugenzhao.mnga.ui.screens.forumlist.ForumListScreen
import com.bugenzhao.mnga.ui.screens.history.HistoryScreen
import com.bugenzhao.mnga.ui.screens.messages.ShortMessageDetailsScreen
import com.bugenzhao.mnga.ui.screens.messages.ShortMessageListScreen
import com.bugenzhao.mnga.ui.screens.misc.AboutScreen
import com.bugenzhao.mnga.ui.screens.misc.BlockWordsScreen
import com.bugenzhao.mnga.ui.screens.misc.CacheScreen
import com.bugenzhao.mnga.ui.screens.search.GlobalSearchScreen
import com.bugenzhao.mnga.ui.screens.search.TopicSearchScreen
import com.bugenzhao.mnga.ui.screens.subforums.SubforumListScreen
import com.bugenzhao.mnga.ui.screens.topicdetails.TopicDetailsScreen
import com.bugenzhao.mnga.ui.screens.topiclist.TopicListScreen
import com.bugenzhao.mnga.ui.screens.user.UserProfileScreen
import com.bugenzhao.mnga.ui.theme.LumaGATheme
import com.bugenzhao.mnga.model.appScope
import kotlinx.coroutines.flow.filter

/** Root composable: theme, navigation stack and global overlays. */
@Composable
fun LumaGARoot(onNewIntent: (android.content.Intent) -> Unit) {
    val prefs = App.prefs
    val themeColor by prefs.themeColorRaw.flow.collectAsState()
    val colorScheme by prefs.colorSchemeRaw.flow.collectAsState()

    LumaGATheme(
        themeColor = com.bugenzhao.mnga.storage.ThemeColor.fromRaw(themeColor),
        colorSchemeMode = com.bugenzhao.mnga.storage.ColorSchemeMode.fromRaw(colorScheme),
    ) {
        val navigator = remember { Navigator(listOf(Route.ForumList)) }
        val editor = remember { com.bugenzhao.mnga.ui.editor.EditorController(appScope) }

        // Opaque theme background under the navigation stack: during the
        // push/pop fade+slide transitions both pages are partially transparent,
        // and without a solid layer beneath them the (always light) window
        // background flashes white in dark mode.
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            NavigationHost(navigator, editor)
        }
        GlobalOverlays(navigator, editor)

        // Pasteboard deep-link handling: when the newest clipboard entry is a
        // navigable NGA/LumaGA link that has not been jumped to yet, navigate
        // there directly. Android 12+ only lets a *focused* app read the
        // clipboard, and on a cold start onResume fires before the window
        // focus arrives (the focus-change callback itself can still race the
        // system's focus check), so the resume check is deferred until the
        // focus has settled.
        val view = androidx.compose.ui.platform.LocalView.current
        DisposableEffect(view) {
            val listener = android.view.ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                if (hasFocus) view.post { maybeNavigateToPasteboardLink() }
            }
            view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
            onDispose {
                view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
            }
        }
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        LaunchedEffect(lifecycleOwner) {
            lifecycleOwner.lifecycle.currentStateFlow
                .filter { it == androidx.lifecycle.Lifecycle.State.RESUMED }
                .collect {
                    App.schemes.refreshPasteboardStatus()
                    // Wait for the window focus (typically well under 350ms)
                    // before touching the clipboard, otherwise the read is
                    // denied on Android 12+.
                    kotlinx.coroutines.delay(350)
                    maybeNavigateToPasteboardLink()
                }
        }
    }
}

@Composable
private fun NavigationHost(
    navigator: Navigator,
    editor: com.bugenzhao.mnga.ui.editor.EditorController?,
) {
    val stack by navigator.stack.collectAsState()
    BackHandler(enabled = navigator.size > 1) { navigator.pop() }
    // Keeps each route's rememberSaveable state (list data, scroll position)
    // alive while the route is off-screen, so popping back to a screen
    // restores it instead of rebuilding and refetching.
    val saveableStateHolder = rememberSaveableStateHolder()
    AnimatedContent(
        targetState = stack.lastOrNull() ?: Route.ForumList,
        transitionSpec = {
            // Forward (push): the new page slides in from the right while the
            // old one exits to the left. Backward (pop): mirrored — the new
            // page slides in from the left and the old one exits to the right.
            val forward =
                stack.indexOf(initialState) != -1 &&
                    stack.indexOf(targetState) > stack.indexOf(initialState)
            if (forward) {
                (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280)))
                    .togetherWith(
                        slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut(tween(280))
                    )
            } else {
                (slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(280)))
                    .togetherWith(
                        slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(280))
                    )
            }
        },
        label = "nav",
    ) { route ->
        saveableStateHolder.SaveableStateProvider(routeKey(route)) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                RouteDispatcher(navigator, route, editor)
            }
        }
    }
}

/** Stable per-route key for [androidx.compose.runtime.saveable.rememberSaveableStateHolder]. */
private fun routeKey(route: Route): String = when (route) {
    Route.ForumList -> "forum-list"
    is Route.TopicList ->
        "topic-list-${if (route.forumId.hasFid()) "f${route.forumId.fid}" else "st${route.forumId.stid}"}-${route.mode}"
    is Route.TopicDetails ->
        "topic-details-${route.topicId}-${route.postId ?: ""}${if (route.anonymousAuthorOnly) "-anon" else ""}-${route.authorId ?: ""}${if (route.localCache) "-cache" else ""}"
    is Route.UserProfile -> "user-profile-${route.userId ?: route.userName ?: "?"}"
    Route.GlobalSearch -> "global-search"
    is Route.TopicSearch ->
        "topic-search-" + (route.forumId?.let { if (it.hasFid()) "f${it.fid}" else "st${it.stid}" } ?: "all")
    Route.HotTopics -> "hot-topics"
    Route.Favorites -> "favorites"
    Route.History -> "history"
    Route.ShortMessages -> "short-messages"
    is Route.ShortMessageDetails -> "short-message-details-${route.id}"
    Route.Subforums -> "subforums"
    is Route.SubforumList ->
        "subforum-list-${if (route.forumId.hasFid()) "f${route.forumId.fid}" else "st${route.forumId.stid}"}"
    is Route.UnknownForum -> "unknown-forum"
    Route.CacheSettings -> "cache-settings"
    Route.BlockWords -> "block-words"
    Route.About -> "about"
    Route.Settings -> "settings"
}

/** Maps a route to its screen. */
@Composable
fun RouteDispatcher(
    navigator: Navigator,
    route: Route,
    editor: com.bugenzhao.mnga.ui.editor.EditorController? = null,
) {
    when (route) {
        is Route.ForumList ->
            ForumListScreen(navigator, onShowUserMenu = { com.bugenzhao.mnga.ui.root.showUserMenuBus.value = true })
        is Route.TopicList ->
            TopicListScreen(
                navigator,
                forumId = route.forumId,
                mode = TopicListMode.NORMAL.takeIf { route.mode == TopicListMode.NORMAL }
                    ?: route.mode,
                dateRange = route.dateRange,
                editor = editor,
                route = route,
            )
        is Route.TopicDetails ->
            TopicDetailsScreen(navigator, route, editor = editor)
        is Route.UserProfile ->
            UserProfileScreen(
                navigator,
                userId = route.userId,
                userName = route.userName,
                user = route.user,
            )
        is Route.GlobalSearch -> GlobalSearchScreen(navigator)
        is Route.TopicSearch -> TopicSearchScreen(navigator, route.forumId)
        is Route.Favorites -> FavoritesScreen(navigator)
        is Route.History -> HistoryScreen(navigator)
        is Route.ShortMessages -> ShortMessageListScreen(navigator)
        is Route.ShortMessageDetails -> ShortMessageDetailsScreen(navigator, route.id)
        is Route.SubforumList -> SubforumListScreen(navigator, route.forumId)
        is Route.CacheSettings -> CacheScreen(navigator)
        is Route.BlockWords -> BlockWordsScreen(navigator)
        is Route.About -> AboutScreen(navigator)
        is Route.Settings ->
            com.bugenzhao.mnga.ui.screens.prefs.PreferencesSheet(
                onDismiss = { navigator.pop() },
                navigator = navigator,
            )
        else -> RoutePlaceholderScreen(navigator, route)
    }
}

/** Simple event bus for the user-menu trigger (forum list toolbar). */
val showUserMenuBus = mutableStateOf(false)

@Composable
private fun RoutePlaceholderScreen(navigator: Navigator, route: Route) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = route.javaClass.simpleName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

/** Toast hosts, global sheets and the deep-link destination overlay. */
@Composable
fun GlobalOverlays(
    navigator: Navigator,
    editor: com.bugenzhao.mnga.ui.editor.EditorController? = null,
) {
    com.bugenzhao.mnga.ui.components.toast.ToastHost()
    DeepLinkDestination(navigator)
    InAppBrowserOverlay()
    GlobalSheets(navigator, editor)
}

/** Sheets presented from anywhere, mirroring `GlobalSheetsModifier`. */
@Composable
private fun GlobalSheets(
    navigator: Navigator,
    editor: com.bugenzhao.mnga.ui.editor.EditorController? = null,
) {
    val context = androidx.compose.ui.platform.LocalContext.current

    // Editor sheets, mirroring the global `GlobalSheetsModifier`.
    if (editor != null) {
        val showPostEditor by editor.postReply.showEditor.collectAsState()
        if (showPostEditor) {
            com.bugenzhao.mnga.ui.editor.PostEditorSheet(editor.postReply) {
                editor.postReply.editorDismissed()
            }
        }
        val showSmEditor by editor.shortMessage.showEditor.collectAsState()
        if (showSmEditor) {
            com.bugenzhao.mnga.ui.editor.ShortMessageEditorSheet(editor.shortMessage) {
                editor.shortMessage.editorDismissed()
            }
        }
    }

    if (showUserMenuBus.value) {
        com.bugenzhao.mnga.ui.screens.user.UserMenuSheet(
            navigator = navigator,
            onDismiss = { showUserMenuBus.value = false },
            onShowLogin = { App.authStorage.setIsSigning(true) },
        )
    }

    val isSigning by App.authStorage.isSigning.collectAsState()
    if (isSigning) {
        com.bugenzhao.mnga.ui.screens.login.LoginSheet(onDismiss = {
            App.authStorage.setIsSigning(false)
        })
    }

    val showingNotis by App.notis.showingSheet.collectAsState()
    if (showingNotis) {
        com.bugenzhao.mnga.ui.screens.notifications.NotificationListSheet(
            navigator = navigator,
            onDismiss = { App.notis.showingSheet.value = false },
        )
    }
}

/** Presents the current deep-link destination as a fresh stack entry. */
@Composable
private fun DeepLinkDestination(navigator: Navigator) {
    val navID by App.schemes.navID.collectAsState()
    val id = navID ?: return
    LaunchedEffect(id) {
        when (id) {
            is NavigationIdentifier.TopicID ->
                navigator.push(Route.TopicDetails(topicId = id.tid, fav = id.fav))
            is NavigationIdentifier.PostID ->
                navigator.push(Route.TopicDetails(topicId = "", postId = id.pid))
            is NavigationIdentifier.ForumID ->
                navigator.push(Route.TopicList(forumId = id.id))
            is NavigationIdentifier.UserID ->
                navigator.push(Route.UserProfile(userId = id.uid))
            is NavigationIdentifier.UserNameID ->
                navigator.push(Route.UserProfile(userName = id.name))
        }
        App.schemes.dismiss()
    }
}

/** In-app browser presentation for external links. */
@Composable
private fun InAppBrowserOverlay() {
    val url by App.openURL.inAppURL.collectAsState()
    val current = url ?: return
    com.bugenzhao.mnga.ui.components.InAppBrowserSheet(uri = current) {
        App.openURL.dismissInApp()
    }
}

// -- Pasteboard deep-link auto-jump -------------------------------------------
//
// On resume the app checks the newest clipboard entry; if it is a navigable
// NGA/LumaGA link that has not been jumped to yet, it navigates there and
// records the link as "jumped" so the same link is never auto-jumped again.

private const val JumpedPasteboardLinksKey = "jumpedPasteboardLinks"

private fun jumpedPasteboardLinks(): Set<String> =
    App.sharedPreferences.getStringSet(JumpedPasteboardLinksKey, emptySet()) ?: emptySet()

private fun recordJumpedPasteboardLink(link: String) {
    val set = jumpedPasteboardLinks().toMutableSet()
    set.add(link)
    App.sharedPreferences.edit().putStringSet(JumpedPasteboardLinksKey, set).apply()
}

private fun maybeNavigateToPasteboardLink() {
    val link = App.schemes.pasteboardLink() ?: return
    if (link in jumpedPasteboardLinks()) return
    // Only record the link once a jump actually happened (an invalid
    // clipboard entry is reported by navigateToPasteboardURL and returns
    // false, leaving the link eligible for the next resume).
    if (App.schemes.navigateToPasteboardURL()) {
        recordJumpedPasteboardLink(link)
    }
}
