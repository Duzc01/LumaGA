package com.bugenzhao.mnga.ui.post

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBox
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableIntState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.model.NavigationIdentifier
import com.bugenzhao.mnga.model.ToastModel
import com.bugenzhao.mnga.protos.datamodel.ForumId
import com.bugenzhao.mnga.protos.datamodel.Post
import com.bugenzhao.mnga.protos.datamodel.PostContent
import com.bugenzhao.mnga.protos.datamodel.PostId
import com.bugenzhao.mnga.protos.datamodel.Span
import com.bugenzhao.mnga.util.DiceRoller
import com.bugenzhao.mnga.util.L
import com.bugenzhao.mnga.util.Stickers
import com.bugenzhao.mnga.util.URLs

/** Post body text sizes; mirrors the iOS `PostFontSize` enum. */
enum class PostFontSize { SMALL, NORMAL }

/**
 * Navigation callbacks the rendered content posts back to the host screen.
 * Mirrors the SwiftUI `TopicDetailsActionModel` writes plus the image viewer
 * and URL opener.
 */
interface ContentActions {
    fun navigateToPost(id: PostId) {}
    fun navigateToTopic(tid: String) {}
    fun navigateToForum(id: ForumId) {}
    fun navigateToUser(uid: String) {}
    fun navigateToUserName(name: String) {}
    fun openURL(url: String) {}
    fun showReplyChain(from: PostId) {}
    fun viewImages(urls: List<String>, current: String) {}

    object None : ContentActions
}

/**
 * Environment for one `PostContent` render, mirroring the `ContentCombiner`
 * environment keys plus the dice context post (from which `id`, `postDate` and
 * `authorId` are derived).
 */
data class ContentEnv(
    val inQuote: Boolean = false,
    val inInlineReplyQuote: Boolean = false,
    val replyTo: PostId? = null,
    val diceContext: Post? = null,
    val actions: ContentActions = ContentActions.None,
)

/**
 * Global-per-render dice seed offset: every `[collapse]` block consumes one so
 * dice inside different collapse blocks of the same post differ (upstream
 * `diceCollapseCounter` env, deliberately rooted at the render root).
 */
val LocalDiceCollapseCounter = compositionLocalOf<MutableIntState> { mutableIntStateOf(0) }

/** Exact port of the upstream color palette (name -> color). */
val ContentPalette: Map<String, Color> = linkedMapOf(
    "skyblue" to Color(0xFF87CEEB),
    "royalblue" to Color(0xFF4169E1),
    "blue" to Color(0xFF0066BB),
    "darkblue" to Color(0xFF00008B),
    "orange" to Color(0xFFA06700),
    "orangered" to Color(0xFFFF4500),
    "crimson" to Color(0xFFDC143C),
    "red" to Color(0xFFDD0000),
    "firebrick" to Color(0xFFB22222),
    "darkred" to Color(0xFF8B0000),
    "green" to Color(0xFF3D9F0E),
    "limegreen" to Color(0xFF32CD32),
    "seagreen" to Color(0xFF2E8B57),
    "teal" to Color(0xFF008080),
    "deeppink" to Color(0xFFFF1493),
    "tomato" to Color(0xFFFF6347),
    "coral" to Color(0xFFFF7F50),
    "purple" to Color(0xFF800080),
    "indigo" to Color(0xFF4B0082),
    "burlywood" to Color(0xFFDEB887),
    "sandybrown" to Color(0xFFF4A460),
    "chocolate" to Color(0xFFD2691E),
    "sienna" to Color(0xFFA0522D),
    "silver" to Color(0xFF888888),
    "white" to Color(0xFFFFFFFF),
)

/** iOS font anchors used by the combiner (callout 16 / subheadline 15 / body 17 / footnote 13 / headline 17). */
private val CALLOUT = 16.sp
private val SUBHEADLINE = 15.sp
private val FOOTNOTE = 13.sp

/** Inline sticker edge, used for both the line box (sp) and the image (dp). */
private const val STICKER_SIZE = 58

/** Horizontal alignment, from `[align]` or the container default. */
internal enum class HAlign { START, CENTER, END }

/** Resolved text styling for one run (the `font`/`color`/`otherStyles` chain). */
internal data class StyleSpec(
    val fontSize: TextUnit = TextUnit.Unspecified,
    val color: Color? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strikethrough: Boolean = false,
    val monospace: Boolean = false,
)

/** One inline element of a text paragraph. */
internal sealed interface InlinePiece {
    val spec: StyleSpec

    data class Text(val text: String, override val spec: StyleSpec) : InlinePiece
    data class Sticker(val name: String, override val spec: StyleSpec) : InlinePiece
    data class Icon(val icon: ImageVector, override val spec: StyleSpec) : InlinePiece
    data class Click(val text: String, override val spec: StyleSpec, val action: ClickAction) : InlinePiece
}

/** Tap targets embedded in text (uid chips, pid references). */
internal sealed interface ClickAction {
    data class Post(val id: PostId) : ClickAction
    data class User(val uid: String) : ClickAction
}

internal enum class TableContext { NONE, TABLE, ROW }

/** The render tree produced by [ContentCombiner]. */
internal sealed interface ContentNode {
    data class Paragraph(
        val pieces: List<InlinePiece>,
        val alignment: HAlign = HAlign.START,
        val maxLines: Int? = null,
    ) : ContentNode

    data class Container(
        val items: List<ContentNode>,
        val tableContext: TableContext = TableContext.NONE,
        val alignment: HAlign = HAlign.START,
        val spacing: androidx.compose.ui.unit.Dp = 12.dp,
    ) : ContentNode

    /** A concrete composable slot; `weight` participates in table rows. */
    class View(
        val weight: Float? = null,
        val content: @Composable () -> Unit,
    ) : ContentNode
}

internal sealed interface Subview {
    data class Text(val pieces: List<InlinePiece>) : Subview
    data object BreakLine : Subview
    data class Other(val node: ContentNode) : Subview
}

