package com.bugenzhao.mnga.util

import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridges protobuf messages and `SharedPreferences`, mirroring the persistence
 * encodings used by the iOS app (`Utilities/RawRepresentable.swift`):
 *
 * - a single message is stored as its protobuf JSON string
 *   (e.g. `{"uid":"123"}`),
 * - a list/set of messages is stored as a JSON array of protobuf JSON strings
 *   (i.e. an array of strings, not an array of objects).
 *
 * Protobuf field names are preserved (`snake_case`) to keep the on-disk shape
 * identical to the iOS one.
 */
object PbJson {
    fun <M : Message> toJson(message: M): String =
        try {
            JsonFormat.printer().preservingProtoFieldNames().print(message)
        } catch (e: Exception) {
            "{}"
        }

    fun <B : Message.Builder> mergeFromJson(json: String, builder: B): B {
        return try {
            JsonFormat.parser().merge(json, builder)
            builder
        } catch (e: Exception) {
            @Suppress("UNCHECKED_CAST")
            builder.clear() as B
        }
    }

    /** Serialize a message list as a JSON array of protobuf JSON strings. */
    fun <M : Message> listToJson(messages: List<M>): String =
        try {
            JSONArray().apply { messages.forEach { put(toJson(it)) } }.toString() }
        catch (e: Exception) {
            "[]"
        }

    /** Parse a JSON array of protobuf JSON strings back into message list. */
    fun <M : Message> listFromJson(
        json: String?,
        newBuilder: () -> com.google.protobuf.Message.Builder,
    ): List<M> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                @Suppress("UNCHECKED_CAST")
                mergeFromJson(array.getString(i), newBuilder()).build() as? M
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Read a plain JSON object string, for `JSONRepr`-style values. */
    fun jsonObjectOrNull(json: String?): JSONObject? =
        json?.let { runCatching { JSONObject(it) }.getOrNull() }
}
