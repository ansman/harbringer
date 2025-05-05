package se.ansman.harbringer.scrubber

import okio.Sink
import se.ansman.harbringer.Harbringer

/**
 * A [BodyScrubber] is used to modify the request body before it is persisted.
 */
fun interface BodyScrubber {
    /**
     * Returns a new [Sink], that will receive the unscrubbed body. The scrubbed data should be written to the given
     * [sink].
     *
     * The given [sink] must be closed when the returned sink is closed.
     *
     * @param request The request the body belongs to.
     * @param sink The sink to write the scrubbed data to.
     * @return A new [Sink] that will receive the unscrubbed body.
     */
    fun scrub(request: Harbringer.Request, sink: Sink): Sink
}