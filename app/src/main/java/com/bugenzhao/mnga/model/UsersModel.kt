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

    /** Returns only the in-memory value and never performs a bridge call. */
    fun cachedUser(id: String): User? = users[id]

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

private const val ANONY_PREFIX = "#anony_"
private const val ANONY_PART_A = "甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉戌亥"
private const val ANONY_PART_B = "王李张刘陈杨黄吴赵周徐孙马朱胡林郭何高罗郑梁谢宋唐许邓冯韩曹曾彭萧蔡潘田董袁于余叶蒋杜苏魏程吕丁沈任姚卢傅钟姜崔谭廖范汪陆金石戴贾韦夏邱方侯邹熊孟秦白江阎薛尹段雷黎史龙陶贺顾毛郝龚邵万钱严赖覃洪武莫孔汤向常温康施文牛樊葛邢安齐易乔伍庞颜倪庄聂章鲁岳翟殷詹申欧耿关兰焦俞左柳甘祝包宁尚符舒阮柯纪梅童凌毕单季裴霍涂成苗谷盛曲翁冉骆蓝路游辛靳管柴蒙鲍华喻祁蒲房滕屈饶解牟艾尤阳时穆农司卓古吉缪简车项连芦麦褚娄窦戚岑景党宫费卜冷晏席卫米柏宗瞿桂全佟应臧闵苟邬边卞姬师和仇栾隋商刁沙荣巫寇桑郎甄丛仲虞敖巩明佘池查麻苑迟邝"

/**
 * Display name for an NGA anonymous raw name (`#anony_` + 32 hex chars):
 * 6 CJK chars derived from the hex code. Returns null for anything else.
 * Pure-Kotlin mirror of Rust `extract_user_name` in `rust/logic/service/src/user.rs`.
 */
fun anonymousDisplayName(raw: String): String? {
    if (!raw.startsWith(ANONY_PREFIX)) return null
    val code = raw.removePrefix(ANONY_PREFIX)
    if (code.toByteArray().size != 32) return null
    val anony = StringBuilder()
    var i = 0
    for (j in 0 until 6) {
        val single = j == 0 || j == 3
        val hex = if (single) code.substring(i, i + 1) else code.substring(i - 1, i + 1)
        val table = if (single) ANONY_PART_A else ANONY_PART_B
        val p = hex.toIntOrNull(16)?.coerceIn(0, table.toByteArray().size - 1) ?: return null
        anony.append(table.getOrNull(p) ?: return null)
        i += 2
    }
    return anony.toString()
}
