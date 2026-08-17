package com.bugenzhao.mnga.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Central URL opener, ported from `Models/OpenURLModel.swift`. In-app browsing
 * uses Chrome Custom Tabs when available.
 */
class OpenURLModel(private val context: Context) {

    private val _inAppURL = MutableStateFlow<Uri?>(null)
    val inAppURL: StateFlow<Uri?> = _inAppURL

    fun open(url: Uri, inApp: Boolean? = null, prefs: com.bugenzhao.mnga.storage.PreferencesStorage) {
        val useInApp = inApp ?: prefs.useInAppSafari.value
        if (useInApp && url.scheme?.startsWith("http") == true) {
            _inAppURL.value = url
        } else {
            runCatching {
                context.startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun dismissInApp() {
        _inAppURL.value = null
    }
}
