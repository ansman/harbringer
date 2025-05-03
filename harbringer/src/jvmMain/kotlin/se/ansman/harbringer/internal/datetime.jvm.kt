package se.ansman.harbringer.internal

import java.time.Instant

actual fun currentTime(): Long = System.currentTimeMillis()
actual fun formatIso8601(time: Long): String = Instant.ofEpochMilli(time).toString()