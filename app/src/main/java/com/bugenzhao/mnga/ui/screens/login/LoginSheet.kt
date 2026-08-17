package com.bugenzhao.mnga.ui.screens.login

import android.net.Uri
import android.webkit.CookieManager
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.protos.datamodel.AuthInfo
import com.bugenzhao.mnga.util.L
import com.bugenzhao.mnga.util.URLs
import kotlin.coroutines.resume
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/** Auto-clicks the password-login entry and hides QR / third-party login. */
private const val HIDE_ELEMENT_SCRIPT = """
(function() {
var iframe = document.getElementById('iff')
function getElementByXpath(document, path) {
  return document.evaluate(path, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue;
}
// Switch from the QR-login view to the password form, if present. Some user
// agents (e.g. when the UA matches /android/) already land on the password
// form, in which case this node does not exist and the click is skipped.
var loginElement = getElementByXpath(iframe.contentDocument, '//*[@id="main"]/div/div[3]/a[2]')
if (loginElement) {
  loginElement.click()
}
var xpaths = [
  '//*[@id="main"]/div/a[2]',          // QRCode login
  '//*[@id="main"]/div/div[last()]',   // 3rd party login
]
for (let xpath of xpaths) {
  let element = getElementByXpath(iframe.contentDocument, xpath)
  if (element) {
    element.style.display = 'none'
  }
}
})(); 'mnga-ok'
"""

/** Disables zoom and applies the NGA cream background. */
private const val INIT_SCRIPT = """
(function() {
var viewport = document.querySelector('meta[name="viewport"]');
if (viewport) {
  viewport.setAttribute('content', 'width=device-width, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=no');
} else {
  var meta = document.createElement('meta');
  meta.name = 'viewport';
  meta.content = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, minimum-scale=1.0, user-scalable=no';
  document.head.appendChild(meta);
}
// NGA sizes the login iframe with percentages (`html {height:100%}` and
// `#iff {height:99.5%}`). If the WebView was laid out at 0 height when the
// page first loaded, those root percentages resolve to 0 and stay cached,
// collapsing the iframe so the form is invisible. Give <html> an explicit
// pixel height so the iframe percentage resolves correctly.
document.documentElement.style.height = window.innerHeight + 'px';
document.body.style.backgroundColor = 'rgb(255, 246, 223)';
})(); 'mnga-ok'
"""

private suspend fun WebView.evaluate(script: String): String? =
    suspendCancellableCoroutine { continuation ->
        try {
            evaluateJavascript(script) { result -> continuation.resume(result) }
        } catch (e: Exception) {
            continuation.resume(null)
        }
    }

/**
 * Starts every login from a clean cookie jar, approximating the iOS
 * non-persistent `WKWebsiteDataStore` (this app's WebView is login-only).
 */
private fun clearLoginCookies() {
    val manager = CookieManager.getInstance()
    manager.removeAllCookies(null)
    manager.removeSessionCookies(null)
    manager.flush()
}

