package com.bugenzhao.mnga.model

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.bugenzhao.mnga.util.AppUpdate
import com.bugenzhao.mnga.util.ReleaseInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Where the update flow currently stands. Only one flow runs at a time. */
sealed interface UpdateState {
    /** Nothing to show; the entry point is idle. */
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** The running build is the latest release. */
    data class UpToDate(val version: String) : UpdateState

    data class Available(val release: ReleaseInfo) : UpdateState

    data class Downloading(
        val release: ReleaseInfo,
        val downloaded: Long,
        /** Content length, or `0` when the server sends none. */
        val total: Long,
    ) : UpdateState {
        /** `0f..1f`, or `null` while the total is unknown. */
        val fraction: Float?
            get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else null
    }

    /** The APK is on disk, waiting for the user to launch the installer. */
    data class Downloaded(val release: ReleaseInfo, val file: File) : UpdateState

    /**
     * The installer needs "install unknown apps" for LumaGA; [file] is kept so
     * the install can be retried once the user comes back from Settings.
     */
    data class NeedsInstallPermission(val release: ReleaseInfo, val file: File) : UpdateState

    /** [release] is set when the failure is retryable by re-downloading. */
    data class Failed(val message: String, val release: ReleaseInfo? = null) : UpdateState
}

/**
 * Drives the "check for updates" flow against the latest GitHub release:
 * compare versions, download the APK asset with progress, then hand it to the
 * system package installer.
 *
 * App-scoped on purpose — a download keeps running while the settings sheet is
 * dismissed, and its progress is still there when the user comes back.
 */
class UpdateModel(
    private val scope: CoroutineScope,
    private val context: Context,
) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    private var job: Job? = null

    /** Cached APKs live here; only the release being installed is kept. */
    private val downloadDir: File get() = File(context.cacheDir, "updates")

    val isBusy: Boolean
        get() = _state.value is UpdateState.Checking || _state.value is UpdateState.Downloading

    /** Dismiss whatever the flow is showing. A running download is cancelled. */
    fun reset() {
        job?.cancel()
        job = null
        _state.value = UpdateState.Idle
    }

    fun check() {
        if (isBusy) return
        _state.value = UpdateState.Checking
        job = scope.launch {
            AppUpdate.fetchLatestRelease()
                .onSuccess { release ->
                    _state.value =
                        if (AppUpdate.isNewer(release.version)) {
                            UpdateState.Available(release)
                        } else {
                            // Whatever sits in the cache is the running version
                            // by now, so it is only taking up space.
                            withContext(Dispatchers.IO) {
                                downloadDir.listFiles()?.forEach { it.delete() }
                            }
                            UpdateState.UpToDate(AppUpdate.currentVersion)
                        }
                }
                .onFailure { error ->
                    _state.value = UpdateState.Failed(error.readableMessage())
                }
        }
    }

    fun download(release: ReleaseInfo) {
        if (isBusy) return
        val url = release.apkUrl
        if (url == null) {
            // Nothing to install; send the user to the release page instead.
            _state.value = UpdateState.Failed(NO_ASSET, release)
            return
        }
        _state.value = UpdateState.Downloading(release, 0, release.apkSize)
        job = scope.launch {
            runCatching { downloadApk(url, release) }
                .onSuccess { file -> _state.value = UpdateState.Downloaded(release, file) }
                .onFailure { error ->
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    _state.value = UpdateState.Failed(error.readableMessage(), release)
                }
        }
    }

    /**
     * Launch the system installer for an already-downloaded APK. Moves to
     * [UpdateState.NeedsInstallPermission] when the app may not request
     * installs yet, so the caller can walk the user through Settings.
     */
    fun install(release: ReleaseInfo, file: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            _state.value = UpdateState.NeedsInstallPermission(release, file)
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
        runCatching { context.startActivity(intent) }
            .onFailure { _state.value = UpdateState.Failed(it.readableMessage(), release) }
    }

    /** Open the per-app "install unknown apps" toggle. */
    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    private suspend fun downloadApk(url: String, release: ReleaseInfo): File =
        withContext(Dispatchers.IO) {
            downloadDir.mkdirs()
            // Drop any APK left over from an earlier version or a partial run.
            downloadDir.listFiles()?.forEach { it.delete() }

            // Named after the version rather than the asset ("app-release.apk"),
            // so a leftover file in the cache is identifiable.
            val target = File(downloadDir, "LumaGA-${release.version}.apk")
            val partial = File(target.path + ".part")

            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "LumaGA/${AppUpdate.currentVersion}")
            }
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                val total =
                    connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
                var downloaded = 0L
                var lastReported = 0L
                connection.inputStream.use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            // Publishing every chunk would recompose ~1000
                            // times over a 60MB APK; 512KB steps is plenty.
                            if (downloaded - lastReported >= PROGRESS_STEP_BYTES) {
                                lastReported = downloaded
                                _state.value =
                                    UpdateState.Downloading(release, downloaded, total)
                            }
                        }
                    }
                }
                if (total > 0 && downloaded < total) {
                    throw IllegalStateException("incomplete download")
                }
                if (!partial.renameTo(target)) {
                    throw IllegalStateException("cannot finalize ${target.name}")
                }
                target
            } finally {
                connection.disconnect()
                partial.delete()
            }
        }

    private fun Throwable.readableMessage(): String =
        message?.takeIf { it.isNotBlank() } ?: this::class.java.simpleName

    companion object {
        /** Sentinel message for "the release has no APK asset". */
        const val NO_ASSET = "no-apk-asset"

        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val PROGRESS_STEP_BYTES = 512L * 1024
    }
}
