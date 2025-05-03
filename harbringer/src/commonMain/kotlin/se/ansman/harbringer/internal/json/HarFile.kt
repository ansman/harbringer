package se.ansman.harbringer.internal.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString
import se.ansman.harbringer.Harbringer
import se.ansman.harbringer.internal.MimeTypes
import se.ansman.harbringer.internal.Url
import se.ansman.harbringer.internal.formatIso8601
import se.ansman.harbringer.internal.readString

@Serializable
internal data class HarFile(
    val log: Log,
) {

    @Serializable
    data class Log(
        val version: String,
        val creator: Creator,
        val browser: Browser? = null,
        @Serializable(with = IterableSerializer::class)
        val entries: Iterable<Entry> = emptyList(),
        val pages: List<Page> = emptyList(),
    )

    @Serializable
    data class Entry(
        val startedDateTime: String,
        val time: Long,
        val request: Request,
        val response: Response,
        val cache: Cache? = null,
        val serverIPAddress: String? = null,
        @SerialName("_serverPort")
        val serverPort: Int? = null,
        @SerialName("_clientAddress")
        val clientIPAddress: String? = null,
        @SerialName("_clientPort")
        val clientPort: Int? = null,
        val timings: Timings,
        val connection: String? = null,
        val pageref: String? = null,
        val comment: String? = null,
        @SerialName("_isHTTPS")
        val isHttps: Boolean? = request.url.startsWith("https://"),
    )

    @Serializable
    data class Timings(
        val blocked: Long? = null,
        val dns: Long? = null,
        val connect: Long? = null,
        val send: Long? = null,
        val wait: Long? = null,
        val receive: Long? = null,
        val ssl: Long? = null,
        val comment: String? = null,
    )

    @Serializable
    data class Request(
        val method: String,
        val url: String,
        val httpVersion: String,
        val cookies: List<Cookie> = emptyList(),
        val headers: List<Header> = emptyList(),
        val queryString: List<QueryParameter> = emptyList(),
        val postData: PostData? = null,
        val headersSize: Long,
        val bodySize: Long,
        val comment: String? = null,
    ) {
        @Serializable
        data class PostData(
            val mimeType: String,
            val text: LazyString,
            val params: List<Parameter> = emptyList(),
            val comment: String? = null,
        ) {
            @Serializable
            data class Parameter(
                val name: String,
                val value: String? = null,
                val fileName: String? = null,
                val contentType: String? = null,
                val comment: String? = null,
            )
        }
    }

    @Serializable
    data class Response(
        val status: Int,
        val statusText: String,
        val httpVersion: String,
        val cookies: List<Cookie> = emptyList(),
        val headers: List<Header> = emptyList(),
        val content: Content,
        val redirectURL: String,
        val headersSize: Long,
        val bodySize: Long,
        val comment: String? = null,
    ) {
        @Serializable
        data class Content(
            val size: Long,
            val mimeType: String,
            val compression: Long? = null,
            val text: LazyString? = null,
            val encoding: String? = null,
            val comment: String? = null,
        )
    }

    // Cache
    @Serializable
    data class Cache(
        val beforeRequest: CacheEntry? = null,
        val afterRequest: CacheEntry? = null,
        val comment: String? = null,
    ) {
        @Serializable
        data class CacheEntry(
            val expires: String? = null,
            val lastAccess: String? = null,
            val eTag: String? = null,
            val hitCount: Long? = null,
            val comment: String? = null,
        )
    }

    @Serializable
    data class Cookie(
        val name: String,
        val value: String,
        val path: String? = null,
        val domain: String? = null,
        val expires: String? = null,
        val httpOnly: Boolean? = null,
        val secure: Boolean? = null,
        val comment: String? = null,
    )

    @Serializable
    data class Header(
        val name: String,
        val value: String,
        val comment: String? = null,
    )

    @Serializable
    data class QueryParameter(
        val name: String,
        val value: String,
        val comment: String? = null,
    )

    @Serializable
    data class Page(
        val startedDateTime: String,
        val id: String,
        val title: String,
        val pageTimings: Timings,
        val comment: String? = null,
    ) {
        @Serializable
        data class Timings(
            val onContentLoad: Long? = null,
            val onLoad: Long? = null,
            val comment: String? = null,
        )
    }

    @Serializable
    data class Browser(
        val name: String,
        val version: String,
        val comment: String? = null,
    )

    @Serializable
    data class Creator(
        val name: String,
        val version: String,
        val comment: String? = null,
    )
}

