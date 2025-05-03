package se.ansman.harbringer.internal

@InternalRequestLoggerApi
object MimeTypes {
    fun isTextMimeType(mimeType: String?): Boolean {
        return when (mimeType?.substringBefore(';')) {
            "application/json",
            "application/javascript",
            "application/html",
            "application/xml",
            "application/css",
            "application/csv",
            "multipart/form-data",
                -> true

            else -> mimeType?.startsWith("text/") == true || mimeType?.contains("charset=") == true
        }
    }

    fun getCharset(mimeType: String?): String? {
        if (mimeType == null) {
            return null
        }
        return PARAMETER.findAll(mimeType)
            .find { it.groups[1]?.value == "charset" }
            ?.run {
                val token = groups[2]?.value
                when {
                    token == null -> {
                        // Value is "double-quoted". That's valid and our regex group already strips the quotes.
                        groups[3]?.value
                    }

                    else -> token.removeSurrounding("'")
                }
            }
    }


    private const val TOKEN = "([a-zA-Z0-9-!#$%&'*+.^_`{|}~]+)"
    private const val QUOTED = "\"([^\"]*)\""
    private val PARAMETER = Regex(";\\s*(?:$TOKEN=(?:$TOKEN|$QUOTED))?")
}