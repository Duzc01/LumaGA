package com.bugenzhao.mnga.ui.screens.messages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AddComment
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.model.PagingDataSource
import com.bugenzhao.mnga.model.PlusFeature
import com.bugenzhao.mnga.model.appScope
import com.bugenzhao.mnga.protos.datamodel.ShortMessage
import com.bugenzhao.mnga.protos.datamodel.UserName
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.ShortMessageListRequest
import com.bugenzhao.mnga.protos.service.ShortMessageListResponse
import com.bugenzhao.mnga.ui.components.PagedList
import com.bugenzhao.mnga.ui.components.RepliesNumText
import com.bugenzhao.mnga.ui.nav.Navigator
import com.bugenzhao.mnga.ui.nav.Route
import com.bugenzhao.mnga.ui.screens.user.checkPlusFeature
import com.bugenzhao.mnga.ui.screens.user.displayString
import com.bugenzhao.mnga.util.DateFormatters
import com.bugenzhao.mnga.util.L
import java.util.Date

/**
 * Short message conversation list, ported from `ShortMessageListView`:
 * paged rows with participants, subject, reply count and last post date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShortMessageListScreen(
    navigator: Navigator,
    onNewMessage: () -> Unit = {},
) {
    val context = LocalContext.current

    val dataSource = remember {
        PagingDataSource<ShortMessageListResponse, ShortMessage>(
            scope = appScope,
            responseParser = { ShortMessageListResponse.parser() },
            buildRequest = { page ->
                AsyncRequest.newBuilder()
                    .setShortMessageList(
                        ShortMessageListRequest.newBuilder().setPage(page)
                    )
                    .build()
            },
            onResponse = { response ->
                Pair(response.messagesList, response.pages.toInt().takeIf { it > 0 })
            },
            id = { it.id },
        )
    }

    LaunchedEffect(Unit) {
        if (dataSource.notLoaded) dataSource.initialLoad()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(L.str(context, "Short Messages")) },
                navigationIcon = {
                    IconButton(onClick = { navigator.pop() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (checkPlusFeature(PlusFeature.SHORT_MESSAGE)) onNewMessage()
                        },
                    ) {
                        Icon(
                            Icons.Outlined.AddComment,
                            contentDescription = L.str(context, "New Short Message"),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            PagedList(
                dataSource = dataSource,
                key = { it.id },
                emptyPlaceholder = L.str(context, "No Short Messages"),
            ) { _, message ->
                ShortMessageRow(message) {
                    navigator.push(Route.ShortMessageDetails(id = message.id))
                }
            }
        }
    }

    BackHandler(enabled = navigator.size > 1) { navigator.pop() }
}

/** One conversation row, ported from `ShortMessageRowView`. */
@Composable
internal fun ShortMessageRow(
    message: ShortMessage,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.fillMaxWidth().clickable { onClick() }.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Chat,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    message.subject.ifEmpty { L.str(context, "Short Message") },
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                RepliesNumText(message.postNum)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ParticipantsLabel(
                    names = message.userNamesList,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    DateFormatters.automatic(
                        context,
                        Date(message.lastPostDate * 1000),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Participant names: none, one (person / masks icon), or many (group icon). */
@Composable
private fun ParticipantsLabel(
    names: List<UserName>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    if (names.isEmpty()) {
        Icon(
            Icons.Outlined.MailOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when (names.size) {
            1 -> {
                val name = names.first()
                Icon(
                    if (name.anonymous.isNotEmpty()) Icons.Outlined.TheaterComedy
                    else Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.padding(0.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    name.displayString,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            else -> {
                Icon(
                    Icons.Filled.Group,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    names.joinToString(", ") { it.displayString },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
