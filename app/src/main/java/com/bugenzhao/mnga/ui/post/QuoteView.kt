package com.bugenzhao.mnga.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.display
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Container for quoted blocks, ported from `Views/QuoteView.swift`. Not inside
 * a quote the surface is accent-tinted; nested quotes dim toward the neutral
 * scheme (the iOS "nested dimming" behavior).
 */
@Composable
fun QuoteView(
    fullWidth: Boolean = true,
    modifier: Modifier = Modifier,
    inQuote: Boolean = false,
    background: Color? = null,
    content: @Composable () -> Unit,
) {
    val resolved =
        background
            ?: if (inQuote) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            }
    Surface(
        modifier = if (fullWidth) modifier.fillMaxWidth() else modifier,
        shape = RoundedCornerShape(12.dp),
        color = resolved,
    ) {
        Box(Modifier.padding(8.dp)) { content() }
    }
}

/**
 * Compact user chip + trailing chevron, ported from `Views/QuoteUserView.swift`.
 * The chevron area is the tap target ("show quoted post"); tapping the chip
 * opens the user's profile.
 */
@Composable
fun QuoteUserView(
    uid: String,
    nameHint: String?,
    onNavigateToPost: (() -> Unit)? = null,
    onNavigateToUser: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var name by remember(uid) { mutableStateOf(nameHint) }
    var avatarURL by remember(uid) { mutableStateOf<String?>(null) }

    LaunchedEffect(uid) {
        if (nameHint != null) return@LaunchedEffect
        val user = withContext(Dispatchers.IO) { runCatching { App.users.localUser(uid) }.getOrNull() }
        if (user != null) {
            name = user.name.display().ifEmpty { null }
            avatarURL = user.avatarUrl.ifEmpty { null }
        }
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onNavigateToUser(uid) }
                .padding(vertical = 2.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (avatarURL != null) {
                AsyncImage(
                    model = avatarURL,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).clip(CircleShape),
                )
            } else {
                Icon(
                    Icons.Outlined.Person,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                name ?: "??????",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        if (onNavigateToPost != null) {
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToPost),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Column helper matching the quote block layout (8dp internal spacing). */
@Composable
fun QuoteColumn(content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}
