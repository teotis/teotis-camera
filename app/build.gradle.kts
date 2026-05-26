plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.opencamera.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.opencamera.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
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
    }
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
    implementation(project(":feature:mode-night"))
    implementation(project(":feature:mode-photo"))
    implementation(project(":feature:mode-portrait"))
    implementation(project(":feature:mode-pro"))
    implementation(project(":feature:mode-video"))

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.activity:activity-ktx:1.9.2")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-core:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-video:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.8.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.google.mlkit:segmentation-selfie:16.0.0-beta6")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.robolectric:robolectric:4.13")
}
