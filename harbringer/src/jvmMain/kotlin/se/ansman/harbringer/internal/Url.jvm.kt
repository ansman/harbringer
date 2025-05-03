package se.ansman.harbringer.internal

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

internal actual class Url(private val uri: URI) {
    actual val queryParameters: Sequence<Pair<String, String?>> = uri.query
        ?.splitToSequence('&')
        ?.map { param ->
            val sep = param.indexOf('=')
            val name: String
            val value: String?
            if (sep < 0) {
                name = param
                value = null
            } else {
                name = param.substring(0, sep)
                value = param.substring(sep + 1, param.length)
            }
            URLDecoder.decode(name, Charsets.UTF_8) to value?.let { URLDecoder.decode(it, Charsets.UTF_8) }
        }
        ?: emptySequence()

    actual constructor(url: String) : this(URI(url))

    actual fun replaceQueryParameters(parameters: Iterable<Pair<String, String?>>): Url =
        Url(
            URI(
                uri.scheme,
                uri.userInfo,
                uri.host,
                uri.port,
                uri.path,
                parameters.joinToString("&") { (name, value) ->
                    if (value == null) {
                        URLEncoder.encode(name, Charsets.UTF_8)
                    } else {
                        "${URLEncoder.encode(name, Charsets.UTF_8)}=${URLEncoder.encode(value, Charsets.UTF_8)}"
                    }
                },
                uri.fragment
            )
        )

    override fun toString(): String = uri.toString()
    override fun hashCode(): Int = uri.hashCode()
    override fun equals(other: Any?): Boolean = other === this || other is Url && uri == other.uri
}
