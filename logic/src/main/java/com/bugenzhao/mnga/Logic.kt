package com.bugenzhao.mnga

import android.util.Log
import com.bugenzhao.mnga.protos.datamodel.Configuration
import com.bugenzhao.mnga.protos.service.ConfigureRequest
import com.bugenzhao.mnga.protos.service.ConfigureResponse
import com.bugenzhao.mnga.protos.service.SyncRequest
import com.google.protobuf.Message
import com.google.protobuf.Parser
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Bridge to the Rust `logic` library.
 *
 * The native symbols are looked up on this exact class (`LogicKt`), so the
 * `external` declarations below must stay top-level in this file.
 */
private external fun rustCall(data: ByteArray): ByteArray

private external fun rustCallAsync(data: ByteArray, callback: LogicCallback)

private interface LogicCallback {
    fun run(data: ByteArray?, error: String?)
}

private var loaded = false

/** Load `liblogic.so`. Must be called once before any logic call. */
fun loadLogic() {
    if (!loaded) {
        System.loadLibrary("logic")
        loaded = true
    }
}

class LogicException(val error: String) : Exception(error) {
    val isXMLParseError: Boolean
        get() = error.startsWith("XML Parse")
}

/**
 * Configure the logic library with an app-local writable directory, used by the
 * Rust side for its on-disk cache.
 */
fun logicInitialConfigure(documentDirPath: String, isEmulator: Boolean = false) {
    loadLogic()
    val request = SyncRequest.newBuilder()
        .setConfigure(
            ConfigureRequest.newBuilder()
                .setConfig(Configuration.newBuilder().setDocumentDirPath(documentDirPath))
                // Mirror the iOS behavior: clear the cache when running in an
                // emulator, where data is ephemeral anyway.
                .setDebug(isEmulator),
        )
        .build()
    logicCall(request, ConfigureResponse.parser())
}

/** Invoke a synchronous service. Throws [LogicException] on failure. */
fun <M : Message> logicCall(request: SyncRequest, parser: Parser<M>): M {
    loadLogic()
    val bytes =
        try {
            rustCall(request.toByteArray())
        } catch (e: Throwable) {
            // The JNI layer throws with the error message produced by Rust.
            throw LogicException(e.message ?: "unknown sync error")
        }
    return try {
        parser.parseFrom(bytes)
    } catch (e: Exception) {
        throw LogicException("${e.javaClass.simpleName}: ${e.message}")
    }
}

/** Invoke a synchronous service that returns no payload. */
fun logicCall(request: SyncRequest) {
    loadLogic()
    try {
        rustCall(request.toByteArray())
    } catch (e: Throwable) {
        throw LogicException(e.message ?: "unknown sync error")
    }
}

/**
 * Invoke an asynchronous service and suspend until the response arrives.
 * Never throws; failures are reported as [Result.failure] with a
 * [LogicException] cause.
 */
suspend fun <M : Message> logicCallAsync(
    request: com.bugenzhao.mnga.protos.service.AsyncRequest,
    parser: Parser<M>,
): Result<M> = suspendCancellableCoroutine { continuation ->
    loadLogic()
    val data = request.toByteArray()
    val callback = object : LogicCallback {
        override fun run(data: ByteArray?, error: String?) {
            val result: Result<M> =
                when {
                    data != null ->
                        try {
                            Result.success(parser.parseFrom(data))
                        } catch (e: Exception) {
                            Log.e("Logic", "parse response failed", e)
                            Result.failure(LogicException("${e.javaClass.simpleName}: ${e.message}"))
                        }
                    else -> {
                        Log.e("Logic", "logicCallAsync failed: $error")
                        Result.failure(LogicException(error ?: "unknown async error"))
                    }
                }
            continuation.resume(result)
        }
    }
    rustCallAsync(data, callback)
}
