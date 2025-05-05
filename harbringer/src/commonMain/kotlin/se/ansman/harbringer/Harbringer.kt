package se.ansman.harbringer

import okio.BufferedSink
import okio.IOException
import okio.Sink
import okio.Source
import se.ansman.harbringer.internal.RealHarbringer
import se.ansman.harbringer.scrubber.Scrubber
import se.ansman.harbringer.storage.HarbringerStorage
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

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
    fun clear()

    /**
     * Records a request and returns a [PendingRequest] that can be used to record the response.
     *
     * Returns `null` if not [enabled].
     */
    fun record(request: Request): PendingRequest?

    /**
     * Exports all entries to the [sink].
     *
     * The sink does not need to be buffered, as this will be done automatically.
     *
     * @param sink the [Sink] to write the exported data to.
     * @param format the format to export the data in, defaults to [ExportFormat.Har].
     * @throws IOException if an error is thrown.
     */
    @Throws(IOException::class)
    fun exportTo(sink: Sink, format: ExportFormat = ExportFormat.Har)

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
         * @param scrubber A lambda that returns a [se.ansman.harbringer.scrubber.Scrubber] for the given [Request]. This can be used to redact sensitive data.
         * @see se.ansman.harbringer.storage.FileSystemHarbringerStorage
         * @see se.ansman.harbringer.scrubber.Scrubber
         */
        @JvmName("create")
        @JvmOverloads
        operator fun invoke(
            storage: HarbringerStorage,
            maxRequests: Int,
            maxDiskSize: Int,
            enabled: Boolean = true,
            scrubber: Scrubber = Scrubber.noScrubbing,
        ): Harbringer = RealHarbringer(
            storage = storage,
            maxRequests = maxRequests,
            maxDiskUsage = maxDiskSize,
            enabled = enabled,
            scrubber = scrubber,
        )
    }

    /** What format to export the data in. */
    sealed class ExportFormat {
        /**
         * Export the data in the [HAR format](https://en.wikipedia.org/wiki/HAR_(file_format)).
         *
         * This format is used by many tools for analyzing HTTP requests and can be imported into tools like
         * [Charles Proxy](https://www.charlesproxy.com/) and [Proxyman](https://proxyman.io/).
         */
        data object Har : ExportFormat()
    }

    /**
     * A [PendingRequest] represents an in-flight request that has not yet been completed.
     *
     * You can use [requestBody] and [responseBody] to write the request and response bodies, respectively.
     *
     * You must call [onComplete] or [onFailed] when the request is completed, or there will be memory leaks.
     */
    interface PendingRequest {
        /** The ID of the request. */
        val id: String

        /** Information about the server, if available. */
        var server: Device?

        /** Information about the client, if available. */
        var client: Device?

        /** A [BufferedSink] that you can write the request body to, if available.*/
        val requestBody: BufferedSink

        /** A [BufferedSink] that you can write the response body to, if available.*/
        val responseBody: BufferedSink

        /** Discards this request. This will delete all data associated with the request. */
        fun discard()

        /**
         * Marks the request as completed.
         *
         * @param response the response.
         * @param timings the timings for the request, if available. If not provided the creation time of the
         *   [PendingRequest] and now will be used as the total duration.
         */
        fun onComplete(
            response: Response,
            timings: Timings? = null,
        )

        /**
         * Marks the request as having failed.
         *
         * @param error the error, if available.
         * @param timings the timings for the request, if available. If not provided the creation time of the
         *   [PendingRequest] and now will be used as the total duration.
         */
        fun onFailed(
            error: Throwable?,
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
    ) {
        constructor(
            total: Long,
            blocked: Long? = null,
            dns: Long? = null,
            connect: Long? = null,
            send: Long? = null,
            wait: Long? = null,
            receive: Long? = null,
            ssl: Long? = null,
        ) : this(
            total = total.milliseconds,
            blocked = blocked?.milliseconds,
            dns = dns?.milliseconds,
            connect = connect?.milliseconds,
            send = send?.milliseconds,
            wait = wait?.milliseconds,
            receive = receive?.milliseconds,
            ssl = ssl?.milliseconds,
        )
    }

    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
    data class Headers(val values: List<Header>) : List<Header> by values {
        constructor(vararg values: Pair<String, String>) : this(values.map { Header(it.first, it.second) })

        operator fun get(name: String): String? = values.lastOrNull { it.name.equals(name, ignoreCase = true) }?.value
    }

    data class Header(val name: String, val value: String)
}