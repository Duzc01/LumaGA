package com.bugenzhao.mnga.storage

import android.content.SharedPreferences
import com.bugenzhao.mnga.logicCall
import com.bugenzhao.mnga.protos.datamodel.AuthInfo
import com.bugenzhao.mnga.protos.service.AuthRequest
import com.bugenzhao.mnga.protos.service.AuthResponse
import com.bugenzhao.mnga.protos.service.SyncRequest
import com.bugenzhao.mnga.util.PbJson
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the current logged-in account and the set of all known accounts,
 * ported from `Storage/AuthStorage.swift`. Auth changes are pushed into the
 * Rust logic layer so subsequent requests carry the token.
 */
class AuthStorage(private val prefs: SharedPreferences) {

    companion object {
        var shared: AuthStorage? = null
    }

    private val _isSigning = MutableStateFlow(false)
    val isSigning: StateFlow<Boolean> = _isSigning

    private val _authInfo = MutableStateFlow(AuthInfo.getDefaultInstance())
    val authInfo: StateFlow<AuthInfo> = _authInfo

    private val _allAuthInfos = MutableStateFlow<List<AuthInfo>>(emptyList())
    val allAuthInfos: StateFlow<List<AuthInfo>> = _allAuthInfos

    /** Fires after every auth mutation; consumers reload dependent state. */
    val authChanged = MutableSharedFlow<Unit>(extraBufferCapacity = 8)

    val signedIn: Boolean
        get() = _authInfo.value.token.isNotEmpty()

    init {
        loadAuthInfo()
        loadAllAuthInfos()
        if (signedIn && _allAuthInfos.value.isEmpty()) {
            // Backward compatibility with single-account installs.
            _allAuthInfos.value = listOf(_authInfo.value)
            persistAllAuthInfos()
        }
        syncAuthWithLogic()
    }

    private fun loadAuthInfo() {
        val stored = prefs.getString("authInfo", null) ?: return
        _authInfo.value = PbJson.mergeFromJson(stored, AuthInfo.newBuilder()).build()
    }

    private fun loadAllAuthInfos() {
        _allAuthInfos.value =
            PbJson.listFromJson(prefs.getString("allAuthInfos", null)) { AuthInfo.newBuilder() }
    }

    private fun persistAuthInfo() {
        prefs.edit().putString("authInfo", PbJson.toJson(_authInfo.value)).apply()
    }

    private fun persistAllAuthInfos() {
        prefs.edit()
            .putString("allAuthInfos", PbJson.listToJson(_allAuthInfos.value))
            .apply()
    }

    private fun syncAuthWithLogic() {
        try {
            logicCall(
                SyncRequest.newBuilder()
                    .setAuth(AuthRequest.newBuilder().setInfo(_authInfo.value))
                    .build(),
                AuthResponse.parser(),
            )
        } catch (e: Exception) {
            android.util.Log.e("AuthStorage", "sync auth with logic failed", e)
        }
    }

    /** Set the current account, deduplicating the known-account set by uid. */
    fun setCurrentAuth(info: AuthInfo) {
        _allAuthInfos.value = _allAuthInfos.value.filterNot { it.uid == info.uid } + info
        persistAllAuthInfos()
        _authInfo.value = info
        persistAuthInfo()
        _isSigning.value = false
        syncAuthWithLogic()
        authChanged.tryEmit(Unit)
    }

    /** Sign out; fall back to another known account when one exists. */
    fun clearCurrentAuth() {
        val currentUid = _authInfo.value.uid
        _allAuthInfos.value = _allAuthInfos.value.filterNot { it.uid == currentUid }
        persistAllAuthInfos()
        val fallback = _allAuthInfos.value.firstOrNull() ?: AuthInfo.getDefaultInstance()
        _authInfo.value = fallback
        persistAuthInfo()
        syncAuthWithLogic()
        authChanged.tryEmit(Unit)
    }

    fun setIsSigning(value: Boolean) {
        _isSigning.value = value
    }
}
