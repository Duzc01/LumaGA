package com.bugenzhao.mnga.model

import com.bugenzhao.mnga.protos.datamodel.User
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.ClockInRequest
import com.bugenzhao.mnga.protos.service.ClockInResponse
import com.bugenzhao.mnga.protos.service.RemoteUserRequest
import com.bugenzhao.mnga.protos.service.RemoteUserResponse
import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.storage.AuthStorage

import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Tracks the currently signed-in user, performs the daily clock-in and toasts
 * on account switches, ported from `Models/CurrentUserModel.swift`.
 */
class CurrentUserModel(
    private val scope: kotlinx.coroutines.CoroutineScope,
    private val authStorage: AuthStorage,
) {

    private val _user = MutableStateFlow<User?>(null)
    val user: StateFlow<User?> = _user

    /** 今日是否已签到（本地记录，供账号菜单显示"已签到"）。 */
    private val _todayClockedIn = MutableStateFlow(false)
    val todayClockedIn: StateFlow<Boolean> = _todayClockedIn

    /** 签到统计：连续/累计天数、金币（拆金/银/铜）、N币。 */
    data class ClockInStats(
        val continuedDays: Int,
        val totalDays: Int,
        val gold: Int,
        val silver: Int,
        val copper: Int,
        val nCoins: Int,
    )

    private val _clockInStats = MutableStateFlow<ClockInStats?>(null)
    val clockInStats: StateFlow<ClockInStats?> = _clockInStats

    private var lastUid: String? = null
    private var clockInJob: Job? = null
    private var switchToastShown = false

    init {
        // 1. Reload on account change.
        scope.launch {
            authStorage.authChanged.collect {
                val uid = authStorage.authInfo.value.uid
                if (uid != lastUid) {
                    lastUid = uid
                    loadData(uid)
                }
            }
        }
        // Initial load.
        lastUid = authStorage.authInfo.value.uid
        scope.launch { loadData(authStorage.authInfo.value.uid) }

        // 2. Periodic clock-in every 2 minutes, skipping the first tick.
        //    仅实验室功能"启动自动签到"开启时运行（每日一次由逻辑层缓存保证）。
        scope.launch {
            var first = true
            while (true) {
                delay(2 * 60 * 1000L)
                if (first) {
                    first = false
                    continue
                }
                if (autoClockInEnabled()) clockIn()
            }
        }

        // 3. Toast on subsequent account switches.
        scope.launch {
            var lastId: String? = null
            _user.collect { user ->
                val id = user?.id
                if (id != null && id.isNotEmpty() && id != lastId) {
                    if (lastId != null) {
                        ToastModel.showAuto(ToastModel.Message.UserSwitch(user.name.display()))
                    }
                    lastId = id
                }
            }
        }
    }

    /** Called by AuthStorage watchers after auth sync (delays 5 s like iOS). */
    fun scheduleClockInAfterAuth() {
        clockInJob?.cancel()
        clockInJob = scope.launch {
            delay(5_000)
            clockIn()
        }
    }

    fun loadData(uid: String) {
        if (uid.isEmpty()) {
            _user.value = null
            return
        }
        if (uid == _user.value?.id) return
        scope.launch {
            val result = logicCallAsync(
                AsyncRequest.newBuilder()
                    .setRemoteUser(RemoteUserRequest.newBuilder().setUserId(uid))
                    .build(),
                RemoteUserResponse.parser(),
            )
            result.onSuccess { response ->
                val user = response.userOrNull ?: return@onSuccess
                _user.value = user
                // Cache the display name into AuthInfo for offline survival.
                val current = authStorage.authInfo.value
                if (current.uid == uid && current.cachedName.isEmpty()) {
                    authStorage.setCurrentAuth(
                        current.toBuilder().setCachedName(user.name.normal).build()
                    )
                }
            }
        }
    }

    /** 实验室功能开关：自动签到 = 启用签到 + 启动自动签到。 */
    private fun autoClockInEnabled(): Boolean =
        com.bugenzhao.mnga.App.prefs.clockInEnabled.value &&
            com.bugenzhao.mnga.App.prefs.autoClockInOnLaunch.value

    /** 账号菜单手动签到入口。 */
    fun clockInOnce() {
        scope.launch { clockIn() }
    }

    /** 刷新"今日已签到"状态（打开账号菜单/签到页时调用，跨天或切号时更新）。
     * 签到日期按账号存储（lastClockInDate_{uid}），未登录或非本人数据一律视为未签。 */
    fun refreshTodayClockIn() {
        val uid = authStorage.authInfo.value.uid
        if (uid.isEmpty()) {
            _todayClockedIn.value = false
            _clockInStats.value = null
            return
        }
        val today = java.text.SimpleDateFormat(
            "yyyy-MM-dd", java.util.Locale.US,
        ).format(java.util.Date())
        val saved = com.bugenzhao.mnga.App.sharedPreferences
            .getString("lastClockInDate_$uid", null)
        _todayClockedIn.value = saved == today
        if (!_todayClockedIn.value) {
            // 今天还没签（或数据属于旧账号）：统计一并清空，避免残留。
            _clockInStats.value = null
        }
    }

    private suspend fun clockIn() {
        val uid = authStorage.authInfo.value.uid
        if (uid.isEmpty()) return
        val result = logicCallAsync(
            AsyncRequest.newBuilder().setClockIn(ClockInRequest.getDefaultInstance()).build(),
            ClockInResponse.parser(),
        )
        result.onSuccess { response ->
            // 无论是否今日首次，都同步本地"已签到"状态——逻辑层缓存判定
            // 今天是否已签（重复点击也不会重复签到），首次成功才弹提示。
            com.bugenzhao.mnga.App.sharedPreferences.edit()
                .putString(
                    "lastClockInDate_$uid",
                    java.text.SimpleDateFormat(
                        "yyyy-MM-dd", java.util.Locale.US,
                    ).format(java.util.Date()),
                )
                .apply()
            refreshTodayClockIn()
            // 同步签到统计（累计/连续天数、金币拆金/银/铜、N币）。
            _clockInStats.value = ClockInStats(
                continuedDays = response.continuedDays,
                totalDays = response.totalDays,
                gold = response.money / 10000,
                silver = (response.money / 100) % 100,
                copper = response.money % 100,
                nCoins = response.moneyN,
            )
            if (response.isFirstTime) {
                val name = _user.value?.name?.display() ?: "???"
                ToastModel.showAuto(ToastModel.Message.ClockIn("$name @ ${response.date}"))
            }
        }
    }
}
