# Harbringer
Harbringer is an HTTP request logger. It can store your network requests and let you inspect or export them.

It supports OkHttp out of the box, but you can use it with any HTTP client.

To read more, please refer to the [documentation](https://harbringer.ansman.se/).

For the changelog, see the [releases page](https://github.com/ansman/harbringer/releases).

## Basic usage
```kotlin
val harbringer = Harbringer(
    storage = FileSystemHarbringerStorage(storageDirectory.toPath()),
    maxRequests = 1000, // 1000 requests
    maxDiskSize = 100 * 1024 * 1024, // 100MB
    maxAge = 2.days,
)

val okHttpClient = OkHttpClient.Builder()
    .addHarbringer(harbringer)
    .build()

outputFile.sink().use { sink ->
    harbringer.exportTo(sink)
}
```

For the full documentation see https://harbringer.ansman.se/

Setup
---
For detailed instructions, see the [getting-started](https://harbringer.ansman.se/latest/getting-started/) page.
```kotlin
dependencies {
    implementation("se.ansman.harbringer:harbringer:0.1.0")
    // If using okhttp
    implementation("se.ansman.harbringer:harbringer-okhttp3:0.1.0")
}
```

## License

This project is licensed under the Apache-2.0 license. See [LICENSE](LICENSE) for the full license.

```
Copyright 2025 Nicklas Ansman

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```