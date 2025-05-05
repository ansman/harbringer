# Logging Requests
If you want to log requests, but your HTTP client isn't supported out of the box then you can implement
the logging yourself.

To log a request, you need to call `Harbringer.record`:
```kotlin
// Start the request
val pendingRequest = harbringer.record(
    request = Harbringer.Request(
        method = "POST",
        url = "https://example.com",
        protocol = "HTTP/1.1",
        headers = Harbringer.Headers("Content-Type" to "application/json"),
    )
)
// Write the request body
pendingRequest.requestBody.buffer().use { it.writeUtf8("""{"example":"request"}""") }
// Write the response body
pendingRequest.responseBody.buffer().use { it.writeUtf8("""{"example":"response"}""") }
// Log the response
pendingRequest.onComplete(
    request = Harbringer.Request(
        code = 200,
        message = "OK",
        protocol = "HTTP/1.1",
        headers = Harbringer.Headers("Content-Type" to "application/json"),
        body = Harbringer.Body(
            contentType = "application/json",
            byteCount = 22,
        )
    ),
    // If known, you can pass the timings here too
    timings = Harbringer.Timings(
        total   = 123.milliseconds,
        blocked =  10.milliseconds,
        dns     =   8.milliseconds,
        connect =  10.milliseconds,
        send    =  40.milliseconds,
        wait    =   4.milliseconds,
        receive =  48.milliseconds, 
        ssl     =   3.milliseconds,
    )
)
// Or log the failure
pendingRequest.onFailure(IOException("Failed to connect"))
```