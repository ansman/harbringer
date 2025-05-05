package se.ansman.harbringer.internal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.*
import se.ansman.harbringer.Harbringer
import se.ansman.harbringer.Harbringer.Request
import se.ansman.harbringer.internal.json.HarFile
import se.ansman.harbringer.internal.json.toHarEntry
import se.ansman.harbringer.scrubber.Scrubber
import se.ansman.harbringer.storage.HarbringerStorage
import se.ansman.requestlogger.internal.VERSION
import kotlin.concurrent.Volatile
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalSerializationApi::class)
internal class RealHarbringer(
    private val storage: HarbringerStorage,
    private val maxRequests: Int,
    private val maxDiskUsage: Int, // bytes
    @Volatile
    override var enabled: Boolean,
    private val scrubber: Scrubber,
    private val clock: Clock = Clock.System,
) : Harbringer {
    init {
        require(maxRequests > 0) {
            "maxRequests must be greater than 0, was $maxRequests"
        }
        require(maxDiskUsage > 0) {
            "maxDiskUsage must be greater than 0, was $maxDiskUsage"
        }
    }

    override fun getIds(): Set<String> = storage.getIds()

    override fun getEntry(id: String): Harbringer.Entry? = storage.getEntry(id)

    override fun deleteEntry(id: String) {
        storage.deleteEntry(id)
    }

    override fun getRequestBody(id: String): Source? = storage.readRequestBody(id)

    override fun getResponseBody(id: String): Source? = storage.readResponseBody(id)

    private fun cleanup() {
        while (storage.entriesStored > maxRequests || storage.bytesStored > maxDiskUsage) {
            if (storage.deleteOldestEntry() == null) {
                break
            }
        }
    }

    override fun clear() {
        storage.clear()
    }

    override fun record(request: Request): Harbringer.PendingRequest? {
        if (!enabled) {
            return null
        }

        val scrubbedRequest = scrubber.scrubRequest(request)
            ?: return NoOpPendingRequest()
        val id = randomUuid()
        return PendingRequest(
            pendingEntry = storage.store(id),
            scrubber = scrubber,
            request = scrubbedRequest,
            startedAt = clock.currentTime()
        )
    }

    override fun exportTo(sink: Sink, format: Harbringer.ExportFormat) {
        when (format) {
            Harbringer.ExportFormat.Har -> exportHarArchive(sink, Json)
        }
    }

    fun exportHarArchive(sink: Sink, json: Json) {
        val buffer = sink.buffer()
        json.encodeToBufferedSink(
            serializer = HarFile.serializer(),
            value = HarFile(
                log = HarFile.Log(
                    version = "1.2",
                    creator = HarFile.Creator(
                        name = "Harbringer",
                        version = VERSION,
                        comment = "Exported at ${formatIso8601(clock.currentTime())}"
                    ),
                    entries = storage.getIds().asSequence().map { id -> readHarEntry(id) }.asIterable()
                )
            ),
            sink = buffer,
        )
        buffer.flush()
    }

    private fun readHarEntry(id: String): HarFile.Entry {
        val entry = storage.getEntry(id) ?: throw FileNotFoundException("Could not read entry $id")
        return entry.toHarEntry(
            requestBody = getRequestBody(id)?.buffer()?.use { it.readByteString() },
            responseBody = getResponseBody(id)?.buffer()?.use { it.readByteString() },
        )
    }

    private class NoOpPendingRequest : Harbringer.PendingRequest {
        override val id: String = ""

        override var server: Harbringer.Device? = null
        override var client: Harbringer.Device? = null
        override val requestBody: BufferedSink = blackholeSink().buffer()
        override val responseBody: BufferedSink = blackholeSink().buffer()
        override fun discard() {
            requestBody.close()
            responseBody.close()
        }

        override fun onComplete(
            response: Harbringer.Response,
            timings: Harbringer.Timings?
        ) {
            discard()
        }

        override fun onFailed(error: Throwable?, timings: Harbringer.Timings?) {
            discard()
        }
    }

    private inner class PendingRequest(
        private val pendingEntry: HarbringerStorage.PendingEntry,
        private val scrubber: Scrubber,
        private val request: Request,
        private val startedAt: Long
    ) : Harbringer.PendingRequest {
        private var isClosed = false
        override val id: String get() = pendingEntry.id
        override var server: Harbringer.Device? = null
        override var client: Harbringer.Device? = null
        override val requestBody: BufferedSink = pendingEntry.requestBody
            .let { sink -> scrubber.scrubRequestBody(request, sink) }
            .buffer()
        override val responseBody: BufferedSink = pendingEntry.responseBody
            .let { sink -> scrubber.scrubResponseBody(request, sink) }
            .buffer()

        override fun discard() {
            finalize(allowClosed = true)
            pendingEntry.discard()
        }

        override fun onComplete(
            response: Harbringer.Response,
            timings: Harbringer.Timings?
        ) {
            finalize(
                response = response,
                timings = timings,
            )
        }

        override fun onFailed(
            error: Throwable?,
            timings: Harbringer.Timings?,
        ) {
            finalize(
                response = error.toResponse(request),
                timings = timings,
            )
        }

        private fun finalize(allowClosed: Boolean = false) {
            check(allowClosed || !isClosed) {
                "This request has already been finalized"
            }
            isClosed = true
            requestBody.close()
            responseBody.close()
        }

        private fun finalize(
            response: Harbringer.Response,
            timings: Harbringer.Timings?,
        ) {
            val response = scrubber.scrubResponse(request, response)
            if (response == null) {
                discard()
                return
            }
            finalize()
            pendingEntry.write(
                Harbringer.Entry(
                    id = id,
                    request = request,
                    response = response,
                    startedAt = startedAt,
                    server = server,
                    client = client,
                    timings = (timings ?: Harbringer.Timings((clock.currentTime() - startedAt).milliseconds))
                )
            )
            cleanup()
        }

        private fun Throwable?.toResponse(request: Request): Harbringer.Response =
            Harbringer.Response(
                code = 999,
                message = "Error",
                protocol = request.protocol,
                headers = Harbringer.Headers(),
                body = Harbringer.Response.Body(
                    byteCount = 0L,
                    contentType = "",
                ),
                error = this?.message,
            )
    }
}