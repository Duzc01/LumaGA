package com.bugenzhao.mnga.ui.screens.user

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.bugenzhao.mnga.logicCallAsync
import com.bugenzhao.mnga.model.GenericPostModel
import com.bugenzhao.mnga.model.ToastModel
import com.bugenzhao.mnga.model.appScope
import com.bugenzhao.mnga.protos.datamodel.PostContent
import com.bugenzhao.mnga.protos.service.AsyncRequest
import com.bugenzhao.mnga.protos.service.UserSignatureUpdateRequest
import com.bugenzhao.mnga.protos.service.UserSignatureUpdateResponse
import com.bugenzhao.mnga.util.L
import kotlinx.coroutines.CoroutineScope

/** Editor open intent, mirroring `UserSignatureEditAction`. */
data class UserSignatureEditAction(
    val userID: String = "",
    val initialSignature: String = "",
)

/** Signature editing task: no attachments, no fetch round-trip. */
data class UserSignatureEditTask(
    val action: UserSignatureEditAction = UserSignatureEditAction(),
) : GenericPostModel.Task {
    override val actionTitle: String get() = "Edit Signature"
    override val hashKey: String get() = "signature-${action.userID}"

    override fun buildUploadAttachmentRequest(data: ByteArray): AsyncRequest? = null
}

/**
 * Signature compose-and-send model, ported from `UserSignaturePostModel`:
 * builds its context from the initial signature and sends
 * `AsyncRequest.user_signature_update`.
 */
class SignaturePostModel(scope: CoroutineScope = appScope) : GenericPostModel(scope) {

    override suspend fun buildContext(task: GenericPostModel.Task) {
        if (task !is UserSignatureEditTask) return
        onBuildContextSuccess(
            task,
            GenericPostModel.Context(task = task, content = task.action.initialSignature),
        )
    }

    /** Push the edited text back into the live context (called by the editor). */
    fun updateContent(content: String) {
        val ctx = context.value ?: return
        onBuildContextSuccess(ctx.task, ctx.copy(content = content))
    }

    override suspend fun doSend(context: GenericPostModel.Context) {
        val signature = context.content ?: ""
        val result = logicCallAsync(
            AsyncRequest.newBuilder()
                .setUserSignatureUpdate(
                    UserSignatureUpdateRequest.newBuilder().setSignature(signature)
                )
                .build(),
            UserSignatureUpdateResponse.parser(),
        )
        result.fold(
            onSuccess = { onSendSuccess(context) },
            onFailure = { e ->
                ToastModel.editorAlert.show(ToastModel.Message.Error(e.message ?: "error"))
                onSendError()
            },
        )
    }
}

/**
 * Signature display widget, ported from `UserSignatureView`: the (rich or
 * raw) post content at small size.
 */
@Composable
fun UserSignatureView(
    content: PostContent,
    color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Unspecified,
    contentRenderer: (@Composable (PostContent) -> Unit)? = null,
) {
    val renderer = contentRenderer
    if (renderer != null) renderer(content)
    else RawPostContent(
        content,
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

/**
 * The signature editor sheet shell ("Edit Signature"): a plain text field with
 * send and discard controls, ported from `GenericEditorView` for the signature
 * flow (no subject, no recipients, no attachments).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignatureEditorSheet(
    model: SignaturePostModel,
    onDismiss: () -> Unit = {},
) {
    val context = LocalContext.current
    val ctx by model.context.collectAsState()
    val isSending by model.isSending.collectAsState()
    var showDiscardDialog by remember { mutableStateOf(false) }

    val text = ctx?.content ?: ""
    var selection by remember { mutableStateOf(TextRange(text.length)) }
    var textState by remember(ctx) { mutableStateOf(text) }

    ModalBottomSheet(
        onDismissRequest = {
            model.editorDismissed()
            onDismiss()
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                L.str(context, "Edit Signature"),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = textState,
                onValueChange = {
                    textState = it
                    selection = TextRange(it.length)
                    model.updateContent(it)
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 10,
            )
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                IconButton(onClick = { showDiscardDialog = true }) {
                    Icon(
                        Icons.Outlined.Delete,
                        contentDescription = L.str(context, "Discard"),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
                androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                if (isSending) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                } else {
                    Button(
                        onClick = {
                            model.updateContent(textState)
                            model.send()
                        },
                        enabled = textState.isNotBlank() || text.isNotBlank(),
                    ) {
                        Icon(Icons.Outlined.Send, contentDescription = null)
                        androidx.compose.foundation.layout.Spacer(Modifier.size(6.dp))
                        Text(L.str(context, "Send"))
                    }
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(L.str(context, "Discard the draft?")) },
            text = {
                Text(L.str(context, "Save the draft by swiping down to dismiss the editor."))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        model.discardCurrentContext()
                        onDismiss()
                    },
                ) { Text(L.str(context, "Discard"), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(L.str(context, "Cancel"))
                }
            },
        )
    }
}
