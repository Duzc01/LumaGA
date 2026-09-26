package com.bugenzhao.mnga.util

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.bugenzhao.mnga.App
import java.io.OutputStream

/**
 * Access to the user-selected download folder.
 *
 * A blank preference means callers should keep using their legacy
 * `Download/LumaGA` MediaStore destination. A non-blank preference is a
 * persisted Storage Access Framework tree URI and points to the exact folder
 * selected by the user.
 */
object DownloadDestination {
    fun customDirectoryUri(): Uri? =
        App.prefs.downloadDirectoryUri.value
            .takeIf { it.isNotBlank() }
            ?.let(Uri::parse)

    /**
     * Writes a file into the selected custom folder.
     *
     * Returns `null` when the default folder is configured, otherwise the
     * success state of the custom-folder write.
     */
    fun writeCustom(
        context: Context,
        fileName: String,
        mimeType: String,
        write: (OutputStream) -> Unit,
    ): Boolean? {
        val treeUri = customDirectoryUri() ?: return null
        return runCatching {
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                DocumentsContract.getTreeDocumentId(treeUri),
            )
            val targetUri = DocumentsContract.createDocument(
                context.contentResolver,
                documentUri,
                mimeType,
                fileName,
            ) ?: return@runCatching false
            context.contentResolver.openOutputStream(targetUri)?.use { output ->
                write(output)
                true
            } ?: false
        }.getOrDefault(false)
    }

    fun label(uriString: String): String {
        if (uriString.isBlank()) return "Download/LumaGA"
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return uriString
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        return documentId
            ?.substringAfter(':', documentId)
            ?.ifBlank { documentId.substringBefore(':') }
            ?: uri.lastPathSegment
            ?: uriString
    }

    fun currentLabel(): String = label(App.prefs.downloadDirectoryUri.value)
}
