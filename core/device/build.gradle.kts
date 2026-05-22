plugins {
    id("org.jetbrains.kotlin.jvm")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    api(project(":core:media"))
    implementation(project(":core:settings"))
    implementation(project(":core:capability"))
    implementation(project(":core:effect"))
    testImplementation(kotlin("test"))
}
