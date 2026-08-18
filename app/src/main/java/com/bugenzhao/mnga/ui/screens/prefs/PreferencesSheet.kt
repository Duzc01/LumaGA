package com.bugenzhao.mnga.ui.screens.prefs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FrontHand
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.BuildConfig
import com.bugenzhao.mnga.protos.datamodel.Device
import com.bugenzhao.mnga.storage.ColorSchemeMode
import com.bugenzhao.mnga.storage.ContentImageScalePref
import com.bugenzhao.mnga.storage.DateTimeStrategy
import com.bugenzhao.mnga.storage.ThemeColor
import com.bugenzhao.mnga.storage.TopicListOrder
import com.bugenzhao.mnga.storage.TopicResumeFrom
import com.bugenzhao.mnga.storage.WebApiStrategy
import com.bugenzhao.mnga.ui.components.GroupedList
import com.bugenzhao.mnga.ui.components.GroupedRow
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.ui.nav.Route
import com.bugenzhao.mnga.util.L
import kotlinx.coroutines.launch

/** The mock topic id that backs the about & acknowledgements page. */
private const val ABOUT_TOPIC_ID = "mnga_about_feedback"

/** Which picker dialog is currently presented. */
private enum class PickerKind {
    COLOR_SCHEME,
    THEME_COLOR,
    LIST_STYLE,
    ORDER,
    HIDE_BLOCKED,
    SEARCH_BAR,
    WEB_API,
    RESUME,
    DATE_TIME,
    IMAGE_SCALE,
    SWIPE_EDGE,
    SWIPE_ACTION,
    DEVICE,
}

private data class PickerOption<T>(
    val value: T,
    val label: String,
    val icon: ImageVector? = null,
    val iconTint: Color? = null,
    val dot: Color? = null,
)

