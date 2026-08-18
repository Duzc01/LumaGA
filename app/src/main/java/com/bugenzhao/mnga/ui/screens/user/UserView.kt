package com.bugenzhao.mnga.ui.screens.user

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.Comment
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.FrontHand
import androidx.compose.material.icons.outlined.TheaterComedy
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.PlusFeature
import com.bugenzhao.mnga.model.PlusModel
import com.bugenzhao.mnga.protos.datamodel.PostContent
import com.bugenzhao.mnga.protos.datamodel.User
import com.bugenzhao.mnga.protos.datamodel.UserName
import com.bugenzhao.mnga.storage.pref
import com.bugenzhao.mnga.ui.components.AvatarImage
import com.bugenzhao.mnga.util.L
import com.bugenzhao.mnga.util.URLs
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Layout styles of [UserView], mirroring `UserView.Style`. */
enum class UserViewStyle(val avatarSize: Int) {
    COMPACT(24),
    NORMAL(36),
    HUGE(60),
    VERTICAL(48),
}

/** `User.anonymousExample`: the placeholder for fully anonymous users. */
private val anonymousExample: User by lazy {
    User.newBuilder()
        .setName(
            UserName.newBuilder().setNormal("??????").setAnonymous("??????")
        )
        .build()
}

val User.isAnonymousUser: Boolean get() = name.anonymous.isNotEmpty()

/** `name.display` with an empty-value fallback. */
val UserName.displayString: String
    get() = if (anonymous.isNotEmpty()) anonymous else normal

/** `User.nameDisplayCompat`: display name with a legacy raw-name fallback. */
val User.nameDisplayCompat: String
    get() = name.displayString.ifEmpty { nameRaw }

/** `<br/>` -> newline, used for editable/plain-text rendering. */
val PostContent.rawReplacingBr: String
    get() = raw.replace("<br/>", "\n")

/**
 * Minimal `PostContent` rendering used until the shared rich-text renderer
 * (`ui.post.PostContent`) lands: plain raw text with `<br/>` restored.
 */
@Composable
internal fun RawPostContent(
    content: PostContent,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    Text(
        content.rawReplacingBr,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow,
    )
}

private val User.idDisplayString: String
    get() = if (isAnonymousUser) name.normal else id

/**
 * Reusable user identity widget: avatar + name (+ detail stats), ported from
 * `Views/UserView.swift`. Non-navigating; interactions are surfaced through
 * the [onAvatarClick] / [onShowUserProfile] callbacks.
 */
@Composable
fun UserView(
    user: User,
    style: UserViewStyle = UserViewStyle.NORMAL,
    isAuthor: Boolean = false,
    modifier: Modifier = Modifier,
    onAvatarClick: ((String) -> Unit)? = null,
    onShowUserProfile: ((User) -> Unit)? = null,
) {
    UserViewImpl(
        initialUser = user,
        id = user.id,
        style = style,
        isAuthor = isAuthor,
        loadRemote = false,
        modifier = modifier,
        onAvatarClick = onAvatarClick,
        onShowUserProfile = onShowUserProfile,
    )
}

/** Resolution-by-id constructor, mirroring `UserView(id:, nameHint:)`. */
@Composable
fun UserView(
    id: String,
    nameHint: String? = null,
    style: UserViewStyle = UserViewStyle.NORMAL,
    isAuthor: Boolean = false,
    loadRemote: Boolean = false,
    modifier: Modifier = Modifier,
    onAvatarClick: ((String) -> Unit)? = null,
    onShowUserProfile: ((User) -> Unit)? = null,
) {
    val initial = remember(id, nameHint) {
        nameHint?.takeIf { it.isNotEmpty() }?.let { hint ->
            User.newBuilder()
                .setId(id)
                .setName(UserName.newBuilder().setNormal(hint))
                .build()
        }
    }
    UserViewImpl(
        initialUser = initial,
        id = id,
        style = style,
        isAuthor = isAuthor,
        loadRemote = loadRemote,
        modifier = modifier,
        onAvatarClick = onAvatarClick,
        onShowUserProfile = onShowUserProfile,
    )
}

