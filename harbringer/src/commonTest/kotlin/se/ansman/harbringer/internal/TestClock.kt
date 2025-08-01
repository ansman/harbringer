package se.ansman.harbringer.internal

import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class TestClock(
    var now: Long = 1745787552123L
) : Clock {
    val kotlinClock = object : kotlin.time.Clock {
        override fun now(): Instant = Instant.fromEpochMilliseconds(now)
    }

    override fun currentTime(): Long = now

    operator fun plusAssign(delta: Duration) {
        now += delta.inWholeMilliseconds
    }
}