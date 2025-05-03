package se.ansman.harbringer.internal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.Buffer
import okio.Sink
import okio.Timeout

internal class JsonScrubber(
    patterns: List<String>,
    private val json: Json,
    private val replace: (path: String, element: JsonElement) -> JsonElement?,
) : (Sink) -> Sink {
    private val pattern = Regex(patterns.joinToString(separator = "|") {
        var i = 0
        val sb = StringBuilder()
        while (i < it.length) {
            val next = it.indexOf('*', startIndex = 0)
            if (next < 0) {
                break
            }
            sb.append(Regex.escape(it.substring(i, next)))
            sb.append(".*")
            i = next + 1
        }
        if (i < it.length) {
            sb.append(Regex.escape(it.substring(i)))
        }
        sb.toString()
    })

    @OptIn(ExperimentalSerializationApi::class)
    override fun invoke(input: Sink): Sink = object : Sink {
        private var isClosed = false
        private val buffer = Buffer()

        override fun write(source: Buffer, byteCount: Long) {
            buffer.write(source, byteCount)
        }

        override fun flush() {}

        override fun timeout(): Timeout = Timeout.Companion.NONE

        override fun close() {
            if (isClosed) {
                return
            }
            isClosed = true
            val status = ReplacementStatus()
            val element = json.decodeFromBufferedSource(JsonElement.Companion.serializer(), buffer.peek()).scrub(status)
            if (status.wasReplaced) {
                buffer.clear()
                if (element == null) {
                    buffer.writeUtf8("null")
                } else {
                    json.encodeToBufferedSink(JsonElement.Companion.serializer(), element, buffer)
                }
            }
            buffer.readAll(input)
        }

        private fun JsonElement.scrub(status: ReplacementStatus, path: String = "$"): JsonElement? {
            if (pattern.matches(path)) {
                status.wasReplaced = true
                return replace(path, this)
            }
            return when (this) {
                is JsonArray -> {
                    val next = "$path[]"
                    JsonArray(mapNotNull { it.scrub(status, next) })
                }

                is JsonObject -> {
                    JsonObject(mapNotNull { (key, value) ->
                        val scrubbed = value.scrub(status, "$path.$key")
                        if (scrubbed == null) {
                            null
                        } else {
                            key to scrubbed
                        }
                    }.toMap())
                }

                is JsonPrimitive,
                JsonNull -> this
            }
        }
    }

    private class ReplacementStatus(var wasReplaced: Boolean = false)
}