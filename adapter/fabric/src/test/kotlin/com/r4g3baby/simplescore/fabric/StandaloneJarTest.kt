package com.r4g3baby.simplescore.fabric

import java.io.File
import java.net.URLClassLoader
import java.util.zip.ZipFile
import kotlin.test.*

class StandaloneJarTest {
    @Test fun `distributable runs Kotlin SemVer and YAML with only Java as its parent classloader`() {
        val jar = File(System.getProperty("simplescore.fabricJar"))
        URLClassLoader(arrayOf(jar.toURI().toURL()), ClassLoader.getPlatformClassLoader()).use { isolated ->
            assertFailsWith<ClassNotFoundException> { isolated.loadClass("kotlin.jvm.internal.Intrinsics") }
            assertFailsWith<ClassNotFoundException> { isolated.loadClass("net.fabricmc.language.kotlin.KotlinAdapter") }
            val info = isolated.loadClass("com.r4g3baby.simplescore.ProjectInfo")
            val instance = info.getField("INSTANCE").get(null)
            assertTrue(info.getMethod("getVERSION").invoke(instance).toString().isNotBlank())
            val yaml = isolated.loadClass("com.r4g3baby.simplescore.fabric.config.YamlConfiguration")
            val companion = yaml.getField("Companion").get(null)
            val config = companion.javaClass.getMethod("loadConfiguration", java.io.Reader::class.java)
                .invoke(companion, "version: 4\nworlds: {'.*': [default]}".reader())
            assertEquals(4, config.javaClass.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType).invoke(config, "version", 0))
        }
    }
    @Test fun `jar excludes optional providers server implementations and unrelocated Kotlin`() {
        ZipFile(System.getProperty("simplescore.fabricJar")).use { jar ->
            val entries = jar.entries().asSequence().map { it.name }.toList()
            assertTrue(entries.any { it == "com/r4g3baby/simplescore/fabric/lib/kotlin/jvm/internal/Intrinsics.class" })
            for (prefix in listOf("kotlin/", "org/bukkit/", "net/minecraft/", "net/fabricmc/", "eu/pb4/", "net/luckperms/")) {
                assertFalse(entries.any { it.startsWith(prefix) }, "Must not bundle $prefix")
            }
            val metadata = jar.getInputStream(jar.getEntry("fabric.mod.json")).reader().readText()
            assertFalse(metadata.contains("fabric-language-kotlin"))
            assertFalse(metadata.contains("\${"))
            assertTrue(metadata.contains("simplescore.mixins.json"))
        }
    }
}