internal fun Harbringer.Entry.toHarEntry(
    requestBody: () -> ByteString?,
    responseBody: () -> ByteString?,
): HarFile.Entry =
    HarFile.Entry(
        startedDateTime = formatIso8601(startedAt),
        time = timings.total.inWholeMilliseconds,
        request = request.toHarRequest(requestBody),
        response = response.toHarResponse(responseBody),
        serverIPAddress = server?.ip,
        serverPort = server?.port,
        clientIPAddress = client?.ip,
        clientPort = client?.port,
        timings = timings.toHarTimings(),
    )

internal fun Harbringer.Request.toHarRequest(
    requestBody: () -> ByteString?,
): HarFile.Request =
    HarFile.Request(
        method = method,
        url = url,
        httpVersion = protocol,
        cookies = emptyList(),
        headers = headers.map { HarFile.Header(it.name, it.value) },
        queryString = Url(url).queryParameters.map { HarFile.QueryParameter(it.first, it.second ?: "") }.toList(),
        postData = body?.run {
            HarFile.Request.PostData(
                mimeType = contentType ?: "",
                text = LazyString {
                    val requestBody = requestBody()
                    (if (MimeTypes.isTextMimeType(contentType)) {
                        requestBody?.readString(MimeTypes.getCharset(contentType))
                    } else {
                        requestBody?.base64()
                    }) ?: ""
                },
                params = params.map { HarFile.Request.PostData.Parameter(it.name, it.value) },
            )
        },
        headersSize = headers.sumOf { it.name.length + it.value.length + 4L },
        bodySize = body?.byteCount ?: -1,
    )

internal fun Harbringer.Response.toHarResponse(
    responseBody: () -> ByteString?,
): HarFile.Response =
    HarFile.Response(
        status = code,
        statusText = message,
        httpVersion = protocol,
        cookies = emptyList(), // TODO
        headers = headers.map { HarFile.Header(it.name, it.value) },
        headersSize = headers.sumOf { it.name.length + it.value.length + 4L },
        redirectURL = headers.lastOrNull { it.name.equals("Location", ignoreCase = true) }?.value ?: "",
        bodySize = body?.byteCount ?: -1,
        comment = error,
        content = body
            ?.let {
                val isText = MimeTypes.isTextMimeType(body.contentType)
                HarFile.Response.Content(
                    size = it.byteCount,
                    mimeType = it.contentType ?: "",
                    text = LazyString {
                        val responseBody = responseBody()
                        if (isText) {
                            responseBody?.readString(MimeTypes.getCharset(body.contentType))
                        } else {
                            responseBody?.base64()
                        }
                    },
                    encoding = if (isText) null else "base64",
                )
            }
            ?: HarFile.Response.Content(
                size = -1,
                mimeType = "",
                text = null,
                encoding = null,
            ),
    )

private fun Harbringer.Timings.toHarTimings(): HarFile.Timings =
    HarFile.Timings(
        blocked = blocked?.inWholeMilliseconds,
        dns = dns?.inWholeMilliseconds,
        connect = connect?.inWholeMilliseconds,
        send = send?.inWholeMilliseconds,
        wait = wait?.inWholeMilliseconds,
        receive = receive?.inWholeMilliseconds,
        ssl = ssl?.inWholeMilliseconds,
    )