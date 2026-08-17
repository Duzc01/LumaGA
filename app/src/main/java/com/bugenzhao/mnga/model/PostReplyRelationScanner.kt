package com.bugenzhao.mnga.model

import com.bugenzhao.mnga.protos.datamodel.PostContent
import com.bugenzhao.mnga.protos.datamodel.PostId
import com.bugenzhao.mnga.protos.datamodel.Span

/**
 * Finds the post a piece of content replies to / quotes, ported from
 * `Utilities/PostReplyRelationScanner.swift`. At most one relation per post;
 * the deepest (latest) hit wins.
 */
object PostReplyRelationScanner {

    fun target(content: PostContent): PostId? = scanSpans(content.spansList)

    private fun scanSpans(spans: List<Span>): PostId? {
        var found: PostId? = null
        for (span in spans) {
            val hit = scanSpan(span) ?: continue
            found = hit // latest wins
        }
        return found
    }

    private fun scanSpan(span: Span): PostId? {
        if (!span.hasTagged()) return null
        val tagged = span.tagged
        return when (tagged.tag) {
            "quote" -> {
                // Metadata = spans before the first break line.
                val firstBreak = tagged.spansList.indexOfFirst { it.hasBreakLine() }
                val metaSpans =
                    if (firstBreak >= 0) tagged.spansList.subList(0, firstBreak)
                    else emptyList()
                val meta = if (metaSpans.isNotEmpty()) extractMeta(metaSpans) else null
                if (meta != null) {
                    // A nested quote overrides as the effective target.
                    val rest = tagged.spansList.drop(firstBreak.coerceAtLeast(0))
                    scanSpans(rest) ?: meta
                } else {
                    scanSpans(tagged.spansList)
                }
            }
            "b" -> {
                val first = tagged.spansList.firstOrNull()
                val plain = first?.takeIf { it.hasPlain() }?.plain?.text ?: ""
                if (plain.startsWith("Reply to")) {
                    val meta = tagged.spansList.drop(1)
                    extractMeta(meta) ?: scanSpans(tagged.spansList)
                } else {
                    scanSpans(tagged.spansList)
                }
            }
            else -> scanSpans(tagged.spansList)
        }
    }

    private fun extractMeta(spans: List<Span>): PostId? {
        var uid: String? = null
        var hit: PostId? = null
        fun walk(list: List<Span>) {
            for (span in list) {
                if (!span.hasTagged()) continue
                val tagged = span.tagged
                when (tagged.tag) {
                    "uid" -> {
                        val attr = tagged.attributesList.firstOrNull()
                        if (attr != null) uid = attr
                        else tagged.spansList.firstOrNull()
                            ?.takeIf { it.hasPlain() }
                            ?.plain?.text?.let { if (it.isNotEmpty()) uid = it }
                    }
                    "pid" -> {
                        if (tagged.attributesList.size > 2) {
                            hit = PostId.newBuilder()
                                .setPid(tagged.attributesList[0])
                                .setTid(tagged.attributesList[1])
                                .build()
                        }
                    }
                    "tid" -> {
                        hit = PostId.newBuilder()
                            .setPid("0")
                            .setTid(tagged.attributesList[0])
                            .build()
                    }
                    else -> walk(tagged.spansList)
                }
            }
        }
        walk(spans)
        // A uid is required for valid metadata.
        return if (uid != null) hit else null
    }
}
