import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("io.gitlab.arturbosch.detekt")
    id("org.jlleitschuh.gradle.ktlint")
}

val releaseKeystoreProperties = Properties()
val releaseKeystorePropertiesFile = rootProject.file("release-keystore.properties")
if (releaseKeystorePropertiesFile.isFile) {
    releaseKeystorePropertiesFile.inputStream().use(releaseKeystoreProperties::load)
}

fun releaseSigningProperty(
    propertyName: String,
    envName: String,
): String? =
    (
        releaseKeystoreProperties.getProperty(propertyName)
            ?: providers.gradleProperty(propertyName).orNull
            ?: providers.environmentVariable(envName).orNull
    )?.takeIf { it.isNotBlank() }

val releaseStoreFile = releaseSigningProperty("storeFile", "OPENCAMERA_RELEASE_STORE_FILE")
val releaseStorePassword = releaseSigningProperty("storePassword", "OPENCAMERA_RELEASE_STORE_PASSWORD")
val releaseKeyAlias = releaseSigningProperty("keyAlias", "OPENCAMERA_RELEASE_KEY_ALIAS")
val releaseKeyPassword = releaseSigningProperty("keyPassword", "OPENCAMERA_RELEASE_KEY_PASSWORD")
val hasReleaseSigning =
    listOf(
        releaseStoreFile,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    ).all { !it.isNullOrBlank() }

// Version management
val versionPropertiesFile = rootProject.file("version.properties")
val versionProperties = Properties()
if (versionPropertiesFile.isFile) {
    versionPropertiesFile.inputStream().use(versionProperties::load)
}
val versionMajor = versionProperties.getProperty("VERSION_MAJOR", "2").toInt()
val versionMinor = versionProperties.getProperty("VERSION_MINOR", "0").toInt()
val versionPatch = versionProperties.getProperty("VERSION_PATCH", "0").toInt()

fun nextArtifactVersion(): Triple<Int, Int, Int> {
    var nextMinor = versionMinor
    var nextPatch = versionPatch + 1
    if (nextPatch > 19) {
        nextPatch = 0
        nextMinor += 1
        check(nextMinor <= 19) { "Minor version overflow: $nextMinor > 19" }
    }
    return Triple(versionMajor, nextMinor, nextPatch)
}

val artifactGenerationRequested =
    providers.environmentVariable("OPENCAMERA_HANDOFF_GENERATION_AUTHORIZED").orNull == "1"
if (artifactGenerationRequested) {
    val lockPath =
        providers.environmentVariable("OPENCAMERA_HANDOFF_LOCK_FILE").orNull?.let(::file)?.toPath()
            ?: file("${System.getProperty("java.io.tmpdir")}/opencamera-handoff-artifact.lock").toPath()
    Files.createDirectories(lockPath.parent)
    val lockChannel =
        FileChannel.open(
            lockPath,
            StandardOpenOption.CREATE,
            StandardOpenOption.WRITE,
        )
    val artifactLock = lockChannel.tryLock()
    check(artifactLock != null) {
        lockChannel.close()
        "another handoff artifact generation is already running: $lockPath"
    }
    gradle.buildFinished {
        artifactLock.release()
        lockChannel.close()
    }
}
val configuredVersion =
    if (artifactGenerationRequested) nextArtifactVersion() else Triple(versionMajor, versionMinor, versionPatch)
val (configuredVersionMajor, configuredVersionMinor, configuredVersionPatch) = configuredVersion
val computedVersionName = "$configuredVersionMajor.$configuredVersionMinor.$configuredVersionPatch"
val computedVersionCode = configuredVersionMajor * 400 + configuredVersionMinor * 20 + configuredVersionPatch + 1

android {
    namespace = "com.opencamera.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.opencamera.app"
        minSdk = 26
        targetSdk = 35
        versionCode = computedVersionCode
        versionName = computedVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = rootProject.file(releaseStoreFile!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Forward capture-reliability knobs (-Dcapture.reliability.*) to the
            // test JVM so stress seed counts and single-seed replay work from the CLI.
            it.systemProperties(
                System.getProperties().entries
                    .filter { entry -> entry.key.toString().startsWith("capture.reliability.") }
                    .associate { entry -> entry.key.toString() to entry.value.toString() }
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Test>().configureEach {
    javaLauncher.set(
        javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(17))
        },
    )
}

dependencies {
    implementation(project(":core:device"))
    implementation(project(":core:capability"))
    implementation(project(":core:effect"))
    implementation(project(":core:media"))
    implementation(project(":core:mode"))
    implementation(project(":core:settings"))
    implementation(project(":core:session"))
    implementation(project(":feature:mode-document"))
    implementation(project(":feature:mode-humanistic"))
    implementation(project(":feature:mode-photo"))
    implementation(project(":feature:mode-checkin"))
    implementation(project(":feature:mode-video"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.camera:camera-camera2:1.4.2")
    implementation("androidx.camera:camera-core:1.4.2")
    implementation("androidx.camera:camera-extensions:1.4.2")
    implementation("androidx.camera:camera-lifecycle:1.4.2")
    implementation("androidx.camera:camera-video:1.4.2")
    implementation("androidx.camera:camera-view:1.4.2")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")
    implementation("com.google.mlkit:image-labeling:17.0.9")
    implementation("com.google.mlkit:object-detection:17.0.2")
    implementation("com.google.mlkit:face-detection:16.1.7")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
}

val generateHandoffDebugApk = tasks.register("generateHandoffDebugApk") {
    group = "build"
    description = "Internal task used only by tool/generate_handoff_debug_apk.sh."
    dependsOn("assembleDebug")

    doLast {
        val sourceApk = layout.buildDirectory.file("outputs/apk/debug/app-debug.apk").get().asFile
        check(sourceApk.isFile) {
            "Debug APK was not found at ${sourceApk.absolutePath}"
        }

        val archiveDir = rootProject.layout.projectDirectory.dir("work/outputs/apks/debug").asFile
        archiveDir.mkdirs()

        val versionPart = computedVersionName.replace(Regex("[^A-Za-z0-9._-]"), "-")
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
        val archiveApk = archiveDir.resolve("OpenCamera-$versionPart-$timestamp-debug.apk")
        val latestArchiveApk = archiveDir.resolve("OpenCamera-latest-debug.apk")
        val latestRootApk = rootProject.layout.projectDirectory.file("OpenCamera-latest-debug.apk").asFile

        sourceApk.copyTo(archiveApk, overwrite = false)
        sourceApk.copyTo(latestArchiveApk, overwrite = true)
        sourceApk.copyTo(latestRootApk, overwrite = true)

        println("Archived debug APK: ${archiveApk.relativeTo(rootProject.projectDir)}")
        println("Updated latest APK: ${latestArchiveApk.relativeTo(rootProject.projectDir)}")
        println("Updated root latest APK: ${latestRootApk.relativeTo(rootProject.projectDir)}")

        versionProperties.setProperty("VERSION_MAJOR", configuredVersionMajor.toString())
        versionProperties.setProperty("VERSION_MINOR", configuredVersionMinor.toString())
        versionProperties.setProperty("VERSION_PATCH", configuredVersionPatch.toString())
        versionPropertiesFile.outputStream().use {
            versionProperties.store(it, "Allocated by generateHandoffDebugApk")
        }
        println("Allocated handoff artifact version: $computedVersionName")
    }
}

gradle.taskGraph.whenReady {
    if (hasTask(generateHandoffDebugApk.get()) && !artifactGenerationRequested) {
        error("generateHandoffDebugApk is internal; run ./tool/generate_handoff_debug_apk.sh")
    }
}
