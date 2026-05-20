plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:settings"))
    implementation(project(":core:media"))
    implementation(project(":core:device"))
    testImplementation(kotlin("test"))
}
