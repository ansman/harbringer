# Scrubbing
You often want to ensure that sensitive data is not included in the logs. Harbringer supports scrubbing of sensitive
data from requests and responses. You can pass a `Scrubber` to the `Harbringer` constructor:

```kotlin
val harbringer = Harbringer(
    scrubber = Scrubber(
        request = Scrubber.request(
            // Replaces the value of the "apiKey" query parameter with "******"
            queryParameter = Scrubber.queryParameter("apiKey"),
            // Replaces the value of the "Authorization" query parameter with "******"
            header = Scrubber.header("Authorization"),
        ),
        // Removes the "password" and "username" fields from the request body, and if the request is against the login endpoint.
        requestBody = Scrubber.json("$.username", "$.password", onlyIf = { it.url.endsWith("/login") }),
        response = Scrubber.request(
            header = Scrubber.header("Sensitive-Header"),
        ),
        // Removes the "token" field from the response body, and if the request is against the login endpoint.
        responseBody = Scrubber.json("$.token", onlyIf = { it.url.endsWith("/login") }),
    )
)
```