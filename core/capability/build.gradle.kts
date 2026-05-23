plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:effect"))
    implementation(project(":core:media"))
    testImplementation(project(":core:settings"))
    testImplementation(kotlin("test"))
}
