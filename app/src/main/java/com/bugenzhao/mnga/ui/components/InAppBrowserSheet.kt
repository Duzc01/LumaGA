package com.bugenzhao.mnga.ui.components

import android.net.Uri
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * In-app browser sheet, the Android stand-in for the iOS `SafariView`.
 * Presents [uri] in an embedded WebView until dismissed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InAppBrowserSheet(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val webView = remember { WebView(context) }

    DisposableEffect(Unit) {
        webView.webViewClient = WebViewClient()
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.loadUrl(uri.toString())
        onDispose { webView.stopLoading(); webView.destroy() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uri.host ?: "", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = {
                        runCatching {
                            context.startActivity(
                                android.content.Intent(
                                    android.content.Intent.ACTION_VIEW, uri
                                )
                            )
                        }
                        onDismiss()
                    }) {
                        Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        AndroidView(
            factory = { webView },
            modifier = Modifier.fillMaxSize().padding(padding),
        )
    }
}
