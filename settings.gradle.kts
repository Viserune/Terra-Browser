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
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "TerraBrowser"
include(":app")

val localLiquidKitDir = providers.gradleProperty("localLiquidKitDir")
    .orElse(providers.environmentVariable("LIQUIDKIT_KMP_DIR"))
    .orNull
    ?.let(::file)
    ?.takeIf { it.isDirectory }
if (localLiquidKitDir != null) {
    includeBuild(localLiquidKitDir) {
        dependencySubstitution {
            substitute(module("io.liquidkit:liquidkit-android")).using(project(":liquidkit"))
            substitute(module("io.liquidkit:liquidkit")).using(project(":liquidkit"))
            substitute(module("io.github.kyant0:backdrop-android")).using(project(":backdrop"))
            substitute(module("io.github.kyant0:backdrop")).using(project(":backdrop"))
        }
    }
}
