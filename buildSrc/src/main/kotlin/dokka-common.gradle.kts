plugins {
    id("org.jetbrains.dokka")
    id("org.jetbrains.dokka-javadoc")
}


fun repo(path: String = "") = "https://github.com/ansman/harbringer$path"

val gitCommit = providers
    .exec {
        commandLine("git", "rev-parse", "HEAD")
        workingDir = project.rootDir
    }
    .run {
        result.flatMap {
            it.assertNormalExitValue()
            it.rethrowFailure()
            standardOutput.asText
        }
    }
    .map { it.trim() }

val remoteSource: Provider<String> = providers.gradleProperty("version")
    .filter { !it.endsWith("-SNAPSHOT") }
    .orElse(gitCommit)
    .map { repo("/blob/$it") }

dokka {
    val projectPath = project.path.removePrefix(":").replace(':', '/')
    dokkaSourceSets.configureEach {
        suppressGeneratedFiles = true
        reportUndocumented = false
        sourceLink {
            localDirectory.set(project.file("src/main/kotlin"))
            remoteUrl.set(remoteSource.map { remoteSource ->
                uri("$remoteSource/$projectPath/src/main/kotlin")
            })
            remoteLineSuffix.set("#L")
        }
    }
}