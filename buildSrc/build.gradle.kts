plugins {
    kotlin("jvm") version embeddedKotlinVersion
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    compilerOptions {
        allWarningsAsErrors = true
    }
}

dependencies {
    api(libs.kotlinGradlePlugin)
    api(libs.gradleMavenPublish)
    api(libs.dokka.gradlePlugin)
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