/** Render-scoped values captured at composition time (no composable calls later). */
internal class RenderHost(
    val context: Context,
    val actions: ContentActions,
    val accent: Color,
    val onSurface: Color,
    val secondary: Color,
    val appVersion: String,
    val diceCounter: MutableIntState,
) {
    fun toastInvalidURL(urlString: String) {
        ToastModel.showAuto(ToastModel.Message.Error(L.str(context, "Invalid URL") + ": " + urlString))
    }

    /** Classify and dispatch a link, mirroring `URL.mngaNavigationIdentifier`. */
    fun open(urlString: String) {
        val trimmed = urlString.trim()
        if (trimmed.isEmpty()) return
        val full =
            if (trimmed.contains("://")) trimmed
            else URLs.base.trimEnd('/') + "/" + trimmed.trimStart('/')
        val uri = runCatching { Uri.parse(full) }.getOrNull()
        if (uri == null || uri.scheme.isNullOrEmpty()) {
            toastInvalidURL(urlString)
            return
        }
        when (val nav = NavigationIdentifier.parse(uri)) {
            is NavigationIdentifier.TopicID -> actions.navigateToTopic(nav.tid)
            is NavigationIdentifier.PostID ->
                actions.navigateToPost(PostId.newBuilder().setPid(nav.pid).build())
            is NavigationIdentifier.ForumID -> actions.navigateToForum(nav.id)
            is NavigationIdentifier.UserID -> actions.navigateToUser(nav.uid)
            is NavigationIdentifier.UserNameID -> actions.navigateToUserName(nav.name)
            null -> actions.openURL(full)
        }
    }
}

/**
 * The recursive span renderer, ported from `Utilities/ContentCombiner.swift`.
 * Runs inside composition but performs no composable calls itself; it produces
 * a [ContentNode] tree rendered by [RenderNode].
 */
