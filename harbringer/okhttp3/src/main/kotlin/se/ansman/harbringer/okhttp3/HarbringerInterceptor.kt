package se.ansman.harbringer.okhttp3

import okhttp3.*
import okhttp3.EventListener
import okio.*
import se.ansman.harbringer.Harbringer
import se.ansman.harbringer.internal.MimeTypes
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * Adds the given [Harbringer] instance to the [OkHttpClient.Builder].
 *
 * This OkHttp client will record the requests and responses to the harbringer.
 */
fun OkHttpClient.Builder.addHarbringer(harbringer: Harbringer): OkHttpClient.Builder =
    addHarbringer(harbringer, strict = false)

internal fun OkHttpClient.Builder.addHarbringer(
    harbringer: Harbringer,
    strict: Boolean
): OkHttpClient.Builder {
    val interceptor = HarbringerInterceptor(harbringer, strict)
    return this
        .apply { interceptors().addFirst(interceptor) }
        .addNetworkInterceptor(interceptor)
        .eventListener(interceptor)
}

private class HarbringerInterceptor(
    private val harbringer: Harbringer,
    private val strict: Boolean,
) : EventListener(), Interceptor {
    private val calls = Collections.synchronizedMap(HashMap<Call, RequestMetadata>())

    private val Call.metadata: RequestMetadata?
        get() = calls[this] ?: run {
            if (strict) {
                throw IllegalStateException("No metadata for call")
            } else {
                null
            }
        }

    override fun callStart(call: Call) {
        calls[call] = RequestMetadata()
    }

    override fun callEnd(call: Call) {
        calls -= call
    }

    override fun dnsStart(call: Call, domainName: String) {
        call.metadata?.dns?.onStart()
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<@JvmSuppressWildcards InetAddress>) {
        call.metadata?.dns?.onEnd()
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        call.metadata?.connect?.onStart()
    }

    override fun secureConnectStart(call: Call) {
        call.metadata?.ssl?.onStart()
    }

    override fun secureConnectEnd(call: Call, handshake: Handshake?) {
        call.metadata?.ssl?.onEnd()
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: Protocol?) {
        call.metadata?.connect?.onEnd()
    }

    override fun requestHeadersStart(call: Call) {
        call.metadata?.send?.onStart()
    }

    override fun requestHeadersEnd(call: Call, request: Request) {
        if (request.body == null) {
            call.metadata?.send?.onEnd()
        }
    }

    override fun requestBodyEnd(call: Call, byteCount: Long) {
        call.metadata?.send?.onEnd()
    }

    override fun responseHeadersStart(call: Call) {
        call.metadata?.receive?.onStart()
    }

    override fun responseBodyEnd(call: Call, byteCount: Long) {
        call.metadata?.receive?.onEnd()
    }

    override fun intercept(chain: Interceptor.Chain): Response =
        if (chain.connection() == null) {
            interceptApplication(chain)
        } else {
            interceptNetwork(chain)
        }

    private fun interceptApplication(chain: Interceptor.Chain): Response =
        // TODO: Handle cache responses
        chain.proceed(chain.request())

    private fun interceptNetwork(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val pendingRequest = harbringer
            .record(
                request.toRequestLoggerRequest(
                    protocol = chain.connection()?.protocol(),
                ),
            )
            ?.apply {
                server = chain.connection()?.route()?.socketAddress?.run {
                    Harbringer.Device(address.hostAddress, port)
                }
                client = chain.connection()?.socket()?.run {
                    Harbringer.Device(localAddress.hostAddress, localPort)
                }
            }
            ?: return chain.proceed(request)
        val response = try {
            chain.proceed(
                request
                    .newBuilder()
                    .method(request.method, request.body?.let {
                        RecordingRequestBody(
                            delegate = it,
                            record = pendingRequest.requestBody
                        )
                    })
                    .addTag<Harbringer.PendingRequest>(pendingRequest)
                    .build()
            )
        } catch (e: Exception) {
            pendingRequest.onFailed(
                timings = chain.call().metadata?.toTimings(),
                error = e,
            )
            throw e
        }

        val responseBody = response.body
        return if (responseBody?.contentLength()?.equals(0L) == false) {
            response.newBuilder()
                .body(
                    RecordingResponseBody(
                        call = chain.call(),
                        response = response,
                        delegate = responseBody,
                        pendingRequest = pendingRequest,
                    )
                )
                .build()
        } else {
            onComplete(response, chain.call().metadata)
            response
        }
    }

    private fun onComplete(response: Response, metadata: RequestMetadata?) {
        val pendingRequest = response.request.tagOf<Harbringer.PendingRequest>()!!
        pendingRequest.onComplete(
            response = Harbringer.Response(
                code = response.code,
                message = response.message,
                protocol = response.protocol.toString(),
                headers = response.headers.toRequestLoggerHeaders(),
                body = response.body?.run {
                    Harbringer.Response.Body(
                        byteCount = contentLength(),
                        contentType = contentType()?.toString(),
                    )
                }
            ),
            timings = metadata?.toTimings()
        )
    }


    companion object {
        private inline fun <reified T> Request.Builder.addTag(tag: T) = tag(T::class.java, tag)
        private inline fun <reified T> Request.tagOf() = tag(T::class.java)

        private fun Headers.toRequestLoggerHeaders(): Harbringer.Headers =
            Harbringer.Headers(
                values = map { (k, v) -> Harbringer.Header(k, v) }
            )

        private fun Request.toRequestLoggerRequest(
            protocol: Protocol?,
        ): Harbringer.Request =
            Harbringer.Request(
                method = method,
                url = url.toString(),
                protocol = protocol?.toString() ?: "HTTP/1.1",
                headers = headers.toRequestLoggerHeaders(),
                body = body?.toBody()
            )

        private fun RequestBody.toBody(): Harbringer.Request.Body =
            when (this) {
                is RecordingRequestBody -> delegate.toBody()
                is FormBody -> Harbringer.Request.Body(
                    byteCount = contentLength(),
                    contentType = contentType().toString(),
                    params = List(size) { i ->
                        Harbringer.Request.Body.Param(name(i), value(i))
                    }
                )

                is MultipartBody -> Harbringer.Request.Body(
                    byteCount = contentLength(),
                    contentType = contentType().toString(),
                    params = parts.map { part ->
                        var name: String? = null
                        var fileName: String? = null
                        part.headers?.get("Content-Disposition")
                            ?.split("; ")
                            ?.forEach {
                                when {
                                    it.startsWith("name=") -> {
                                        name = it.substringAfter("=").decodeContentTypeParameter()
                                    }

                                    it.startsWith("filename=") -> {
                                        fileName = it.substringAfter("=").decodeContentTypeParameter()
                                    }
                                }
                            }

                        val canReadBody = MimeTypes.isTextMimeType(part.body.contentType()?.toString()) &&
                                part.body.contentLength() in 0L..8192L &&
                                !part.body.isOneShot()

                        Harbringer.Request.Body.Param(
                            name = name ?: "",
                            value = if (canReadBody) {
                                Buffer()
                                    .apply { part.body.writeTo(this) }
                                    .readString(part.body.contentType()?.charset() ?: Charsets.UTF_8)
                            } else {
                                null
                            },
                            fileName = fileName,
                            contentType = part.body.contentType()?.toString()
                        )
                    }
                )

                else -> Harbringer.Request.Body(
                    byteCount = contentLength(),
                    contentType = contentType()?.toString()
                )
            }
    }

    private inner class RequestMetadata {
        val start = TimeSource.Monotonic.markNow()
        val dns = Timing()
        val connect = Timing()
        val ssl = Timing()
        val send = Timing()
        val wait: Duration?
            get() = receive.start?.let { r -> send.end?.let { s -> r - s } }
        val receive = Timing()

        fun toTimings(): Harbringer.Timings = Harbringer.Timings(
            total = start.elapsedNow(),
            blocked = (dns.start ?: connect.start ?: send.start)?.let { it - start },
            dns = dns.duration,
            connect = connect.duration,
            ssl = ssl.duration,
            send = send.duration,
            wait = wait,
            receive = receive.duration,
        )

        inner class Timing {
            var start: TimeSource.Monotonic.ValueTimeMark? = null
                private set
            val end: TimeSource.Monotonic.ValueTimeMark?
                get() = start?.let { s -> duration?.let { d -> s + d } }
            private var _duration = Duration.Companion.INFINITE

            val duration: Duration?
                get() = _duration.takeIf { it.isFinite() }

            fun onStart() {
                start = TimeSource.Monotonic.markNow()
            }

            fun onEnd() {
                if (strict) {
                    checkNotNull(start) { "onEnd called without onStart" }
                }
                _duration = start?.elapsedNow() ?: Duration.Companion.INFINITE
                start = null
            }
        }
    }

    private class RecordingRequestBody(
        val delegate: RequestBody,
        private val record: BufferedSink,
    ) : RequestBody() {
        override fun contentLength(): Long = delegate.contentLength()
        override fun contentType(): MediaType? = delegate.contentType()
        override fun isDuplex(): Boolean = delegate.isDuplex()
        override fun isOneShot(): Boolean = delegate.isOneShot()

        override fun writeTo(sink: BufferedSink) {
            val recordingSink = RecordingSink(sink, record).buffer()
            delegate.writeTo(recordingSink)
            recordingSink.flush()
        }
    }

    private class RecordingSink(
        private val delegate: BufferedSink,
        private val record: BufferedSink,
    ) : Sink {
        override fun close() {
            delegate.close()
            record.close()
        }

        override fun flush() {
            delegate.flush()
            record.flush()
        }

        override fun timeout(): Timeout = delegate.timeout()

        override fun write(source: Buffer, byteCount: Long) {
            source.copyTo(record.buffer, byteCount = byteCount)
            record.emitCompleteSegments()
            delegate.write(source, byteCount)
        }
    }

    private inner class RecordingResponseBody(
        private val call: Call,
        private val response: Response,
        private val delegate: ResponseBody,
        private val pendingRequest: Harbringer.PendingRequest,
        private val metadata: RequestMetadata? = call.metadata,
    ) : ResponseBody() {
        private val source = RecordingSource().buffer()

        override fun close() {
            source.close()
            delegate.close()
        }

        override fun contentLength(): Long = delegate.contentLength()
        override fun contentType(): MediaType? = delegate.contentType()
        override fun source(): BufferedSource = source

        private inner class RecordingSource() : AtomicBoolean(false), Source {
            private val record: BufferedSink = pendingRequest.requestBody
            private val delegate = this@RecordingResponseBody.delegate.source()

            override fun close() {
                if (compareAndSet(false, true)) {
                    if (delegate.isOpen && record.isOpen) {
                        record.writeAll(delegate)
                    }
                    delegate.close()
                    record.close()
                    onComplete(response, metadata)
                }
            }

            override fun read(sink: Buffer, byteCount: Long): Long {
                val offset = sink.size
                val read = delegate.read(sink, byteCount)
                sink.copyTo(record.buffer, offset, read)
                record.emitCompleteSegments()
                return read
            }

            override fun timeout(): Timeout = delegate.timeout()
        }
    }
}