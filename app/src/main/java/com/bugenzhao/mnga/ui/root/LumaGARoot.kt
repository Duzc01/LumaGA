package com.bugenzhao.mnga.ui.root

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.NavigationIdentifier
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.ui.nav.Route
import com.bugenzhao.mnga.ui.nav.RouteCodec
import com.bugenzhao.mnga.ui.nav.TopicListMode
import com.bugenzhao.mnga.ui.screens.favorites.FavoritesScreen
import com.bugenzhao.mnga.ui.screens.forumlist.ForumListScreen
import com.bugenzhao.mnga.ui.screens.history.HistoryScreen
import com.bugenzhao.mnga.ui.screens.messages.ShortMessageDetailsScreen
import com.bugenzhao.mnga.ui.screens.messages.ShortMessageListScreen
import com.bugenzhao.mnga.ui.screens.misc.AboutScreen
import com.bugenzhao.mnga.ui.screens.misc.BlockWordsScreen
import com.bugenzhao.mnga.ui.screens.misc.CacheScreen
import com.bugenzhao.mnga.ui.screens.search.SearchScreen
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
        val navController = rememberNavController()
        val navigator = remember { Navigator(navController, listOf(Route.ForumList)) }
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
    // 首页（导航栈只有根）双击返回退出：3 秒内按两次退出，第一次提示。
    // sheet/弹窗打开时它们自己的返回处理优先（后注册的 BackHandler 先触发）。
    val context = LocalContext.current
    var backPressedAt by remember { mutableLongStateOf(0L) }
    BackHandler(enabled = navigator.size <= 1) {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - backPressedAt < 3000) {
            (context as? android.app.Activity)?.finish()
        } else {
            backPressedAt = now
            android.widget.Toast.makeText(
                context,
                "再按一次退出LumaGA",
                android.widget.Toast.LENGTH_SHORT,
            ).show()
        }
    }

    // Derive the route stack from the NavController back stack (system back
    // presses included), keeping navigator.stack/size/lastOp in sync.
    LaunchedEffect(navigator) { navigator.observe(this) }

    // 实验室功能「启动自动签到」：任意页面打开/切换时补一次签到检查
    // （Rust 缓存判定当天已签则零网络开销）。配合回前台触发覆盖所有
    // 应用活跃时机。
    LaunchedEffect(navigator.navController) {
        navigator.navController.addOnDestinationChangedListener { _, _, _ ->
            if (App.prefs.clockInEnabled.value &&
                App.prefs.autoClockInOnLaunch.value
            ) {
                App.currentUser.clockInOnce()
            }
        }
    }

    NavHost(
        navController = navigator.navController,
        startDestination = RouteCodec.ROUTE_FORUM_LIST,
        modifier = Modifier.fillMaxSize(),
        // Forward (push): the new page slides in from the right while the old
        // one exits to the left. Backward (pop): mirrored.
        enterTransition = { slideInHorizontally(tween(280)) { it / 3 } + fadeIn(tween(280)) },
        exitTransition = { slideOutHorizontally(tween(280)) { -it / 4 } + fadeOut(tween(280)) },
        popEnterTransition = { slideInHorizontally(tween(280)) { -it / 3 } + fadeIn(tween(280)) },
        popExitTransition = { slideOutHorizontally(tween(280)) { it / 4 } + fadeOut(tween(280)) },
    ) {
        composable(RouteCodec.ROUTE_FORUM_LIST) {
            RouteDispatcher(navigator, Route.ForumList, editor)
        }
        composable(RouteCodec.ROUTE_TOPIC_LIST, arguments = payloadArgument) { entry ->
            val route = remember(entry) { RouteCodec.decode(entry) }
            if (route != null) RouteDispatcher(navigator, route, editor)
        }
        composable(RouteCodec.ROUTE_TOPIC_DETAILS, arguments = payloadArgument) { entry ->
            val route = remember(entry) { RouteCodec.decode(entry) }
            if (route != null) RouteDispatcher(navigator, route, editor)
        }
        composable(RouteCodec.ROUTE_USER_PROFILE, arguments = payloadArgument) { entry ->
            val route = remember(entry) { RouteCodec.decode(entry) }
            if (route != null) RouteDispatcher(navigator, route, editor)
        }
        composable(RouteCodec.ROUTE_GLOBAL_SEARCH) {
            RouteDispatcher(navigator, Route.GlobalSearch, editor)
        }
        composable(RouteCodec.ROUTE_TOPIC_SEARCH, arguments = payloadArgument) { entry ->
            val route = remember(entry) { RouteCodec.decode(entry) }
            if (route != null) RouteDispatcher(navigator, route, editor)
        }
        composable(RouteCodec.ROUTE_HOT_TOPICS) {
            RouteDispatcher(navigator, Route.HotTopics, editor)
        }
        composable(RouteCodec.ROUTE_FAVORITES) {
            RouteDispatcher(navigator, Route.Favorites, editor)
        }
        composable(RouteCodec.ROUTE_HISTORY) {
            RouteDispatcher(navigator, Route.History, editor)
        }
        composable(RouteCodec.ROUTE_SHORT_MESSAGES) {
            RouteDispatcher(navigator, Route.ShortMessages, editor)
        }
        composable(RouteCodec.ROUTE_SHORT_MESSAGE_DETAILS, arguments = payloadArgument) { entry ->
            val route = remember(entry) { RouteCodec.decode(entry) }
            if (route != null) RouteDispatcher(navigator, route, editor)
        }
        composable(RouteCodec.ROUTE_SUBFORUMS) {
            RouteDispatcher(navigator, Route.Subforums, editor)
        }
        composable(RouteCodec.ROUTE_SUBFORUM_LIST, arguments = payloadArgument) { entry ->
            val route = remember(entry) { RouteCodec.decode(entry) }
            if (route != null) RouteDispatcher(navigator, route, editor)
        }
        composable(RouteCodec.ROUTE_UNKNOWN_FORUM, arguments = payloadArgument) { entry ->
            val route = remember(entry) { RouteCodec.decode(entry) }
            if (route != null) RouteDispatcher(navigator, route, editor)
        }
        composable(RouteCodec.ROUTE_CACHE_SETTINGS) {
            RouteDispatcher(navigator, Route.CacheSettings, editor)
        }
        composable(RouteCodec.ROUTE_BLOCK_WORDS) {
            RouteDispatcher(navigator, Route.BlockWords, editor)
        }
        composable(RouteCodec.ROUTE_ABOUT) {
            RouteDispatcher(navigator, Route.About, editor)
        }
        composable(RouteCodec.ROUTE_SETTINGS) {
            RouteDispatcher(navigator, Route.Settings, editor)
        }
        composable(RouteCodec.ROUTE_NOTIFICATIONS) {
            RouteDispatcher(navigator, Route.Notifications, editor)
        }
        composable(RouteCodec.ROUTE_CLOCK_IN) {
            RouteDispatcher(navigator, Route.ClockIn, editor)
        }
    }
}