internal class ContentCombiner(
    private val host: RenderHost,
    private val parent: ContentCombiner? = null,
    private val fontModifier: (StyleSpec?) -> StyleSpec? = { it },
    private val colorModifier: (Color?) -> Color? = { it },
    private val otherStylesModifier: (Int) -> Int = { it },
    overrideAlignment: HAlign? = null,
    private val rootStyle: StyleSpec? = null,
) {
    companion object {
        private const val UNDERLINE = 1
        private const val STRIKETHROUGH = 1 shl 1
        private val ignoredTags = setOf("font")
        private val durationRegex = Regex("""duration=([^&]+)""", RegexOption.IGNORE_CASE)
    }

    private val subviews = mutableListOf<Subview>()
    private val envs = mutableMapOf<String, Any?>()
    var lineLimitLocal: Int? = null
    private var pendingClick: ClickAction? = null

    val font: StyleSpec? get() = fontModifier(parent?.font ?: rootStyle)
    private val color: Color? get() = colorModifier(parent?.color ?: rootStyle?.color)
    private val otherStyles: Int get() = otherStylesModifier(parent?.otherStyles ?: 0)
    val alignment: HAlign = overrideAlignment ?: parent?.alignment ?: HAlign.START

    // MARK: env plumbing

    private fun setEnvLocal(key: String, value: Any?) { envs[key] = value }

    private fun setEnvGlobal(key: String, value: Any?) {
        if (parent == null) envs[key] = value else parent.setEnvGlobal(key, value)
    }

    private fun getEnv(key: String): Any? = envs[key] ?: parent?.getEnv(key)

    var inInlineReplyQuote: Boolean
        get() = getEnv("inInlineReplyQuote") != null
        set(value) = setEnvLocal("inInlineReplyQuote", if (value) "true" else null)

    val inQuote: Boolean
        get() = inInlineReplyQuote || getEnv("inQuote") != null

    fun markInQuote() { setEnvLocal("inQuote", "true") }

    var replyTo: PostId?
        get() = getEnv("replyTo") as? PostId
        set(value) { setEnvLocal("replyTo", value) }

    private val selfId: PostId? get() = getEnv("id") as? PostId
    private val postDate: Long? get() = getEnv("postDate") as? Long

    var tableContext: TableContext
        get() = getEnv("tableContext") as? TableContext ?: TableContext.NONE
        set(value) { setEnvLocal("tableContext", value) }

    var diceContext: DiceRoller.Context?
        get() = getEnv("diceContext") as? DiceRoller.Context
        set(value) { setEnvLocal("diceContext", value) }

    private val effectiveLineLimit: Int?
        get() = lineLimitLocal ?: parent?.effectiveLineLimit

    private fun nextDiceSeedOffset(): Int {
        val next = (getEnv("diceCollapseCounter") as? Int ?: 0) + 1
        setEnvGlobal("diceCollapseCounter", next)
        host.diceCounter.intValue = next // keep LocalDiceCollapseCounter in sync
        return next
    }

    /** Root-only: publish the containing post's identity into the env. */
    fun seedRoot(id: PostId?, postDate: Long?, authorId: String?) {
        setEnvLocal("id", id)
        setEnvLocal("postDate", postDate)
        setEnvLocal("authorId", authorId)
    }

    fun replaceEnvs(from: Map<String, Any?>) {
        envs.clear()
        envs.putAll(from)
    }

    // MARK: appending

    private fun currentSpec(overriddenFont: StyleSpec? = null, overriddenColor: Color? = null): StyleSpec {
        val base = overriddenFont ?: font ?: StyleSpec()
        return base.copy(
            color = overriddenColor ?: color ?: base.color,
            underline = base.underline || (otherStyles and UNDERLINE != 0),
            strikethrough = base.strikethrough || (otherStyles and STRIKETHROUGH != 0),
        )
    }

    private fun appendText(piece: InlinePiece) {
        val spec = piece.spec
        val wrapped: InlinePiece =
            if (pendingClick != null) {
                when (piece) {
                    is InlinePiece.Text -> InlinePiece.Click(piece.text, spec, pendingClick!!)
                    is InlinePiece.Icon -> piece // glyph stays inert
                    else -> piece
                }
            } else piece
        subviews.add(Subview.Text(listOf(wrapped)))
    }

    private fun appendPlainText(raw: String) {
        val text = if (raw == "Post by ") L.str(host.context, "Post by") + " " else raw
        appendText(InlinePiece.Text(text, currentSpec()))
    }

    private fun appendBreakLine() { subviews.add(Subview.BreakLine) }

    private fun appendOther(node: ContentNode) { subviews.add(Subview.Other(node)) }

    private fun appendBuilt(node: ContentNode?) {
        when (node) {
            null -> {}
            is ContentNode.Paragraph -> subviews.add(Subview.Text(node.pieces))
            else -> appendOther(node)
        }
    }

    // MARK: build() — the paragraph-merging algorithm (ported exactly)

    fun build(): ContentNode? {
        var textBuffer = mutableListOf<InlinePiece>()
        val results = mutableListOf<ContentNode>()

        fun flush() {
            if (textBuffer.isNotEmpty()) {
                results.add(ContentNode.Paragraph(textBuffer.toList(), alignment, effectiveLineLimit))
                textBuffer = mutableListOf()
            }
        }

        val trimmed = subviews.dropWhile { it == Subview.BreakLine }
            .dropLastWhile { it == Subview.BreakLine }

        for (subview in trimmed) {
            when (subview) {
                is Subview.Text -> textBuffer.addAll(subview.pieces)
                is Subview.BreakLine -> flush()
                is Subview.Other -> {
                    flush()
                    results.add(subview.node)
                }
            }
        }

        if (results.isEmpty()) {
            return if (textBuffer.isNotEmpty()) {
                ContentNode.Paragraph(textBuffer.toList(), alignment, effectiveLineLimit)
            } else null
        }

        flush()
        // Truncate overly long inline reply quotes.
        val limited =
            if (tableContext == TableContext.NONE && inInlineReplyQuote && results.size > 5) {
                results.take(5)
            } else results
        val spacing = if (inQuote) 8.dp else 12.dp
        return ContentNode.Container(limited, tableContext, alignment, spacing)
    }

    // MARK: visiting

    fun visit(spans: List<Span>) { spans.forEach(::visit) }

    fun visit(span: Span) {
        when {
            span.hasBreakLine() -> appendBreakLine()
            span.hasPlain() -> visitPlain(span.plain)
            span.hasSticker() -> visitSticker(span.sticker)
            span.hasTagged() -> visitTagged(span.tagged)
        }
    }

    private fun visitPlain(plain: Span.Plain) {
        if (!plain.text.contains("[*]")) {
            appendPlainText(plain.text)
            return
        }
        val segments = plain.text.split("[*]")
        segments.forEachIndexed { index, segment ->
            if (segment.isEmpty()) return@forEachIndexed
            if (index == 0) {
                appendPlainText(segment)
            } else {
                buildListItemView(listOf(plainSpan(segment)))?.let(::appendOther)
            }
        }
    }

    private fun splitListItems(spans: List<Span>): List<List<Span>> {
        val items = mutableListOf<List<Span>>()
        var current = mutableListOf<Span>()
        fun flushCurrent() {
            if (current.isNotEmpty()) {
                items.add(current)
                current = mutableListOf()
            }
        }
        for (span in spans) {
            if (!span.hasPlain()) {
                current.add(span)
                continue
            }
            val plain = span.plain
            if (!plain.text.contains("[*]")) {
                current.add(span)
                continue
            }
            val segments = plain.text.split("[*]")
            segments.forEachIndexed { index, segment ->
                if (index == 0) {
                    if (segment.isNotEmpty()) current.add(plainSpan(segment))
                    return@forEachIndexed
                }
                flushCurrent()
                if (segment.isNotEmpty()) current.add(plainSpan(segment))
            }
        }
        flushCurrent()
        return items
    }

    private fun buildListItemView(spans: List<Span>): ContentNode? {
        val itemCombiner = ContentCombiner(host, parent = this)
        itemCombiner.visit(spans)
        val content = itemCombiner.build() ?: return null
        val bulletSpec = currentSpec()
        return ContentNode.View(weight = 1f) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "•",
                    style = MaterialTheme.typography.bodyMedium,
                    fontSize = bulletSpec.fontSize,
                    color = bulletSpec.color ?: Color.Unspecified,
                    modifier = Modifier.width(12.dp),
                )
                Box(Modifier.weight(1f)) { RenderNode(content) }
            }
        }
    }

    private fun visitSticker(sticker: Span.Sticker) {
        val name = sticker.name.replace(':', '|')
        if (name in Stickers.all) {
            val spec = currentSpec()
            appendText(InlinePiece.Sticker(name, spec))
        } else {
            appendText(InlinePiece.Text("[🐶" + sticker.name + "]", currentSpec().copy(color = host.secondary)))
        }
    }

    // MARK: tag dispatch

    private fun visitTagged(tagged: Span.Tagged) {
        when {
            tagged.tag == "_divider" || tagged.tag == "h" -> visitDivider(tagged)
            tagged.tag == "img" -> visitImage(tagged)
            tagged.tag == "album" -> visitAlbum(tagged)
            tagged.tag == "noimg" -> visitNoimg(tagged)
            tagged.tag == "quote" -> visitQuote(tagged)
            tagged.tag == "b" -> visitBold(tagged)
            tagged.tag == "uid" -> visitUid(tagged)
            tagged.tag == "pid" -> visitPid(tagged)
            tagged.tag == "tid" -> visitTid(tagged)
            tagged.tag == "url" -> visitUrl(tagged)
            tagged.tag == "code" -> visitCode(tagged)
            tagged.tag == "u" -> visitUnderlined(tagged)
            tagged.tag == "i" -> visitItalic(tagged)
            tagged.tag == "del" -> visitDeleted(tagged)
            tagged.tag == "color" -> visitColored(tagged)
            tagged.tag == "size" -> visitSized(tagged)
            tagged.tag == "collapse" -> visitCollapsed(tagged)
            tagged.tag == "flash" -> visitFlash(tagged)
            tagged.tag == "attach" -> visitAttach(tagged)
            tagged.tag == "list" -> visitList(tagged)
            tagged.tag == "align" -> visitAlign(tagged)
            tagged.tag == "table" -> visitTable(tagged)
            tagged.tag == "tr" -> visitTableRow(tagged)
            tagged.tag.startsWith("td") -> visitTableCell(tagged)
            tagged.tag == "dice" -> visitDice(tagged)
            tagged.tag == "at" -> visitAt(tagged)
            tagged.tag == "_mnga" -> visitMnga(tagged)
            else -> visitDefaultTagged(tagged)
        }
    }

    private fun visitDivider(tagged: Span.Tagged) {
        val combiner = ContentCombiner(
            host, parent = this,
            fontModifier = { StyleSpec(fontSize = 17.sp, color = host.accent, bold = true) },
        )
        if (tagged.spansCount > 0) {
            combiner.appendOther(ContentNode.View {
                Spacer(Modifier.height(6.dp))
            })
            combiner.visit(tagged.spansList)
        }
        combiner.appendOther(ContentNode.View { HorizontalDivider() })
        appendBuilt(combiner.build())
    }

    private fun visitImage(tagged: Span.Tagged) {
        val urlText = firstPlainText(tagged) ?: return
        val url = URLs.attachmentURL(urlText) ?: return
        if (url.substringAfterLast('.').equals("mp4", true)) {
            visitFlashVideo(url)
            return
        }
        val onlyThumbs = inQuote && replyTo != null
        appendOther(ContentImageViewNode(url, onlyThumbs))
    }

    private fun ContentImageViewNode(url: String, onlyThumbs: Boolean): ContentNode =
        ContentNode.View {
            ContentImageView(
                url = url,
                onlyThumbs = onlyThumbs,
                onViewImage = { urls, current -> host.actions.viewImages(urls, current) },
            )
        }

    private fun visitAlbum(tagged: Span.Tagged) {
        val urls = tagged.spansList.filter { it.hasPlain() }
        val name = (tagged.attributesList.firstOrNull() ?: L.str(host.context, "Album")) +
            " (" + urls.size + ")"
        visitDivider(spanBuilder("h") { addSpan(plainSpan(name)) })
        for (url in urls) {
            visitImage(spanBuilder("img") { addSpan(url) })
        }
    }

    private fun visitNoimg(tagged: Span.Tagged) {
        val urlText = firstPlainText(tagged) ?: return
        val date = postDate ?: return

        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("GMT+8"))
        calendar.timeInMillis = date * 1000
        val prefix = "mon_%04d%02d/%02d/".format(
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH) + 1,
            calendar.get(java.util.Calendar.DAY_OF_MONTH),
        )
        val url = URLs.attachmentURL(prefix + urlText) ?: return
        if (url.substringAfterLast('.').equals("mp4", true)) {
            visitFlashVideo(url)
            return
        }
        val onlyThumbs = inQuote && replyTo != null
        appendOther(ContentImageViewNode(url, onlyThumbs))
    }

    private class QuoteMeta(val pid: PostId, val uid: String, val username: String?, val envs: Map<String, Any?>)

    private fun buildQuoteMeta(spans: List<Span>): QuoteMeta? {
        val metaCombiner = ContentCombiner(host)
        metaCombiner.markInQuote()
        metaCombiner.visit(spans)
        val pid = metaCombiner.replyTo ?: return null
        val uid = metaCombiner.getEnv("uid") as? String ?: return null
        val username = metaCombiner.getEnv("username") as? String
        return QuoteMeta(pid, uid, username, metaCombiner.envs.toMap())
    }

    private fun visitQuote(tagged: Span.Tagged) {
        val combiner = ContentCombiner(
            host, parent = this,
            fontModifier = { f -> (f ?: StyleSpec()).copy(fontSize = if (f?.fontSize == CALLOUT) SUBHEADLINE else CALLOUT) },
            colorModifier = { _ -> host.onSurface.copy(alpha = 0.9f) },
        )
        combiner.markInQuote()

        val spans = tagged.spansList
        val metaSpans = spans.takeWhile { !it.hasBreakLine() }

        var tapAction: (() -> Unit)? = null
        if (metaSpans.isNotEmpty()) {
            val meta = buildQuoteMeta(metaSpans)
            if (meta != null) {
                if (inInlineReplyQuote) return // skip nested reply quotes
                if (selfId != null) {
                    val self = selfId!!
                    tapAction = { host.actions.showReplyChain(self) }
                    combiner.lineLimitLocal = 5
                }
                combiner.appendOther(
                    ContentNode.View {
                        QuoteUserView(
                            uid = meta.uid,
                            nameHint = meta.username,
                            onNavigateToPost = tapAction,
                            onNavigateToUser = { host.actions.navigateToUser(it) },
                        )
                    }
                )
                combiner.replaceEnvs(meta.envs)
                combiner.visit(spans.drop(metaSpans.size))
                appendOther(QuoteNode(combiner.build()))
                return
            }
        }
        // No metadata extracted: plain quote formatting.
        combiner.visit(spans)
        appendOther(QuoteNode(combiner.build()))
    }

    private fun QuoteNode(inner: ContentNode?): ContentNode = ContentNode.View {
        QuoteView(fullWidth = true) { RenderNode(inner) }
    }

    private fun visitBold(tagged: Span.Tagged) {
        val first = tagged.spansList.firstOrNull()?.takeIf { it.hasPlain() }?.plain?.text
        if (first?.startsWith("Reply to") == true) {
            val metaSpans = tagged.spansList.drop(1)
            val meta = if (metaSpans.isNotEmpty()) buildQuoteMeta(metaSpans) else null
            if (meta != null) {
                if (inInlineReplyQuote) return // skip nested reply quotes
                val quotedFontSize = if (font?.fontSize == CALLOUT) SUBHEADLINE else CALLOUT
                val quotedFontSpec = (font ?: StyleSpec()).copy(fontSize = quotedFontSize)
                appendOther(ContentNode.View {
                    InlineQuotedPost(
                        postId = meta.pid,
                        uid = meta.uid,
                        nameHint = meta.username,
                        sourcePostId = selfId,
                        defaultStyle = quotedFontSpec,
                        host = host,
                    )
                })
                return
            }
        }
        val combiner = ContentCombiner(host, parent = this, fontModifier = { f -> (f ?: StyleSpec()).copy(bold = true) })
        combiner.visit(tagged.spansList)
        appendBuilt(combiner.build())
    }

    private fun visitUid(tagged: Span.Tagged) {
        val combiner = ContentCombiner(host, parent = this, colorModifier = { _ -> host.accent })
        combiner.appendText(InlinePiece.Icon(Icons.Outlined.Person, combiner.currentSpec()))
        combiner.pendingClick = ClickAction.User(tagged.attributesList.firstOrNull() ?: "")
        combiner.visit(tagged.spansList)
        combiner.pendingClick = null

        val name = tagged.spansList.firstOrNull()?.takeIf { it.hasPlain() }?.plain?.text
        if (!name.isNullOrEmpty()) setEnvGlobal("username", name)
        val uidEnv = tagged.attributesList.firstOrNull() ?: name
        if (uidEnv != null) setEnvGlobal("uid", uidEnv)

        appendBuilt(combiner.build())
    }

    private fun visitPid(tagged: Span.Tagged) {
        val combiner = ContentCombiner(host, parent = this, fontModifier = { f -> (f ?: StyleSpec()).copy(bold = true) })
        if (tagged.attributesCount > 2) {
            val id = PostId.newBuilder()
                .setPid(tagged.attributesList[0])
                .setTid(tagged.attributesList[1])
                .build()
            combiner.pendingClick = ClickAction.Post(id)
            combiner.appendText(InlinePiece.Text("Post", combiner.currentSpec()))
            combiner.appendText(InlinePiece.Text(" #" + id.pid + " ", combiner.currentSpec()))
            replyTo = id
        } else {
            combiner.appendText(InlinePiece.Text("Post", combiner.currentSpec()))
        }
        appendBuilt(combiner.build())
    }

    private fun visitTid(tagged: Span.Tagged) {
        val tid = tagged.attributesList.firstOrNull()
        if (tid != null) {
            replyTo = PostId.newBuilder().setPid("0").setTid(tid).build()
            val url = spanBuilder("url") {
                addSpans(tagged.spansList)
                addAttribute("/read.php?tid=" + tid)
            }
            visitUrl(url, defaultTitle = "Topic " + tid, icon = Icons.Outlined.Link)
        }
    }

    private fun visitUrl(
        tagged: Span.Tagged,
        defaultTitle: String? = null,
        icon: ImageVector = Icons.Outlined.Link,
    ) {
        val combiner = ContentCombiner(
            host, parent = this,
            fontModifier = { StyleSpec(fontSize = FOOTNOTE) },
            colorModifier = { _ -> host.accent },
        )
        combiner.visit(tagged.spansList)
        val inner: ContentNode? =
            if (tagged.spansCount == 0 && defaultTitle != null) {
                ContentNode.Paragraph(listOf(InlinePiece.Text(defaultTitle, combiner.currentSpec())))
            } else {
                combiner.build()
            }
        val urlString = tagged.attributesList.firstOrNull()
            ?: tagged.spansList.firstOrNull()?.takeIf { it.hasPlain() }?.plain?.text

        appendOther(ContentNode.View {
            ContentButton(icon = icon, title = inner?.let { node -> { RenderNode(node) } }, inQuote = inQuote) {
                if (urlString != null) host.open(urlString)
            }
        })
    }

    private fun visitDice(tagged: Span.Tagged) {
        val expression = firstPlainText(tagged) ?: return
        val context = diceContext
        appendOther(ContentNode.View {
            if (context != null) {
                DiceView(DiceRoller.roll(expression, context))
            } else {
                UnresolvedDiceView(expression)
            }
        })
    }

    private fun visitCode(tagged: Span.Tagged) {
        val combiner = ContentCombiner(
            host, parent = this,
            fontModifier = { StyleSpec(fontSize = FOOTNOTE, monospace = true) },
        )
        combiner.visit(tagged.spansList)
        appendBuilt(combiner.build())
    }

    private fun visitUnderlined(tagged: Span.Tagged) {
        val combiner = ContentCombiner(host, parent = this, otherStylesModifier = { it or UNDERLINE })
        combiner.visit(tagged.spansList)
        appendBuilt(combiner.build())
    }

    private fun visitItalic(tagged: Span.Tagged) {
        val combiner = ContentCombiner(host, parent = this, fontModifier = { f -> (f ?: StyleSpec()).copy(italic = true) })
        combiner.visit(tagged.spansList)
        appendBuilt(combiner.build())
    }

    private fun visitDeleted(tagged: Span.Tagged) {
        val combiner = ContentCombiner(host, parent = this, otherStylesModifier = { it or STRIKETHROUGH })
        combiner.visit(tagged.spansList)
        appendBuilt(combiner.build())
    }

    private fun visitColored(tagged: Span.Tagged) {
        val color = tagged.attributesList.firstOrNull()?.let { ContentPalette[it] }
        val combiner = ContentCombiner(host, parent = this, colorModifier = { c -> color ?: c })
        combiner.visit(tagged.spansList)
        appendBuilt(combiner.build())
    }

    private fun visitSized(tagged: Span.Tagged) {
        val scale = tagged.attributesList.firstOrNull()
            ?.trim('%')?.toDoubleOrNull() ?: 100.0
        val factor = (scale / 100.0).toFloat()
        val combiner = ContentCombiner(host, parent = this, fontModifier = { f ->
            if (f == null) f else f.copy(fontSize = f.fontSize * factor)
        })
        combiner.visit(tagged.spansList)
        appendBuilt(combiner.build())
    }

    private fun visitCollapsed(tagged: Span.Tagged) {
        val title = tagged.attributesList.firstOrNull() ?: L.str(host.context, "Collapsed Content")
        val combiner = ContentCombiner(host, parent = this)
        val seedOffset = nextDiceSeedOffset()
        diceContext?.let { combiner.diceContext = it.copy(withSeedOffset = seedOffset) }
        combiner.visit(tagged.spansList)
        val content = combiner.build()
        appendOther(ContentNode.View {
            CollapsedContent(title) { RenderNode(content) }
        })
    }

    private fun visitFlash(tagged: Span.Tagged) {
        when (tagged.attributesList.firstOrNull()) {
            "audio" -> visitFlashAudio(tagged)
            else -> visitFlashVideoTag(tagged)
        }
    }

    private fun visitFlashVideoTag(tagged: Span.Tagged) {
        val urlText = firstPlainText(tagged) ?: return
        val url = URLs.attachmentURL(urlText) ?: return
        visitFlashVideo(url)
    }

    private fun visitFlashVideo(url: String) {
        appendOther(ContentNode.View {
            ContentButton(
                icon = Icons.Outlined.Movie,
                title = textNode(L.str(host.context, "View Video")),
                inQuote = inQuote,
            ) { host.actions.openURL(url) }
        })
    }

    private fun visitFlashAudio(tagged: Span.Tagged) {
        val text = firstPlainText(tagged) ?: return
        val tokens = text.split("?")
        val duration = tokens.lastOrNull { it.contains("duration") }
        val urlText = tokens.firstOrNull() ?: return
        val url = URLs.attachmentURL(urlText.trim()) ?: return
        val durationValue = duration?.let { durationRegex.find(it)?.groupValues?.get(1) }
        appendOther(ContentNode.View {
            ContentButton(
                icon = Icons.Outlined.GraphicEq,
                title = textNode(durationValue ?: L.str(host.context, "Audio")),
                inQuote = inQuote,
            ) { host.actions.openURL(url) }
        })
    }

    private fun visitAttach(tagged: Span.Tagged) {
        val urlText = firstPlainText(tagged) ?: return
        val url = URLs.attachmentURL(urlText) ?: return
        appendOther(ContentNode.View {
            ContentButton(
                icon = Icons.Outlined.AttachFile,
                title = textNode(L.str(host.context, "View Attachment")),
                inQuote = inQuote,
            ) { host.actions.openURL(url) }
        })
    }

    private fun visitList(tagged: Span.Tagged) {
        val hasListItemMarker = tagged.spansList.any { span ->
            span.hasPlain() && span.plain.text.contains("[*]")
        }
        if (!hasListItemMarker) {
            visit(tagged.spansList)
            return
        }
        val rows = splitListItems(tagged.spansList).mapNotNull(::buildListItemView)
        if (rows.isEmpty()) return
        rows.forEach(::appendOther)
    }

    private fun visitAlign(tagged: Span.Tagged) {
        val attr = tagged.attributesList.firstOrNull()
        val alignment = when (attr) {
            "center" -> HAlign.CENTER
            "left" -> HAlign.START
            "right" -> HAlign.END
            else -> null
        }
        val combiner = ContentCombiner(host, parent = this, overrideAlignment = alignment)
        combiner.visit(tagged.spansList)
        val inner = combiner.build()
        if (alignment == null) {
            appendBuilt(inner)
            return
        }
        appendOther(ContentNode.View(weight = 1f) {
            Box(
                Modifier.fillMaxWidth(),
                contentAlignment = when (alignment) {
                    HAlign.CENTER -> Alignment.Center
                    HAlign.END -> Alignment.CenterEnd
                    HAlign.START -> Alignment.CenterStart
                },
            ) { RenderNode(inner) }
        })
    }

    private fun visitTable(tagged: Span.Tagged) {
        val combiner = ContentCombiner(host, parent = this)
        combiner.tableContext = TableContext.TABLE
        combiner.visit(tagged.spansList)
        appendOther(combiner.build() ?: ContentNode.Container(emptyList(), TableContext.TABLE))
    }

    private fun visitTableRow(tagged: Span.Tagged) {
        val combiner = ContentCombiner(host, parent = this)
        combiner.tableContext = TableContext.ROW
        combiner.visit(tagged.spansList)
        appendOther(combiner.build() ?: ContentNode.Container(emptyList(), TableContext.ROW))
    }

    private fun visitTableCell(tagged: Span.Tagged) {
        val colSpan = tagged.complexAttributesList
            .firstOrNull { it.startsWith("colspan=") }
            ?.drop(8)?.toIntOrNull()
        val combiner = ContentCombiner(host, parent = this)
        // Reset so multiple views inside the cell combine normally.
        combiner.tableContext = TableContext.NONE
        combiner.visit(tagged.spansList)
        val cell = combiner.build()
        appendOther(
            ContentNode.View(weight = (colSpan ?: 1).toFloat()) {
                Box(Modifier.padding(end = 8.dp)) { RenderNode(cell) }
            }
        )
    }

    private fun visitAt(tagged: Span.Tagged) {
        val user = tagged.attributesList.firstOrNull() ?: return
        val urlString = "/nuke.php?func=ucp&" +
            (if (user.all { it.isDigit() }) "uid=" + user else "username=" + user)
        val url = spanBuilder("url") {
            addSpan(plainSpan(user))
            addAttribute(urlString)
        }
        visitUrl(url, defaultTitle = user, icon = Icons.Outlined.AccountBox)
    }

    private fun visitMnga(tagged: Span.Tagged) {
        val fn = tagged.attributesList.firstOrNull() ?: return
        if (fn != "version") return
        appendPlainText(host.appVersion)
    }

    private fun visitDefaultTagged(tagged: Span.Tagged) {
        if (tagged.tag in ignoredTags) {
            visit(tagged.spansList)
            return
        }
        val combiner = ContentCombiner(host, parent = this)
        var openTag = tagged.tag
        if (tagged.attributesCount > 0) openTag += "=" + tagged.attributesList.joinToString(",")
        if (tagged.complexAttributesCount > 0) openTag += " " + tagged.complexAttributesList.joinToString(" ")
        val tagSpec = StyleSpec(fontSize = FOOTNOTE, monospace = true)
        combiner.appendText(InlinePiece.Text("[" + openTag + "]", tagSpec))
        combiner.visit(tagged.spansList)
        combiner.appendText(InlinePiece.Text("[/" + tagged.tag + "]", tagSpec))
        appendBuilt(combiner.build())
    }

    // MARK: helpers

    private fun firstPlainText(tagged: Span.Tagged): String? =
        tagged.spansList.firstOrNull()?.takeIf { it.hasPlain() }?.plain?.text?.trim()

    private fun textNode(text: String): @Composable () -> Unit = { Text(text) }

    private fun plainSpan(text: String): Span = Span.newBuilder()
        .setPlain(Span.Plain.newBuilder().setText(text))
        .build()

    private class TagBuilder(private val tag: String) {
        val spans = mutableListOf<Span>()
        val attributes = mutableListOf<String>()
        fun addSpan(span: Span) { spans.add(span) }
        fun addSpans(list: List<Span>) { spans.addAll(list) }
        fun addAttribute(value: String) { attributes.add(value) }
        fun build(): Span.Tagged = Span.Tagged.newBuilder()
            .setTag(tag)
            .addAllSpans(spans)
            .addAllAttributes(attributes)
            .build()
    }

    private fun spanBuilder(tag: String, block: TagBuilder.() -> Unit): Span.Tagged =
        TagBuilder(tag).apply(block).build()
}

