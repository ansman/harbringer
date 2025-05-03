plugins {
    id("library-jvm")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xopt-in=se.ansman.harbringer.internal.InternalRequestLoggerApi",
        )
    }
}

dependencies {
    api(project(":harbringer"))
    implementation(platform(libs.okhttp3.bom))
    implementation(libs.okhttp3)
    testImplementation(libs.okhttp3.mockwebserver)
    testImplementation(libs.okio.fakeFileSystem)
}