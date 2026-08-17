package com.bugenzhao.mnga.ui.editor

import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.model.GenericPostModel
import com.bugenzhao.mnga.model.ToastModel
import com.bugenzhao.mnga.protos.datamodel.PostAttachment
import com.bugenzhao.mnga.protos.datamodel.PostReplyAction
import com.bugenzhao.mnga.protos.datamodel.forumIdOrNull
import com.bugenzhao.mnga.protos.datamodel.postIdOrNull
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.PostReplyFetchContentRequest
import com.bugenzhao.mnga.protos.service.PostReplyFetchContentResponse
import com.bugenzhao.mnga.protos.service.PostReplyRequest
import com.bugenzhao.mnga.protos.service.PostReplyResponse
import com.bugenzhao.mnga.protos.service.UploadAttachmentRequest
import com.bugenzhao.mnga.protos.service.UploadAttachmentResponse
import com.google.protobuf.ByteString
import kotlinx.coroutines.CoroutineScope

/**
 * One posting intent, ported from `PostReplyTask` in `PostEditorView.swift`.
 * Equality follows operation + forum id + post id (see [hashKey]).
 */
data class PostReplyTask(
    val action: PostReplyAction,
    val pageToReload: GenericPostModel.PageToReload = GenericPostModel.PageToReload.Last,
) : GenericPostModel.Task {

    override val actionTitle: String
        get() {
            val op = action.operation ?: return ""
            return when (op) {
                PostReplyAction.Operation.REPLY -> "Reply"
                PostReplyAction.Operation.QUOTE -> "Quote"
                PostReplyAction.Operation.MODIFY ->
                    if (action.verbatim.modifyAppend) "Append" else "Edit"
                PostReplyAction.Operation.COMMENT -> "Comment"
                PostReplyAction.Operation.NEW -> "New Topic"
                PostReplyAction.Operation.REPORT -> "Report"
                PostReplyAction.Operation.UNRECOGNIZED -> ""
            }
        }

    override val hashKey: String
        get() {
        val forum = action.forumIdOrNull
        val forumPart =
            when {
                forum == null -> ""
                forum.hasStid() -> "stid=${forum.stid}"
                forum.hasFid() -> "fid=${forum.fid}"
                else -> ""
            }
        val post = action.postIdOrNull
        val postPart = if (post != null) "tid=${post.tid},pid=${post.pid}" else ""
        return "op=${action.operation};$forumPart;$postPart"
        }

    override fun buildUploadAttachmentRequest(data: ByteArray): AsyncRequest =
        AsyncRequest.newBuilder()
            .setUploadAttachment(
                UploadAttachmentRequest.newBuilder()
                    .setAction(action)
                    .setFile(ByteString.copyFrom(data))
                    .build(),
            )
            .build()
}

/**
 * Compose-and-send model for every topic posting flow (reply, quote, comment,
 * modify, report, new topic), ported from `PostReplyModel`.
 */
class PostReplyModel(scope: CoroutineScope) : GenericPostModel(scope) {

    override suspend fun buildContext(task: GenericPostModel.Task) {
        if (task !is PostReplyTask) return
        val response =
            logicCallAsync(
                AsyncRequest.newBuilder()
                    .setPostReplyFetchContent(
                        PostReplyFetchContentRequest.newBuilder().setAction(task.action).build(),
                    )
                    .build(),
                PostReplyFetchContentResponse.parser(),
            )

        response.fold(
            onSuccess = { resp ->
                // Only build the context after a successful fetch; the verbatim
                // auth/attach info must ride along every later request.
                val newTask =
                    task.copy(
                        action = task.action.toBuilder().setVerbatim(resp.verbatim).build()
                    )
                val subject =
                    if (resp.hasSubject() ||
                        task.action.operation == PostReplyAction.Operation.NEW
                    )
                        resp.subject
                    else null
                val content = resp.content
                val anonymous =
                    if (task.action.operation == PostReplyAction.Operation.REPORT) null
                    else false
                onBuildContextSuccess(
                    newTask,
                    GenericPostModel.Context(
                        task = newTask,
                        subject = subject,
                        content = content,
                        anonymous = anonymous,
                    ),
                )
            },
            onFailure = { e ->
                ToastModel.editorAlert.show(ToastModel.Message.Error(e.message ?: "error"))
                onBuildContextError()
            },
        )
    }

    override suspend fun doSend(context: GenericPostModel.Context) {
        val task = context.task as? PostReplyTask ?: return
        val builder =
            PostReplyRequest.newBuilder()
                .setAction(task.action)
                .setContent(context.content ?: "")
                .setAnonymous(context.anonymous ?: false)
        context.subject?.let { builder.setSubject(it) }
        context.attachments.forEach { builder.addAttachments(it) }

        val response =
            logicCallAsync(
                AsyncRequest.newBuilder().setPostReply(builder.build()).build(),
                PostReplyResponse.parser(),
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

    /** Record a completed attachment upload so it is sent with the post. */
    fun addAttachment(attachment: PostAttachment) {
        update { it.copy(attachments = it.attachments + attachment) }
    }
}