/** Every argument-carrying route packs its fields into one JSON `payload` path arg. */
private val payloadArgument =
    listOf(navArgument("payload") { type = NavType.StringType })

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
            TopicDetailsScreen(navigator, route, editor = editor)
        is Route.UserProfile ->
            UserProfileScreen(
                navigator,
                userId = route.userId,
                userName = route.userName,
                user = route.user,
            )
        is Route.GlobalSearch -> SearchScreen(navigator)
        is Route.TopicSearch -> SearchScreen(navigator, route.forumId)
        is Route.Favorites -> FavoritesScreen(navigator)
        is Route.History -> HistoryScreen(navigator)
        is Route.ShortMessages -> ShortMessageListScreen(navigator)
        is Route.ShortMessageDetails -> ShortMessageDetailsScreen(navigator, route.id)
        is Route.SubforumList -> SubforumListScreen(navigator, route.forumId)
        is Route.CacheSettings -> CacheScreen(navigator)
        is Route.BlockWords -> BlockWordsScreen(navigator)
        is Route.About -> AboutScreen(navigator)
        is Route.ClockIn -> com.bugenzhao.mnga.ui.screens.user.ClockInScreen(navigator)
        is Route.Settings ->
            com.bugenzhao.mnga.ui.screens.prefs.PreferencesSheet(
                onDismiss = { navigator.pop() },
                navigator = navigator,
            )
        is Route.Notifications ->
            com.bugenzhao.mnga.ui.screens.notifications.NotificationListSheet(
                navigator = navigator,
                onDismiss = { navigator.pop() },
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
