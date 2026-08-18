package com.bugenzhao.mnga.storage

import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import com.bugenzhao.mnga.logicCall
import com.bugenzhao.mnga.protos.datamodel.Device
import com.bugenzhao.mnga.protos.datamodel.RequestOption
import com.bugenzhao.mnga.protos.service.SetRequestOptionRequest
import com.bugenzhao.mnga.protos.service.SyncRequest
import com.bugenzhao.mnga.util.PbJson
import com.bugenzhao.mnga.util.URLs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class TopicListOrder(val raw: Int) {
    LAST_POST(0),
    POST_DATE(1);

    companion object {
        fun fromRaw(raw: Int): TopicListOrder = entries.firstOrNull { it.raw == raw } ?: LAST_POST
    }
}

enum class ThemeColor(val raw: Int, val label: String, val lightColor: Long, val darkColor: Long) {
    LUMAGA(0, "LumaGA", 0xFFC09D73, 0xFFE4BD88),
    RED(1, "Red", 0xFFFF3B30, 0xFFFF453A),
    ORANGE(2, "Orange", 0xFFFF9500, 0xFFFF9F0A),
    YELLOW(3, "Yellow", 0xFFFFCC00, 0xFFFFD60A),
    GREEN(4, "Green", 0xFF34C759, 0xFF30D158),
    MINT(5, "Mint", 0xFF00C7BE, 0xFF63E6E2),
    TEAL(6, "Teal", 0xFF30B0C7, 0xFF40C8E0),
    CYAN(7, "Cyan", 0xFF32ADE6, 0xFF64D2FF),
    BLUE(8, "Blue", 0xFF007AFF, 0xFF0A84FF),
    INDIGO(9, "Indigo", 0xFF5856D6, 0xFF5E5CE6),
    PURPLE(10, "Purple", 0xFFAF52DE, 0xFFBF5AF2),
    PINK(11, "Pink", 0xFFFF2D55, 0xFFFF375F),
    BROWN(12, "Brown", 0xFFA2845E, 0xFFAC8E68),
    GRAY(13, "Gray", 0xFF8E8E93, 0xFF8E8E93);

    companion object {
        fun fromRaw(raw: Int): ThemeColor = entries.firstOrNull { it.raw == raw } ?: LUMAGA
    }
}

enum class ColorSchemeMode(val raw: Int) {
    AUTO(0), LIGHT(1), DARK(2);

    companion object {
        fun fromRaw(raw: Int): ColorSchemeMode = entries.firstOrNull { it.raw == raw } ?: AUTO
    }
}

enum class DateTimeStrategy(val raw: Int, val label: String) {
    AUTOMATIC(0, "Automatic"),
    DETAILED(1, "Detailed"),
    TIME_AGO(2, "Time Ago");

    companion object {
        fun fromRaw(raw: Int): DateTimeStrategy = entries.firstOrNull { it.raw == raw } ?: AUTOMATIC
    }
}

enum class ContentImageScalePref(val raw: String, val label: String, val scale: Float) {
    SMALL("small", "Small", 0.5f),
    MEDIUM("medium", "Medium", 2f / 3f),
    FULL_SIZE("fullSize", "Full Size", 1.0f);

    companion object {
        fun fromRaw(raw: String): ContentImageScalePref =
            entries.firstOrNull { it.raw == raw } ?: FULL_SIZE
    }
}

enum class TopicResumeFrom(val raw: String, val label: String) {
    NONE("none", "None"),
    LAST("last", "Last Viewed"),
    HIGHEST("highest", "Highest Viewed");

    companion object {
        fun fromRaw(raw: String): TopicResumeFrom = entries.firstOrNull { it.raw == raw } ?: NONE
    }
}

enum class WebApiStrategy(val raw: Int, val label: String) {
    DISABLED(0, "Disabled"),
    SECONDARY(1, "Secondary"),
    PRIMARY(2, "Primary"),
    ONLY(3, "Only");

