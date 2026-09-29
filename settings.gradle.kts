pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Only consulted in AAR-consumer test mode (see fragmentApp/build.gradle.kts);
        // scoped to our group so mavenLocal can't shadow anything else.
        if (providers.gradleProperty("useAarDependency").isPresent) {
            mavenLocal { content { includeGroup("ai.adgeist") } }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "native-android-adgeist"
include(":fragmentApp")
include(":composeApp")
include(":adgeistkit")
