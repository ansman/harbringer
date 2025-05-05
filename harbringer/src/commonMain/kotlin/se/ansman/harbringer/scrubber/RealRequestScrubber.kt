package se.ansman.harbringer.scrubber

import se.ansman.harbringer.Harbringer
import se.ansman.harbringer.internal.Url

internal class RealRequestScrubber(
    private val scrubUrl: (String) -> String?,
    private val scrubQueryParameter: (name: String, value: String?) -> Pair<String, String?>?,
    private val scrubHeader: (Harbringer.Header) -> Harbringer.Header?,
    private val scrubBodyParameter: (Harbringer.Request.Body.Param) -> Harbringer.Request.Body.Param?,
) : RequestScrubber {

    override fun scrub(request: Harbringer.Request): Harbringer.Request? {
        var url = scrubUrl(request.url)
            ?: return null

        if (scrubQueryParameter != defaultScrubQueryParameter) {
            url = scrubQueryParameters(Url(url))
        }
        return request.copy(
            url = url,
            headers = if (scrubHeader == defaultScrubHeader) {
                request.headers
            } else {
                request.headers.values
                    .mapNotNull(scrubHeader)
                    .let(Harbringer::Headers)
            },
            body = request.body?.run {
                if (scrubBodyParameter == defaultBodyParameterScrubber) {
                    this
                } else {
                    copy(params = params.mapNotNull(scrubBodyParameter))
                }
            }
        )
    }

    private fun scrubQueryParameters(url: Url): String =
        url.replaceQueryParameters(
            url.queryParameters
                .mapNotNull { scrubQueryParameter(it.first, it.second) }
                .asIterable())
            .toString()
}