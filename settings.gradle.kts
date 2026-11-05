pluginManagement {
    // Settings plugins are resolved before Gradle creates version catalog accessors.
    val catalog = file("gradle/libs.versions.toml").readText()
    val foojayVersion = Regex("""(?m)^foojay\s*=\s*"([^"]+)"""").find(catalog)
        ?.groupValues?.get(1) ?: error("Missing foojay version in version catalog")
    plugins {
        id("org.gradle.toolchains.foojay-resolver-convention") version foojayVersion
    }

    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositories {
        mavenCentral()

        maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/") {
            name = "spigotmc"
        }
        maven("https://repo.helpch.at/releases/") {
            name = "helpchat"
        }
        maven("https://maven.enginehub.org/repo/") {
            name = "enginehub"
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention")
}

rootProject.name = "SimpleScore"

include("api")
include("core")
include("bukkit")
include("bukkit:worldguard")
include("bukkit:worldguard:common")
include("bukkit:worldguard:v6")
include("bukkit:worldguard:v7")
