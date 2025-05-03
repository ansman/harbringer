package se.ansman.harbringer.internal

import java.time.Instant

internal actual fun currentTime(): Long = System.currentTimeMillis()
internal actual fun formatIso8601(time: Long): String = Instant.ofEpochMilli(time).toString()