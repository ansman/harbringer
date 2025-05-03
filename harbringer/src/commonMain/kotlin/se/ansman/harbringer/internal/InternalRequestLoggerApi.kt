package se.ansman.harbringer.internal

@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This API is internal to the RequestLogger library and should not be used directly.")
annotation class InternalRequestLoggerApi()
