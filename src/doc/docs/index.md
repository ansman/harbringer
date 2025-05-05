Harbringer
===
Harbringer tracks and logs networks requests in your application, letting you inspect and export them.

This is particularly useful for mobile application, where you often want to inspect network requests and responses, 
but don't want to set up a proxy to capture the requests.

This library can help you export the requests or display them in the app itself.

It can support any HTTP client, but for now it supports:

- [OkHttp](okhttp.md)


## Getting started
You can add Harbringer to your project using Gradle:
```kotlin
dependencies {
    implementation("se.ansman.harbringer:harbringer:{{gradle.version}}")
    implementation("se.ansman.harbringer:harbringer-okhttp3:{{gradle.version}}")
}
```

See the [installation guide](getting-started.md) for more details.

## Example
```kotlin
val harbringer = Harbringer(
    storage = FileSystemHarbringerStorage(storageDirectory.toPath()),
    maxRequests = 1000, // 1000 requests
    maxDiskSize = 100 * 1024 * 1024, // 100MB
)

outputFile.sink().use { sink ->
    harbringer.exportTo(sink)
}
```

For more details on how to use Harbringer, see the [usage guide](usage.md).

## Scrubbing
You often want to ensure that sensitive data is not included in the logs. Harbringer supports scrubbing of sensitive 
data from requests and responses. You can pass a `Scrubber` to the `Harbringer` constructor.

See the [scrubbing guide](scrubbing.md) for more details.