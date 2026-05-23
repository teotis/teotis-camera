pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "OpenCamera"

include(":app")
include(":core:device")
include(":core:media")
include(":core:mode")
include(":core:settings")
include(":core:session")
include(":core:capability")
include(":core:effect")
include(":feature:mode-document")
include(":feature:mode-humanistic")
include(":feature:mode-night")
include(":feature:mode-photo")
include(":feature:mode-portrait")
include(":feature:mode-pro")
include(":feature:mode-video")
