package se.ansman.harbringer.internal

internal expect class Url(url: String) {
    val queryParameters: Sequence<Pair<String, String?>>
    fun replaceQueryParameters(parameters: Iterable<Pair<String, String?>>): Url
}
