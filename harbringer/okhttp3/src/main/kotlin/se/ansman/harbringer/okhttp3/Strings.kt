package se.ansman.harbringer.okhttp3

import java.net.URLDecoder

internal fun String.decodeContentTypeParameter(): String = URLDecoder.decode(removeSurrounding("\""), Charsets.UTF_8)