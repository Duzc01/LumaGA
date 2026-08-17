package com.bugenzhao.mnga.util

import android.net.Uri
import com.bugenzhao.mnga.storage.PreferencesStorage

/** URL helpers, ported from `Utilities/URLs.swift`. */
object URLs {
    const val attachmentBase = "https://img.nga.cn/attachments/"
    const val defaultHost = "bbs.nga.cn"
    val hosts = listOf("bbs.nga.cn", "ngabbs.com", "bbs.ngacn.cc")

    fun base(host: String): String = "https://$host/"
    const val defaultBase = "https://bbs.nga.cn/"

    /** The active base, respecting the user's backend override. */
    val base: String
        get() {
            val raw = PreferencesStorage.shared?.requestOption?.value?.baseUrlV2
            return if (raw.isNullOrEmpty()) defaultBase else raw
        }

    val login: String get() = base + "nuke.php?__lib=login&__act=account&login"
    val agreement: String get() = base + "misc/agreement.html"
    val privacy: String get() = base + "misc/privacy.html"

    private val legacyHostSuffixes = listOf(".nga.178.com", ".ngacn.cc", ".ngabbs.com")

    /**
     * Resolve [value] (possibly relative) against [base] and rewrite legacy
     * NGA image hosts (`img123.ngabbs.com` etc.) to their modern equivalents.
     */
    fun resourceURL(value: String, base: String? = null): String? {
        val resolved = if (base != null && !value.contains("://")) {
            base.trimEnd('/') + "/" + value.trimStart('/')
        } else {
            value
        }
        val uri = Uri.parse(resolved) ?: return null
        val host = uri.host?.lowercase() ?: return resolved

        val parts = host.split(".")
        if (parts.size == 2 && parts[0].take(3) == "img" &&
            parts[0].drop(3).all { it.isDigit() } &&
            host.substringAfter(".") in legacyHostSuffixes.map { it.drop(1) }
        ) {
            val newHost =
                if (uri.path?.startsWith("/ngabbs/") == true) "img4.nga.cn" else "img.nga.cn"
            val builder = uri.buildUpon().scheme("https").authority(newHost)
            return builder.build().toString()
        }
        return resolved
    }

    fun attachmentURL(value: String): String? = resourceURL(value, attachmentBase)
}
