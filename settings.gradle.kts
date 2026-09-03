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
        maven(url = "https://jitpack.io")
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
        maven {
            url = uri("https://maven.pkg.github.com/webscarepk/WebCareAds")
            credentials {
                username = "webscarepk"
                password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("GITHUB_TOKEN") ?: "")
            }
        }
        maven {
            url = uri("https://maven.pkg.github.com/webscarepk/WebsCareCanvas")
            credentials {
                username = "webscarepk"
                password = providers.gradleProperty("gpr.key").getOrElse(System.getenv("GITHUB_TOKEN") ?: "")
            }
        }
    }
}

rootProject.name = "UrduCanvas"
include(":app")

// Redirect build outputs outside OneDrive to eliminate Windows file locking and deletion errors
val buildDirBase = java.io.File(System.getProperty("user.home"), ".gradle_builds/UrduCanvas")
gradle.beforeProject {
    val projName = if (name == "UrduCanvas") "root" else name
    layout.buildDirectory.set(java.io.File(buildDirBase, projName))
}