@Composable
private fun UserViewImpl(
    initialUser: User?,
    id: String,
    style: UserViewStyle,
    isAuthor: Boolean,
    loadRemote: Boolean,
    modifier: Modifier = Modifier,
    onAvatarClick: ((String) -> Unit)? = null,
    onShowUserProfile: ((User) -> Unit)? = null,
) {
    val context = LocalContext.current
    var user by remember(id) { mutableStateOf(initialUser) }
    var showId by remember(id) { mutableStateOf(false) }

    val showAuthorIndicator = pref(App.prefs.postRowShowAuthorIndicator).value
    val showRegDatePref = pref(App.prefs.postRowShowUserRegDate).value

    // Resolve from the local cache (sync bridge, off the main thread).
    LaunchedEffect(id, initialUser) {
        if (initialUser == null || initialUser.name.normal.isEmpty()) {
            val resolved = withContext(Dispatchers.IO) { App.users.localUser(id) }
            if (resolved != null) user = resolved
        }
    }
    // Optionally upgrade with a remote fetch (richer info like `ipLocation`).
    LaunchedEffect(id, loadRemote) {
        if (loadRemote && id.isNotEmpty()) {
            App.users.remoteUser(id, showError = false)?.let { user = it }
        }
    }

    val resolved = user
    val name = when {
        resolved != null && resolved.name.displayString.isNotEmpty() ->
            resolved.name.displayString
        id.isNotEmpty() -> id
        else -> "????????"
    }
    val anonymous = resolved?.isAnonymousUser == true
    val avatarURL = resolved?.avatarUrl?.takeIf { it.isNotEmpty() }
        ?.let { URLs.resourceURL(it) }

    val avatar: @Composable () -> Unit = {
        val size = style.avatarSize.dp
        val handler = onAvatarClick
        val url = avatarURL
        val showProfile = onShowUserProfile
        val resolvedUser = resolved
        val click: (() -> Unit)? =
            if (style == UserViewStyle.HUGE && url != null && handler != null) {
                { handler(url) }
            } else if (style != UserViewStyle.HUGE && resolvedUser != null && showProfile != null) {
                { showProfile(resolvedUser) }
            } else {
                null
            }
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .then(if (click != null) Modifier.clickable { click() } else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            val placeholderIcon: @Composable (ImageVector) -> Unit = { icon ->
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(size),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            AvatarImage(
                url = avatarURL,
                name = name,
                size = size,
                contentDescription = name,
                // Anonymous names are server-generated noise, so their
                // initial would mean nothing — keep the masks glyph.
                fallback = if (anonymous) {
                    { placeholderIcon(Icons.Outlined.TheaterComedy) }
                } else {
                    null
                },
            )
        }
    }

    val nameStyle = when (style) {
        UserViewStyle.COMPACT -> MaterialTheme.typography.bodyMedium
        UserViewStyle.NORMAL ->
            MaterialTheme.typography.bodyMedium
        UserViewStyle.HUGE -> MaterialTheme.typography.headlineMedium
        UserViewStyle.VERTICAL -> MaterialTheme.typography.bodySmall
    }
    val nameWeight = when {
        style == UserViewStyle.HUGE -> FontWeight.Bold
        isAuthor -> FontWeight.SemiBold
        else -> FontWeight.Medium
    }

    val nameView: @Composable () -> Unit = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.clickable { showId = !showId },
        ) {
            Text(
                if (showId) (resolved?.idDisplayString ?: id).ifEmpty { name } else name,
                style = nameStyle.copy(fontWeight = nameWeight),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (style != UserViewStyle.VERTICAL) {
                if (resolved?.mute == true) {
                    Icon(
                        Icons.Filled.MicOff,
                        contentDescription = null,
                        modifier = Modifier.size(nameStyle.fontSize.value.dp * 0.9f),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                if (isAuthor && showAuthorIndicator) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        modifier = Modifier.size(nameStyle.fontSize.value.dp * 0.9f),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }

    val showDetails = style == UserViewStyle.HUGE ||
        (style == UserViewStyle.NORMAL)
    val showRegDate = style == UserViewStyle.HUGE ||
        (style == UserViewStyle.NORMAL && showRegDatePref)
    val showIpLocation = style == UserViewStyle.HUGE && !resolved?.ipLocation.isNullOrEmpty()

    val stats: @Composable () -> Unit = {
        StatsFlow(
            user = resolved,
            showRegDate = showRegDate,
            showIpLocation = showIpLocation,
        )
    }

    when (style) {
        UserViewStyle.VERTICAL -> Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            avatar()
            Box(Modifier.width((style.avatarSize * 1.5f).dp)) { nameView() }
        }
        else -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            avatar()
            Column(
                verticalArrangement =
                    Arrangement.spacedBy(if (style == UserViewStyle.HUGE) 4.dp else 2.dp),
            ) {
                nameView()
                if (showDetails) stats()
            }
        }
    }
}

/** Wrapping footnote stats row: posts, fame, registration date, IP location. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsFlow(
    user: User?,
    showRegDate: Boolean,
    showIpLocation: Boolean,
) {
    val context = LocalContext.current
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val red = MaterialTheme.colorScheme.error
    val footnote = MaterialTheme.typography.bodySmall

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val postNum = user?.postNum ?: 0
        StatItem(
            icon = Icons.Outlined.Comment,
            text = postNum.toString(),
            tint = if (postNum in 1..49) red else secondary,
            style = footnote,
        )
        val fame = user?.fame ?: 0L
        StatItem(
            icon = Icons.Outlined.Flag,
            // Fame is stored x10 signed; render with one decimal.
            text = String.format(Locale.US, "%.01f", fame / 10.0),
            tint = if (fame < 0) red else secondary,
            style = footnote,
        )
        if (showRegDate) {
            StatItem(
                icon = Icons.Outlined.Event,
                text = SimpleDateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                    .format(Date((user?.regDate ?: 0L) * 1000)),
                tint = secondary,
                style = footnote,
            )
        }
        if (showIpLocation) {
            StatItem(
                icon = Icons.Filled.Place,
                text = user?.ipLocation?.takeIf { it.isNotEmpty() }
                    ?: L.str(context, "Unknown"),
                tint = secondary,
                style = footnote,
            )
        }
    }
}

@Composable
private fun StatItem(
    icon: ImageVector,
    text: String,
    tint: Color,
    style: androidx.compose.ui.text.TextStyle,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(13.dp), tint = tint)
        Text(text, style = style, color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The big profile header card (section 1 of `UserProfileView`): huge user
 * header plus signature and, when requested, the profile action row.
 */
@Composable
fun UserProfileHeader(
    user: User,
    blocked: Boolean,
    isMyself: Boolean,
    modifier: Modifier = Modifier,
    onAvatarClick: (String) -> Unit = {},
    onEditSignature: () -> Unit = {},
    onNewMessage: () -> Unit = {},
    onBlockUser: () -> Unit = {},
    onShare: () -> Unit = {},
    signatureContent: (@Composable (PostContent) -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UserView(
                user = user,
                style = UserViewStyle.HUGE,
                onAvatarClick = onAvatarClick,
            )
            if (user.signature.spansList.isNotEmpty() && !blocked) {
                UserSignatureView(
                    content = user.signature,
                    contentRenderer = signatureContent,
                )
            }
            ActionRow(
                isMyself = isMyself,
                anonymous = user.isAnonymousUser,
                blocked = blocked,
                onEditSignature = onEditSignature,
                onNewMessage = onNewMessage,
                onBlockUser = onBlockUser,
                onShare = onShare,
            )
        }
    }
}

@Composable
private fun ActionRow(
    isMyself: Boolean,
    anonymous: Boolean,
    blocked: Boolean,
    onEditSignature: () -> Unit,
    onNewMessage: () -> Unit,
    onBlockUser: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val tint = MaterialTheme.colorScheme.onSurfaceVariant
        if (isMyself) {
            HeaderAction(Icons.Outlined.EditNote, "Edit Signature", tint, onEditSignature)
        }
        if (!isMyself && !anonymous) {
            HeaderAction(Icons.AutoMirrored.Filled.Chat, "New Short Message", tint, onNewMessage)
        }
        if (!isMyself) {
            HeaderAction(
                Icons.Outlined.FrontHand,
                if (blocked) "Unblock This User" else "Block This User",
                tint,
                onBlockUser,
            )
        }
        if (!anonymous) {
            HeaderAction(Icons.Filled.Share, "Share", tint, onShare)
        }
    }
}

@Composable
private fun HeaderAction(
    icon: ImageVector,
    labelKey: String,
    tint: Color,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Icon(icon, contentDescription = L.str(context, labelKey), tint = tint)
        Text(
            L.str(context, labelKey),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            maxLines = 1,
        )
    }
}

/** Plus gate helper; always true in this port, see [PlusModel.ALWAYS_UNLOCKED]. */
internal fun checkPlusFeature(feature: PlusFeature): Boolean =
    PlusModel.shared?.let { PlusModel.checkPlus(feature) } == true
