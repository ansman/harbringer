package se.ansman.harbringer.internal.json

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import se.ansman.harbringer.Harbringer
import se.ansman.harbringer.internal.json.FileSystemDatabase.Entry
import se.ansman.harbringer.internal.json.FileSystemDatabase.Entry.Device
import se.ansman.harbringer.internal.json.FileSystemDatabase.Header
import se.ansman.harbringer.internal.json.FileSystemDatabase.Request
import se.ansman.harbringer.internal.json.FileSystemDatabase.Response
import se.ansman.harbringer.internal.json.FileSystemDatabase.Timings
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

internal object FileSystemDatabase {
    @Serializable
    data class SimpleEntry(
        @SerialName("id")
        val id: String,
        @SerialName("sa")
        val startedAt: Long,
    )

    @Serializable
    data class Entry(
        @SerialName("id")
        val id: String,
        @SerialName("tx")
        val request: Request,
        @SerialName("rx")
        val response: Response,
        @SerialName("ts")
        val timings: Timings,
        @SerialName("s")
        val server: Device?,
        @SerialName("c")
        val client: Device?,
        @SerialName("sa")
        val startedAt: Long,
    ) {
        @Serializable
        data class Device(
            @SerialName("i")
            val ip: String,
            @SerialName("p")
            val port: Int? = null
        )
    }

    @Serializable
    data class Request(
        @SerialName("m")
        val method: String,
        @SerialName("u")
        val url: String,
        @SerialName("hv")
        val httpVersion: String,
        @SerialName("hs")
        val headers: List<Header>,
        @SerialName("bs")
        val body: Body? = null,
    ) {
        @Serializable
        data class Body(
            @SerialName("s")
            val size: Long,
            @SerialName("ct")
            val contentType: String?,
            @SerialName("ps")
            val params: List<Parameter> = emptyList()
        ) {
            @Serializable
            data class Parameter(
                @SerialName("n")
                val name: String,
                @SerialName("v")
                val value: String?,
                @SerialName("f")
                val fileName: String? = null,
                @SerialName("ct")
                val contentType: String? = null,
            ) {
                constructor(parameter: Harbringer.Request.Body.Param) : this(
                    name = parameter.name,
                    value = parameter.value,
                    fileName = parameter.fileName,
                    contentType = parameter.contentType,
                )
            }
        }
    }

    @Serializable
    data class Response(
        @SerialName("s")
        val status: Int,
        @SerialName("st")
        val statusText: String,
        @SerialName("hv")
        val httpVersion: String,
        @SerialName("hs")
        val headers: List<Header>,
        @SerialName("b")
        val body: Body? = null,
        @SerialName("e")
        val error: String? = null,
    ) {
        @Serializable
        data class Body(
            @SerialName("s")
            val size: Long,
            @SerialName("ct")
            val contentType: String?,
            @SerialName("c")
            val compression: Long? = null,
        )
    }

    @Serializable
    data class Timings(
        @SerialName("t") val total: Long,
        @SerialName("b") val blocked: Long = -1,
        @SerialName("d") val dns: Long = -1,
        @SerialName("c") val connect: Long = -1,
        @SerialName("tx") val send: Long = -1,
        @SerialName("w") val wait: Long = -1,
        @SerialName("rx") val receive: Long = -1,
        @SerialName("s") val ssl: Long = -1,
    )

    @Serializable
    data class Header(
        @SerialName("n")
        val name: String,
        @SerialName("v")
        val value: String,
    ) {
        constructor(header: Harbringer.Header) : this(
            name = header.name,
            value = header.value,
        )
    }
}

internal fun Entry.toRequestLoggerEntry(): Harbringer.Entry =
    Harbringer.Entry(
        id = id,
        request = request.toRequestLoggerRequest(),
        response = response.toRequestLoggerResponse(),
        timings = timings.toRequestLoggerTimings(),
        startedAt = startedAt,
        server = server?.toRequestLoggerDevice(),
        client = client?.toRequestLoggerDevice(),
    )

