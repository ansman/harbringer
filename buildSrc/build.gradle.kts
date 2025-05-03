plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
}

dependencies {
    implementation(libs.kotlinGradlePlugin)
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}
