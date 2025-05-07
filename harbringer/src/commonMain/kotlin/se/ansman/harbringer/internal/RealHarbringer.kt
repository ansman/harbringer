package se.ansman.harbringer.internal

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.okio.encodeToBufferedSink
import okio.*
import se.ansman.harbringer.Harbringer
import se.ansman.harbringer.Harbringer.Listener
import se.ansman.harbringer.Harbringer.Request
import se.ansman.harbringer.internal.atomic.newLock
import se.ansman.harbringer.internal.atomic.tryWithLock
import se.ansman.harbringer.internal.atomic.withLock
import se.ansman.harbringer.internal.json.HarFile
import se.ansman.harbringer.internal.json.toHarEntry
import se.ansman.harbringer.scrubber.Scrubber
import se.ansman.harbringer.storage.HarbringerStorage
import kotlin.concurrent.Volatile
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalSerializationApi::class)
internal class RealHarbringer(
    private val storage: HarbringerStorage,
    private val maxRequests: Int,
    private val maxDiskUsage: Long, // bytes
    private val maxAge: Duration,
    @Volatile
    override var enabled: Boolean,
    private val scrubber: Scrubber,
    private val clock: Clock = Clock.System,
) : Harbringer {
    private val listenersLock = newLock()
    private var listeners = emptyArray<Listener>()
    private val exportLock = newLock()

    init {
        require(maxRequests > 0) {
            "maxRequests must be greater than 0, was $maxRequests"
        }
        require(maxDiskUsage > 0) {
            "maxDiskUsage must be greater than 0, was $maxDiskUsage"
        }
        require(maxAge > Duration.ZERO) {
            "maxAge must be greater than 0, was $maxAge"
        }
    }

    override fun getIds(): Set<String> {
        cleanup()
        return storage.getIds()
    }

    override fun getEntry(id: String): Harbringer.Entry? = storage.getEntry(id)

    override fun deleteEntry(id: String) {
        val entry = exportLock.withLock {
            storage.deleteEntry(id)
        }
        if (entry != null) {
            for (listener in listeners) {
                listener.onEntryDeleted(id)
            }
        }
    }

    override fun getRequestBody(id: String): Source? = storage.readRequestBody(id)

    override fun getResponseBody(id: String): Source? = storage.readResponseBody(id)

    private fun cleanup() {
        var deletedEntries: MutableList<HarbringerStorage.StoredEntry>? = null
        exportLock.tryWithLock {
            while (storage.entriesStored > maxRequests || storage.bytesStored > maxDiskUsage) {
                if (storage.deleteOldestEntry() == null) {
                    break
                }
            }
            while (true) {
                val oldestEntry = storage.getOldestEntryMetadata() ?: break
                if ((clock.currentTime() - oldestEntry.startedAt).milliseconds > maxAge) {
                    storage.deleteEntry(oldestEntry.id)?.let { entry ->
                        (deletedEntries ?: mutableListOf<HarbringerStorage.StoredEntry>().also { deletedEntries = it })
                            .add(entry)
                    }
                } else {
                    break
                }
            }
        }
        deletedEntries?.forEach {
            for (listener in listeners) {
                listener.onEntryDeleted(it.id)
            }
        }
    }

    override fun clear() {
        exportLock.withLock {
            storage.clear()
        }
        for (listener in listeners) {
            listener.onCleared()
        }
    }

    override fun record(request: Request): Harbringer.PendingRequest? {
        if (!enabled) {
            return null
        }

        val scrubbedRequest = scrubber.scrubRequest(request)
            ?: return NoOpPendingRequest()
        val id = randomUuid()
        for (listener in listeners) {
            listener.onRecordingStarted(request)
        }
        return PendingRequest(
            pendingEntry = storage.store(id),
            scrubber = scrubber,
            request = scrubbedRequest,
            startedAt = clock.currentTime()
        )
    }

    override fun exportTo(sink: Sink, format: Harbringer.ExportFormat) {
        cleanup()
        exportLock.withLock {
            when (format) {
                Harbringer.ExportFormat.Har -> exportHarArchive(sink, Json)
            }
        }
        cleanup()
    }

    override fun addListener(listener: Listener) {
        listenersLock.withLock {
            if (listener !in listeners) {
                listeners = listeners + listener
            }
        }
    }

    override fun removeListener(listener: Listener) {
        listenersLock.withLock {
            listeners = (listeners.asList() - listener).toTypedArray()
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
                        version = HARBRINGER_VERSION,
                        comment = "Exported at ${formatIso8601(clock.currentTime())}"
                    ),
                    entries = storage.getIds().map { id -> readHarEntry(id) }//.asIterable()
                )
            ),
            sink = buffer,
        )
        buffer.flush()
    }

    private fun readHarEntry(id: String): HarFile.Entry {
        val entry = storage.getEntry(id) ?: throw FileNotFoundException("Could not read entry $id")
        return entry.toHarEntry(
            requestBody = { getRequestBody(id)?.buffer()?.use { it.readByteString() } },
            responseBody = { getResponseBody(id)?.buffer()?.use { it.readByteString() } },
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
            if (!isClosed) {
                for (listener in listeners) {
                    listener.onRequestDiscarded(request)
                }
            }
            finalize(allowClosed = true)
            pendingEntry.discard()
        }

        override fun onComplete(
            response: Harbringer.Response,
            timings: Harbringer.Timings?
        ) {
            val entry = finalize(
                response = response,
                timings = timings,
            )
            if (entry != null) {
                for (listener in listeners) {
                    listener.onRequestCompleted(entry)
                }
            }
        }

        override fun onFailed(
            error: Throwable?,
            timings: Harbringer.Timings?,
        ) {
            val entry = finalize(
                response = error.toResponse(request),
                timings = timings,
            )
            if (entry != null) {
                for (listener in listeners) {
                    listener.onRequestFailed(entry, error)
                }
            }
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
        ): Harbringer.Entry? {
            val response = scrubber.scrubResponse(request, response)
            if (response == null) {
                discard()
                return null
            }
            finalize()

            val entry = Harbringer.Entry(
                id = id,
                request = request,
                response = response,
                startedAt = startedAt,
                server = server,
                client = client,
                timings = (timings ?: Harbringer.Timings((clock.currentTime() - startedAt).milliseconds))
            )
            pendingEntry.write(entry)
            cleanup()
            return entry
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