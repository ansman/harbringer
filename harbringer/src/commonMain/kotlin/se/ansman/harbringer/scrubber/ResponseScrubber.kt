package se.ansman.harbringer.scrubber

import se.ansman.harbringer.Harbringer

/**
 * A [ResponseScrubber] is used to modify a response before it is persisted.
 */
fun interface ResponseScrubber {
    /**
     * Scrubs the given [response], and returns a scrubbed version, or `null` if the request should be
     * discarded.
     *
     * @param request The request the response belongs to.
     * @param response The response to scrub.
     * @return A scrubbed version of the response, or `null` if the request should be discarded.
     */
    fun scrub(request: Harbringer.Request, response: Harbringer.Response): Harbringer.Response?
}