internal fun Request.toRequestLoggerRequest(): Harbringer.Request =
    Harbringer.Request(
        method = method,
        url = url,
        protocol = httpVersion,
        headers = headers.toRequestLoggerHeaders(),
        body = body?.run {
            Harbringer.Request.Body(
                byteCount = size,
                contentType = contentType,
                params = params.map {
                    Harbringer.Request.Body.Param(
                        name = it.name,
                        value = it.value,
                        fileName = it.fileName,
                        contentType = it.contentType
                    )
                },
            )
        },
    )

internal fun Response.toRequestLoggerResponse(): Harbringer.Response =
    Harbringer.Response(
        code = status,
        message = statusText,
        protocol = httpVersion,
        headers = headers.toRequestLoggerHeaders(),
        body = body?.run {
            Harbringer.Response.Body(
                byteCount = size,
                contentType = contentType,
            )
        },
        error = error,
    )

internal fun Timings.toRequestLoggerTimings(): Harbringer.Timings =
    Harbringer.Timings(
        total = total.milliseconds,
        blocked = if (blocked == -1L) null else blocked.milliseconds,
        dns = if (dns == -1L) null else dns.milliseconds,
        connect = if (connect == -1L) null else connect.milliseconds,
        send = if (send == -1L) null else send.milliseconds,
        wait = if (wait == -1L) null else wait.milliseconds,
        receive = if (receive == -1L) null else receive.milliseconds,
        ssl = if (ssl == -1L) null else ssl.milliseconds,
    )

internal fun List<Header>.toRequestLoggerHeaders(): Harbringer.Headers =
    Harbringer.Headers(map {
        Harbringer.Header(
            name = it.name,
            value = it.value
        )
    })

internal fun Device.toRequestLoggerDevice(): Harbringer.Device =
    Harbringer.Device(
        ip = ip,
        port = port
    )

internal fun Harbringer.Entry.toDatabaseEntry(): Entry =
    Entry(
        id = id,
        request = request.toDatabaseRequest(),
        response = response.toDatabaseResponse(),
        timings = timings.toDatabaseTimings(),
        server = server?.toDatabaseDevice(),
        client = client?.toDatabaseDevice(),
        startedAt = startedAt,
    )

internal fun Harbringer.Request.toDatabaseRequest(): Request =
    Request(
        method = method,
        url = url,
        httpVersion = protocol,
        headers = headers.values.map { Header(it) },
        body = body?.toDatabaseBody(),
    )

internal fun Harbringer.Response.toDatabaseResponse(): Response =
    Response(
        status = code,
        statusText = message,
        httpVersion = protocol,
        headers = headers.values.map { Header(it) },
        body = body?.toDatabaseBody(),
        error = error,
    )

internal fun Harbringer.Device.toDatabaseDevice(): Device =
    Device(
        ip = ip,
        port = port
    )

internal fun Harbringer.Request.Body.toDatabaseBody(): Request.Body =
    Request.Body(
        size = byteCount,
        contentType = contentType,
        params = params.map { Request.Body.Parameter(it) }
    )

internal fun Harbringer.Response.Body.toDatabaseBody(): Response.Body =
    Response.Body(
        size = byteCount,
        contentType = contentType,
    )

internal fun Harbringer.Timings.toDatabaseTimings(): Timings =
    Timings(
        total = total.toDouble(DurationUnit.MILLISECONDS).roundToLong(),
        blocked = blocked?.toDouble(DurationUnit.MILLISECONDS)?.roundToLong() ?: -1,
        dns = dns?.toDouble(DurationUnit.MILLISECONDS)?.roundToLong() ?: -1,
        connect = connect?.toDouble(DurationUnit.MILLISECONDS)?.roundToLong() ?: -1,
        send = send?.toDouble(DurationUnit.MILLISECONDS)?.roundToLong() ?: -1,
        wait = wait?.toDouble(DurationUnit.MILLISECONDS)?.roundToLong() ?: -1,
        receive = receive?.toDouble(DurationUnit.MILLISECONDS)?.roundToLong() ?: -1,
        ssl = ssl?.toDouble(DurationUnit.MILLISECONDS)?.roundToLong() ?: -1,
    )