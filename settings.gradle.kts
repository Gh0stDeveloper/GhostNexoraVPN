pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com[.]android([.].*)?")
                includeGroupByRegex("androidx([.].*)?")
                includeGroupByRegex("com[.]google[.]testing[.]platform([.].*)?")
            }
        }
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
    // Gradle 9+ detecta libs.versions.toml automáticamente
    // NO declarar versionCatalogs manualmente aquí
}

rootProject.name = "GhostNexoraVPN"
include(":app")
