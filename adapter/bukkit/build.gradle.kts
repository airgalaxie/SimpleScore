import org.apache.tools.ant.filters.ReplaceTokens

plugins {
    alias(libs.plugins.shadow)
    alias(libs.plugins.dokka)
}

dependencies {
    api(project(":core"))
    implementation(project(":adapter:bukkit:worldguard"))

    compileOnly(libs.bukkit)
    compileOnly(libs.netty)
    compileOnly(libs.papi)
    compileOnly(libs.luckperms)
    compileOnly(libs.adventureMiniMessage)
    compileOnly(libs.adventureSerializer)

    testImplementation(libs.kotlinTest)

    implementation(libs.objenesis)
}

tasks {
    processResources {
        inputs.property("projectVersion", project.version.toString())
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            filter<ReplaceTokens>(
                "tokens" to mapOf(
                    "name" to rootProject.name,
                    "version" to rootProject.version,
                    "package" to rootProject.group,
                    "description" to "A simple animated scoreboard plugin for your server.",
                    "website" to "https://ruipereira.dev"
                )
            )
        }
    }
}

// Preserve Bukkit's existing package relocation and standalone plugin distribution.
tasks.shadowJar {
    archiveFileName.set("${rootProject.name}-${project.version}-bukkit.jar")
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("target"))
    manifest { attributes["paperweight-mappings-namespace"] = "mojang" }
    val bundled = "${project.group}.lib"
    relocate("org.objenesis", "$bundled.objenesis")
    relocate("net.swiftzer.semver", "$bundled.semver")
    relocate("org.jetbrains", "$bundled.jetbrains")
    relocate("kotlin", "$bundled.kotlin")
    from(rootProject.file("LICENSE"))
    exclude("META-INF/**")
    minimize()
}
tasks.assemble { dependsOn(tasks.shadowJar) }
