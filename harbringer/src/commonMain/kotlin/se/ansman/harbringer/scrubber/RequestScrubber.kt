package se.ansman.harbringer.scrubber

import se.ansman.harbringer.Harbringer

/**
 * A [RequestScrubber] is used to modify the request before it is persisted.
 */
fun interface RequestScrubber {
    /**
     * Scrubs the given [request], and returns a scrubbed version, or `null` if the request should be discarded.
     */
    fun scrub(request: Harbringer.Request): Harbringer.Request?
}