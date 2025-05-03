package se.ansman.harbringer.internal

import okio.ByteString

internal expect fun ByteString.readString(charset: String?): String