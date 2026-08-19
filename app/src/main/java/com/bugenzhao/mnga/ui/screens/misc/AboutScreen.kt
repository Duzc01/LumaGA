package com.bugenzhao.mnga.ui.screens.misc

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.BuildConfig
import com.bugenzhao.mnga.R
import com.bugenzhao.mnga.ui.components.GroupedList
import com.bugenzhao.mnga.ui.components.GroupedRow
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.util.Constants
import com.bugenzhao.mnga.util.L

/**
 * About page: app icon, version, and the update check. Links point at the
 * GitHub repository that releases are published to (see [Constants.GitHub]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(navigator: Navigator? = null) {
    val context = LocalContext.current

    fun open(url: String) {
        App.openURL.open(Uri.parse(url), inApp = false, prefs = App.prefs)
    }

    BackHandler(enabled = navigator != null && navigator.size > 1) { navigator?.pop() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L.str(context, "About")) },
                navigationIcon = {
                    IconButton(onClick = { navigator?.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            item(key = "header") {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(R.mipmap.ic_launcher),
                        contentDescription = null,
                        modifier = Modifier.size(96.dp).clip(RoundedCornerShape(22.dp)),
                    )
                    Text(
                        // Brand name, intentionally not localized.
                        "LumaGA",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                    )
                    Text(
                        "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item(key = "update") {
                GroupedList { Column { CheckForUpdatesRow() } }
            }

            item(key = "links") {
                GroupedList {
                    Column {
                        GroupedRow(
                            onClick = { open(Constants.GitHub.repoUrl) },
                            leading = {
                                Icon(
                                    Icons.Filled.Code,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            title = L.str(context, "Source Code"),
                            subtitle = Constants.GitHub.repo,
                            trailing = { LinkChevron() },
                        )
                        GroupedRow(
                            onClick = { open(Constants.GitHub.releasesUrl) },
                            leading = {
                                Icon(
                                    Icons.Filled.NewReleases,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            title = L.str(context, "Release Notes"),
                            trailing = { LinkChevron() },
                        )
                    }
                }
            }

            item(key = "footer") {
                Text(
                    L.str(context, "An NGA forum client, ported from MNGA."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }
    }

    UpdateFlowDialogs()
}

@Composable
private fun LinkChevron() {
    Icon(
        Icons.Filled.ChevronRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(18.dp),
    )
}