    companion object {
        fun fromRaw(raw: Int): WebApiStrategy = entries.firstOrNull { it.raw == raw } ?: SECONDARY
    }
}

/**
 * A reactive, `SharedPreferences`-backed preference value. Compose code should
 * read through [asState] (or the top-level [pref] helper) to subscribe.
 */
open class Pref<T : Any>(
    private val prefs: SharedPreferences,
    val key: String,
    private val default: T,
    private val getter: (SharedPreferences, String, T) -> T,
    private val setter: (SharedPreferences.Editor, String, T) -> SharedPreferences.Editor,
) {
    private val _flow = MutableStateFlow(default)

    init {
        _flow.value = getter(prefs, key, default)
    }

    val flow: StateFlow<T> get() = _flow

    var value: T
        get() = _flow.value
        set(value) {
            setter(prefs.edit(), key, value).apply()
            _flow.value = value
        }

    fun update(transform: (T) -> T) {
        value = transform(value)
    }
}

/** Collect a [Pref] in a composable. */
@Composable
fun <T : Any> pref(p: Pref<T>): State<T> = p.flow.collectAsState()

/**
 * The app-wide settings store, ported from `Storage/PreferencesStorage.swift`.
 * All keys and defaults are identical to the iOS implementation.
 */
class PreferencesStorage(private val prefs: SharedPreferences) {

    companion object {
        /** Set in [com.bugenzhao.mnga.LumaGAApplication]; null before that. */
        var shared: PreferencesStorage? = null
    }

    private fun boolPref(key: String, default: Boolean) =
        Pref(prefs, key, default,
            { p, k, d -> p.getBoolean(k, d) },
            { e, k, v -> e.putBoolean(k, v) })

    private fun intPref(key: String, default: Int) =
        Pref(prefs, key, default,
            { p, k, d -> p.getInt(k, d) },
            { e, k, v -> e.putInt(k, v) })

    private fun stringPref(key: String, default: String) =
        Pref(prefs, key, default,
            { p, k, d -> p.getString(k, d) ?: d },
            { e, k, v -> e.putString(k, v) })

    val showSignature = boolPref("showSignatureNew", false)
    val showAvatar = boolPref("showAvatar", true)
    val usePaginatedDetails = boolPref("usePaginatedDetails", false)
    val useInAppSafari = boolPref("useInAppSafari", true)
    val topicListHideBlocked = boolPref("topicListHideBlocked", false)
    val topicListShowRefreshButton = boolPref("topicListShowRefreshButton", true)
    val topicListShowForumShortcut = boolPref("topicListShowForumShortcut", true)
    val topicListShowSearchInBottomBar = boolPref("topicListShowSearchInbottomBar", true)
    val topicListSubjectMulticolor = boolPref("topicListSubjectMulticolor", true)
    val hideNotificationToolbarShortcut = boolPref("hideNotificationToolbarShortcut", false)
    val useInsetGroupedModern = boolPref("useInsetGroupedModern", true)
    val alwaysPortraitOnPhone = boolPref("alwaysPortraitOnPhone", false)
    val postRowSwipeActionLeading = boolPref("postRowSwipeActionLeading", false)
    val postRowSwipeVoteFirst = boolPref("postRowSwipeVoteFirst", false)
    val postRowShowUserDetails = boolPref("postRowShowUserDetails", true)
    val postRowShowUserRegDate = boolPref("postRowShowUserRegDate", false)
    val postRowShowAuthorIndicator = boolPref("postRowShowAuthorIndicator", true)
    val postRowLargerFont = boolPref("postRowLargerFont", false)
    val postRowDimImagesInDarkMode = boolPref("postRowDimImagesInDarkMode", false)
    val autoOpenInBrowserWhenBanned = boolPref("autoOpenInBrowserWhenBannedNew", false)
    val alwaysShareImageAsFile = boolPref("alwaysShareImageAsFile", false)
    val debugAlwaysShowNotificationBadge = boolPref("debugAlwaysShowNotificationBadge", false)

