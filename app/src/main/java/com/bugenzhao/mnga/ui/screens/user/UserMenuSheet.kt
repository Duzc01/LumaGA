package com.bugenzhao.mnga.ui.screens.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HistoryEdu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material.icons.filled.PersonRemoveAlt1
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.PlusFeature
import com.bugenzhao.mnga.protos.datamodel.AuthInfo
import com.bugenzhao.mnga.protos.datamodel.User
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.ui.nav.Route
import com.bugenzhao.mnga.util.L

/**
 * The account sheet, ported from `UserMenuView` (the iOS toolbar avatar menu):
 * current user card, account switcher, sign in/out and app links.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserMenuSheet(
    navigator: Navigator,
    onDismiss: () -> Unit,
    onShowLogin: () -> Unit = {},
) {
    val context = LocalContext.current
    val authInfo by App.authStorage.authInfo.collectAsState()
    val allAuthInfos by App.authStorage.allAuthInfos.collectAsState()
    val currentUser by App.currentUser.user.collectAsState()
    val unreadCount by App.notis.unreadCountAnimated.collectAsState()

    val signedIn = authInfo.token.isNotEmpty()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Current user card.
            item(key = "current-user") {
                MenuCard {
                    if (signedIn) {
                        CurrentUserCard(user = currentUser) {
                            currentUser?.let { user ->
                                onDismiss()
                                navigator.push(Route.UserProfile(user = user))
                            }
                        }
                    } else {
                        MenuRow(
                            icon = Icons.Filled.Person,
                            title = L.str(context, "Sign in to NGA"),
                            subtitle = L.str(
                                context,
                                "Sign in to NGA and discover more features here.",
                            ),
                        ) {
                            App.authStorage.setIsSigning(true)
                            onShowLogin()
                            onDismiss()
                        }
                    }
                }
            }

            if (signedIn) {
                item(key = "section-quick") {
                    MenuCard {
                        MenuRow(
                            icon = Icons.Filled.Notifications,
                            title = L.str(context, "Notifications"),
                            trailing = {
                                if (unreadCount > 0) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    ) {
                                        Text(
                                            unreadCount.toString(),
                                            Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                    }
                                }
                            },
                        ) {
                            onDismiss()
                            App.notis.showingSheet.value = true
                        }
                        MenuRow(
                            icon = Icons.AutoMirrored.Filled.Chat,
                            title = L.str(context, "Short Messages"),
                        ) {
                            if (checkPlusFeature(PlusFeature.SHORT_MESSAGE)) {
                                onDismiss()
                                navigator.push(Route.ShortMessages)
                            }
                        }
                        MenuRow(
                            icon = Icons.Outlined.Badge,
                            title = L.str(context, "My Profile"),
                        ) {
                            currentUser?.let { user ->
                                onDismiss()
                                navigator.push(Route.UserProfile(user = user))
                            }
                        }
                        MenuRow(
                            icon = Icons.Filled.Bookmark,
                            title = L.str(context, "Favorite Topics"),
                        ) {
                            onDismiss()
                            navigator.push(Route.Favorites)
                        }
                        MenuRow(
                            icon = Icons.Filled.HistoryEdu,
                            title = L.str(context, "History"),
                        ) {
                            if (checkPlusFeature(PlusFeature.TOPIC_HISTORY)) {
                                onDismiss()
                                navigator.push(Route.History)
                            }
                        }
                    }
                }
            }

            // Account switcher.
            item(key = "section-accounts") {
                MenuCard {
                    SectionLabel(L.str(context, "Accounts"))
                    val accounts = allAuthInfos.sortedBy { it.uid }
                    accounts.forEach { info ->
                        key("acc-${info.uid}") {
                            AccountRow(
                                info = info,
                                selected = info.uid == authInfo.uid,
                            ) {
                                if (checkPlusFeature(PlusFeature.MULTI_ACCOUNT)) {
                                    App.authStorage.setCurrentAuth(info)
                                }
                            }
                        }
                    }
                    MenuRow(
                        icon = Icons.Filled.PersonAddAlt1,
                        title = L.str(context, "Add Account"),
                    ) {
                        if (checkPlusFeature(PlusFeature.MULTI_ACCOUNT)) {
                            App.authStorage.setIsSigning(true)
                            onShowLogin()
                            onDismiss()
                        }
                    }
                    if (signedIn) {
                        MenuRow(
                            icon = Icons.Filled.PersonRemoveAlt1,
                            title = L.str(context, "Sign Out"),
                            tint = MaterialTheme.colorScheme.error,
                        ) {
                            App.authStorage.clearCurrentAuth()
                        }
                    }
                }
            }

            // App links.
            item(key = "section-links") {
                MenuCard {
                    MenuRow(
                        icon = Icons.Filled.Settings,
                        title = L.str(context, "Settings"),
                    ) {
                        App.prefs.showing.value = true
                        onDismiss()
                    }
                    MenuRow(
                        icon = Icons.Outlined.Info,
                        title = L.str(context, "About & Feedback"),
                    ) {
                        onDismiss()
                        navigator.push(Route.TopicDetails(topicId = "mnga_about_feedback"))
                    }
                }
            }

            item(key = "footer-spacing") {
                Box(Modifier.size(12.dp))
            }
        }
    }
}

@Composable
private fun MenuCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) { Column { content() } }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    trailingIcon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (trailing != null) trailing()
        if (trailingIcon != null) Icon(trailingIcon, contentDescription = null, tint = tint)
    }
}

@Composable
private fun AccountRow(
    info: AuthInfo,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            if (selected) Icons.Filled.Person else Icons.Outlined.Badge,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            info.cachedName.ifEmpty { info.uid.ifEmpty { L.str(context, "Unknown") } },
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (selected) {
            Text(
                L.str(context, "Current Version"),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun CurrentUserCard(
    user: User?,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        UserView(
            user = user ?: User.getDefaultInstance(),
            style = UserViewStyle.NORMAL,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Filled.Group,
            contentDescription = L.str(context, "Accounts"),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
