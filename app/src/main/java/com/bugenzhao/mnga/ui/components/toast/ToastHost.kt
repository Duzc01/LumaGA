package com.bugenzhao.mnga.ui.components.toast

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.ToastModel
import com.bugenzhao.mnga.util.L
import kotlinx.coroutines.delay

/**
 * Renders the toast channels (banner at top, HUD centered), ported from
 * `Modifiers/ToastModifier.swift`.
 */
@Composable
fun ToastHost() {
    BannerToast(ToastModel.banner)
    HudToast(ToastModel.hud)
}

@Composable
private fun ToastSlot(
    model: ToastModel,
    modifier: Modifier = Modifier,
    content: @Composable (ToastModel.Message) -> Unit,
) {
    val message by model.message.collectAsState()
    AnimatedVisibility(
        visible = message != null,
        enter = fadeIn() + slideInVertically { -it / 2 },
        exit = fadeOut() + slideOutVertically { -it / 2 },
        modifier = modifier,
    ) {
        message?.let { msg ->
            LaunchedEffect(msg.id) {
                delay(3200)
                model.dismiss()
            }
            content(msg)
        }
    }
}

@Composable
private fun BannerToast(model: ToastModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    ToastSlot(
        model,
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp),
    ) { msg ->
        val (title, subtitle, container, icon) = when (msg) {
            is ToastModel.Message.Success ->
                ToastVisual(
                    L.str(context, "Success"),
                    msg.message,
                    green,
                    Icons.Filled.CheckCircle,
                )
            is ToastModel.Message.Error ->
                ToastVisual(
                    L.str(context, "Error"),
                    run {
                        val parts = msg.error.split("|", limit = 2)
                        if (parts.size == 2)
                            com.bugenzhao.mnga.util.L.str(context, parts[0]) + ": " + parts[1]
                        else com.bugenzhao.mnga.util.L.str(context, msg.error)
                    },
                    red,
                    Icons.Filled.Error,
                )
            is ToastModel.Message.CacheLoaded ->
                ToastVisual(
                    L.str(context, "Cache Loaded"),
                    msg.message,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    Icons.Filled.CheckCircle,
                )
            else -> ToastVisual(L.str(context, "Error"), "", red, Icons.Filled.Error)
        }
        ToastCard(title, subtitle, container, icon)
    }
}

@Composable
private fun HudToast(model: ToastModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    ToastSlot(model, Modifier.fillMaxSize().padding(24.dp)) { msg ->
        val (title, subtitle) = when (msg) {
            is ToastModel.Message.Notification ->
                L.str(context, "Notifications") to
                    L.str(context, "%lld new unread notifications", msg.count)
            is ToastModel.Message.UserSwitch ->
                L.str(context, "Account Switched") to msg.name
            is ToastModel.Message.ClockIn ->
                L.str(context, "Clocked in Successfully") to msg.message
            is ToastModel.Message.OpenURL ->
                L.str(context, "Navigated to Link") to msg.url
            ToastModel.Message.AutoRefreshed ->
                L.str(context, "Auto Refreshed") to null
            else -> "" to null
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            ToastCard(
                title,
                subtitle,
                MaterialTheme.colorScheme.surfaceContainerHigh,
                Icons.Filled.CheckCircle,
            )
        }
    }
}

private data class ToastVisual(
    val title: String,
    val subtitle: String?,
    val container: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private val green = Color(0xE634C759)
private val red = Color(0xE6FF3B30)

@Composable
private fun ToastCard(
    title: String,
    subtitle: String?,
    container: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(icon, contentDescription = null, tint = container)
            Column {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
