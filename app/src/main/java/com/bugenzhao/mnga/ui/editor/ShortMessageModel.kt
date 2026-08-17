package com.bugenzhao.mnga.ui.editor

import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.model.GenericPostModel
import com.bugenzhao.mnga.model.ToastModel
import com.bugenzhao.mnga.protos.datamodel.ShortMessagePostAction
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.ShortMessagePostRequest
import com.bugenzhao.mnga.protos.service.ShortMessagePostResponse
import kotlinx.coroutines.CoroutineScope

/** Default subject for outgoing short messages, mirroring the iOS behavior. */
private const val DEFAULT_SUBJECT = "From MNGA"

/**
 * One short-message intent, ported from `ShortMessagePostTask` in
 * `ShortMessageEditorView.swift`.
 */
data class ShortMessageTask(
    val action: ShortMessagePostAction,
) : GenericPostModel.Task {

    override val actionTitle: String
        get() {
            val op = action.operation ?: return ""
            return when (op) {
                ShortMessagePostAction.Operation.REPLY -> "Reply"
                ShortMessagePostAction.Operation.NEW,
                ShortMessagePostAction.Operation.NEW_SINGLE_TO -> "New Short Message"
                ShortMessagePostAction.Operation.UNRECOGNIZED -> ""
            }
        }

    override val hashKey: String =
        "op=${action.operation};mid=${action.mid};to=${action.singleTo}"

    /** Short messages never carry attachments. */
    override fun buildUploadAttachmentRequest(data: ByteArray): AsyncRequest? = null
}

/**
 * Compose-and-send model for short messages, ported from
 * `ShortMessagePostModel`: the context is built locally (no fetch call).
 */
class ShortMessageModel(scope: CoroutineScope) : GenericPostModel(scope) {

    override suspend fun buildContext(task: GenericPostModel.Task) {
        if (task !is ShortMessageTask) return
        val op = task.action.operation
        val to =
            when (op) {
                ShortMessagePostAction.Operation.NEW -> ""
                ShortMessagePostAction.Operation.NEW_SINGLE_TO -> task.action.singleTo
                else -> null
            }
        onBuildContextSuccess(
            task,
            GenericPostModel.Context(
                task = task,
                to = to,
                subject = DEFAULT_SUBJECT,
                content = "",
            ),
        )
    }

    override suspend fun doSend(context: GenericPostModel.Context) {
        val task = context.task as? ShortMessageTask ?: return
        val builder =
            ShortMessagePostRequest.newBuilder()
                .setAction(task.action)
                .setContent(context.content ?: "")
                .setSubject(context.subject ?: DEFAULT_SUBJECT)
        (context.to ?: "")
            .split(' ')
            .filter { it.isNotBlank() }
            .forEach { builder.addTo(it) }

        val response =
            logicCallAsync(
                AsyncRequest.newBuilder().setShortMessagePost(builder.build()).build(),
                ShortMessagePostResponse.parser(),
            )

        response.fold(
            onSuccess = { onSendSuccess(context) },
            onFailure = { e ->
                ToastModel.editorAlert.show(ToastModel.Message.Error(e.message ?: "error"))
                onSendError()
            },
        )
    }

    /** Push an edited field back into the live context (and its draft slot). */
    fun update(transform: (GenericPostModel.Context) -> GenericPostModel.Context) {
        val current = context.value ?: return
        onBuildContextSuccess(current.task, transform(current))
    }
}
