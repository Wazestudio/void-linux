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
        maven { url = uri("https://jitpack.io") }
        maven { url = uri("https://raw.githubusercontent.com/guardianproject/gpmaven/master") }
    }
}

rootProject.name = "Void-Linux"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:native")
include(":feature:linux")
include(":feature:terminal")
include(":feature:windows")
include(":feature:tor")
include(":feature:security")
include(":feature:location")
include(":feature:settings")