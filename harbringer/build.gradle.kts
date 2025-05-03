plugins {
    kotlin("multiplatform")
    alias(libs.plugins.kotlin.plugin.serialization)
}

group = "se.ansman.harexporter"
version = "0.1.0"

val generateRequestLoggerMetadata by tasks.registering {
    val metadataOutput = layout.buildDirectory.dir("generated/$name/kotlin")
    val version = version
    inputs.property("version", version)
    outputs.dir(metadataOutput)
    doFirst {
        metadataOutput.get().file("RequestLoggerMetadata.kt").asFile.writeText(
            """
            package se.ansman.requestlogger.internal

            internal const val VERSION = "$version"
            """.trimIndent()
        )
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-Xopt-in=se.ansman.harbringer.internal.InternalRequestLoggerApi",
        )
    }

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
    commonTestImplementation(libs.assertk)
    commonTestImplementation(libs.okio.fakeFileSystem)
}