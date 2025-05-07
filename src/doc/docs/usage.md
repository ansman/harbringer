# Usage
To use Harbringer, you first need to create a `Harbringer` instance:
```kotlin
val harbringer = Harbringer(
    // Store requests on disk
    storage = FileSystemHarbringerStorage(storageDirectory.toPath()),
    // Store the last 1000 requests
    maxRequests = 1000,
    // Store up to 100MB of requests
    maxDiskSize = 100 * 1024 * 1024,
    // Store up to 2 days worth of logs
    maxAge = 2.days,
)
```

## Storage
Harbringer ships with two storage implementations; `FileSystemHarbringerStorage` and `InMemoryHarbringerStorage`.
The former stores requests on disk, while the latter stores them in memory. You can also implement your own storage by 
implementing the `HarbringerStorage` interface.

Please bear in mind that the storage my be thread safe so implementing it can be challenging.

## Exporting
You can export the requests to a file using the `exportTo` method:
```kotlin
FileSystem.SYSTEM.sink("/path/to/requests.har".toPath()).use { sink ->
    harbringer.exportTo(sink)
}
```

This will write the requests to the file in the HAR format. You can then open the file in a HAR viewer, such as
[Google Chrome's HAR viewer](https://toolbox.googleapps.com/apps/har_analyzer/), or import it into a tool like
[Postman](https://www.postman.com/), [Charles Proxy](https://www.charlesproxy.com) or [Proxyman](https://proxyman.io).

You can also implement your own exporter by reading the entries from the `Harbringer` instance.

## Scrubbing
You often want to ensure that sensitive data is not included in the logs. Harbringer supports scrubbing of sensitive.
For more information on scrubbing, see the [scrubbing guide](scrubbing.md).
