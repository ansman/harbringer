package se.ansman.harbringer.okhttp3

import assertk.all
import assertk.assertThat
import assertk.assertions.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.buffer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import se.ansman.harbringer.Harbringer
import se.ansman.harbringer.storage.InMemoryHarbringerStorage
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.days
import kotlin.time.ExperimentalTime


@OptIn(ExperimentalAtomicApi::class, ExperimentalTime::class)
class RequestLoggerInterceptorTest {
    private val storage = InMemoryHarbringerStorage()
    private val harbringer = Harbringer(
        storage = storage,
        maxRequests = 100,
        maxDiskUsage = 1000,
        maxAge = 1.days,
    )
    private val entries = sequence { yieldAll(storage.getIds()) }
        .mapNotNull { storage.getEntry(it) }

    private val okHttpClient = OkHttpClient.Builder()
        .addHarbringer(harbringer, strict = true)
        .build()

    private val fakeServer = MockWebServer()

    @BeforeEach
    fun setUp() {
        fakeServer.start()
    }

    @AfterEach
    fun tearDown() {
        fakeServer.shutdown()
    }

    @Test
    fun `recording should register the request`() {
        fakeServer.enqueue(MockResponse().apply {
            setResponseCode(210)
            addHeader("Example-Header", "Request")
            addHeader("Content-Type", "text/plain; charset=utf-8")
            setBody("Response Body")
        })
        val response = okHttpClient
            .newCall(
                Request.Builder()
                    .method("POST", "Request Body".toRequestBody("text/plain".toMediaType()))
                    .url(
                        fakeServer.url("/")
                            .newBuilder()
                            .addQueryParameter("example", "query")
                            .addQueryParameter("empty", null)
                            .build()
                    )
                    .header("Example-Header", "Request")
                    .build()
            )
            .execute()
        assertThat(response.code).isEqualTo(210)
        assertThat(entries.toList()).isEmpty()
        response.close()

        val entry = entries.toList().single()
        assertThat(entry).apply {
            prop(Harbringer.Entry::server).isEqualTo(Harbringer.Device("127.0.0.1", fakeServer.port))
            prop(Harbringer.Entry::client).isNotNull().prop(Harbringer.Device::ip).isEqualTo("127.0.0.1")
            prop(Harbringer.Entry::request).isEqualTo(
                Harbringer.Request(
                    method = "POST",
                    url = fakeServer.url("/")
                        .newBuilder()
                        .addQueryParameter("example", "query")
                        .addQueryParameter("empty", null)
                        .build()
                        .toString(),
                    protocol = "http/1.1",
                    headers = Harbringer.Headers(
                        "Example-Header" to "Request",
                        "Content-Type" to "text/plain; charset=utf-8",
                        "Content-Length" to "12",
                        "Host" to "${fakeServer.hostName}:${fakeServer.port}",
                        "Connection" to "Keep-Alive",
                        "Accept-Encoding" to "gzip",
                        "User-Agent" to "okhttp/${OkHttp.VERSION}",
                    ),
                    body = Harbringer.Request.Body(
                        byteCount = 12,
                        contentType = "text/plain; charset=utf-8",
                    ),
                )
            )
            prop(Harbringer.Entry::response).isEqualTo(
                Harbringer.Response(
                    code = 210,
                    message = "OK",
                    protocol = "http/1.1",
                    headers = Harbringer.Headers(
                        "Example-Header" to "Request",
                        "Content-Type" to "text/plain; charset=utf-8",
                        "Content-Length" to "13",
                    ),
                    body = Harbringer.Response.Body(
                        byteCount = 13,
                        contentType = "text/plain; charset=utf-8",
                    ),
                )
            )
        }
        assertThat(harbringer.getRequestBody(entry.id)?.buffer()?.use { it.readUtf8() })
            .isEqualTo("Request Body")
        assertThat(harbringer.getResponseBody(entry.id)?.buffer()?.use { it.readUtf8() })
            .isEqualTo("Response Body")
    }

    @Test
    fun `form bodies are registered correctly`() {
        fakeServer.enqueue(MockResponse())
        okHttpClient
            .newCall(
                Request.Builder()
                    .method(
                        "POST", FormBody.Builder()
                            .add("key1", "value1")
                            .add("key2", "(value2)")
                            .build()
                    )
                    .url(fakeServer.url("/"))
                    .build()
            )
            .execute()
            .close()
        assertThat(entries.toList())
            .single()
            .prop(Harbringer.Entry::request)
            .prop(Harbringer.Request::body)
            .isEqualTo(
                Harbringer.Request.Body(
                    byteCount = 29,
                    contentType = "application/x-www-form-urlencoded",
                    params = listOf(
                        Harbringer.Request.Body.Param("key1", "value1"),
                        Harbringer.Request.Body.Param("key2", "(value2)"),
                    )
                )
            )
    }

    @Test
    fun `multipart bodies are registered correctly`() {
        fakeServer.enqueue(MockResponse())
        okHttpClient
            .newCall(
                Request.Builder()
                    .method(
                        "POST", MultipartBody.Builder()
                            .addFormDataPart("key1", "value1")
                            .addFormDataPart(
                                "file1",
                                filename = null,
                                body = "contents1".toByteArray().toRequestBody("application/octet-stream".toMediaType())
                            )
                            .addFormDataPart(
                                "file2",
                                filename = "file2.txt",
                                body = "contents2".toRequestBody("text/plain; charset=utf-8".toMediaType())
                            )
                            .build()
                    )
                    .url(fakeServer.url("/"))
                    .build()
            )
            .execute()
            .close()
        assertThat(entries.toList())
            .single()
            .prop(Harbringer.Entry::request)
            .prop(Harbringer.Request::body)
            .isNotNull()
            .all {
                prop(Harbringer.Request.Body::byteCount).isEqualTo(495)
                prop(Harbringer.Request.Body::contentType).isNotNull().startsWith("multipart/mixed; boundary=")
                prop(Harbringer.Request.Body::params).isEqualTo(
                    listOf(
                        Harbringer.Request.Body.Param("key1"),
                        Harbringer.Request.Body.Param(
                            name = "file1",
                            value = null,
                            contentType = "application/octet-stream"
                        ),
                        Harbringer.Request.Body.Param(
                            name = "file2",
                            value = "contents2",
                            fileName = "file2.txt",
                            contentType = "text/plain; charset=utf-8"
                        ),
                    )
                )
            }
    }
}