// MARK: rendering

/** Actions for embedded text taps; provided by [PostContent]. */
private val LocalContentActions = compositionLocalOf<ContentActions> { ContentActions.None }

private const val CLICK_TAG = "mnga-click"

private fun encodeClick(action: ClickAction): String = when (action) {
    is ClickAction.Post -> "p|" + action.id.pid + "|" + action.id.tid
    is ClickAction.User -> "u|" + action.uid
}

private fun dispatchClick(encoded: String, actions: ContentActions) {
    val parts = encoded.split("|", limit = 3)
    when (parts.firstOrNull()) {
        "p" -> actions.navigateToPost(
            PostId.newBuilder().setPid(parts.getOrElse(1) { "" }).setTid(parts.getOrElse(2) { "" }).build()
        )
        "u" -> if (parts.size > 1) actions.navigateToUser(parts[1])
    }
}

private fun toSpanStyle(spec: StyleSpec): SpanStyle = SpanStyle(
    color = spec.color ?: Color.Unspecified,
    fontSize = spec.fontSize.takeIf { it != TextUnit.Unspecified } ?: TextUnit.Unspecified,
    fontWeight = if (spec.bold) FontWeight.Bold else null,
    fontStyle = if (spec.italic) androidx.compose.ui.text.font.FontStyle.Italic else null,
    fontFamily = if (spec.monospace) FontFamily.Monospace else null,
    textDecoration = when {
        spec.underline && spec.strikethrough ->
            TextDecoration.combine(listOf(TextDecoration.Underline, TextDecoration.LineThrough))
        spec.underline -> TextDecoration.Underline
        spec.strikethrough -> TextDecoration.LineThrough
        else -> null
    },
)

