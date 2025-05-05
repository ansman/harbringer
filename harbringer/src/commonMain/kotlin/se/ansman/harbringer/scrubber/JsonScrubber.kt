package se.ansman.harbringer.scrubber

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import kotlinx.serialization.json.okio.decodeFromBufferedSource
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.Buffer
import okio.Sink
import okio.Timeout
import se.ansman.harbringer.Harbringer

@ExperimentalSerializationApi
internal class JsonScrubber(
    private val json: Json,
    private val replace: (path: String, element: JsonElement) -> JsonElement?,
    private val onlyIf: (Harbringer.Request) -> Boolean,
) : BodyScrubber {
    override fun scrub(request: Harbringer.Request, sink: Sink): Sink =
        if (onlyIf(request)) {
            object : Sink {
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
                    val element = json.decodeFromBufferedSource(JsonElement.serializer(), buffer.peek()).scrub(status)
                    if (status.wasReplaced) {
                        buffer.clear()
                        if (element == null) {
                            buffer.writeUtf8("null")
                        } else {
                            json.encodeToBufferedSink(JsonElement.serializer(), element, buffer)
                        }
                    }
                    buffer.readAll(sink)
                }
            }
        } else {
            sink
        }

    private fun JsonElement.scrub(
        status: ReplacementStatus,
        path: String = "$"
    ): JsonElement? {
        val replacement = replace(path, this)
        if (replacement != this) {
            status.wasReplaced = true
            return replacement
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

    private class ReplacementStatus(var wasReplaced: Boolean = false)
}