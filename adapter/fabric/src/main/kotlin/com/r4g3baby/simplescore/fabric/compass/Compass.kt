package com.r4g3baby.simplescore.fabric.compass

import com.r4g3baby.simplescore.fabric.config.ConfigurationSection
import com.r4g3baby.simplescore.fabric.config.YamlConfiguration
import java.io.File

/** Immutable snapshot prepared during configuration loading; no I/O or layout work per query. */
class Compass private constructor(private val renderer: CompassRenderer?) {
    val enabled: Boolean get() = renderer != null
    fun atYaw(yaw: Float): String = renderer?.atYaw(yaw) ?: ""

    companion object {
        fun disabled() = Compass(null)

        fun load(folder: File): Compass {
            folder.mkdirs()
            val file = File(folder, "compass.yml")
            val defaults = checkNotNull(Compass::class.java.classLoader.getResourceAsStream("configs/compass.yml")) {
                "Missing bundled compass.yml"
            }.bufferedReader(Charsets.UTF_8).use { it.readText() }
            if (!file.exists()) file.writeText(defaults, Charsets.UTF_8)
            val supplied = file.bufferedReader(Charsets.UTF_8).use(YamlConfiguration::loadConfiguration)
            val fallback = defaults.reader().use(YamlConfiguration::loadConfiguration)
            val configuration = object : ConfigurationSection() {
                override fun get(path: String): Any? = supplied.get(path) ?: fallback.get(path)
            }
            val enabled = configuration.get("enabled")
            require(enabled is Boolean) { "compass.yml: enabled must be true or false" }
            // Disabled configurations need no renderer, font validation, or frame cache.
            return if (enabled) Compass(CompassRenderer(CompassConfig.read(configuration))) else disabled()
        }
    }
}
