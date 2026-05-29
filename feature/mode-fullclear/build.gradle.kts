plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:mode"))
    implementation(project(":core:effect"))
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")
}
