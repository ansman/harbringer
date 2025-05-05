package se.ansman.harbringer.scrubber

import se.ansman.harbringer.Harbringer

internal class RealResponseScrubber(
    private val scrubHeader: (Harbringer.Header) -> Harbringer.Header?,
    private val onlyIf: (Harbringer.Request) -> Boolean,
) : ResponseScrubber {

    override fun scrub(request: Harbringer.Request, response: Harbringer.Response): Harbringer.Response? {
        if (!onlyIf(request) || scrubHeader == defaultScrubHeader) {
            return response
        }
        return response.copy(
            headers = response.headers.values
                .mapNotNull(scrubHeader)
                .let(Harbringer::Headers)
        )
    }
}