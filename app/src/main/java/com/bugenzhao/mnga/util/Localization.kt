package com.bugenzhao.mnga.util

import android.content.Context
import com.bugenzhao.mnga.App

/**
 * Localization helpers mirroring `Utilities/Localization.swift`: English string
 * literals are the keys; look them up through [StringsMap] with a fallback to
 * the literal itself (exactly how the iOS app behaves on non-zh-Hans devices).
 */
object L {
    fun str(context: Context?, key: String, vararg args: Any): String {
        val name = StringsMap.map[key] ?: return formatFallback(key, args)
        val ctx = context ?: App.appContext ?: return formatFallback(key, args)
        val id = ctx.resources.getIdentifier(name, "string", ctx.packageName)
        if (id == 0) return formatFallback(key, args)
        if (args.isEmpty()) {
            // 无参数：取原始文本（getText 不解析格式符）。资源值可能残留
            // iOS 风格占位符（%@ 等），走 getString 的 Java format 会崩。
            return ctx.resources.getText(id).toString()
        }
        return try {
            ctx.getString(id, *args)
        } catch (_: Exception) {
            formatFallback(ctx.resources.getText(id).toString(), args)
        }
    }

    private fun formatFallback(key: String, args: Array<out Any>): String {
        if (args.isEmpty()) return key
        var index = 0
        return key.replace(Regex("%(\\d+\\$)?(?:ll?[du]|u|d|s|@(?!\\w))")) { match ->
            val explicit = match.groupValues[1].removeSuffix("$").toIntOrNull()
            val argIdx = if (explicit != null) explicit - 1 else index++
            args.getOrNull(argIdx)?.toString() ?: match.value
        }
    }
}

/**
 * Localized format helper: like [L.str] but tolerant of resources that keep
 * the iOS-style specifiers (`%@`, `%lld`) in their English fallback values.
 */
fun fmtL(context: Context?, key: String, vararg args: Any): String =
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
