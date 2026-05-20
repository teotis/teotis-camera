plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:media"))
    implementation(project(":core:settings"))

    testImplementation(kotlin("test"))
}
