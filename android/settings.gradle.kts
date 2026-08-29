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

@Suppress("UnstableApiUsage")
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FinCore360"

include(":app")

// Core Modules
include(":core:common")
include(":core:network")
include(":core:database")
include(":core:security")
include(":core:ui")
include(":core:testing")

// Feature Modules
include(":feature:auth")
include(":feature:dashboard")
include(":feature:accounts")
include(":feature:transactions")
include(":feature:transfer")
include(":feature:notifications")

