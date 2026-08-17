package com.bugenzhao.mnga.model

import com.bugenzhao.mnga.protos.datamodel.PostAttachment
import com.bugenzhao.mnga.protos.service.AsyncRequest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Generic compose-and-send state machine behind every posting flow, ported
 * from `Models/GenericPostModel.swift`.
 */
abstract class GenericPostModel(private val scope: CoroutineScope) {

    /** Which page the refreshed list should reload after sending. */
    sealed class PageToReload {
        data object Last : PageToReload()
        data class Exact(val page: Int) : PageToReload()

        companion object {
            val First = Exact(1)
        }
    }

    interface Task {
        val actionTitle: String
        val hashKey: String
        fun buildUploadAttachmentRequest(data: ByteArray): AsyncRequest?
    }

    /** One compose session; identified by its random seed. */
    data class Context(
        val seed: UUID = UUID.randomUUID(),
        val task: Task,
        val to: String? = null,
        val subject: String? = null,
        val content: String? = null,
        val attachments: List<PostAttachment> = emptyList(),
        val anonymous: Boolean? = null,
    )

    private val _showEditor = MutableStateFlow(false)
    val showEditor: StateFlow<Boolean> = _showEditor

    private val _context = MutableStateFlow<Context?>(null)
    val context: StateFlow<Context?> = _context

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending

    private val _sent = MutableStateFlow<Context?>(null)
    val sent: StateFlow<Context?> = _sent

    private val contexts = HashMap<String, Context>()

    /** Present the editor after a short delay (let the trigger menu dismiss). */
    fun showAfter(action: Task) {
        scope.launch {
            delay(300)
            show(action)
        }
    }

    fun show(action: Task, pageToReload: PageToReload = PageToReload.Last) {
        val key = action.hashKey
        if (_showEditor.value) return
        _context.value = null
        _showEditor.value = true
        val draft = contexts[key]
        if (draft != null) {
            _context.value = draft
        } else {
            scope.launch { buildContext(action) }
        }
    }

    fun discardCurrentContext() {
        if (!_showEditor.value) return
        reset()
        _showEditor.value = false
    }

    fun send() {
        val ctx = _context.value ?: return
        _isSending.value = true
        scope.launch { doSend(ctx) }
    }

    private fun reset() {
        _context.value?.let { contexts.remove(it.task.hashKey) }
        _context.value = null
        _isSending.value = false
    }

    fun onSendSuccess(context: Context) {
        _sent.value = context
        _showEditor.value = false
        scope.launch {
            delay(500)
            reset()
        }
    }

    fun onSendError() {
        _isSending.value = false
    }

    /** Called when the editor is dismissed without sending. */
    fun editorDismissed() {
        if (_context.value != null && _sent.value == null) {
            // Keep the draft for the task, toast like iOS.
            ToastModel.showAuto(ToastModel.Message.Success("Draft Saved"))
        }
        _showEditor.value = false
    }

    protected fun onBuildContextSuccess(task: Task, context: Context) {
        contexts[task.hashKey] = context
        _context.value = context
    }

    protected fun onBuildContextError() {
        scope.launch {
            delay(2500)
            _showEditor.value = false
        }
    }

    /** Fetch prerequisites and end in [onBuildContextSuccess]/[onBuildContextError]. */
    protected abstract suspend fun buildContext(task: Task)

    /** Perform the flow's request(s); end in [onSendSuccess]/[onSendError]. */
    protected abstract suspend fun doSend(context: Context)
}

/**
 * Text + selection state for BBCode editing, ported from
 * `Models/ContentEditorModel.swift`.
 */
class ContentEditorModel(initialText: String = "") {

    enum class Panel { STICKER, NONE }

    val showing = MutableStateFlow(Panel.NONE)
    val text = MutableStateFlow(initialText)
    val selection = MutableStateFlow(
        androidx.compose.ui.text.TextRange(initialText.length, initialText.length)
    )
    val image = MutableStateFlow<ByteArray?>(null)
    val showingImagePicker = MutableStateFlow(false)

    fun showSticker() {
        showing.value = Panel.STICKER
    }

    fun showImagePicker() {
        showingImagePicker.value = true
    }

    /** Insert [string] at the selection, replacing it; caret lands after it. */
    fun insert(string: String) {
        val current = text.value
        val sel = selection.value
        val start = sel.min.coerceIn(0, current.length)
        val end = sel.max.coerceIn(0, current.length)
        val newText = current.substring(0, start) + string + current.substring(end)
        text.value = newText
        selection.value = androidx.compose.ui.text.TextRange(start + string.length, start + string.length)
    }

    /** Wrap the current selection with open/close markers. */
    fun wrapTag(open: String, close: String) {
        val current = text.value
        val sel = selection.value
        val start = sel.min.coerceIn(0, current.length)
        val end = sel.max.coerceIn(0, current.length)
        val inner = current.substring(start, end)
        val newText =
            current.substring(0, start) + open + inner + close + current.substring(end)
        text.value = newText
        selection.value = androidx.compose.ui.text.TextRange(start + open.length, start + open.length + inner.length)
    }

    fun appendTag(tag: String, attribute: String? = null) {
        val open = if (attribute != null) "[$tag=$attribute]" else "[$tag]"
        wrapTag(open, "[/$tag]")
    }

    fun appendBold() = appendTag("b")
    fun appendDel() = appendTag("del")
    fun appendQuoted() = appendTag("quote")

    fun appendCollapsed(collapsedLabel: String) = appendTag("collapse", collapsedLabel)

    fun appendAt() = wrapTag("[@", "]")

    fun insertSeparator() = insert("\n======\n")

    fun appendHeader() = wrapTag("===", "===")

    fun appendColor(c: String) = appendTag("color", c)

    fun appendSize(s: String) = appendTag("size", s)

    fun appendDice() = appendTag("dice")

    /** Insert the BBCode for a sticker image name. */
    fun insertSticker(name: String) = insert(com.bugenzhao.mnga.util.Stickers.code(name))
}

private typealias TextRange = androidx.compose.ui.text.TextRange