/**
 * The full-screen settings sheet, ported from `Views/PreferencesView.swift`.
 * Every preference of `PreferencesStorage` is wired to its `Pref` here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreferencesSheet(onDismiss: () -> Unit, navigator: Navigator? = null) {
    val context = LocalContext.current
    val prefs = App.prefs
    val scope = rememberCoroutineScope()

    val useRemote by App.favoriteForums.useRemote.flow.collectAsState()

    val colorSchemeRaw by prefs.colorSchemeRaw.flow.collectAsState()
    val themeColorRaw by prefs.themeColorRaw.flow.collectAsState()
    val alwaysPortrait by prefs.alwaysPortraitOnPhone.flow.collectAsState()
    val useInAppSafari by prefs.useInAppSafari.flow.collectAsState()
    val hideNotificationShortcut by prefs.hideNotificationToolbarShortcut.flow.collectAsState()
    val useInsetGroupedModern by prefs.useInsetGroupedModern.flow.collectAsState()
    val alwaysShareImageAsFile by prefs.alwaysShareImageAsFile.flow.collectAsState()

    val defaultOrderRaw by prefs.defaultTopicListOrderRaw.flow.collectAsState()
    val hideBlocked by prefs.topicListHideBlocked.flow.collectAsState()
    val showRefreshButton by prefs.topicListShowRefreshButton.flow.collectAsState()
    val showForumShortcut by prefs.topicListShowForumShortcut.flow.collectAsState()
    val searchInBottomBar by prefs.topicListShowSearchInBottomBar.flow.collectAsState()
    val subjectMulticolor by prefs.topicListSubjectMulticolor.flow.collectAsState()

    val usePaginatedDetails by prefs.usePaginatedDetails.flow.collectAsState()
    val webApiStrategyRaw by prefs.topicDetailsWebApiStrategyRaw.flow.collectAsState()
    val resumeFromRaw by prefs.resumeTopicFromRaw.flow.collectAsState()
    val autoOpenInBrowserWhenBanned by prefs.autoOpenInBrowserWhenBanned.flow.collectAsState()

    val swipeLeading by prefs.postRowSwipeActionLeading.flow.collectAsState()
    val swipeVoteFirst by prefs.postRowSwipeVoteFirst.flow.collectAsState()
    val dateTimeStrategyRaw by prefs.postRowDateTimeStrategyRaw.flow.collectAsState()
    val showSignature by prefs.showSignature.flow.collectAsState()
    val showAvatar by prefs.showAvatar.flow.collectAsState()
    val showAuthorIndicator by prefs.postRowShowAuthorIndicator.flow.collectAsState()
    val showUserDetails by prefs.postRowShowUserDetails.flow.collectAsState()
    val showUserRegDate by prefs.postRowShowUserRegDate.flow.collectAsState()
    val largerFont by prefs.postRowLargerFont.flow.collectAsState()
    val imageScaleRaw by prefs.postRowImageScaleRaw.flow.collectAsState()
    val dimImages by prefs.postRowDimImagesInDarkMode.flow.collectAsState()

    val debugBadge by prefs.debugAlwaysShowNotificationBadge.flow.collectAsState()

    val requestOption by prefs.requestOption.collectAsState()
    val device = requestOption.device


    var picker by remember { mutableStateOf<PickerKind?>(null) }
    var baseURLText by remember(requestOption.baseUrlV2) {
        mutableStateOf(requestOption.baseUrlV2)
    }
    var customUAText by remember(requestOption.customUa) {
        mutableStateOf(requestOption.customUa)
    }

    fun dismiss() {
        prefs.showing.value = false
        onDismiss()
    }

    fun pushAboutTopic() {
        navigator?.push(Route.TopicDetails(topicId = ABOUT_TOPIC_ID))
        dismiss()
    }

    val themeColor = ThemeColor.fromRaw(themeColorRaw)
    val colorScheme = ColorSchemeMode.fromRaw(colorSchemeRaw)
    val defaultOrder = TopicListOrder.fromRaw(defaultOrderRaw)
    val webApiStrategy = WebApiStrategy.fromRaw(webApiStrategyRaw)
    val resumeFrom = TopicResumeFrom.fromRaw(resumeFromRaw)
    val dateTimeStrategy = DateTimeStrategy.fromRaw(dateTimeStrategyRaw)
    val imageScale = ContentImageScalePref.fromRaw(imageScaleRaw)

    Dialog(
        onDismissRequest = { dismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text(L.str(context, "Settings")) },
                    actions = {
                        TextButton(onClick = { dismiss() }) {
                            Icon(
                                Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(L.str(context, "Done"))
                        }
                    },
                )
                LazyColumn(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    // region General
                    item(key = "general") {
                        Section(header = L.str(context, "General")) {
                            PickerRow(
                                icon = Icons.Filled.Contrast,
                                title = L.str(context, "Color Scheme"),
                                valueLabel = colorSchemeLabel(context, colorScheme),
                                onClick = { picker = PickerKind.COLOR_SCHEME },
                            )
                            PickerRow(
                                icon = Icons.Filled.Palette,
                                title = L.str(context, "Theme Color"),
                                valueLabel = L.str(context, themeColor.label),
                                dot = themeColorDot(themeColor),
                                onClick = { picker = PickerKind.THEME_COLOR },
                            )
                            SwitchRow(
                                icon = Icons.Filled.PhoneIphone,
                                title = L.str(context, "Lock Screen Rotation"),
                                checked = alwaysPortrait,
                                onChange = { prefs.alwaysPortraitOnPhone.value = it },
                            )
                            SwitchRow(
                                icon = Icons.Filled.Public,
                                title = L.str(context, "Always Use In-App Safari"),
                                checked = useInAppSafari,
                                onChange = { prefs.useInAppSafari.value = it },
                            )
                            NavigationRow(
                                icon = Icons.Outlined.FrontHand,
                                title = L.str(context, "Block Contents"),
                                onClick = {
                                    navigator?.push(Route.BlockWords)
                                    dismiss()
                                },
                            )
                            SwitchRow(
                                icon = Icons.Filled.NotificationsOff,
                                title = L.str(context, "Hide Notification Shortcut"),
                                checked = hideNotificationShortcut,
                                onChange = { prefs.hideNotificationToolbarShortcut.value = it },
                            )
                            NavigationRow(
                                icon = Icons.Filled.CleaningServices,
                                title = L.str(context, "Cache Management"),
                                onClick = {
                                    navigator?.push(Route.CacheSettings)
                                    dismiss()
                                },
                            )
                            SwitchRow(
                                icon = Icons.Filled.CloudQueue,
                                title = L.str(context, "Sync Favorites"),
                                checked = useRemote,
                                onChange = { next ->
                                    App.favoriteForums.useRemote.value = next
                                    scope.launch { App.favoriteForums.sync() }
                                },
                            )
                        }
                    }
                    // endregion

                    // region Topic List
                    item(key = "topic-list") {
                        Section(header = L.str(context, "Topic List")) {
                            PickerRow(
                                icon = Icons.Filled.Refresh,
                                title = L.str(context, "Default Order"),
                                valueLabel = orderLabel(context, defaultOrder),
                                onClick = { picker = PickerKind.ORDER },
                            )
                            PickerRow(
                                icon = Icons.Filled.VisibilityOff,
                                title = L.str(context, "Blocked Topics Style"),
                                valueLabel = if (hideBlocked) {
                                    L.str(context, "Hide Topic")
                                } else {
                                    L.str(context, "Redact Subject")
                                },
                                onClick = { picker = PickerKind.HIDE_BLOCKED },
                            )
                            SwitchRow(
                                icon = Icons.Filled.Refresh,
                                title = L.str(context, "Refresh Button"),
                                checked = showRefreshButton,
                                onChange = { prefs.topicListShowRefreshButton.value = it },
                            )
                            SwitchRow(
                                icon = Icons.Filled.Link,
                                title = L.str(context, "Show Forum Shortcuts"),
                                checked = showForumShortcut,
                                onChange = { prefs.topicListShowForumShortcut.value = it },
                            )
                            PickerRow(
                                icon = Icons.Filled.Search,
                                title = L.str(context, "Search Bar Position"),
                                valueLabel = if (searchInBottomBar) {
                                    L.str(context, "Bottom")
                                } else {
                                    L.str(context, "Top")
                                },
                                onClick = { picker = PickerKind.SEARCH_BAR },
                            )
                            SwitchRow(
                                icon = Icons.Filled.Palette,
                                title = L.str(context, "Multicolor Subject"),
                                checked = subjectMulticolor,
                                onChange = { prefs.topicListSubjectMulticolor.value = it },
                            )
                        }
                    }
                    // endregion

                    // region Topic Details
                    item(key = "topic-details") {
                        Section(
                            header = L.str(context, "Topic Details"),
                            footer = L.str(context, "Web API Explained"),
                        ) {
                            SwitchRow(
                                icon = Icons.Filled.Layers,
                                title = L.str(context, "Paginated Reading"),
                                checked = usePaginatedDetails,
                                onChange = { prefs.usePaginatedDetails.value = it },
                            )
                            PickerRow(
                                icon = Icons.Filled.Lan,
                                title = L.str(context, "Web API"),
                                valueLabel = webApiStrategyLabel(context, webApiStrategy),
                                onClick = { picker = PickerKind.WEB_API },
                            )
                            PickerRow(
                                icon = Icons.Filled.RestartAlt,
                                title = L.str(context, "Resume Reading Progress"),
                                valueLabel = resumeFromLabel(context, resumeFrom),
                                onClick = { picker = PickerKind.RESUME },
                            )
                            SwitchRow(
                                icon = Icons.Filled.OpenInBrowser,
                                title = L.str(context, "Auto Open in Browser when Banned"),
                                checked = autoOpenInBrowserWhenBanned,
                                onChange = { prefs.autoOpenInBrowserWhenBanned.value = it },
                            )
                        }
                    }
                    // endregion

                    // region Post Row
                    item(key = "post-row") {
                        Section(header = L.str(context, "Post Row")) {
                            PickerRow(
                                icon = Icons.Filled.Swipe,
                                title = L.str(context, "Swipe Trigger Edge"),
                                valueLabel = if (swipeLeading) {
                                    L.str(context, "Leading")
                                } else {
                                    L.str(context, "Trailing")
                                },
                                onClick = { picker = PickerKind.SWIPE_EDGE },
                            )
                            PickerRow(
                                icon = Icons.Filled.ThumbUp,
                                title = L.str(context, "Primary Swipe Action"),
                                valueLabel = if (swipeVoteFirst) {
                                    L.str(context, "Vote Up")
                                } else {
                                    L.str(context, "Quote")
                                },
                                onClick = { picker = PickerKind.SWIPE_ACTION },
                            )
                            PickerRow(
                                icon = Icons.Filled.Event,
                                title = L.str(context, "Date Display"),
                                valueLabel = dateTimeStrategyLabel(context, dateTimeStrategy),
                                onClick = { picker = PickerKind.DATE_TIME },
                            )
                            SwitchRow(
                                icon = Icons.Outlined.Edit,
                                title = L.str(context, "Show Signature"),
                                checked = showSignature,
                                onChange = { prefs.showSignature.value = it },
                            )
                            SwitchRow(
                                icon = Icons.Outlined.AccountCircle,
                                title = L.str(context, "Show Avatar"),
                                checked = showAvatar,
                                onChange = { prefs.showAvatar.value = it },
                            )
                            SwitchRow(
                                icon = Icons.Filled.Person,
                                title = L.str(context, "Show Author Indicator"),
                                checked = showAuthorIndicator,
                                onChange = { prefs.postRowShowAuthorIndicator.value = it },
                            )
                            SwitchRow(
                                icon = Icons.Filled.Info,
                                title = L.str(context, "Show User Details"),
                                checked = showUserDetails,
                                onChange = { prefs.postRowShowUserDetails.value = it },
                            )
                            if (showUserDetails) {
                                SwitchRow(
                                    icon = Icons.Filled.CalendarMonth,
                                    title = L.str(context, "Show User Register Date"),
                                    checked = showUserRegDate,
                                    onChange = { prefs.postRowShowUserRegDate.value = it },
                                )
                            }
                            SwitchRow(
                                icon = Icons.Filled.TextFields,
                                title = L.str(context, "Larger Font"),
                                checked = largerFont,
                                onChange = { prefs.postRowLargerFont.value = it },
                            )
                            PickerRow(
                                icon = Icons.Filled.Photo,
                                title = L.str(context, "Image Scale"),
                                valueLabel = L.str(context, imageScale.label),
                                onClick = { picker = PickerKind.IMAGE_SCALE },
                            )
                            SwitchRow(
                                icon = Icons.Filled.DarkMode,
                                title = L.str(context, "Dim Images in Dark Mode"),
                                checked = dimImages,
                                onChange = { prefs.postRowDimImagesInDarkMode.value = it },
                            )
                        }
                    }
                    // endregion

                    // region Appearance
                    item(key = "appearance") {
                        Section(header = L.str(context, "Appearance")) {
                            PickerRow(
                                icon = Icons.Filled.ListAlt,
                                title = L.str(context, "List Style"),
                                valueLabel = if (useInsetGroupedModern) {
                                    L.str(context, "Modern")
                                } else {
                                    L.str(context, "Compact")
                                },
                                onClick = { picker = PickerKind.LIST_STYLE },
                            )
                            SwitchRow(
                                icon = Icons.Filled.Share,
                                title = L.str(context, "Always Share Image as File"),
                                checked = alwaysShareImageAsFile,
                                onChange = { prefs.alwaysShareImageAsFile.value = it },
                            )
                        }
                    }
                    // endregion

                    // region Backend
                    item(key = "backend") {
                        Section(header = L.str(context, "Backend")) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                OutlinedTextField(
                                    value = baseURLText,
                                    onValueChange = { baseURLText = it },
                                    label = { Text(L.str(context, "LumaGA Backend")) },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                TextButton(
                                    onClick = { prefs.setBaseURL(baseURLText.trim()) },
                                    enabled = baseURLText.trim() != requestOption.baseUrlV2,
                                ) { Text(L.str(context, "Apply")) }
                            }
                            PickerRow(
                                icon = Icons.Filled.Devices,
                                title = L.str(context, "Device Identity"),
                                valueLabel = deviceLabel(context, device),
                                onClick = { picker = PickerKind.DEVICE },
                            )
                            if (device == Device.CUSTOM) {
                                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                    OutlinedTextField(
                                        value = customUAText,
                                        onValueChange = { customUAText = it },
                                        label = { Text(L.str(context, "Custom User-Agent")) },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    TextButton(
                                        onClick = { prefs.setCustomUA(customUAText.trim()) },
                                        enabled = customUAText.trim() != requestOption.customUa,
                                    ) { Text(L.str(context, "Apply")) }
                                }
                            }
                        }
                    }
                    // endregion

                    // region Debug
                    item(key = "debug") {
                        Section(header = L.str(context, "Debug")) {
                            SwitchRow(
                                icon = Icons.Filled.Notifications,
                                title = L.str(context, "Always Show Notification Badge"),
                                checked = debugBadge,
                                onChange = { prefs.debugAlwaysShowNotificationBadge.value = it },
                            )
                        }
                    }
                    // endregion

                    // region About
                    item(key = "about") {
                        Section(
                            header = "LumaGA ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        ) {
                            NavigationRow(
                                icon = Icons.Filled.AutoAwesome,
                                title = L.str(context, "About & Feedback"),
                                onClick = { pushAboutTopic() },
                            )
                            NavigationRow(
                                icon = Icons.Filled.Check,
                                title = L.str(context, "Acknowledgements"),
                                onClick = { pushAboutTopic() },
                            )
                        }
                    }
                    // endregion
                }
            }
        }
    }

    // region picker dialogs
    val currentPicker = picker
    when (currentPicker) {
        PickerKind.COLOR_SCHEME ->
            PickerDialog(
                title = L.str(context, "Color Scheme"),
                options = ColorSchemeMode.entries.map {
                    PickerOption(it, colorSchemeLabel(context, it))
                },
                selected = colorScheme,
                onSelect = { prefs.colorSchemeRaw.value = it.raw },
                onDismiss = { picker = null },
            )
        PickerKind.THEME_COLOR ->
            PickerDialog(
                title = L.str(context, "Theme Color"),
                options = ThemeColor.entries.map {
                    PickerOption(it, L.str(context, it.label), dot = themeColorDot(it))
                },
                selected = themeColor,
                onSelect = { prefs.themeColorRaw.value = it.raw },
                onDismiss = { picker = null },
            )
        PickerKind.LIST_STYLE ->
            PickerDialog(
                title = L.str(context, "List Style"),
                options = listOf(
                    PickerOption(false, L.str(context, "Compact")),
                    PickerOption(true, L.str(context, "Modern")),
                ),
                selected = useInsetGroupedModern,
                onSelect = { prefs.useInsetGroupedModern.value = it },
                onDismiss = { picker = null },
            )
        PickerKind.ORDER ->
            PickerDialog(
                title = L.str(context, "Default Order"),
                options = TopicListOrder.entries.map { PickerOption(it, orderLabel(context, it)) },
                selected = defaultOrder,
                onSelect = { prefs.defaultTopicListOrderRaw.value = it.raw },
                onDismiss = { picker = null },
            )
        PickerKind.HIDE_BLOCKED ->
            PickerDialog(
                title = L.str(context, "Blocked Topics Style"),
                options = listOf(
                    PickerOption(false, L.str(context, "Redact Subject")),
                    PickerOption(true, L.str(context, "Hide Topic")),
                ),
                selected = hideBlocked,
                onSelect = { prefs.topicListHideBlocked.value = it },
                onDismiss = { picker = null },
            )
        PickerKind.SEARCH_BAR ->
            PickerDialog(
                title = L.str(context, "Search Bar Position"),
                options = listOf(
                    PickerOption(false, L.str(context, "Top")),
                    PickerOption(true, L.str(context, "Bottom")),
                ),
                selected = searchInBottomBar,
                onSelect = { prefs.topicListShowSearchInBottomBar.value = it },
                onDismiss = { picker = null },
            )
        PickerKind.WEB_API ->
            PickerDialog(
                title = L.str(context, "Web API"),
                options = WebApiStrategy.entries.map {
                    PickerOption(
                        it,
                        webApiStrategyLabel(context, it),
                        icon = webApiStrategyIcon(it),
                        iconTint = if (it == WebApiStrategy.ONLY) {
                            MaterialTheme.colorScheme.error
                        } else {
                            null
                        },
                    )
                },
                selected = webApiStrategy,
                onSelect = { prefs.topicDetailsWebApiStrategyRaw.value = it.raw },
                onDismiss = { picker = null },
            )
        PickerKind.RESUME ->
            PickerDialog(
                title = L.str(context, "Resume Reading Progress"),
                options = TopicResumeFrom.entries.map { PickerOption(it, resumeFromLabel(context, it)) },
                selected = resumeFrom,
                onSelect = { prefs.resumeTopicFromRaw.value = it.raw },
                onDismiss = { picker = null },
            )
        PickerKind.DATE_TIME ->
            PickerDialog(
                title = L.str(context, "Date Display"),
                options = DateTimeStrategy.entries.map {
                    PickerOption(it, dateTimeStrategyLabel(context, it))
                },
                selected = dateTimeStrategy,
                onSelect = { prefs.postRowDateTimeStrategyRaw.value = it.raw },
                onDismiss = { picker = null },
            )
        PickerKind.IMAGE_SCALE ->
            PickerDialog(
                title = L.str(context, "Image Scale"),
                options = ContentImageScalePref.entries.map {
                    PickerOption(it, L.str(context, it.label))
                },
                selected = imageScale,
                onSelect = { prefs.postRowImageScaleRaw.value = it.raw },
                onDismiss = { picker = null },
            )
        PickerKind.SWIPE_EDGE ->
            PickerDialog(
                title = L.str(context, "Swipe Trigger Edge"),
                options = listOf(
                    PickerOption(true, L.str(context, "Leading")),
                    PickerOption(false, L.str(context, "Trailing")),
                ),
                selected = swipeLeading,
                onSelect = { prefs.postRowSwipeActionLeading.value = it },
                onDismiss = { picker = null },
            )
        PickerKind.SWIPE_ACTION ->
            PickerDialog(
                title = L.str(context, "Primary Swipe Action"),
                options = listOf(
                    PickerOption(true, L.str(context, "Vote Up")),
                    PickerOption(false, L.str(context, "Quote")),
                ),
                selected = swipeVoteFirst,
                onSelect = { prefs.postRowSwipeVoteFirst.value = it },
                onDismiss = { picker = null },
            )
        PickerKind.DEVICE ->
            PickerDialog(
                title = L.str(context, "Device Identity"),
                options = Device.entries.map { PickerOption(it, deviceLabel(context, it)) },
                selected = device,
                onSelect = { prefs.setDevice(it) },
                onDismiss = { picker = null },
            )
        null -> {}
    }
    // endregion
}

// region row components

@Composable
private fun Section(
    header: String? = null,
    footer: String? = null,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        if (header != null) {
            Text(
                header,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                // Left edge aligned with the grouped card below (the list's
                // 16dp content padding applies to both).
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        GroupedList { Column { content() } }
        if (footer != null) {
            Text(
                footer,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    GroupedRow(
        leading = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        title = title,
        trailing = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun PickerRow(
    icon: ImageVector,
    title: String,
    valueLabel: String,
    onClick: () -> Unit,
    dot: Color? = null,
) {
    GroupedRow(
        onClick = onClick,
        leading = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        title = title,
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (dot != null) {
                    Box(Modifier.size(14.dp).background(dot, CircleShape))
                }
                Text(
                    valueLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}

@Composable
private fun NavigationRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
) {
    GroupedRow(
        onClick = onClick,
        leading = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        title = title,
        trailing = {
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        },
    )
}

@Composable
private fun <T> PickerDialog(
    title: String,
    options: List<PickerOption<T>>,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                onSelect(option.value)
                                onDismiss()
                            }
                            .padding(vertical = 10.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        RadioButton(
                            selected = option.value == selected,
                            onClick = null,
                        )
                        if (option.dot != null) {
                            Box(Modifier.size(16.dp).background(option.dot, CircleShape))
                        }
                        option.icon?.let { icon ->
                            Icon(
                                icon,
                                contentDescription = null,
                                tint = option.iconTint ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(option.label)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(L.str(context, "Cancel")) }
        },
    )
}

// endregion

// region label helpers

private fun colorSchemeLabel(context: android.content.Context, mode: ColorSchemeMode): String =
    when (mode) {
        ColorSchemeMode.AUTO -> L.str(context, "Auto")
        ColorSchemeMode.LIGHT -> L.str(context, "Light")
        ColorSchemeMode.DARK -> L.str(context, "Dark")
    }

private fun orderLabel(context: android.content.Context, order: TopicListOrder): String =
    when (order) {
        TopicListOrder.LAST_POST -> L.str(context, "Latest Replies")
        TopicListOrder.POST_DATE -> L.str(context, "Latest Topics")
    }

private fun resumeFromLabel(context: android.content.Context, from: TopicResumeFrom): String =
    when (from) {
        TopicResumeFrom.NONE -> L.str(context, "Disabled")
        TopicResumeFrom.LAST -> L.str(context, "Last Read Floor")
        TopicResumeFrom.HIGHEST -> L.str(context, "Highest Read Floor")
    }

private fun dateTimeStrategyLabel(
    context: android.content.Context,
    strategy: DateTimeStrategy,
): String =
    when (strategy) {
        DateTimeStrategy.AUTOMATIC -> L.str(context, "Auto")
        DateTimeStrategy.DETAILED -> L.str(context, "Detailed")
        DateTimeStrategy.TIME_AGO -> L.str(context, "Time Ago")
    }

private fun webApiStrategyLabel(context: android.content.Context, strategy: WebApiStrategy): String =
    when (strategy) {
        WebApiStrategy.DISABLED -> L.str(context, "Disabled")
        WebApiStrategy.SECONDARY -> L.str(context, "Secondary")
        WebApiStrategy.PRIMARY -> L.str(context, "Primary")
        WebApiStrategy.ONLY -> L.str(context, "Only Web API")
    }

private fun webApiStrategyIcon(strategy: WebApiStrategy): ImageVector = when (strategy) {
    WebApiStrategy.SECONDARY, WebApiStrategy.PRIMARY -> Icons.Filled.Science
    WebApiStrategy.ONLY -> Icons.Filled.Warning
    WebApiStrategy.DISABLED -> Icons.Filled.Lan
}

private fun deviceLabel(context: android.content.Context, device: Device): String =
    when (device) {
        Device.APPLE -> L.str(context, "iOS")
        Device.ANDROID -> L.str(context, "Android")
        Device.DESKTOP -> L.str(context, "Desktop")
        Device.WINDOWS_PHONE -> L.str(context, "Windows Phone")
        Device.CUSTOM -> L.str(context, "Custom")
        else -> L.str(context, "Unknown")
    }

@Composable
private fun themeColorDot(color: ThemeColor): Color {
    val dark = isSystemInDarkTheme()
    return Color(if (dark) color.darkColor else color.lightColor)
}

// endregion
