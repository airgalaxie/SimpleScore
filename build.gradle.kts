plugins {
    id("maven-publish")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.dokka)
    alias(libs.plugins.shadow)
}

group = "com.r4g3baby.simplescore"
version = libs.versions.project.get()

dependencies {
    dokka(project(":api"))
    dokka(project(":core"))
    dokka(project(":adapter:bukkit"))
    dokka(project(":adapter:fabric"))
}

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply(plugin = "maven-publish")
    apply(plugin = "org.jetbrains.kotlin.jvm")

    publishing {
        publications {
            create<MavenPublication>("mavenJava") {
                artifactId = "${rootProject.name}-${path.replace(":", "-").drop(1)}".lowercase()

                from(components["java"])
            }
        }
    }
}

allprojects {
    repositories {
        mavenLocal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.nucleoid.xyz/")
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

    java {
        withSourcesJar()
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(rootProject.libs.versions.java.get().toInt()))
        }
    }

    kotlin {
        jvmToolchain(rootProject.libs.versions.java.get().toInt())
    }
}

tasks {
    wrapper {
        gradleVersion = libs.versions.gradle.get()
    }

    jar { enabled = false }

    shadowJar {
        enabled = false
        dependsOn(":adapter:bukkit:shadowJar", ":adapter:fabric:shadowJar")
    }

    register("adapterJars") {
        group = "build"
        description = "Build both standalone platform adapters into target/."
        dependsOn(":adapter:bukkit:shadowJar", ":adapter:fabric:shadowJar")
    }

    assemble { dependsOn("adapterJars") }
    clean { delete(layout.projectDirectory.dir("target")) }
}