/** Renders one merged text paragraph with inline stickers/icons and tap zones. */
@Composable
internal fun ParagraphText(paragraph: ContentNode.Paragraph) {
    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

    val annotated = remember(paragraph) {
        buildAnnotatedString {
            paragraph.pieces.forEachIndexed { index, piece ->
                val style = toSpanStyle(piece.spec)
                when (piece) {
                    is InlinePiece.Text -> withStyle(style) { append(piece.text) }
                    is InlinePiece.Click -> {
                        pushStringAnnotation(CLICK_TAG, encodeClick(piece.action))
                        withStyle(style) { append(piece.text) }
                        pop()
                    }
                    is InlinePiece.Sticker ->
                        appendInlineContent("stk-$index", "[" + piece.name.replace('|', ':') + "]")
                    is InlinePiece.Icon -> appendInlineContent("ico-$index", "[*]")
                }
            }
        }
    }
    val iconTintFallback = MaterialTheme.colorScheme.onSurface
    val inlineContent = remember(paragraph, iconTintFallback) {
        paragraph.pieces.mapIndexedNotNull { index, piece ->
            when (piece) {
                is InlinePiece.Sticker -> "stk-$index" to InlineTextContent(
                    Placeholder(
                        STICKER_SIZE.sp,
                        STICKER_SIZE.sp,
                        PlaceholderVerticalAlign.TextCenter,
                    )
                ) { StickerImage(piece.name, size = STICKER_SIZE.dp) }
                is InlinePiece.Icon -> "ico-$index" to InlineTextContent(
                    Placeholder(18.sp, 18.sp, PlaceholderVerticalAlign.TextCenter)
                ) {
                    Icon(
                        piece.icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = piece.spec.color ?: iconTintFallback,
                    )
                }
                else -> null
            }
        }.toMap()
    }

    val actions = LocalContentActions.current
    val alignment = when (paragraph.alignment) {
        HAlign.START -> Alignment.Start
        HAlign.CENTER -> Alignment.CenterHorizontally
        HAlign.END -> Alignment.End
    }
    // A sticker is much taller than the text, and the typography's fixed
    // lineHeight clamps the line box, so it would spill onto the neighbouring
    // lines. Leaving the line height unspecified lets only the line that
    // actually holds a sticker grow to fit it.
    val baseStyle = MaterialTheme.typography.bodyMedium
    val style = remember(paragraph, baseStyle) {
        if (paragraph.pieces.any { it is InlinePiece.Sticker }) {
            baseStyle.copy(lineHeight = TextUnit.Unspecified)
        } else {
            baseStyle
        }
    }
    Column(horizontalAlignment = alignment) {
        Text(
            annotated,
            inlineContent = inlineContent,
            style = style,
            onTextLayout = { layoutResult = it },
            maxLines = paragraph.maxLines ?: Int.MAX_VALUE,
            overflow = if (paragraph.maxLines != null) TextOverflow.Ellipsis else TextOverflow.Clip,
            modifier = Modifier.pointerInput(annotated) {
                detectTapGestures { position ->
                    val offset = layoutResult?.getOffsetForPosition(position) ?: return@detectTapGestures
                    annotated.getStringAnnotations(CLICK_TAG, offset, offset)
                        .firstOrNull()?.let { dispatchClick(it.item, actions) }
                }
            },
        )
    }
}

