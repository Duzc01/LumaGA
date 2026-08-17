package com.bugenzhao.mnga.ui.post

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.util.L

/**
 * `[collapse]` region, ported from `Views/CollapsedContentView.swift`: a
 * `<title>...` disclosure row ("..." marker) with an animated height change;
 * tapping toggles.
 */
@Composable
fun CollapsedContent(
    title: String,
    initiallyCollapsed: Boolean = true,
    content: @Composable () -> Unit,
) {
    var collapsedState by remember(title) { mutableStateOf(initiallyCollapsed) }
    val collapsed = collapsedState
    val rotation by animateFloatAsState(if (collapsed) 0f else 180f, label = "chevron")

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { collapsedState = !collapsedState }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (collapsed) Icons.Outlined.ExpandMore else Icons.Outlined.ExpandLess,
                contentDescription = null,
                modifier = Modifier.size(18.dp).rotate(rotation),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                "$title...",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        AnimatedVisibility(
            visible = !collapsed,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                Spacer(Modifier.height(4.dp))
                content()
            }
        }
    }
}

/** Localized default collapse title (upstream: `"Collapsed Content"...`). */
@Composable
fun collapsedTitle(attribute: String?): String =
    attribute ?: L.str(LocalContext.current, "Collapsed Content")
