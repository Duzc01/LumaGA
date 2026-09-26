package com.bugenzhao.mnga.ui.screens.user

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.protos.datamodel.BlockWord
import com.bugenzhao.mnga.storage.BlockWordsStorage
import com.bugenzhao.mnga.ui.components.InitialAvatar
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.util.Haptics
import com.bugenzhao.mnga.util.L

/** A focused roster for reviewing and removing user blocks. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockedUsersScreen(navigator: Navigator) {
    val context = LocalContext.current
    val view = LocalView.current
    val storage = App.blockWords
    val words by storage.words.collectAsState()
    val blockedUsers = remember(words) {
        words.mapNotNull { word ->
            word.word.takeIf { it.startsWith(BlockWordsStorage.userPrefix) }
                ?.removePrefix(BlockWordsStorage.userPrefix)
                ?.takeIf { it.isNotBlank() }
                ?.let { name -> BlockedUser(word, name) }
        }.sortedBy { it.name.lowercase() }
    }
    var confirmUnblockAll by remember { mutableStateOf(false) }

    fun unblock(word: BlockWord) {
        storage.remove(word)
        Haptics.play(view, Haptics.NotificationType.SUCCESS)
    }

    BackHandler(enabled = navigator.size > 1) { navigator.pop() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L.str(context, "Block Management")) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "privacy-summary") {
                BlockedUsersSummary(
                    count = blockedUsers.size,
                    onUnblockAll = if (blockedUsers.size > 1) {
                        { confirmUnblockAll = true }
                    } else null,
                )
            }

            if (blockedUsers.isEmpty()) {
                item(key = "empty") { BlockedUsersEmptyState() }
            } else {
                item(key = "section-label") {
                    Text(
                        L.str(context, "Blocked Users"),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                    )
                }
                items(blockedUsers, key = { it.word.word }) { blockedUser ->
                    BlockedUserRow(
                        name = blockedUser.name,
                        onUnblock = { unblock(blockedUser.word) },
                    )
                }
            }
        }
    }

    if (confirmUnblockAll) {
        AlertDialog(
            onDismissRequest = { confirmUnblockAll = false },
            icon = { Icon(Icons.Outlined.Shield, contentDescription = null) },
            title = { Text(L.str(context, "Unblock All Users?")) },
            text = { Text(L.str(context, "Their topics and replies will be visible again.")) },
            confirmButton = {
                TextButton(
                    onClick = {
                        blockedUsers.forEach { storage.remove(it.word) }
                        Haptics.play(view, Haptics.NotificationType.SUCCESS)
                        confirmUnblockAll = false
                    },
                ) {
                    Text(L.str(context, "Unblock All"))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnblockAll = false }) {
                    Text(L.str(context, "Cancel"))
                }
            },
        )
    }
}

private data class BlockedUser(val word: BlockWord, val name: String)

@Composable
private fun BlockedUsersSummary(count: Int, onUnblockAll: (() -> Unit)?) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.10f),
            ) {
                Box(Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    L.str(context, "%lld blocked users", count),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    L.str(context, "Topics and replies from these users are hidden."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                )
            }
            if (onUnblockAll != null) {
                TextButton(onClick = onUnblockAll) {
                    Text(L.str(context, "Unblock All"))
                }
            }
        }
    }
}

@Composable
private fun BlockedUserRow(name: String, onUnblock: () -> Unit) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InitialAvatar(name = name, size = 42.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    L.str(context, "Topics and replies hidden"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onUnblock,
                contentPadding = PaddingValues(horizontal = 12.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Text(L.str(context, "Unblock"))
            }
        }
    }
}

@Composable
private fun BlockedUsersEmptyState() {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.VerifiedUser,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                L.str(context, "No Blocked Users"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                L.str(context, "People you block will appear here."),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
