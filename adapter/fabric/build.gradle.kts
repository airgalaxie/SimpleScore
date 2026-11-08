plugins {
    alias(libs.plugins.loom)
    alias(libs.plugins.shadow)
    alias(libs.plugins.dokka)
}

dependencies {
    minecraft(libs.minecraft)
    implementation(libs.fabricLoader)
    implementation(libs.fabricApi)
    implementation(project(":core"))
    implementation(libs.snakeYaml)
    implementation(libs.adventureMiniMessage)
    implementation(libs.adventureSerializer)
    // Patbox is supplied by the server, never bundled or required.
    compileOnly(libs.luckperms)
    compileOnly(libs.patbox)
    testImplementation(libs.kotlinTest)
}

loom {
    serverOnlyMinecraftJar()
    runs {
        named("server") {
            runDir("run")
        }
    }
}

// Bundle and relocate our Kotlin runtime. No Fabric Language Kotlin entrypoint or dependency.
val bundled by configurations.creating
configurations.implementation { extendsFrom(bundled) }
dependencies {
    bundled(project(":core"))
    bundled(libs.snakeYaml)
    bundled(libs.adventureMiniMessage)
    bundled(libs.adventureSerializer)
    bundled(kotlin("stdlib", libs.versions.kotlin.get()))
}

tasks.processResources {
    val properties = mapOf(
        "version" to project.version.toString(),
        "java" to libs.versions.java.get(),
        "minecraft" to libs.versions.minecraftRuntime.get(),
        "loader" to libs.versions.fabricLoader.get(),
        "fabricApi" to libs.versions.fabricApi.get(),
        "patbox" to libs.versions.patbox.get()
    )
    inputs.properties(properties)
    filesMatching("fabric.mod.json") { expand(properties) }
}

tasks.shadowJar {
    configurations = listOf(bundled)
    archiveFileName.set("${rootProject.name}-${project.version}-fabric.jar")
    destinationDirectory.set(rootProject.layout.projectDirectory.dir("target"))
    val bundledPackage = "${project.group}.fabric.lib"
    relocate("kotlin", "$bundledPackage.kotlin")
    relocate("org.jetbrains.annotations", "$bundledPackage.annotations")
    relocate("org.yaml.snakeyaml", "$bundledPackage.yaml")
    relocate("net.swiftzer.semver", "$bundledPackage.semver")
    relocate("net.kyori", "$bundledPackage.kyori")
    filesMatching("META-INF/services/**") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    filesMatching("META-INF/*.kotlin_module") { duplicatesStrategy = DuplicatesStrategy.INCLUDE }
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.RSA", "META-INF/*.DSA", "META-INF/versions/**/module-info.class")
    from(rootProject.file("LICENSE"))
    // Do not minimize: Kotlin and serializers contain reflective/service-loaded entrypoints.
}
tasks.assemble { dependsOn(tasks.shadowJar) }

// Verify the distributable itself, with no parent Kotlin runtime available to its classloader.
tasks.test {
    dependsOn(tasks.shadowJar)
    systemProperty("simplescore.fabricJar", tasks.shadowJar.get().archiveFile.get().asFile.absolutePath)
}

// Runs the exact distributable in Fabric's production launcher, never the development classpath.
dependencies { "productionRuntimeMods"(libs.fabricApi) }
val patboxRuntime by configurations.creating
dependencies { patboxRuntime(libs.patbox) { isTransitive = false } }

tasks.register<net.fabricmc.loom.task.prod.ServerProductionRunTask>("runProductionServer") {
    dependsOn(tasks.shadowJar)
    mods.setFrom(tasks.shadowJar.flatMap { it.archiveFile }, configurations.named("productionRuntimeMods"))
    installerVersion.set(libs.versions.fabricInstaller)
    runDir.set(layout.buildDirectory.dir("production-server"))
    if (providers.gradleProperty("withPatbox").isPresent) mods.from(patboxRuntime)
    if (providers.gradleProperty("serverHelp").isPresent) programArgs.set(listOf("--help"))
}

val smoke by sourceSets.creating {
    compileClasspath += sourceSets.main.get().output + configurations.compileClasspath.get()
}
val smokeJar by tasks.registering(Jar::class) {
    from(smoke.output)
    archiveClassifier.set("smoke-test")
}
tasks.named<ProcessResources>(smoke.processResourcesTaskName) {
    inputs.property("version", project.version.toString())
    filesMatching("fabric.mod.json") { expand("version" to project.version.toString()) }
}
tasks.register<net.fabricmc.loom.task.prod.ServerProductionRunTask>("runProductionSmoke") {
    dependsOn(tasks.shadowJar, smokeJar)
    mods.setFrom(tasks.shadowJar.flatMap { it.archiveFile }, smokeJar.flatMap { it.archiveFile }, configurations.named("productionRuntimeMods"))
    installerVersion.set(libs.versions.fabricInstaller)
    runDir.set(layout.buildDirectory.dir(if (providers.gradleProperty("withPatbox").isPresent) "smoke-patbox" else "smoke-standalone"))
    if (providers.gradleProperty("withPatbox").isPresent) mods.from(patboxRuntime)
    doFirst {
        val directory = runDir.get().asFile.apply { mkdirs() }
        if (providers.gradleProperty("acceptMinecraftEula").orNull == "true") {
            directory.resolve("eula.txt").writeText("eula=true\n")
        }
        require(directory.resolve("eula.txt").takeIf { it.exists() }?.readText()?.contains("eula=true") == true) {
            "Accept the Minecraft EULA in ${directory.resolve("eula.txt")} before running the production smoke test."
        }
        directory.resolve("smoke-success.txt").delete()
        directory.resolve("smoke-failure.txt").delete()
        directory.resolve("server.properties").writeText("server-ip=127.0.0.1\nserver-port=0\nonline-mode=false\nlevel-type=minecraft:flat\ngenerate-structures=false\nspawn-protection=0\nview-distance=2\nsimulation-distance=2\nmax-tick-time=60000\n")
        val config = directory.resolve("config/simplescore").apply { mkdirs() }
        config.resolve("compass.yml").writeText(file("src/main/resources/configs/compass.yml").readText())
        config.resolve("config.yml").writeText("version: 4\nlanguage: en\ncheckForUpdates: false\ntaskUpdateTime: 1\nscoreboardTaskAsync: false\nworlds: {'.*': [default]}\n")
    }
    doLast {
        require(runDir.file("smoke-success.txt").get().asFile.exists()) { "Production smoke failed; see the server log in ${runDir.get().asFile}" }
    }
}
