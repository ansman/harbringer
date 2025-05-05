val org.gradle.api.Project.gitCommit
    get() = providers
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