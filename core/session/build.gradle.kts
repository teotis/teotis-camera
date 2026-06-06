plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:device"))
    implementation(project(":core:media"))
    implementation(project(":core:mode"))
    implementation(project(":core:effect"))
    implementation(project(":core:capability"))
    implementation(project(":core:settings"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
    testImplementation(project(":feature:mode-document"))
    testImplementation(project(":feature:mode-humanistic"))
    testImplementation(project(":feature:mode-photo"))
    testImplementation(project(":feature:mode-video"))
}
