package se.ansman.harbringer.internal

import okio.ByteString
import java.nio.charset.Charset
import java.nio.charset.UnsupportedCharsetException

internal actual fun ByteString.readString(charset: String?): String {
    Charsets.UTF_8
    val cs = try {
        Charset.forName(charset ?: "UTF-8")
    } catch (_: UnsupportedCharsetException) {
        Charsets.UTF_8
    }
    return if (cs == Charsets.UTF_8) {
        utf8()
    } else {
        string(cs)
    }
}