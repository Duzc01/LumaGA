package com.bugenzhao.mnga.model

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.util.Constants
import com.bugenzhao.mnga.util.URLs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** Destinations a deep link can navigate to. */
sealed class NavigationIdentifier {
    data class TopicID(val tid: String, val fav: String?) : NavigationIdentifier()
    data class PostID(val pid: String) : NavigationIdentifier()
    data class ForumID(val id: ForumId) : NavigationIdentifier()
    data class UserID(val uid: String) : NavigationIdentifier()
    data class UserNameID(val name: String) : NavigationIdentifier()

    val isMNGAMockID: Boolean
        get() = when (this) {
            is TopicID -> tid.startsWith("mnga_")
            is PostID -> pid.startsWith("mnga_")
            is ForumID -> (if (id.hasFid()) id.fid else id.stid).startsWith("mnga_")
            is UserID -> uid.startsWith("mnga_")
            is UserNameID -> name.startsWith("mnga_")
        }

    /** The `mnga://` deep link; null for user names (not supported). */
    val mngaURL: String?
        get() = when (this) {
            is TopicID ->
                Constants.MNGA.topicBase + tid + (fav?.let { "?fav=$it" } ?: "")
            is PostID -> Constants.MNGA.postBase + pid
            is ForumID ->
                if (id.hasStid()) Constants.MNGA.forumSTBase + id.stid
                else Constants.MNGA.forumFBase + id.fid
            is UserID -> Constants.MNGA.userBase + uid
            is UserNameID -> null
        }

    /** The NGA web equivalent; null for mock ids. */
    val webpageURL: String?
        get() {
            if (isMNGAMockID) return null
            val base = URLs.base
            return when (this) {
                is TopicID -> base + "read.php?tid=" + tid + (fav?.let { "&fav=$it" } ?: "")
                is PostID -> base + "read.php?pid=" + pid
                is ForumID ->
                    if (id.hasStid()) base + "thread.php?stid=" + id.stid
                    else base + "thread.php?fid=" + id.fid
                is UserID -> base + "nuke.php?func=ucp&uid=" + uid
                is UserNameID -> base + "nuke.php?func=ucp&username=" + name
            }
        }

    companion object {
        /** Parse a `mnga://` or NGA web link; null when not navigable. */
        fun parse(url: Uri): NavigationIdentifier? {
            fun firstQuery(name: String): String? = runCatching { url.getQueryParameter(name) }.getOrNull()

            if (url.scheme == Constants.MNGA.scheme) {
                val path = url.path?.trimStart('/') ?: return null
                val full = url.toString()
                return when {
                    full.startsWith(Constants.MNGA.topicBase) ->
                        NavigationIdentifier.TopicID(path, firstQuery("fav"))
                    full.startsWith(Constants.MNGA.postBase) ->
                        NavigationIdentifier.PostID(path)
                    full.startsWith(Constants.MNGA.forumFBase) ->
                        NavigationIdentifier.ForumID(
                            ForumId.newBuilder().setFid(path).build()
                        )
                    full.startsWith(Constants.MNGA.forumSTBase) ->
                        NavigationIdentifier.ForumID(
                            ForumId.newBuilder().setStid(path).build()
                        )
                    full.startsWith(Constants.MNGA.userBase) ->
                        NavigationIdentifier.UserID(path)
                    else -> null
                }
            }

            if (url.scheme in listOf("http", "https") &&
                url.host?.lowercase() in URLs.hosts.map { it.lowercase() }
            ) {
                val tid = firstQuery("tid")
                val pid = firstQuery("pid")
                val stid = firstQuery("stid")
                val fid = firstQuery("fid")
                val uid = firstQuery("uid")
                val username = firstQuery("username")
                return when (url.path) {
                    "/read.php" -> when {
                        !tid.isNullOrEmpty() -> NavigationIdentifier.TopicID(tid, firstQuery("fav"))
                        !pid.isNullOrEmpty() -> NavigationIdentifier.PostID(pid)
                        else -> null
                    }
                    "/thread.php" -> when {
                        !stid.isNullOrEmpty() -> NavigationIdentifier.ForumID(
                            ForumId.newBuilder().setStid(stid).build()
                        )
                        !fid.isNullOrEmpty() -> NavigationIdentifier.ForumID(
                            ForumId.newBuilder().setFid(fid).build()
                        )
                        else -> null
                    }
                    "/nuke.php" -> when {
                        !uid.isNullOrEmpty() -> NavigationIdentifier.UserID(uid)
                        !username.isNullOrEmpty() -> NavigationIdentifier.UserNameID(username)
                        else -> null
                    }
                    else -> null
                }
            }
            return null
        }
    }
}

/**
 * Deep-link navigation, ported from `Models/SchemesModel.swift`.
 */
class SchemesModel(
    private val scope: CoroutineScope,
    private val clipboard: ClipboardManager?,
) {

    private val _navID = MutableStateFlow<NavigationIdentifier?>(null)
    val navID: StateFlow<NavigationIdentifier?> = _navID

    private val _canTryNavigateToPasteboardURL = MutableStateFlow(false)
    val canTryNavigateToPasteboardURL: StateFlow<Boolean> = _canTryNavigateToPasteboardURL

    /** Refresh clipboard possibility on resume. */
    fun refreshPasteboardStatus() {
        _canTryNavigateToPasteboardURL.value = clipHasUrl(clipboard?.primaryClip)
    }

    private fun clipHasUrl(clip: android.content.ClipData?): Boolean {
        val item = clip?.getItemAt(0) ?: return false
        return item.uri != null || (item.text?.toString()?.startsWith("http") == true) ||
            (item.text?.toString()?.startsWith("mnga://") == true)
    }

    fun canNavigateTo(url: Uri): Boolean = NavigationIdentifier.parse(url) != null

    fun navigateTo(url: Uri) {
        val id = NavigationIdentifier.parse(url) ?: return
        ToastModel.showAuto(ToastModel.Message.OpenURL(url.toString()))
        scope.launch {
            // Dismiss any current destination, then present fresh (mirrors the
            // iOS re-presentation hop; the delay also lets the toast settle).
            _navID.value = null
            delay(500)
            _navID.value = id
        }
    }

    fun navigateTo(identifier: NavigationIdentifier) {
        _navID.value = identifier
    }

    fun dismiss() {
        _navID.value = null
    }

    fun navigateToPasteboardURL() {
        val clip = clipboard?.primaryClip?.getItemAt(0)
        val text = clip?.text?.toString()
        val uri = clip?.uri ?: text?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (uri == null || !canNavigateTo(uri)) {
            ToastModel.showAuto(
                ToastModel.Message.Error("Not a valid NGA or LumaGA link in the pasteboard.")
            )
        } else {
            navigateTo(uri)
        }
    }
}
