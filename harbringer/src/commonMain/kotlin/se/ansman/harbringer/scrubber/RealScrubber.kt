package se.ansman.harbringer.scrubber

import okio.Sink
import se.ansman.harbringer.Harbringer

internal class RealScrubber(
    private val requestScrubber: RequestScrubber,
    private val requestBodyScrubber: BodyScrubber,
    private val responseScrubber: ResponseScrubber,
    private val responseBodyScrubber: BodyScrubber,
) : Scrubber {
    override fun scrubRequest(request: Harbringer.Request): Harbringer.Request? = requestScrubber.scrub(request)

    override fun scrubRequestBody(request: Harbringer.Request, sink: Sink): Sink =
        requestBodyScrubber.scrub(request, sink)

    override fun scrubResponse(request: Harbringer.Request, response: Harbringer.Response): Harbringer.Response? =
        responseScrubber.scrub(request, response)

    override fun scrubResponseBody(request: Harbringer.Request, sink: Sink): Sink =
        responseBodyScrubber.scrub(request, sink)
}