    val defaultTopicListOrderRaw = intPref("defaultTopicListOrder", 0)
    val themeColorRaw = intPref("themeColorNew", 0)
    val colorSchemeRaw = intPref("colorScheme", 0)
    val postRowDateTimeStrategyRaw = intPref("postRowDateTimeStrategy", 0)
    val topicDetailsWebApiStrategyRaw = intPref("topicDetailsWebApiStrategyNew", 1)

    val postRowImageScaleRaw = stringPref("postRowImageScale", "fullSize")
    val resumeTopicFromRaw = stringPref("resumeTopicFrom", "none")

    val defaultTopicListOrder: TopicListOrder
        get() = TopicListOrder.fromRaw(defaultTopicListOrderRaw.value)
    val themeColor: ThemeColor
        get() = ThemeColor.fromRaw(themeColorRaw.value)
    val colorScheme: ColorSchemeMode
        get() = ColorSchemeMode.fromRaw(colorSchemeRaw.value)
    val postRowDateTimeStrategy: DateTimeStrategy
        get() = DateTimeStrategy.fromRaw(postRowDateTimeStrategyRaw.value)
    val postRowImageScale: ContentImageScalePref
        get() = ContentImageScalePref.fromRaw(postRowImageScaleRaw.value)
    val resumeTopicFrom: TopicResumeFrom
        get() = TopicResumeFrom.fromRaw(resumeTopicFromRaw.value)
    val topicDetailsWebApiStrategy: WebApiStrategy
        get() = WebApiStrategy.fromRaw(topicDetailsWebApiStrategyRaw.value)

    // region request option

    private val _requestOption = MutableStateFlow(RequestOption.getDefaultInstance())
    val requestOption: StateFlow<RequestOption> = _requestOption

    var requestOptionValue: RequestOption
        get() = _requestOption.value
        set(value) {
            prefs.edit().putString("requestOption", PbJson.toJson(value)).apply()
            _requestOption.value = value
            syncRequestOptionWithLogic()
        }

    private fun loadRequestOption(): RequestOption {
        val stored = prefs.getString("requestOption", null)
        var option =
            if (stored.isNullOrBlank()) RequestOption.getDefaultInstance()
            else PbJson.mergeFromJson(stored, RequestOption.newBuilder()).build()
        // Backend migration, mirroring the iOS behavior.
        if (option.baseUrlV2.isEmpty() ||
            option.baseUrlV2 in listOf("https://nga.178.com", "https://nga.178.com/")
        ) {
            option = option.toBuilder().setBaseUrlV2(URLs.defaultBase).build()
            prefs.edit().putString("requestOption", PbJson.toJson(option)).apply()
        }
        return option
    }

    fun setDevice(device: Device) {
        requestOptionValue = requestOptionValue.toBuilder().setDevice(device).build()
    }

    fun setBaseURL(url: String) {
        requestOptionValue = requestOptionValue.toBuilder().setBaseUrlV2(url).build()
    }

    fun setCustomUA(ua: String) {
        requestOptionValue =
            requestOptionValue.toBuilder()
                .setDevice(Device.CUSTOM)
                .setCustomUa(ua)
                .build()
    }

    private fun syncRequestOptionWithLogic() {
        try {
            logicCall(
                SyncRequest.newBuilder()
                    .setSetRequestOption(
                        SetRequestOptionRequest.newBuilder().setOption(_requestOption.value)
                    )
                    .build()
            )
        } catch (e: Exception) {
            android.util.Log.e("Preferences", "sync request option failed", e)
        }
    }

    // endregion

    /** Presentation flag of the preferences sheet (not persisted). */
    val showing = MutableStateFlow(false)

    init {
        _requestOption.value = loadRequestOption()
        syncRequestOptionWithLogic()
    }
}
