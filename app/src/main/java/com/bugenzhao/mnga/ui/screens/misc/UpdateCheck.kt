package com.bugenzhao.mnga.ui.screens.misc

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.UpdateModel
import com.bugenzhao.mnga.model.UpdateState
import com.bugenzhao.mnga.ui.components.GroupedRow
import com.bugenzhao.mnga.util.AppUpdate
import com.bugenzhao.mnga.util.L

/**
 * The "Check for Updates" row. Tapping it starts a check; [UpdateFlowDialogs]
 * renders whatever comes back. Shared by the settings sheet and the about page
 * so both drive the one app-scoped [UpdateModel].
 */
@Composable
fun CheckForUpdatesRow() {
    val context = LocalContext.current
    val model = App.update
    val state by model.state.collectAsState()

    GroupedRow(
        onClick = { if (!model.isBusy) model.check() },
        leading = {
            Icon(
                Icons.Filled.SystemUpdate,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        title = L.str(context, "Check for Updates"),
        trailing = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (val current = state) {
                    is UpdateState.Checking ->
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                    is UpdateState.Downloading ->
                        Text(
                            "${((current.fraction ?: 0f) * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    is UpdateState.Available ->
                        Text(
                            current.release.version,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    else ->
                        Text(
                            AppUpdate.currentVersion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                }
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
    )
}

/**
 * Hosts every dialog of the update flow. Mount it once per screen that offers
 * [CheckForUpdatesRow]; it draws nothing while the flow is idle.
 */
@Composable
fun UpdateFlowDialogs() {
    val context = LocalContext.current
    val model = App.update
    val state by model.state.collectAsState()

    fun bytes(value: Long): String = Formatter.formatShortFileSize(context, value)

    when (val current = state) {
        is UpdateState.Idle, is UpdateState.Checking -> {}

        is UpdateState.UpToDate ->
            AlertDialog(
                onDismissRequest = { model.reset() },
                title = { Text(L.str(context, "You're up to date")) },
                text = { Text("LumaGA ${current.version}") },
                confirmButton = {
                    TextButton(onClick = { model.reset() }) { Text(L.str(context, "OK")) }
                },
            )

        is UpdateState.Available ->
            AlertDialog(
                onDismissRequest = { model.reset() },
                title = { Text(L.str(context, "New Version Available")) },
                text = {
                    Column(
                        Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "${current.release.version}  ←  ${AppUpdate.currentVersion}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (current.release.notes.isNotBlank()) {
                            Text(
                                current.release.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (current.release.apkSize > 0) {
                            Text(
                                bytes(current.release.apkSize),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { model.download(current.release) }) {
                        Text(L.str(context, "Download & Install"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { model.reset() }) { Text(L.str(context, "Later")) }
                },
            )

        is UpdateState.Downloading ->
            AlertDialog(
                // Only the explicit Cancel button aborts the download.
                onDismissRequest = {},
                title = { Text(L.str(context, "Downloading Update")) },
                text = {
                    Column(
                        Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val fraction = current.fraction
                        if (fraction != null) {
                            LinearProgressIndicator(
                                progress = { fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "${(fraction * 100).toInt()}%  ·  " +
                                    "${bytes(current.downloaded)} / ${bytes(current.total)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text(
                                bytes(current.downloaded),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { model.reset() }) { Text(L.str(context, "Cancel")) }
                },
            )

        is UpdateState.Downloaded ->
            AlertDialog(
                onDismissRequest = { model.reset() },
                title = { Text(L.str(context, "Download Complete")) },
                text = { Text("LumaGA ${current.release.version}") },
                confirmButton = {
                    TextButton(onClick = { model.install(current.release, current.file) }) {
                        Text(L.str(context, "Install"))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { model.reset() }) { Text(L.str(context, "Later")) }
                },
            )

        is UpdateState.NeedsInstallPermission ->
            AlertDialog(
                onDismissRequest = { model.reset() },
                title = { Text(L.str(context, "Permission Required")) },
                text = { Text(L.str(context, "Allow LumaGA to install apps, then tap Install again.")) },
                confirmButton = {
                    TextButton(onClick = { model.openInstallPermissionSettings() }) {
                        Text(L.str(context, "Open Settings"))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = { model.install(current.release, current.file) },
                    ) { Text(L.str(context, "Install")) }
                },
            )

        is UpdateState.Failed -> {
            val release = current.release
            val noAsset = current.message == UpdateModel.NO_ASSET
            AlertDialog(
                onDismissRequest = { model.reset() },
                title = {
                    Text(
                        if (release == null) L.str(context, "Update Check Failed")
                        else L.str(context, "Download Failed")
                    )
                },
                text = {
                    Text(
                        if (noAsset) L.str(context, "This release has no APK to download.")
                        else current.message
                    )
                },
                confirmButton = {
                    when {
                        // No APK asset: the release page is the only way forward.
                        noAsset && release != null ->
                            TextButton(
                                onClick = {
                                    model.reset()
                                    App.openURL.open(
                                        android.net.Uri.parse(release.pageUrl),
                                        inApp = false,
                                        prefs = App.prefs,
                                    )
                                },
                            ) { Text(L.str(context, "Release Page")) }
                        release != null ->
                            TextButton(onClick = { model.download(release) }) {
                                Text(L.str(context, "Retry"))
                            }
                        else ->
                            TextButton(onClick = { model.check() }) {
                                Text(L.str(context, "Retry"))
                            }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { model.reset() }) { Text(L.str(context, "Close")) }
                },
            )
        }
    }
}
