package se.ansman.harbringer

import kotlinx.serialization.json.*
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ForwardingSink
import okio.Sink
import se.ansman.harbringer.internal.JsonScrubber
import se.ansman.harbringer.internal.Url
import java.util.*

interface Scrubber {
    fun scrubRequest(request: Harbringer.Request): Harbringer.Request?
    fun scrubRequestBody(requestBody: Sink): Sink
    fun scrubResponse(response: Harbringer.Response): Harbringer.Response?
    fun scrubResponseBody(responseBody: Sink): Sink

    companion object {
        val noScrubbing: Scrubber = invoke()

        @JvmName("create")
        @JvmStatic
        operator fun invoke(
            request: (request: Harbringer.Request) -> Harbringer.Request? = { it },
            requestBody: (requestBody: Sink) -> Sink = { it },
            response: (response: Harbringer.Response) -> Harbringer.Response? = { it },
            responseBody: (responseBody: Sink) -> Sink = { it },
        ) = object : Scrubber {
            override fun scrubRequest(request: Harbringer.Request): Harbringer.Request? = request(request)
            override fun scrubRequestBody(requestBody: Sink): Sink = requestBody(requestBody)
            override fun scrubResponse(response: Harbringer.Response): Harbringer.Response? = response(response)
            override fun scrubResponseBody(responseBody: Sink): Sink = responseBody(responseBody)
        }

        fun header(
            vararg headers: String,
            replacement: String? = "******"
        ): (Harbringer.Header) -> Harbringer.Header? = header(headers.associateWith { { replacement } })

        fun header(
            vararg headers: Pair<String, (Harbringer.Header) -> String?>
        ): (Harbringer.Header) -> Harbringer.Header? = header(headers.toMap())

        fun header(
            headers: Map<String, (Harbringer.Header) -> String?>
        ): (Harbringer.Header) -> Harbringer.Header? {
            val downCased = headers.mapKeys { it.key.lowercase(Locale.ROOT) }
            return scrub@{ it ->
                val scrub = downCased[it.name.lowercase(Locale.ROOT)] ?: return@scrub it
                Harbringer.Header(it.name, scrub(it) ?: return@scrub null)
            }
        }

        fun queryParameter(
            vararg parameters: String,
            replacement: String? = "******"
        ): (name: String, value: String?) -> Pair<String, String?>? =
            queryParameter(parameters.associateWith { { replacement } })

        fun queryParameter(
            vararg parameters: Pair<String, (String) -> String?>
        ): (name: String, value: String?) -> Pair<String, String?>? = queryParameter(parameters.toMap())

        fun queryParameter(
            parameters: Map<String, (String) -> String?>
        ): (name: String, value: String?) -> Pair<String, String?>? = scrub@{ k, v ->
            if (v == null) {
                k to v
            } else {
                val scrub = parameters[k] ?: return@scrub k to v
                k to (scrub(v) ?: return@scrub null)
            }
        }

        fun bodyParameter(
            vararg parameters: String,
            replacement: String? = "******"
        ): (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param? =
            bodyParameter(parameters.associateWith {
                { p -> p.copy(value = replacement) }
            })

        fun bodyParameter(
            vararg parameters: Pair<String, (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param?>
        ): (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param? {
            val map = parameters.toMap()
            return scrub@{
                val scrub = map[it.name] ?: return@scrub it
                scrub(it)
            }
        }

        fun bodyParameter(
            parameters: Map<String, (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param?>
        ): (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param? {
            return scrub@{
                val scrub = parameters[it.name] ?: return@scrub it
                scrub(it)
            }
        }

        fun request(
            url: (String) -> String? = defaultScrubUrl,
            queryParameter: (name: String, value: String?) -> Pair<String, String?>? = defaultScrubQueryParameter,
            header: (Harbringer.Header) -> Harbringer.Header? = defaultScrubHeader,
            bodyParameter: (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param? = defaultBodyParameterScrubber,
        ): (Harbringer.Request) -> Harbringer.Request? = RequestScrubber(
            scrubUrl = url,
            scrubQueryParameter = queryParameter,
            scrubHeader = header,
            scrubBodyParameter = bodyParameter,
        )

        fun response(
            header: (Harbringer.Header) -> Harbringer.Header? = defaultScrubHeader,
        ): (Harbringer.Response) -> Harbringer.Response? = ResponseScrubber(
            scrubHeader = header,
        )

        fun discardBody(): (Sink) -> Sink = { delegate ->
            object : ForwardingSink(delegate) {
                override fun write(source: Buffer, byteCount: Long) = source.skip(byteCount)
            }
        }

        fun replaceBody(replacement: String): (Sink) -> Sink = replaceBody(replacement.encodeUtf8())

        fun replaceBody(replacement: ByteString): (Sink) -> Sink = { delegate ->
            object : ForwardingSink(delegate) {
                private var isClosed = false
                override fun write(source: Buffer, byteCount: Long) = source.skip(byteCount)
                override fun close() {
                    if (!isClosed) {
                        delegate.write(Buffer().write(replacement), replacement.size.toLong())
                        isClosed = true
                    }
                    super.close()
                }
            }
        }

        fun json(
            patterns: List<String>,
            json: Json = Json,
            replace: (path: String, element: JsonElement) -> JsonElement? = { _, element ->
                when (element) {
                    is JsonArray -> JsonArray(emptyList())
                    is JsonObject -> JsonObject(emptyMap())
                    is JsonPrimitive -> JsonPrimitive("******")
                    JsonNull -> JsonNull
                }
            }
        ): (Sink) -> Sink = JsonScrubber(
            patterns = patterns,
            json = json,
            replace = replace
        )
    }
}

private val defaultScrubUrl: (String) -> String? = { it }
private val defaultScrubQueryParameter: (name: String, value: String?) -> Pair<String, String?>? = { n, v -> n to v }
private val defaultScrubHeader: (Harbringer.Header) -> Harbringer.Header? = { it }
private val defaultBodyParameterScrubber: (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param? =
    { it }

private class RequestScrubber(
    private val scrubUrl: (String) -> String?,
    private val scrubQueryParameter: (name: String, value: String?) -> Pair<String, String?>?,
    private val scrubHeader: (Harbringer.Header) -> Harbringer.Header?,
    private val scrubBodyParameter: (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param?,
) : (Harbringer.Request) -> Harbringer.Request? {

    override fun invoke(input: Harbringer.Request): Harbringer.Request? {
        var url = scrubUrl(input.url)
            ?: return null

        if (scrubQueryParameter != defaultScrubQueryParameter) {
            url = scrubQueryParameters(Url(url))
        }
        return input.copy(
            url = url,
            headers = if (scrubHeader == defaultScrubHeader) {
                input.headers
            } else {
                input.headers.values
                    .mapNotNull(scrubHeader)
                    .let(Harbringer::Headers)
            },
            body = input.body?.run {
                if (scrubBodyParameter == defaultBodyParameterScrubber) {
                    this
                } else {
                    copy(params = params.mapNotNull(scrubBodyParameter))
                }
            }
        )
    }

    private fun scrubQueryParameters(url: Url): String =
        url.replaceQueryParameters(
            url.queryParameters
                .mapNotNull { scrubQueryParameter(it.first, it.second) }
                .asIterable())
            .toString()
}

private class ResponseScrubber(
    private val scrubHeader: (Harbringer.Header) -> Harbringer.Header?,
) : (Harbringer.Response) -> Harbringer.Response? {

    override fun invoke(input: Harbringer.Response): Harbringer.Response? {
        if (scrubHeader == defaultScrubHeader) {
            return input
        }
        return input.copy(
            headers = input.headers.values
                .mapNotNull(scrubHeader)
                .let(Harbringer::Headers)
        )
    }
}