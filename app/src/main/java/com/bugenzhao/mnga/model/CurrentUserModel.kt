package com.bugenzhao.mnga.model

import com.bugenzhao.mnga.protos.datamodel.User
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.ClockInRequest
import com.bugenzhao.mnga.protos.service.ClockInResponse
import com.bugenzhao.mnga.protos.service.ClockInStatsRequest
import com.bugenzhao.mnga.protos.service.ClockInStatsResponse
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

        // 2. Toast on subsequent account switches.
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

    /** 延迟 [delayMillis] 后签到——自动路径（回前台/页面切换/登录）统一
     * 延迟 5 秒，等界面稳定；手动按钮用 [clockInOnce] 立即签到。 */
    fun scheduleClockIn(delayMillis: Long = 5_000) {
        clockInJob?.cancel()
        clockInJob = scope.launch {
            delay(delayMillis)
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

    /** 账号菜单手动签到入口。 */
    fun clockInOnce() {
        scope.launch { clockIn() }
    }

    /** 刷新"今日已签到"状态（打开账号菜单/签到页时调用）。判断依据是
     * 服务器返回的 last_time（上次签到时间戳）：与今天是同一天即已签，
     * 跨设备一致。本地只缓存最近一次查询结果用于快速显示。 */
    fun refreshTodayClockIn() {
        val uid = authStorage.authInfo.value.uid
        if (uid.isEmpty()) {
            _todayClockedIn.value = false
            _clockInStats.value = null
            return
        }
        val saved = com.bugenzhao.mnga.App.sharedPreferences
            .getLong("lastClockInTime_$uid", 0L)
        _todayClockedIn.value = isTodayTimestamp(saved)
        if (!_todayClockedIn.value) {
            _clockInStats.value = null
        }
    }

    private fun isTodayTimestamp(timestamp: Long): Boolean {
        if (timestamp <= 0) return false
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = timestamp * 1000 }
        val now = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.YEAR) == now.get(java.util.Calendar.YEAR) &&
            cal.get(java.util.Calendar.DAY_OF_YEAR) == now.get(java.util.Calendar.DAY_OF_YEAR)
    }

    /** 只查询签到统计与"今日已签"状态（服务器权威，不触发签到）。 */
    fun queryClockInStats() {
        scope.launch {
            val uid = authStorage.authInfo.value.uid
            if (uid.isEmpty()) return@launch
            val result = logicCallAsync(
                AsyncRequest.newBuilder()
                    .setClockInStats(ClockInStatsRequest.getDefaultInstance())
                    .build(),
                ClockInStatsResponse.parser(),
            )
            result.onSuccess { response ->
                // 服务器权威的 last_time（上次签到时间戳）→ "今日已签"判断。
                if (response.lastTime > 0) {
                    com.bugenzhao.mnga.App.sharedPreferences.edit()
                        .putLong("lastClockInTime_$uid", response.lastTime)
                        .apply()
                }
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
            }
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
