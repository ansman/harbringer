package se.ansman.harbringer.internal

internal interface Clock {
    fun currentTime(): Long

    object System : Clock {
        override fun currentTime(): Long = se.ansman.harbringer.internal.currentTime()
    }
}

internal expect fun currentTime(): Long
internal expect fun formatIso8601(time: Long): String
