package com.bugenzhao.mnga.util

import com.bugenzhao.mnga.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** One GitHub release, reduced to what the update flow needs. */
data class ReleaseInfo(
    /** Tag with the leading `v` stripped, e.g. `1.2.0`. */
    val version: String,
    val tagName: String,
    val title: String,
    /** Release notes (markdown, rendered as plain text). */
    val notes: String,
    val pageUrl: String,
    /** Download URL of the `.apk` asset, absent when a release ships without one. */
    val apkUrl: String?,
    /** Asset size in bytes, `0` when unknown. */
    val apkSize: Long,
)

/**
 * Update check against the repository's latest GitHub release. Uses
 * `HttpURLConnection` + `org.json` on purpose: the app carries no HTTP client
 * of its own, and the logic layer only speaks to NGA.
 */
object AppUpdate {

    private const val TIMEOUT_MS = 15_000

    /** The running build's `versionName`, e.g. `1.1.1`. */
    val currentVersion: String get() = BuildConfig.VERSION_NAME

    /** Fetch the latest release. Fails on network errors and non-2xx replies. */
    suspend fun fetchLatestRelease(): Result<ReleaseInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val connection =
                (URL(Constants.GitHub.latestReleaseApi).openConnection() as HttpURLConnection)
                    .apply {
                        requestMethod = "GET"
                        connectTimeout = TIMEOUT_MS
                        readTimeout = TIMEOUT_MS
                        // GitHub rejects requests without a User-Agent.
                        setRequestProperty("User-Agent", "LumaGA/${BuildConfig.VERSION_NAME}")
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                    }
            try {
                val code = connection.responseCode
                if (code !in 200..299) throw IllegalStateException("HTTP $code")
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                parseRelease(JSONObject(body))
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun parseRelease(json: JSONObject): ReleaseInfo {
        val tag = json.optString("tag_name").ifBlank { throw IllegalStateException("no tag_name") }
        var apkUrl: String? = null
        var apkSize = 0L
        val assets = json.optJSONArray("assets")
        for (i in 0 until (assets?.length() ?: 0)) {
            val asset = assets?.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk", ignoreCase = true)) continue
            apkUrl = asset.optString("browser_download_url").takeIf { it.isNotBlank() } ?: continue
            apkSize = asset.optLong("size")
            break
        }
        return ReleaseInfo(
            version = normalize(tag),
            tagName = tag,
            title = json.optString("name").ifBlank { tag },
            notes = json.optString("body").trim(),
            pageUrl = json.optString("html_url").ifBlank { Constants.GitHub.releasesUrl },
            apkUrl = apkUrl,
            apkSize = apkSize,
        )
    }

    /** True when [latest] is a strictly higher version than [current]. */
    fun isNewer(latest: String, current: String = currentVersion): Boolean =
        compareVersions(latest, current) > 0

    /**
     * Compare two dotted version strings numerically, tolerating a `v` prefix
     * and missing components (`1.2` == `1.2.0`). Anything after the numeric
     * prefix is ignored, so `1.2.0-beta` ranks equal to `1.2.0` rather than
     * above it — `releases/latest` already excludes pre-releases.
     */
    fun compareVersions(lhs: String, rhs: String): Int {
        val left = components(lhs)
        val right = components(rhs)
        for (i in 0 until maxOf(left.size, right.size)) {
            val a = left.getOrElse(i) { 0 }
            val b = right.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }

    private fun normalize(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

    private fun components(raw: String): List<Int> =
        normalize(raw)
            .takeWhile { it.isDigit() || it == '.' }
            .split('.')
            .mapNotNull { it.toIntOrNull() }
}
