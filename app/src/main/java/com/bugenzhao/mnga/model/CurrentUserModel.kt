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
        scope.launch {
            var first = true
            while (true) {
                delay(2 * 60 * 1000L)
                if (first) {
                    first = false
                    continue
                }
                clockIn()
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

    private suspend fun clockIn() {
        val uid = authStorage.authInfo.value.uid
        if (uid.isEmpty()) return
        val result = logicCallAsync(
            AsyncRequest.newBuilder().setClockIn(ClockInRequest.getDefaultInstance()).build(),
            ClockInResponse.parser(),
        )
        result.onSuccess { response ->
            if (response.isFirstTime) {
                val name = _user.value?.name?.display() ?: "???"
                ToastModel.showAuto(ToastModel.Message.ClockIn("$name @ ${response.date}"))
            }
        }
    }
}
