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

rootProject.name = "Void-Linux"

include(":app")
include(":core:common")
include(":core:designsystem")
include(":core:native")
project(":core:native").projectDir = file("native")
include(":core:data")
include(":feature:linux")
include(":feature:terminal")
include(":feature:windows")
include(":feature:tor")
include(":feature:security")
include(":feature:location")
include(":feature:settings")
include(":library:proot-engine")
project(":library:proot-engine").projectDir = file("library/proot-engin/proot-engine")
