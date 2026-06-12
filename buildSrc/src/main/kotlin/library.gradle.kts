import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.jetbrains.kotlin.gradle.dsl.HasConfigurableKotlinCompilerOptions
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

val libs = the<LibrariesForLibs>()

pluginManager.withPlugin("java") {
    configure<JavaPluginExtension> {
        toolchain {
            languageVersion = libs.versions.java.get().let(JavaLanguageVersion::of)
            implementation = JvmImplementation.VENDOR_SPECIFIC
            vendor = JvmVendorSpec.AZUL
        }
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = sourceCompatibility
    }
}
afterEvaluate {
    configure<KotlinProjectExtension> {
        jvmToolchain(libs.versions.java.get().toInt())
        if (this is HasConfigurableKotlinCompilerOptions<*>)  {
            compilerOptions {
                freeCompilerArgs.addAll(
                    "-opt-in=se.ansman.harbringer.internal.InternalRequestLoggerApi",
                    "-Xjvm-default=all",
                )
            }
        }
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget = if (name == "compileTestKotlin") JvmTarget.JVM_17 else JvmTarget.JVM_11
    }
}

pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
    tasks.named<JavaCompile>("compileTestJava") {
        sourceCompatibility = JavaVersion.VERSION_17.toString()
        targetCompatibility = JavaVersion.VERSION_17.toString()
    }

    configurations.named("testCompileClasspath") {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }
    configurations.named("testRuntimeClasspath") {
        attributes.attribute(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, 17)
    }

    dependencies {
        "testImplementation"(libs.assertk)
        "testImplementation"(platform(libs.junit.bom))
        "testImplementation"(libs.junit.jupiter.api)
        "testRuntimeOnly"(libs.junit.jupiter.engine)
        "testRuntimeOnly"(libs.junit.platform.launcher)
    }
}

pluginManager.withPlugin("org.jetbrains.kotlin.multiplatform") {
    configure<KotlinMultiplatformExtension> {
        compilerOptions {
            freeCompilerArgs.addAll(
                "-Xexpect-actual-classes",
            )
        }
    }
    dependencies {
        "commonTestImplementation"(libs.assertk)
    }
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()

    testLogging {
        events(
            TestLogEvent.FAILED,
            TestLogEvent.PASSED,
            TestLogEvent.SKIPPED
        )
    }
}