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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LifecycleResumeEffect
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
import com.bugenzhao.mnga.ui.screens.misc.BlockWordsScreen
import com.bugenzhao.mnga.ui.screens.misc.CacheScreen
import com.bugenzhao.mnga.ui.screens.search.GlobalSearchScreen
import com.bugenzhao.mnga.ui.screens.search.TopicSearchScreen
import com.bugenzhao.mnga.ui.screens.subforums.SubforumListScreen
import com.bugenzhao.mnga.ui.screens.topicdetails.TopicDetailsScreen
import com.bugenzhao.mnga.ui.screens.topiclist.TopicListScreen
import com.bugenzhao.mnga.ui.screens.user.UserProfileScreen
import com.bugenzhao.mnga.ui.theme.MNGATheme
import com.bugenzhao.mnga.model.appScope

/** Root composable: theme, navigation stack and global overlays. */
@Composable
fun MNGARoot(onNewIntent: (android.content.Intent) -> Unit) {
    val prefs = App.prefs
    val themeColor by prefs.themeColorRaw.flow.collectAsState()
    val colorScheme by prefs.colorSchemeRaw.flow.collectAsState()

    MNGATheme(
        themeColor = com.bugenzhao.mnga.storage.ThemeColor.fromRaw(themeColor),
        colorSchemeMode = com.bugenzhao.mnga.storage.ColorSchemeMode.fromRaw(colorScheme),
    ) {
        val navigator = remember { Navigator(listOf(Route.ForumList)) }
        val editor = remember { com.bugenzhao.mnga.ui.editor.EditorController(appScope) }

        NavigationHost(navigator, editor)
        GlobalOverlays(navigator, editor)

        // Clipboard deep-link affordance refresh on resume.
        LifecycleResumeEffect(Unit) {
            App.schemes.refreshPasteboardStatus()
            onPauseOrDispose { }
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
    AnimatedContent(
        targetState = stack.lastOrNull() ?: Route.ForumList,
        transitionSpec = {
            (slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280)))
                .togetherWith(
                    slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut(tween(280))
                )
        },
        label = "nav",
    ) { route ->
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            RouteDispatcher(navigator, route, editor)
        }
    }
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
            )
        is Route.TopicDetails ->
            TopicDetailsScreen(navigator, route)
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

    val showingPrefs by App.prefs.showing.collectAsState()
    if (showingPrefs) {
        com.bugenzhao.mnga.ui.screens.prefs.PreferencesSheet(
            onDismiss = { App.prefs.showing.value = false },
            navigator = navigator,
        )
    }

    val showingPaywall by App.plus.isShowingModal.collectAsState()
    if (showingPaywall) {
        com.bugenzhao.mnga.ui.screens.plus.PlusSheet(onDismiss = { App.plus.dismissPaywall() })
    }

    val showingNotis by App.notis.showingSheet.collectAsState()
    if (showingNotis) {
        com.bugenzhao.mnga.ui.screens.notifications.NotificationListSheet(
            navigator = navigator,
            onDismiss = { App.notis.showingSheet.value = false },
        )
    }

    // "What's New" on version upgrades.
    var showWhatsNew by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        showWhatsNew = com.bugenzhao.mnga.ui.screens.misc.shouldShowWhatsNew()
    }
    if (showWhatsNew) {
        com.bugenzhao.mnga.ui.screens.misc.WhatsNewSheet(onDismiss = {
            com.bugenzhao.mnga.ui.screens.misc.markWhatsNewShown()
            showWhatsNew = false
        })
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
