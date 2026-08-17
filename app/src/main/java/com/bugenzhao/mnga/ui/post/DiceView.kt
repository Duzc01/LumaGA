package com.bugenzhao.mnga.ui.post

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Casino
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.util.DiceRoller
import com.bugenzhao.mnga.util.L

/**
 * `[dice]` block, ported from `Views/DiceView.swift`: `expr → total` in an
 * accent-tinted quote surface; tapping toggles the per-die expansion
 * (`expr → d6(4)+d6(2) → 6`).
 */
@Composable
fun DiceView(result: DiceRoller.Result, unresolved: Boolean = false, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showingExpanded by remember(result.originalExpression) { mutableStateOf(false) }
    val largerFont = App.prefs.postRowLargerFont.flow.collectAsState().value
    val fontSize = if (largerFont) 16.sp else 15.sp

    val total = when {
        unresolved -> "???"
        result.total != null -> result.total.toString()
        else -> L.str(context, "ERROR")
    }
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val annotated: AnnotatedString = buildAnnotatedString {
        append(result.originalExpression)
        append(" → ")
        if (showingExpanded && result.expandedExpression.isNotEmpty()) {
            append(result.expandedExpression)
            withStyle(SpanStyle(color = secondary)) { append(" → ") }
        }
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(total) }
    }

    QuoteView(
        fullWidth = false,
        modifier = modifier,
        background = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
    ) {
        Row(
            Modifier.clickable { showingExpanded = !showingExpanded }.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Outlined.Casino,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                annotated,
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Dice block with no deterministic context available (`???` totals). */
@Composable
fun UnresolvedDiceView(expression: String, modifier: Modifier = Modifier) {
    DiceView(
        result = DiceRoller.Result(
            originalExpression = expression,
            expandedExpression = "???",
            total = null,
        ),
        unresolved = true,
        modifier = modifier,
    )
}
