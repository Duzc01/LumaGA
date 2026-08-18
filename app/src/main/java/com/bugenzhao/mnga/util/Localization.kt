package com.bugenzhao.mnga.util

import android.content.Context

/**
 * Localization helpers mirroring `Utilities/Localization.swift`: English string
 * literals are the keys; look them up through [StringsMap] with a fallback to
 * the literal itself (exactly how the iOS app behaves on non-zh-Hans devices).
 */
object L {
    fun str(context: Context, key: String, vararg args: Any): String {
        val name = StringsMap.map[key] ?: return formatFallback(key, args)
        val id = context.resources.getIdentifier(name, "string", context.packageName)
        if (id == 0) return formatFallback(key, args)
        return if (args.isEmpty()) context.getString(id)
        else context.getString(id, *args)
    }

    private fun formatFallback(key: String, args: Array<out Any>): String =
        if (args.isEmpty()) key
        else {
            var index = 0
            key.replace(Regex("%(\\d+\\$)?ll?d|%@(?!\\w)")) { (index + 1).let { i ->
                index++
                args[i - 1].toString()
            } }
        }
}

/**
 * Localized format helper: like [L.str] but tolerant of resources that keep
 * the iOS-style specifiers (`%@`, `%lld`) in their English fallback values.
 */
fun fmtL(context: Context, key: String, vararg args: Any): String =
    try {
        L.str(context, key, *args)
    } catch (e: Exception) {
        var text = L.str(context, key)
        args.forEach { arg ->
            text = text.replaceFirst(Regex("%(\\d+\\$)?(?:ll?d|d|s|@)"), arg.toString())
        }
        text
    }

/** `String.errorLocalized`: split "Category|detail" and localize the category. */
fun Context.errorLocalized(error: String): String {
    val parts = error.split("|", limit = 2)
    return if (parts.size == 2) "${L.str(this, parts[0])}: ${parts[1]}"
    else L.str(this, error)
}