/** Renders the combiner output tree. */
@Composable
internal fun RenderNode(node: ContentNode?) {
    when (node) {
        null -> {}
        is ContentNode.Paragraph -> ParagraphText(node)
        is ContentNode.View -> node.content()
        is ContentNode.Container -> {
            when (node.tableContext) {
                TableContext.NONE ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(node.spacing),
                        horizontalAlignment = when (node.alignment) {
                            HAlign.START -> Alignment.Start
                            HAlign.CENTER -> Alignment.CenterHorizontally
                            HAlign.END -> Alignment.End
                        },
                    ) {
                        node.items.forEach { RenderNode(it) }
                    }
                TableContext.TABLE ->
                    Row(Modifier.horizontalScroll(rememberScrollState())) {
                        Column(verticalArrangement = Arrangement.spacedBy(node.spacing)) {
                            node.items.forEach { RenderNode(it) }
                        }
                    }
                TableContext.ROW ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        node.items.forEach { item ->
                            if (item is ContentNode.View && item.weight != null) {
                                Box(Modifier.weight(item.weight!!)) { item.content() }
                            } else {
                                RenderNode(item)
                            }
                        }
                    }
            }
        }
    }
}

/**
 * Accent-tinted action chip used for links/media, ported from
 * `Views/ContentButtonView.swift`.
 */
