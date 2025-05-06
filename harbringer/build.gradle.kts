plugins {
    kotlin("multiplatform")
    id("published-library")
    alias(libs.plugins.kotlin.plugin.serialization)
}

val generateRequestLoggerMetadata by tasks.registering {
    val metadataOutput = layout.buildDirectory.dir("generated/$name/kotlin")
    val version = version
    inputs.property("version", version)
    outputs.dir(metadataOutput)
    doFirst {
        with(metadataOutput.get()) {
            asFile.deleteRecursively()
            asFile.mkdirs()
            file("HarbringerVersion.kt").asFile.writeText(
                """
                package se.ansman.harbringer.internal
    
                internal const val HARBRINGER_VERSION = "$version"
                """.trimIndent()
            )
        }
    }
}

kotlin {
    sourceSets {
        commonMain {
            kotlin.srcDir(generateRequestLoggerMetadata)
        }
    }

    jvm()
}

dependencies {
    commonMainApi(libs.okio)
    commonMainImplementation(libs.kotlinx.serialization.json)
    commonMainImplementation(libs.kotlinx.serialization.json.okio)

    commonTestImplementation(kotlin("test"))
    commonTestImplementation(libs.okio.fakeFileSystem)
}