package com.bugenzhao.mnga.model

import com.bugenzhao.mnga.App
import com.bugenzhao.mnga.util.URLs
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Author id to avatar path cache, backing the avatars of lists whose payload
 * carries none.
 *
 * A topic list only tells us the author's id and name, and the logic layer's
 * user cache lives in memory, so a freshly launched app knows no avatar at all
 * and every row falls back to a generated initial. Unknown authors are
 * therefore resolved lazily — the logic layer's cache first, then one remote
 * user request each — and every answer, "this author has none" included, is
 * remembered on disk so the request happens once per author rather than once
 * per launch. Failures are deliberately not remembered.
 */
object AvatarUrls {
    private const val Key = "avatarPaths"

    /** Authors remembered on disk; the least recently resolved are dropped. */
    private const val Capacity = 1000

    /**
     * Remote lookups are serialized and spaced out: a page of fresh authors is
     * ~20 of them, and firing those at once earns a 503 from NGA instead of
     * avatars.
     */
    private const val LookupSpacingMillis = 300L

    /** Author id to raw avatar path, `""` meaning "this author has none". */
    private val cache = LinkedHashMap<String, String>()
    private val pending = ConcurrentHashMap<String, Deferred<String?>>()
    private val limiter = Semaphore(1)
    private var loaded = false

    /**
     * The author's avatar URL, or null while it is still unknown and for
     * authors who have none. Suspends for a network round trip the first time
     * an author is seen, and answers from memory afterwards.
     */
    suspend fun resolve(authorId: String): String? {
        if (authorId.isEmpty()) return null
        load()
        remembered(authorId)?.let { return it.toUrl() }
        cached(authorId)?.let { path ->
            remember(authorId, path)
            return path.toUrl()
        }
        return fetch(authorId)?.toUrl()
    }

    /** Avatar path of an author the logic layer already knows, if any. */
    private suspend fun cached(authorId: String): String? =
        withContext(Dispatchers.IO) {
            runCatching { App.users.localUser(authorId) }.getOrNull()
                ?.avatarUrl?.takeIf { it.isNotEmpty() }
        }

    /**
     * One remote user lookup, shared between the rows asking for the same
     * author. Returns the avatar path, `""` for an author who has none, or
     * null when the lookup failed — a failure must not be remembered as
     * "has none", so the next row to ask retries it.
     */
    private suspend fun fetch(authorId: String): String? {
        val lookup = pending.getOrPut(authorId) {
            appScope.async {
                limiter.withPermit {
                    // Another row may have resolved this author while we queued.
                    remembered(authorId) ?: run {
                        val path = App.users.remoteUser(authorId, showError = false)?.avatarUrl
                        path?.let { remember(authorId, it) }
                        delay(LookupSpacingMillis)
                        path
                    }
                }
            }
        }
        val path = lookup.runCatching { await() }.getOrNull()
        pending.remove(authorId, lookup)
        return path
    }

    /**
     * Users who uploaded more than one avatar get them all in a single
     * `first|.a/second|.a/third` string; the first entry is the full URL NGA
     * itself shows, and the rest are relative shorthands Coil cannot load.
     */
    private fun String.toUrl(): String? =
        substringBefore('|').takeIf { it.isNotEmpty() }?.let { URLs.resourceURL(it) }

    private fun remembered(authorId: String): String? = synchronized(cache) { cache[authorId] }

    private fun remember(authorId: String, path: String) {
        val serialized = synchronized(cache) {
            cache.remove(authorId) // Re-insert so the newest entry sorts last.
            cache[authorId] = path
            while (cache.size > Capacity) {
                cache.remove(cache.keys.first())
            }
            cache.entries.joinToString("\n") { "${it.key}\t${it.value}" }
        }
        App.sharedPreferences.edit().putString(Key, serialized).apply()
    }

    private fun load() {
        synchronized(cache) {
            if (loaded) return
            loaded = true
            App.sharedPreferences.getString(Key, null)
                ?.lineSequence()
                ?.forEach { line ->
                    val parts = line.split('\t')
                    val id = parts.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return@forEach
                    cache[id] = parts.getOrElse(1) { "" }
                }
        }
    }
}