@Composable
fun ContentButton(
    icon: ImageVector,
    title: (@Composable () -> Unit)?,
    inQuote: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = if (inQuote) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
        onClick = onClick,
    ) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = accent)
            if (title != null) {
                androidx.compose.foundation.layout.Box {
                    androidx.compose.material3.ProvideTextStyle(
                        MaterialTheme.typography.bodySmall.copy(color = accent)
                    ) {
                        Column { title() }
                    }
                }
            }
        }
    }
}

// MARK: entry points

/**
 * Rich post content renderer, ported from `Views/PostContentView.swift` +
 * `Utilities/ContentCombiner.swift`.
 */
@Composable
fun PostContent(
    content: PostContent,
    env: ContentEnv = ContentEnv(),
    fontSize: PostFontSize = PostFontSize.NORMAL,
    defaultColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    lineLimit: Int? = null,
    baseFontSize: TextUnit? = null,
) {
    val context = LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val largerFont = App.prefs.postRowLargerFont.flow.collectAsState().value
    val baseSize = when (fontSize) {
        PostFontSize.SMALL -> if (largerFont) 16f else 15f
        PostFontSize.NORMAL -> if (largerFont) 17f else 16f
    }.sp
    val effectiveSize = baseFontSize ?: baseSize
    val resolvedColor = if (defaultColor == Color.Unspecified) onSurface else defaultColor
    val appVersion = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: ""
    }
    val counter = remember(content) { mutableIntStateOf(0) }

    val host = RenderHost(context, env.actions, accent, onSurface, secondary, appVersion, counter)
    val post = env.diceContext
    val root = ContentCombiner(
        host,
        rootStyle = StyleSpec(fontSize = effectiveSize, color = resolvedColor),
    )
    root.seedRoot(post?.id, post?.postDate, post?.authorId)
    if (env.inQuote) root.markInQuote()
    if (env.inInlineReplyQuote) root.inInlineReplyQuote = true
    env.replyTo?.let { root.replyTo = it }
    root.lineLimitLocal = lineLimit
    DiceRoller.Context.from(post?.authorId, post?.id?.tid, post?.id?.pid)?.let { root.diceContext = it }

    root.visit(content.spansList)
    val node = root.build()

    Column(modifier) {
        if (content.error.isNotEmpty()) {
            QuoteView(fullWidth = true) {
                Text(
                    L.str(context, "Bad or Unsupported Post Content Format") + "\n" + content.error,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = ContentPalette.getValue("orangered"),
                )
            }
        }
        CompositionLocalProvider(
            LocalContentActions provides env.actions,
            LocalDiceCollapseCounter provides counter,
        ) {
            RenderNode(node)
        }
    }
}

/** Convenience overload rendering a whole post. */
@Composable
fun PostContent(
    post: Post,
    env: ContentEnv = ContentEnv(),
    fontSize: PostFontSize = PostFontSize.NORMAL,
    defaultColor: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
    lineLimit: Int? = null,
    baseFontSize: TextUnit? = null,
) {
    PostContent(
        content = post.content,
        env = env.copy(diceContext = post),
        fontSize = fontSize,
        defaultColor = defaultColor,
        modifier = modifier,
        lineLimit = lineLimit,
        baseFontSize = baseFontSize,
    )
}
