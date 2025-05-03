package se.ansman.harbringer.internal

import kotlinx.datetime.Instant
import kotlin.time.Duration

class TestClock(
    var now: Long = 1745787552123L
) : Clock {
    val dateTimeClock = object : kotlinx.datetime.Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(now)
    }

    override fun currentTime(): Long = now

    operator fun plusAssign(delta: Duration) {
        now += delta.inWholeMilliseconds
    }
}