package com.bugenzhao.mnga.ui.post

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.model.QuotedPostResolver
import com.bugenzhao.mnga.model.appScope
import com.bugenzhao.mnga.protos.datamodel.PostId
import com.bugenzhao.mnga.util.L

/**
 * Compact "Reply to" preview, ported from `Views/InlineQuotedPostView.swift`:
 * the quoted post is fetched from the resolver and rendered inline (nested
 * reply-quotes skipped, images as thumb buttons, 5-item truncation via the
 * renderer env). Tapping the chevron opens the reply chain.
 */
@Composable
internal fun InlineQuotedPost(
    postId: PostId,
    uid: String,
    nameHint: String?,
    sourcePostId: PostId?,
    defaultStyle: StyleSpec,
    host: RenderHost,
    resolver: QuotedPostResolver? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val effectiveResolver =
        resolver ?: remember(postId) { QuotedPostResolver(appScope) }
    val posts by effectiveResolver.posts.collectAsState()
    val failed by effectiveResolver.failed.collectAsState()

    LaunchedEffect(postId) { effectiveResolver.load(postId) }

    val showChainAction: (() -> Unit)? =
        if (sourcePostId != null) {
            { host.actions.showReplyChain(sourcePostId) }
        } else null
    val lineLimit = if (showChainAction != null) 5 else null

    QuoteView(fullWidth = true, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            QuoteUserView(
                uid = uid,
                nameHint = nameHint,
                onNavigateToPost = showChainAction,
                onNavigateToUser = { host.actions.navigateToUser(it) },
            )
            val post = posts[postId]
            when {
                post != null ->
                    PostContent(
                        content = post.content,
                        env = ContentEnv(
                            inInlineReplyQuote = true,
                            replyTo = sourcePostId,
                            diceContext = post,
                            actions = host.actions,
                        ),
                        defaultColor = host.onSurface.copy(alpha = 0.9f),
                        lineLimit = lineLimit,
                        baseFontSize = defaultStyle.fontSize.takeIf { it != TextUnit.Unspecified },
                    )
                postId in failed ->
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text(
                            L.str(context, "Reply not found. It may have been deleted."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                else ->
                    Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
            }
        }
    }
}
