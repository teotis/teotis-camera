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

rootProject.name = "TeotisCamera"

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
include(":feature:mode-photo")
include(":feature:mode-checkin")
include(":feature:mode-video")
