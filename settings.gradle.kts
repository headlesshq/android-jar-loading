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
        google()
        mavenCentral()
        maven {
            name = "3arthMaven"
            url = uri("https://3arthqu4ke.github.io/maven")
        }

        maven {
            name = "libs"
            url = rootProject.projectDir.toPath().toAbsolutePath().resolve("libs").toUri()
        }
    }
}

rootProject.name = "HMC-Android"
include(":app")
