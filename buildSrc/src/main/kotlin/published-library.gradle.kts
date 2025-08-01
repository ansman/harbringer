import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.gradle.accessors.dm.LibrariesForLibs

plugins {
    id("library")
    id("com.vanniktech.maven.publish")
    id("signing")
    id("dokka-common")
}


fun repo(path: String = "") = "https://github.com/ansman/harbringer$path"

group = "se.ansman.harbringer"

tasks.withType<AbstractPublishToMaven>().configureEach {
    doLast {
        with(publication) {
            println("Published artifact $groupId:$artifactId:$version")
        }
    }
}

val signArtifacts = providers.gradleProperty("signArtifacts").orNull?.toBooleanStrict() ?: false
mavenPublishing {
    val version = providers.gradleProperty("version").get()
    publishToMavenCentral()

    if (signArtifacts) {
        signAllPublications()
    }

    coordinates(
        groupId = project.group as String,
        artifactId = project.path
            .removePrefix(":")
            .replace(':', '-'),
        version = version,
    )
    pom {
        val moduleName = project.path
            .removePrefix(":")
            .splitToSequence(":")
            .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }

        name.set("$moduleName")
        description.set("HTTP Request logger and exporter")
        url.set(repo())
        licenses {
            license {
                name.set("The Apache Software License, Version 2.0")
                url.set("https://raw.githubusercontent.com/ansman/harbringer/refs/heads/main/LICENSE")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("ansman")
                name.set("Nicklas Ansman")
                email.set("nicklas@ansman.se")
            }
            scm {
                connection.set("scm:git:git://github.com/ansman/harbringer.git")
                developerConnection.set("scm:git:ssh://git@github.com/ansman/harbringer.git")
                url.set(repo())
            }
        }
    }
}

if (signArtifacts) {
    signing {
        useGpgCmd()
    }
}

tasks.register("publishSnapshot") {
    if (providers.gradleProperty("version").get().endsWith("-SNAPSHOT")) {
        dependsOn("publishAllPublicationsToMavenCentralRepository")
    }
}

pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    mavenPublishing {
        configure(
            KotlinJvm(
                javadocJar = JavadocJar.Dokka(tasks.dokkaGeneratePublicationJavadoc.name),
                sourcesJar = true
            )
        )
    }
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    mavenPublishing {
        configure(
            KotlinMultiplatform(
                javadocJar = JavadocJar.Dokka(tasks.dokkaGeneratePublicationJavadoc.name),
                sourcesJar = true
            )
        )
    }
}