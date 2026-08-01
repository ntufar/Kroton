pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "kroton"

include(
    ":app",
    ":core:model",
    ":core:database",
    ":core:datastore",
    ":core:domain",
    ":core:export",
    ":core:designsystem",
    ":feature:workout",
    ":feature:routines",
    ":feature:history",
    ":feature:exercises",
    ":feature:measure",
    ":feature:stats",
    ":feature:settings",
)
