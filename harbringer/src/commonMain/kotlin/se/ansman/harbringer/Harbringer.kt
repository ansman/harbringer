package se.ansman.harbringer

import okio.BufferedSink
import okio.IOException
import okio.Sink
import okio.Source
import se.ansman.harbringer.internal.RealHarbringer
import se.ansman.harbringer.storage.HarbringerStorage
import kotlin.time.Duration

/**
 * A logger for HTTP requests and responses.
 *
 * This logger can be used to log requests and responses for debugging purposes, or to store them for later analysis.
 *
 * The requests are stored in a [se.ansman.harbringer.storage.HarbringerStorage] and can be persisted across sessions.
 *
 * You can export the logged requests to a HAR archive using [exportHarArchive], which can then be imported into tool
 * for analysis.
 *
 * This class is thread safe and can be used from multiple threads.
 * Unless otherwise noted, all methods and properties can perform I/O and should not be called from a UI thread.
 */
interface Harbringer {
    /**
     * If `true` (default), requests will be stored. If `false`, requests will not be stored.
     *
     * This is useful if you need to conditionally enable or disable logging.
     *
     * Disabling logging will not delete any existing entries or prevent exporting.
     *
     * This property can be read or written from any thread.
     */
    var enabled: Boolean

    /**
     * Returns a set of all the IDs of the logged requests.
     *
     * The order of the set will approximate match the order of the requests.
     */
    fun getIds(): Set<String>

    /**
     * Returns the [Entry] with the given [id], or `null` if there is no entry with that ID.
     */
    fun getEntry(id: String): Entry?

    /**
     * Deletes the entry with the given [id]. This will also delete the request and response bodies for the entry.
     */
    fun deleteEntry(id: String)

    /**
     * Returns a [Source] that reads the request body for the entry with the given [id], or `null` if there is no
     * entry with that ID or if there is no body for the entry.
     *
     * The returned source must be closed after reading from it.
     */
    fun getRequestBody(id: String): Source?

    /**
     * Returns a [Source] that reads the response body for the entry with the given [id], or `null` if there is no
     * entry with that ID or if there is no body for the entry.
     *
     * The returned source must be closed after reading from it.
     */
    fun getResponseBody(id: String): Source?

    /**
     * Deletes all entries in the logger. This will also delete all request and response bodies for the entries.
     *
     * This will not delete any in-flight requests.
     */
    fun clear() = getIds().forEach { deleteEntry(it) }

    /**
     * Records a request and returns a [PendingRequest] that can be used to record the response.
     *
     * Returns `null` if not [enabled].
     */
    fun record(request: Request): PendingRequest?

    /**
     * Exports the logged requests to a HAR archive to the given [sink].
     *
     * @throws IOException if an error is thrown.
     */
    @Throws(IOException::class)
    fun exportHarArchive(sink: Sink)

    companion object {
        /**
         * Creates a new [Harbringer] with the given [storage], [maxRequests], and [maxDiskSize].
         *
         * @param storage The storage to use for the requests.
         * @param maxRequests The maximum number of requests to store. If this is exceeded, the oldest requests will be
         *   deleted.
         * @param maxDiskSize The approximate max size of the storage in bytes. If this is exceeded, the oldest requests
         *   will be deleted. In-flight requests are not counted towards this limit, so more data can be stored temporarily.
         * @param enabled If `true` (default), requests will be stored. If `false`, requests will not be stored.
         * @param scrubber A lambda that returns a [Scrubber] for the given [Request]. This can be used to redact sensitive data.
         * @see se.ansman.harbringer.storage.FileSystemHarbringerStorage
         * @see Scrubber
         */
        @JvmName("create")
        @JvmOverloads
        operator fun invoke(
            storage: HarbringerStorage,
            maxRequests: Int,
            maxDiskSize: Int,
            enabled: Boolean = true,
            scrubber: (Request) -> Scrubber = { Scrubber.noScrubbing },
        ): Harbringer = RealHarbringer(
            storage = storage,
            maxRequests = maxRequests,
            maxDiskUsage = maxDiskSize,
            enabled = enabled,
            scrubber = scrubber,
        )
    }

    interface PendingRequest {
        val id: String
        var server: Device?
        var client: Device?
        val requestBody: BufferedSink
        val responseBody: BufferedSink

        @Throws(IOException::class)
        fun discard()

        @Throws(IOException::class)
        fun onComplete(
            response: Response,
            timings: Timings? = null,
        )

        @Throws(IOException::class)
        fun onFailed(
            error: Throwable,
            timings: Timings? = null,
        )
    }

    data class Entry(
        val id: String,
        val request: Request,
        val response: Response,
        val timings: Timings,
        val startedAt: Long,
        val server: Device? = null,
        val client: Device? = null,
    )

    data class Device(
        val ip: String,
        val port: Int? = null,
    )

    data class Request(
        val method: String,
        val url: String,
        val protocol: String,
        val headers: Headers,
        val body: Body? = null,
    ) {
        data class Body(
            val byteCount: Long,
            val contentType: String?,
            val params: List<Param> = emptyList(),
        ) {
            data class Param(
                val name: String,
                val value: String? = null,
                val fileName: String? = null,
                val contentType: String? = null,
            )
        }

    }

    data class Response(
        val code: Int,
        val message: String,
        val protocol: String,
        val headers: Headers,
        val body: Body? = null,
        val error: String? = null,
    ) {
        data class Body(
            val byteCount: Long,
            val contentType: String? = null,
        )
    }

    data class Timings(
        val total: Duration,
        val blocked: Duration? = null,
        val dns: Duration? = null,
        val connect: Duration? = null,
        val send: Duration? = null,
        val wait: Duration? = null,
        val receive: Duration? = null,
        val ssl: Duration? = null,
    )

    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
    data class Headers(val values: List<Header>) : List<Header> by values {
        constructor(vararg values: Pair<String, String>) : this(values.map { Header(it.first, it.second) })
        operator fun get(name: String): String? = values.lastOrNull { it.name.equals(name, ignoreCase = true) }?.value
    }

    data class Header(val name: String, val value: String)
}