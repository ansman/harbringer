package se.ansman.harbringer.internal

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okio.Buffer
import okio.ByteString.Companion.encodeUtf8
import okio.Path.Companion.toPath
import okio.Sink
import okio.fakefilesystem.FakeFileSystem
import se.ansman.harbringer.Harbringer
import se.ansman.harbringer.Harbringer.Headers
import se.ansman.harbringer.Harbringer.Request
import se.ansman.harbringer.scrubber.Scrubber
import se.ansman.harbringer.storage.FileSystemHarbringerStorage
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class, ExperimentalAtomicApi::class, ExperimentalSerializationApi::class)
class RealHarbringerTest {
    private var scrubber: Scrubber = Scrubber.noScrubbing
    private val clock = TestClock()
    private val json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
    val storage = FileSystemHarbringerStorage(
        root = "/".toPath(),
        fileSystem = FakeFileSystem(clock.kotlinClock),
        json = json,
        rethrowErrors = true
    )

    //    val storage = InMemoryHarbringerStorage()
    private val logger = RealHarbringer(
        storage = storage,
        maxRequests = 3,
        maxDiskUsage = 1024 * 1024, // 1MB
        maxAge = 10.minutes,
        clock = clock,
        scrubber = object : Scrubber {
            override fun scrubRequest(request: Request): Request? = scrubber.scrubRequest(request)
            override fun scrubRequestBody(request: Request, sink: Sink): Sink = scrubber.scrubRequestBody(request, sink)
            override fun scrubResponse(request: Request, response: Harbringer.Response): Harbringer.Response? =
                scrubber.scrubResponse(request, response)

            override fun scrubResponseBody(request: Request, sink: Sink): Sink =
                scrubber.scrubResponseBody(request, sink)
        },
        enabled = true,
    )

