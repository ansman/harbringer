package se.ansman.harbringer.internal

import okio.ByteString
import java.nio.charset.Charset

internal actual fun ByteString.readString(charset: String?): String {
    val cs = Charset.forName(charset ?: "UTF-8", Charsets.UTF_8)
    return if (cs == Charsets.UTF_8) {
        utf8()
    } else {
        string(cs)
    }
}