/**
 * The captive-WebView login sheet, ported from `Views/LoginView.swift`.
 * Watches the cookie store until NGA sets the passport cookies, then stores
 * them as the current [AuthInfo].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var webView by remember { mutableStateOf<WebView?>(null) }
    val loading = remember { mutableStateOf(true) }
    val authing = remember { mutableStateOf(false) }
    val currentUrl = remember { mutableStateOf(URLs.login) }
    val alertMessage = remember { mutableStateOf<String?>(null) }
    val alertResult = remember { mutableStateOf<JsResult?>(null) }
    val pagesMenuOpen = remember { mutableStateOf(false) }

    /**
     * The running "tweak the page" task. Restarted on every `onPageFinished`
     * (the WebView also reports the login iframe's navigation here), so a late
     * navigation can never swallow the scripts and leave the default (QR)
     * login UI in place until the user manually refreshes.
     */
    var scriptJob by remember { mutableStateOf<Job?>(null) }

    fun close() {
        App.authStorage.setIsSigning(false)
        onDismiss()
    }

    fun load(url: String) {
        loading.value = true
        currentUrl.value = url
        webView?.loadUrl(url)
    }

    fun authWithCookies() {
        if (authing.value) return
        val cookies = CookieManager.getInstance().getCookie(URLs.login) ?: return
        val map = cookies.split(";")
            .mapNotNull { part ->
                val pair = part.trim().split("=", limit = 2)
                if (pair.size == 2) pair[0] to pair[1] else null
            }
            .toMap()
        val uid = map["ngaPassportUid"]
        val token = map["ngaPassportCid"]
        if (uid.isNullOrBlank() || token.isNullOrBlank()) return

        authing.value = true
        App.authStorage.setCurrentAuth(
            AuthInfo.newBuilder().setUid(uid).setToken(token).build()
        )
        onDismiss()
    }

    // Poll the cookie store every 0.5 s, like the SwiftUI timer.
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(500)
            authWithCookies()
        }
    }

    fun openExternal(url: String) {
        App.openURL.open(Uri.parse(url), inApp = false, prefs = App.prefs)
    }

    Dialog(
        onDismissRequest = { close() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize()) {
                TopAppBar(
                    title = {
                        Column(Modifier.fillMaxWidth()) {
                            Text(L.str(context, "Sign in to NGA"))
                            Text(
                                currentUrl.value,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { close() }) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                    },
                    actions = {
                        if (authing.value) {
                            CircularProgressIndicator(
                                Modifier.padding(horizontal = 10.dp).size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        IconButton(onClick = { openExternal(currentUrl.value) }) {
                            Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                        }
                        IconButton(onClick = { load(currentUrl.value) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                        }
                        Box {
                            IconButton(onClick = { pagesMenuOpen.value = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = null)
                            }
                            DropdownMenu(
                                expanded = pagesMenuOpen.value,
                                onDismissRequest = { pagesMenuOpen.value = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(L.str(context, "Sign In")) },
                                    onClick = {
                                        pagesMenuOpen.value = false
                                        load(URLs.login)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(L.str(context, "Agreement")) },
                                    onClick = {
                                        pagesMenuOpen.value = false
                                        openExternal(URLs.agreement)
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(L.str(context, "Privacy")) },
                                    onClick = {
                                        pagesMenuOpen.value = false
                                        openExternal(URLs.privacy)
                                    },
                                )
                            }
                        }
                    },
                )

                Box(Modifier.fillMaxSize()) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize().alpha(if (loading.value) 0f else 1f),
                        factory = { ctx ->
                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            val wv = WebView(ctx)
                            cookieManager.setAcceptThirdPartyCookies(wv, true)
                            wv.settings.javaScriptEnabled = true
                            wv.settings.domStorageEnabled = true
                            wv.settings.setSupportZoom(false)
                            wv.settings.builtInZoomControls = false
                            wv.settings.displayZoomControls = false
                            wv.webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                ): Boolean = false

                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: android.graphics.Bitmap?,
                                ) {
                                    loading.value = true
                                    if (url != null) currentUrl.value = url
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    if (url != URLs.login) {
                                        loading.value = false
                                        return
                                    }
                                    // Cancel any in-flight run and start a fresh one: this
                                    // WebView reports sub-frame (login iframe) navigations
                                    // through onPageFinished too, and a naive busy-flag used
                                    // to swallow the whole run on the second callback.
                                    scriptJob?.cancel()
                                    scriptJob = scope.launch {
                                        // Give the login iframe (`account_copy.html`) time to
                                        // finish loading before poking at its DOM.
                                        delay(1000)
                                        try {
                                            wv.evaluate(INIT_SCRIPT)
                                            // Retry the hide script up to 60 times. `evaluate`
                                            // returns the string "null" when the script throws,
                                            // so only a real 'mnga-ok' result counts as success.
                                            for (i in 0 until 60) {
                                                delay(500)
                                                val r = wv.evaluate(HIDE_ELEMENT_SCRIPT)
                                                if (r != null && r.contains("mnga-ok")) break
                                            }
                                            delay(500)
                                        } finally {
                                            loading.value = false
                                        }
                                    }
                                }
                            }
                            wv.webChromeClient = object : WebChromeClient() {
                                override fun onJsAlert(
                                    view: WebView?,
                                    url: String?,
                                    message: String?,
                                    result: JsResult,
                                ): Boolean {
                                    alertMessage.value = message
                                    alertResult.value = result
                                    return true
                                }
                            }
                            webView = wv
                            wv
                        },
                    )
                    if (loading.value) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }

    // Load once the view exists; start from a clean cookie jar. Also wait for
    // the WebView to be attached and measured: loading while it still has zero
    // size makes the page lay out against a 0-height viewport, which breaks
    // NGA's percentage-height login iframe (`html {height:100%}` collapses).
    LaunchedEffect(webView) {
        val wv = webView
        if (wv == null || currentUrl.value != URLs.login || !loading.value) {
            return@LaunchedEffect
        }
        var attempts = 0
        while (attempts < 100 && (wv.width <= 0 || wv.height <= 0)) {
            delay(50)
            attempts++
        }
        clearLoginCookies()
        load(URLs.login)
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webView = null
        }
    }

    val message = alertMessage.value
    val result = alertResult.value
    if (message != null && result != null) {
        AlertDialog(
            onDismissRequest = {
                result.confirm()
                alertMessage.value = null
                alertResult.value = null
            },
            title = { Text(L.str(context, "From NGA")) },
            text = {
                Text(message + "\n\n" + L.str(context, "MNGA Login Notice"))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        result.confirm()
                        alertMessage.value = null
                        alertResult.value = null
                    }
                ) { Text(L.str(context, "OK")) }
            },
        )
    }
}