    @Test
    fun `can export to HAR archive`() {
        val request = fakeRequest()
        logger.record(request)?.apply {
            server = Harbringer.Device("1.2.3.4", 1234)
            client = Harbringer.Device("4.3.2.1", 4321)
            requestBody.writeUtf8("Request")
            responseBody.writeUtf8("Response")
            onComplete(
                response = fakeResponse(),
                timings = fakeTimings()
            )
        }
        logger.record(request)?.apply {
            server = Harbringer.Device("1.2.3.4", 1234)
            client = Harbringer.Device("4.3.2.1", 4321)
            requestBody.writeUtf8("Request")
            responseBody.writeUtf8("Response")
            onComplete(
                response = fakeResponse(),
                timings = fakeTimings()
            )
        }
        val output = Buffer()
        logger.exportHarArchive(output, json)
        val har = output.readUtf8()
        assertThat(har).isEqualTo(
            """
            {
              "log": {
                "version": "1.2",
                "creator": {
                  "name": "Harbringer",
                  "version": "$HARBRINGER_VERSION",
                  "comment": "Exported at 2025-04-27T20:59:12.123Z"
                },
                "entries": [
                  {
                    "startedDateTime": "2025-04-27T20:59:12.123Z",
                    "time": 123,
                    "request": {
                      "method": "POST",
                      "url": "https://example.com/?example=query&empty",
                      "httpVersion": "http/1.1",
                      "headers": [
                        {
                          "name": "Example-Header",
                          "value": "Request"
                        },
                        {
                          "name": "Content-Type",
                          "value": "text/plain; charset=utf-8"
                        },
                        {
                          "name": "Content-Length",
                          "value": "7"
                        },
                        {
                          "name": "Host",
                          "value": "example.com"
                        },
                        {
                          "name": "Connection",
                          "value": "Keep-Alive"
                        },
                        {
                          "name": "Accept-Encoding",
                          "value": "gzip"
                        },
                        {
                          "name": "User-Agent",
                          "value": "okhttp/1.2.3"
                        }
                      ],
                      "queryString": [
                        {
                          "name": "example",
                          "value": "query"
                        },
                        {
                          "name": "empty",
                          "value": ""
                        }
                      ],
                      "postData": {
                        "mimeType": "text/plain; charset=utf-8",
                        "text": "Request"
                      },
                      "headersSize": 177,
                      "bodySize": 7
                    },
                    "response": {
                      "status": 200,
                      "statusText": "OK",
                      "httpVersion": "http/1.1",
                      "headers": [
                        {
                          "name": "Example-Header",
                          "value": "Request"
                        },
                        {
                          "name": "Content-Type",
                          "value": "text/plain; charset=utf-8"
                        },
                        {
                          "name": "Content-Length",
                          "value": "8"
                        }
                      ],
                      "content": {
                        "size": 8,
                        "mimeType": "text/plain; charset=utf-8",
                        "text": "Response"
                      },
                      "redirectURL": "",
                      "headersSize": 85,
                      "bodySize": 8
                    },
                    "serverIPAddress": "1.2.3.4",
                    "_serverPort": 1234,
                    "_clientAddress": "4.3.2.1",
                    "_clientPort": 4321,
                    "timings": {
                      "blocked": 1,
                      "dns": 2,
                      "connect": 3,
                      "send": 4,
                      "wait": 5,
                      "receive": 6,
                      "ssl": 7
                    }
                  },
                  {
                    "startedDateTime": "2025-04-27T20:59:12.123Z",
                    "time": 123,
                    "request": {
                      "method": "POST",
                      "url": "https://example.com/?example=query&empty",
                      "httpVersion": "http/1.1",
                      "headers": [
                        {
                          "name": "Example-Header",
                          "value": "Request"
                        },
                        {
                          "name": "Content-Type",
                          "value": "text/plain; charset=utf-8"
                        },
                        {
                          "name": "Content-Length",
                          "value": "7"
                        },
                        {
                          "name": "Host",
                          "value": "example.com"
                        },
                        {
                          "name": "Connection",
                          "value": "Keep-Alive"
                        },
                        {
                          "name": "Accept-Encoding",
                          "value": "gzip"
                        },
                        {
                          "name": "User-Agent",
                          "value": "okhttp/1.2.3"
                        }
                      ],
                      "queryString": [
                        {
                          "name": "example",
                          "value": "query"
                        },
                        {
                          "name": "empty",
                          "value": ""
                        }
                      ],
                      "postData": {
                        "mimeType": "text/plain; charset=utf-8",
                        "text": "Request"
                      },
                      "headersSize": 177,
                      "bodySize": 7
                    },
                    "response": {
                      "status": 200,
                      "statusText": "OK",
                      "httpVersion": "http/1.1",
                      "headers": [
                        {
                          "name": "Example-Header",
                          "value": "Request"
                        },
                        {
                          "name": "Content-Type",
                          "value": "text/plain; charset=utf-8"
                        },
                        {
                          "name": "Content-Length",
                          "value": "8"
                        }
                      ],
                      "content": {
                        "size": 8,
                        "mimeType": "text/plain; charset=utf-8",
                        "text": "Response"
                      },
                      "redirectURL": "",
                      "headersSize": 85,
                      "bodySize": 8
                    },
                    "serverIPAddress": "1.2.3.4",
                    "_serverPort": 1234,
                    "_clientAddress": "4.3.2.1",
                    "_clientPort": 4321,
                    "timings": {
                      "blocked": 1,
                      "dns": 2,
                      "connect": 3,
                      "send": 4,
                      "wait": 5,
                      "receive": 6,
                      "ssl": 7
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `can export to HAR archive with binary data`() {
        val request = Request(
            method = "POST",
            url = "https://example.com/?example=query&empty",
            protocol = "http/1.1",
            headers = Headers(
                "Example-Header" to "Request",
                "Content-Type" to "application/octet-stream",
                "Content-Length" to "7",
                "Host" to "example.com",
                "Connection" to "Keep-Alive",
                "Accept-Encoding" to "gzip",
                "User-Agent" to "okhttp/1.2.3",
            ),
            body = Request.Body(
                byteCount = 7,
                contentType = "application/octet-stream",
            ),
        )
        logger.record(request)?.apply {
            server = Harbringer.Device("1.2.3.4", 1234)
            client = Harbringer.Device("4.3.2.1", 4321)
            requestBody.writeUtf8("Request")
            responseBody.writeUtf8("Response")
            onComplete(
                response = Harbringer.Response(
                    code = 210,
                    message = "OK",
                    protocol = "http/1.1",
                    headers = Headers(
                        "Example-Header" to "Request",
                        "Content-Type" to "application/octet-stream",
                        "Content-Length" to "8",
                    ),
                    body = Harbringer.Response.Body(
                        byteCount = 8,
                        contentType = "application/octet-stream",
                    ),
                ),
                timings = fakeTimings()
            )
        }
        val output = Buffer()
        logger.exportHarArchive(output, json)
        val har = output.readUtf8()
        assertThat(har).isEqualTo(
            """
            {
              "log": {
                "version": "1.2",
                "creator": {
                  "name": "Harbringer",
                  "version": "$HARBRINGER_VERSION",
                  "comment": "Exported at 2025-04-27T20:59:12.123Z"
                },
                "entries": [
                  {
                    "startedDateTime": "2025-04-27T20:59:12.123Z",
                    "time": 123,
                    "request": {
                      "method": "POST",
                      "url": "https://example.com/?example=query&empty",
                      "httpVersion": "http/1.1",
                      "headers": [
                        {
                          "name": "Example-Header",
                          "value": "Request"
                        },
                        {
                          "name": "Content-Type",
                          "value": "application/octet-stream"
                        },
                        {
                          "name": "Content-Length",
                          "value": "7"
                        },
                        {
                          "name": "Host",
                          "value": "example.com"
                        },
                        {
                          "name": "Connection",
                          "value": "Keep-Alive"
                        },
                        {
                          "name": "Accept-Encoding",
                          "value": "gzip"
                        },
                        {
                          "name": "User-Agent",
                          "value": "okhttp/1.2.3"
                        }
                      ],
                      "queryString": [
                        {
                          "name": "example",
                          "value": "query"
                        },
                        {
                          "name": "empty",
                          "value": ""
                        }
                      ],
                      "postData": {
                        "mimeType": "application/octet-stream",
                        "text": "UmVxdWVzdA=="
                      },
                      "headersSize": 176,
                      "bodySize": 7
                    },
                    "response": {
                      "status": 210,
                      "statusText": "OK",
                      "httpVersion": "http/1.1",
                      "headers": [
                        {
                          "name": "Example-Header",
                          "value": "Request"
                        },
                        {
                          "name": "Content-Type",
                          "value": "application/octet-stream"
                        },
                        {
                          "name": "Content-Length",
                          "value": "8"
                        }
                      ],
                      "content": {
                        "size": 8,
                        "mimeType": "application/octet-stream",
                        "text": "UmVzcG9uc2U=",
                        "encoding": "base64"
                      },
                      "redirectURL": "",
                      "headersSize": 84,
                      "bodySize": 8
                    },
                    "serverIPAddress": "1.2.3.4",
                    "_serverPort": 1234,
                    "_clientAddress": "4.3.2.1",
                    "_clientPort": 4321,
                    "timings": {
                      "blocked": 1,
                      "dns": 2,
                      "connect": 3,
                      "send": 4,
                      "wait": 5,
                      "receive": 6,
                      "ssl": 7
                    }
                  }
                ]
              }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `can scrub data`() {
        val request = Request(
            method = "POST",
            url = "https://example.com/?example=query&empty&apiKey=deadbeef",
            protocol = "http/1.1",
            headers = Headers(
                "Example-Header" to "Request",
                "Authorization" to "deadd00d",
                "Content-Type" to "text/plain; charset=utf-8",
                "Content-Length" to "7",
                "Host" to "example.com",
                "Connection" to "Keep-Alive",
                "Accept-Encoding" to "gzip",
                "User-Agent" to "okhttp/1.2.3",
            ),
            body = Request.Body(
                byteCount = -1,
                contentType = "multipart/form-data; boundary=deadbeef",
                params = listOf(
                    Request.Body.Param("sensitive", "correctHorseBatteryStaple"),
                    Request.Body.Param("empty", ""),
                ),
            ),
        )
        val response = Harbringer.Response(
            code = 200,
            message = "OK",
            protocol = "http/1.1",
            headers = Headers(
                "Example-Header" to "Request",
                "Content-Type" to "text/plain; charset=utf-8",
                "Content-Length" to "8",
                "Sensitive" to "deadbeef",
            ),
            body = Harbringer.Response.Body(
                byteCount = -1,
                contentType = "text/plain; charset=utf-8",
            ),
        )

        scrubber = Scrubber(
            request = Scrubber.request(
                queryParameter = Scrubber.queryParameter("apiKey"),
                header = Scrubber.header("authorization"),
                bodyParameter = Scrubber.bodyParameter("sensitive", replacement = "redacted")
            ),
            requestBody = Scrubber.replaceBody("Redacted".encodeUtf8()),
            response = Scrubber.response(
                header = Scrubber.header("sensitive"),
            ),
            responseBody = Scrubber.discardBody(),
        )

        val id = logger.record(request)!!
            .apply {
                requestBody.writeUtf8("This is a sensitive request")
                responseBody.writeUtf8("This is a sensitive response")
                onComplete(response)
            }
            .id
        assertThat(storage.getEntry(id)?.request).isEqualTo(
            Request(
                method = "POST",
                url = "https://example.com/?example=query&empty&apiKey=******",
                protocol = "http/1.1",
                headers = Headers(
                    "Example-Header" to "Request",
                    "Authorization" to "******",
                    "Content-Type" to "text/plain; charset=utf-8",
                    "Content-Length" to "7",
                    "Host" to "example.com",
                    "Connection" to "Keep-Alive",
                    "Accept-Encoding" to "gzip",
                    "User-Agent" to "okhttp/1.2.3",
                ),
                body = Request.Body(
                    byteCount = -1,
                    contentType = "multipart/form-data; boundary=deadbeef",
                    params = listOf(
                        Request.Body.Param("sensitive", "redacted"),
                        Request.Body.Param("empty", ""),
                    ),
                ),
            )
        )
        assertThat(storage.getEntry(id)?.response).isEqualTo(
            Harbringer.Response(
                code = 200,
                message = "OK",
                protocol = "http/1.1",
                headers = Headers(
                    "Example-Header" to "Request",
                    "Content-Type" to "text/plain; charset=utf-8",
                    "Content-Length" to "8",
                    "Sensitive" to "******",
                ),
                body = Harbringer.Response.Body(
                    byteCount = -1,
                    contentType = "text/plain; charset=utf-8",
                ),
            )
        )
    }

    @Test
    fun `keeps only max count entries`() {
        val entries = mutableListOf<String>()
        repeat(5) {
            val request = logger.record(fakeRequest())!!
            entries.add(request.id)
            request.onComplete(fakeResponse())
            clock += 1.milliseconds
        }
        assertThat(logger.getIds().toList()).isEqualTo(entries.takeLast(3))
        assertThat(storage.getIds()).containsExactlyInAnyOrder(*entries.takeLast(3).toTypedArray())
    }

    @Test
    fun `evicts entries when the max size is reached`() {
        val random = Random(0xdeadbeef)
        val entries = mutableListOf<String>()
        repeat(3) {
            val request = logger.record(fakeRequest())!!
            request.requestBody.write(random.nextBytes(1024 * 400)) // 513KB
            entries.add(request.id)
            request.onComplete(fakeResponse())
            clock += 1.milliseconds
        }
        assertThat(logger.getIds().toList()).isEqualTo(entries.takeLast(2))
        assertThat(storage.getIds()).containsExactlyInAnyOrder(*entries.takeLast(2).toTypedArray())
    }

    @Test
    fun `evicts entries older than the max time`() {
        val entries = mutableListOf<String>()
        repeat(3) {
            val request = logger.record(fakeRequest())!!
            entries.add(request.id)
            request.onComplete(fakeResponse())
            clock += 4.minutes
        }
        assertThat(logger.getIds().toList()).isEqualTo(entries.takeLast(2))
        assertThat(storage.getIds()).containsExactlyInAnyOrder(*entries.takeLast(2).toTypedArray())
    }
}

private fun fakeTimings(): Harbringer.Timings = Harbringer.Timings(
    total = 123.milliseconds,
    blocked = 1.milliseconds,
    dns = 2.milliseconds,
    connect = 3.milliseconds,
    send = 4.milliseconds,
    wait = 5.milliseconds,
    receive = 6.milliseconds,
    ssl = 7.milliseconds,
)

private fun fakeResponse(): Harbringer.Response = Harbringer.Response(
    code = 200,
    message = "OK",
    protocol = "http/1.1",
    headers = Headers(
        "Example-Header" to "Request",
        "Content-Type" to "text/plain; charset=utf-8",
        "Content-Length" to "8",
    ),
    body = Harbringer.Response.Body(
        byteCount = 8,
        contentType = "text/plain; charset=utf-8",
    ),
)

private fun fakeRequest(): Request = Request(
    method = "POST",
    url = "https://example.com/?example=query&empty",
    protocol = "http/1.1",
    headers = Headers(
        "Example-Header" to "Request",
        "Content-Type" to "text/plain; charset=utf-8",
        "Content-Length" to "7",
        "Host" to "example.com",
        "Connection" to "Keep-Alive",
        "Accept-Encoding" to "gzip",
        "User-Agent" to "okhttp/1.2.3",
    ),
    body = Request.Body(
        byteCount = 7,
        contentType = "text/plain; charset=utf-8",
    ),
)