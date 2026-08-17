package com.bugenzhao.mnga.model

import com.bugenzhao.mnga.logicCall
import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.protos.datamodel.PostContent
import com.bugenzhao.mnga.protos.datamodel.User
import com.bugenzhao.mnga.protos.datamodel.UserName
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.LocalUserRequest
import com.bugenzhao.mnga.protos.service.LocalUserResponse
import com.bugenzhao.mnga.protos.service.RemoteUserRequest
import com.bugenzhao.mnga.protos.service.RemoteUserResponse
import com.bugenzhao.mnga.protos.service.SyncRequest
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-wide user cache, ported from `Models/UsersModel.swift`. Local
 * (cached/anonymous) lookups go through the sync bridge; remote lookups use
 * the async one with an in-memory cache.
 */
class UsersModel {

    companion object {
        var shared: UsersModel? = null

        const val dummyID = "dummy"

        val dummy: User by lazy {
            User.newBuilder()
                .setId(dummyID)
                .setName(UserName.newBuilder().setNormal("Dummy User"))
                .setFame(25)
                .setPostNum(2333)
                .setRegDate(1609502400L)
                .setSignature(
                    PostContent.newBuilder().setRaw("This is a signature.")
                )
                .setAvatarUrl("https://img.nga.cn/avatars/2002/03a/000/000/58_0.jpg")
                .build()
        }
    }

    // Deliberately not a StateFlow — views pull imperatively, like the iOS
    // original (publishing hurt performance).
    private val users = ConcurrentHashMap<String, User>()

    init {
        add(dummy)
    }

    fun add(user: User) {
        users[user.id] = user
    }

    fun localUser(id: String): User? {
        users[id]?.let { return it }
        return try {
            val response = logicCall(
                SyncRequest.newBuilder()
                    .setLocalUser(LocalUserRequest.newBuilder().setUserId(id))
                    .build(),
                LocalUserResponse.parser(),
            )
            val user = response.user
            if (user.id.isNotEmpty()) {
                add(user)
                user
            } else null
        } catch (e: Exception) {
            null
        }
    }

    suspend fun remoteUser(id: String, showError: Boolean = true, ignoreCache: Boolean = false): User? =
        remoteUser(
            RemoteUserRequest.newBuilder().setUserId(id).build(),
            showError = showError,
            ignoreCache = ignoreCache,
        )

    suspend fun remoteUser(
        req: RemoteUserRequest,
        showError: Boolean = true,
        ignoreCache: Boolean = false,
    ): User? {
        if (!ignoreCache && req.userId.isNotEmpty()) {
            users[req.userId]?.let { cached ->
                if (cached.remote) return cached
            }
        }
        val result = logicCallAsync(
            AsyncRequest.newBuilder().setRemoteUser(req).build(),
            RemoteUserResponse.parser(),
        )
        return result.fold(
            onSuccess = { response ->
                val user = response.userOrNull ?: return@fold null
                if (user.id.isNotEmpty()) add(user)
                user
            },
            onFailure = { e ->
                if (showError) {
                    ToastModel.showAuto(ToastModel.Message.Error(e.message ?: "error"))
                }
                null
            },
        )
    }
}

/** Remote user response `user` is `optional` — bridge to nullable. */
val RemoteUserResponse.userOrNull: User?
    get() = if (hasUser()) user else null

/** Display name: anonymous representation when present, else the normal name. */
fun com.bugenzhao.mnga.protos.datamodel.UserName.display(): String =
    if (anonymous.isNotEmpty()) anonymous else normal
