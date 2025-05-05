package se.ansman.harbringer.scrubber

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.*
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ForwardingSink
import okio.Sink
import se.ansman.harbringer.Harbringer
import se.ansman.harbringer.scrubber.Scrubber.Companion.json
import java.util.*

/**
 * A [Scrubber] can remove or replace sensitive information from requests and responses.
 *
 * This is useful to avoid storing things like API keys, passwords, and other sensitive information in the Harbringer.
 */
interface Scrubber {
    /**
     * Scrubs a [Harbringer.Request].
     *
     * @param request The request to scrub.
     * @return The scrubbed request, or null if the request should be discarded (i.e., not stored).
     */
    fun scrubRequest(request: Harbringer.Request): Harbringer.Request?

    /**
     * Scrubs a request body.
     *
     * @param request The request the body belongs to.
     * @param sink The sink to write the scrubbed body to.
     * @return The sink that will receive the unscrubbed body.
     */
    fun scrubRequestBody(request: Harbringer.Request, sink: Sink): Sink

    /**
     * Scrubs a [Harbringer.Response].
     *
     * @param request The request that the response belongs to.
     * @param response The response to scrub.
     * @return The scrubbed response, or null if the request should be discarded (i.e., not stored).
     */
    fun scrubResponse(request: Harbringer.Request, response: Harbringer.Response): Harbringer.Response?

    /**
     * Scrubs a response body.
     *
     * @param request The request that the response belongs to.
     * @param sink The sink to write the scrubbed body to.
     * @return The sink that will receive the unscrubbed body.
     */
    fun scrubResponseBody(request: Harbringer.Request, sink: Sink): Sink

