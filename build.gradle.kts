plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("org.jetbrains.kotlin.jvm") version "1.9.24" apply false
}

val sharedBuildRoot = file(
    (project.findProperty("opencamera.buildRoot") as? String)
        ?: System.getProperty("opencamera.buildRoot")
        ?: System.getenv("OPENCAMERA_BUILD_ROOT")
        ?: System.getenv("CODEX_BUILD_ROOT")
        ?: "${System.getProperty("user.home")}/.codex-build/TeotisCamera"
)

allprojects {
    val projectBuildPath = if (path == ":") {
        "root"
    } else {
        path.removePrefix(":").replace(':', '/')
    }
    layout.buildDirectory.set(sharedBuildRoot.resolve(projectBuildPath))
}
