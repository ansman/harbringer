# Getting Started

## Installation
[![Maven Central](https://img.shields.io/maven-central/v/se.ansman.harbringer/harbringer.svg)](https://central.sonatype.com/search?smo=true&q=se.ansman.harbringer)


### Gradle
To use Harbringer in your project, you'll need to add a dependency on the library.
```kotlin
dependencies {
    implementation("se.ansman.harbringer:harbringer:{{gradle.version}}")
    // If you're using a supported HTTP client, then depend on the appropriate module
    implementation("se.ansman.harbringer:harbringer-okhttp3:{{gradle.version}}")
}
```

### Snapshots
Snapshots are published on every commit to [Sonatype's snapshot repository](https://central.sonatype.com/service/rest/repository/browse/maven-snapshots/se/ansman/harbringer). 
To use a snapshot, add the snapshot repository:
```kotlin
buildscripts {
    repositories {
        ...
        maven("https://central.sonatype.com/repository/maven-snapshots/")
    }
}

dependencies {
    implementation("se.ansman.harbringer:harbringer:{{gradle.snapshotVersion}}")
}
```