    companion object {
        /**
         * A [Scrubber] that performs no scrubbing.
         */
        @JvmStatic
        val noScrubbing: Scrubber = invoke()

        /**
         * Creates a new [Scrubber]
         *
         * @param request The [RequestScrubber] to use for scrubbing requests.
         * @param requestBody The [BodyScrubber] to use for scrubbing request bodies.
         * @param response The [ResponseScrubber] to use for scrubbing responses.
         * @param responseBody The [BodyScrubber] to use for scrubbing response bodies.
         * @see Scrubber.request
         * @see Scrubber.response
         * @see Scrubber.json
         * @see Scrubber.replaceBody
         * @see Scrubber.discardBody
         */
        @JvmName("create")
        @JvmStatic
        operator fun invoke(
            request: RequestScrubber = RequestScrubber { it },
            requestBody: BodyScrubber = BodyScrubber { _, sink -> sink },
            response: ResponseScrubber = ResponseScrubber { _, response -> response },
            responseBody: BodyScrubber = BodyScrubber { _, sink -> sink },
        ): Scrubber = RealScrubber(
            requestScrubber = request,
            requestBodyScrubber = requestBody,
            responseScrubber = response,
            responseBodyScrubber = responseBody,
        )

        /**
         * Returns a new [RequestScrubber].
         *
         * @param url A lambda that takes the URL and returns a scrubbed URL, or null to discard the request.
         * @param queryParameter A lambda that takes the name and value of a query parameter and returns a scrubbed name
         *   and value, or `null` to discard the query parameter.
         * @param header A lambda that takes a [Harbringer.Header] and returns a scrubbed header, or `null` to discard
         *   the header.
         * @param bodyParameter A lambda that takes a [Harbringer.Request.Body.Param] and returns a scrubbed parameter,
         *   or `null` to discard the parameter. The parameter is not removed from the body, just the [Harbringer.Request].
         * @param onlyIf A lambda that takes a [Harbringer.Request] and returns `true` if the request should be scrubbed,
         *   `false` otherwise.
         * @see [Scrubber.header]
         * @see [Scrubber.queryParameter]
         * @see [Scrubber.bodyParameter]
         */
        @JvmStatic
        fun request(
            url: (String) -> String? = defaultScrubUrl,
            queryParameter: (name: String, value: String?) -> Pair<String, String?>? = defaultScrubQueryParameter,
            header: (Harbringer.Header) -> Harbringer.Header? = defaultScrubHeader,
            bodyParameter: (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param? = defaultBodyParameterScrubber,
            onlyIf: (Harbringer.Request) -> Boolean = { true },
        ): RequestScrubber = RealRequestScrubber(
            scrubUrl = url,
            scrubQueryParameter = queryParameter,
            scrubHeader = header,
            scrubBodyParameter = bodyParameter,
        )

        /**
         * Returns a new [ResponseScrubber].
         *
         * @param header A lambda that takes a [Harbringer.Header] and returns a scrubbed header, or `null` to discard
         *   the header.
         * @param onlyIf A lambda that takes a [Harbringer.Request] and returns `true` if the response should be scrubbed,
         *   or `false` otherwise.
         */
        @JvmStatic
        fun response(
            header: (Harbringer.Header) -> Harbringer.Header? = defaultScrubHeader,
            onlyIf: (Harbringer.Request) -> Boolean = { true },
        ): ResponseScrubber = RealResponseScrubber(
            scrubHeader = header,
            onlyIf = onlyIf,
        )

        /**
         * Returns a lambda that scrubs headers.
         *
         * Headers in the given [headers] will be replaced with the given [replacement].
         *
         * @param headers The headers to scrub. They are not case-sensitive.
         * @param replacement The replacement value. Defaults to "******". If `null` the header will be discarded.
         */
        @JvmStatic
        fun header(
            vararg headers: String,
            replacement: String? = "******"
        ): (Harbringer.Header) -> Harbringer.Header? = header(headers.associateWith { { replacement } })

        /**
         * Returns a lambda that scrubs headers.
         *
         * @param headers The headers to scrub paired with a lambda that replaces the value.
         */
        @JvmStatic
        fun header(
            vararg headers: Pair<String, (Harbringer.Header) -> String?>
        ): (Harbringer.Header) -> Harbringer.Header? = header(headers.toMap())

        /**
         * Returns a lambda that scrubs headers.
         *
         * @param headers The headers to scrub mapped to a lambda that replaces the value.
         */
        @JvmStatic
        fun header(
            headers: Map<String, (Harbringer.Header) -> String?>
        ): (Harbringer.Header) -> Harbringer.Header? {
            val downCased = headers.mapKeys { it.key.lowercase(Locale.ROOT) }
            return scrub@{ it ->
                val scrub = downCased[it.name.lowercase(Locale.ROOT)] ?: return@scrub it
                Harbringer.Header(it.name, scrub(it) ?: return@scrub null)
            }
        }

        /**
         * Returns a lambda that scrubs query parameters.
         *
         * Query parameters in the given [parameters] will be replaced with the given [replacement].
         *
         * @param parameters The query parameters to scrub. They are not case-sensitive.
         * @param replacement The replacement value. Defaults to "******". If `null` the parameter will be discarded.
         */
        @JvmStatic
        fun queryParameter(
            vararg parameters: String,
            replacement: String? = "******"
        ): (name: String, value: String?) -> Pair<String, String?>? =
            queryParameter(parameters.associateWith { { replacement } })

        /**
         * Returns a lambda that scrubs query parameters.
         *
         * Query parameters in the given [parameters] will be replaced with the paired replacement.
         *
         * @param parameters The query parameters to scrub paired with a lambda that replaces the value.
         */
        @JvmStatic
        fun queryParameter(
            vararg parameters: Pair<String, (String) -> String?>
        ): (name: String, value: String?) -> Pair<String, String?>? = queryParameter(parameters.toMap())

        /**
         * Returns a lambda that scrubs query parameters.
         *
         * @param parameters The query parameters to scrub mapped to a lambda that replaces the value.
         */
        @JvmStatic
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

        /**
         * Returns a lambda that scrubs body parameters.
         *
         * Body parameters in the given [parameters] will be replaced with the given [replacement].
         *
         * @param parameters The body parameters to scrub. They are not case-sensitive.
         * @param replacement The replacement value. Defaults to "******". If `null` the parameter will be discarded.
         */
        @JvmStatic
        fun bodyParameter(
            vararg parameters: String,
            replacement: String? = "******"
        ): (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param? =
            bodyParameter(parameters.associateWith {
                { p -> p.copy(value = replacement) }
            })

        /**
         * Returns a lambda that scrubs body parameters.
         *
         * Body parameters in the given [parameters] will be replaced with the paired replacement.
         *
         * @param parameters The body parameters to scrub paired with a lambda that replaces the value.
         */
        @JvmStatic
        fun bodyParameter(
            vararg parameters: Pair<String, (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param?>
        ): (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param? {
            val map = parameters.toMap()
            return scrub@{
                val scrub = map[it.name] ?: return@scrub it
                scrub(it)
            }
        }

        /**
         * Returns a lambda that scrubs body parameters.
         *
         * @param parameters The body parameters to scrub mapped to a lambda that replaces the value.
         */
        @JvmStatic
        fun bodyParameter(
            parameters: Map<String, (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param?>
        ): (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param? {
            return scrub@{
                val scrub = parameters[it.name] ?: return@scrub it
                scrub(it)
            }
        }

        /**
         * Returns a new [BodyScrubber] that discards the body and writes an empty body.
         *
         * The body will be discarded if the [onlyIf] lambda returns `true`.
         *
         * @param onlyIf A lambda that takes a [Harbringer.Request] and returns `true` (default) if the body should be discarded.
         */
        @JvmStatic
        fun discardBody(onlyIf: (Harbringer.Request) -> Boolean = { true }): BodyScrubber =
            BodyScrubber { request, delegate ->
                if (onlyIf(request)) {
                    object : ForwardingSink(delegate) {
                        override fun write(source: Buffer, byteCount: Long) = source.skip(byteCount)
                    }
                } else {
                    delegate
                }
            }

        /**
         * Returns a new [BodyScrubber] that replaces the body with the given [replacement].
         *
         * The `replacement` will be encoded as UTF-8.
         *
         * The body will be replaced if the [onlyIf] lambda returns `true`.
         *
         * @param replacement The replacement value.
         * @param onlyIf A lambda that takes a [Harbringer.Request] and returns `true` (default) if the body should be replaced.
         */
        @JvmStatic
        fun replaceBody(
            replacement: String,
            onlyIf: (Harbringer.Request) -> Boolean = { true },
        ): BodyScrubber = replaceBody(replacement.encodeUtf8(), onlyIf)

        /**
         * Returns a new [BodyScrubber] that replaces the body with the given [replacement].
         *
         * The body will be replaced if the [onlyIf] lambda returns `true`.
         *
         * @param replacement The replacement value.
         * @param onlyIf A lambda that takes a [Harbringer.Request] and returns `true` (default) if the body should be replaced.
         */
        @JvmStatic
        fun replaceBody(
            replacement: ByteString,
            onlyIf: (Harbringer.Request) -> Boolean = { true },
        ): BodyScrubber = BodyScrubber { request, delegate ->
            if (onlyIf(request)) {
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
            } else {
                delegate
            }
        }

        /**
         * Returns a new [BodyScrubber] that scrubs a JSON body of sensitive keys.
         *
         * The [paths] list is a list of JSON paths to scrub.
         *
         * An example of a path looks like this: `$.array[].key`
         *
         * By default, matching objects will be replaced with an empty object, arrays with an empty array, and
         * primitives with "******". Null values will be kept as `null`
         *
         * The body will be replaced if the [onlyIf] lambda returns `true`.
         *
         * @param paths A list of paths to scrub. The keys are case-sensitive.
         * @param json The [Json] instance to use for parsing and encoding the JSON. Defaults to [Json].
         * @param onlyIf A lambda that takes a [Harbringer.Request] and returns `true` (default) if the body should be replaced.
         * @param replace A lambda that takes the path and the element to replace. If the lambda returns `null` then
         *   matches key is removed.
         */
        @ExperimentalSerializationApi
        fun json(
            vararg paths: String,
            json: Json = Json,
            onlyIf: (Harbringer.Request) -> Boolean = { true },
            replace: (path: String, element: JsonElement) -> JsonElement? = defaultJsonReplacement,
        ): BodyScrubber = json(
            paths = paths.toSet(),
            json = json,
            onlyIf = onlyIf,
            replace = replace,
        )

        /**
         * Returns a new [BodyScrubber] that scrubs a JSON body of sensitive keys.
         *
         * The [paths] is a set of JSON paths to scrub.
         *
         * An example of a path looks like this: `$.array[].key`
         *
         * By default, matching objects will be replaced with an empty object, arrays with an empty array, and
         * primitives with "******". Null values will be kept as `null`
         *
         * The body will be replaced if the [onlyIf] lambda returns `true`.
         *
         * @param paths A list of paths to scrub. The keys are case-sensitive.
         * @param json The [Json] instance to use for parsing and encoding the JSON. Defaults to [Json].
         * @param onlyIf A lambda that takes a [Harbringer.Request] and returns `true` (default) if the body should be replaced.
         * @param replace A lambda that takes the path and the element to replace. If the lambda returns `null` then
         *   matches key is removed.
         */
        @ExperimentalSerializationApi
        fun json(
            paths: Set<String>,
            json: Json = Json,
            onlyIf: (Harbringer.Request) -> Boolean = { true },
            replace: (path: String, element: JsonElement) -> JsonElement? = defaultJsonReplacement
        ): BodyScrubber {
            if (paths.isEmpty()) {
                return BodyScrubber { _, sink -> sink }
            }
            return json(
                json = json,
                onlyIf = onlyIf,
            ) { path, element ->
                if (path in paths) {
                    replace(path, element)
                } else {
                    element
                }
            }
        }

        /**
         * Returns a new [BodyScrubber] that scrubs a JSON body of sensitive keys.
         *
         * @param json The [Json] instance to use for parsing and encoding the JSON. Defaults to [Json].
         * @param onlyIf A lambda that takes a [Harbringer.Request] and returns `true` (default) if the body should be replaced.
         * @param replace A lambda that takes the path and the element to replace. If the lambda returns `null` then
         *   matches key is removed.
         */
        @ExperimentalSerializationApi
        fun json(
            json: Json = Json,
            onlyIf: (Harbringer.Request) -> Boolean = { true },
            replace: (path: String, element: JsonElement) -> JsonElement?
        ): BodyScrubber = JsonScrubber(
            json = json,
            replace = replace,
            onlyIf = onlyIf,
        )
    }
}

internal val defaultScrubUrl: (String) -> String? = { it }
internal val defaultScrubQueryParameter: (name: String, value: String?) -> Pair<String, String?>? = { n, v -> n to v }
internal val defaultScrubHeader: (Harbringer.Header) -> Harbringer.Header? = { it }
internal val defaultBodyParameterScrubber: (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param? = { it }
internal val defaultJsonReplacement: (String, JsonElement) -> JsonElement? = { _, element ->
    when (element) {
        is JsonArray -> JsonArray(emptyList())
        is JsonObject -> JsonObject(emptyMap())
        is JsonPrimitive -> JsonPrimitive("******")
        JsonNull -> JsonNull
    }
}