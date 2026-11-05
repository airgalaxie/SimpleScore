plugins {
    id("maven-publish")
    alias(libs.plugins.kotlin)
    alias(libs.plugins.dokka)
    alias(libs.plugins.shadow)
}

group = "com.r4g3baby.simplescore"
version = libs.versions.project.get()

dependencies {
    api(project("bukkit"))

    dokka(project(":api"))
    dokka(project(":core"))
    dokka(project(":bukkit"))
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
        archiveFileName.set("${project.name}-${project.version}.jar")

        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }

        val libs = "${project.group}.lib"
        relocate("org.objenesis", "$libs.objenesis")
        relocate("net.swiftzer.semver", "$libs.semver")

        relocate("org.jetbrains", "$libs.jetbrains")
        relocate("kotlin", "$libs.kotlin")

        from(file("LICENSE"))
        exclude("META-INF/**")

        minimize()
    }
}
