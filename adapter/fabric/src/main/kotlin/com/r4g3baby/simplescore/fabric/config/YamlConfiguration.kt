package com.r4g3baby.simplescore.fabric.config

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.Reader

/** Small section view preserving the Bukkit configuration schema without loading Bukkit. */
open class ConfigurationSection(
    private val values: MutableMap<String, Any?> = linkedMapOf(),
    val currentPath: String = ""
) {
    open fun get(path: String): Any? {
        if (values.containsKey(path)) return values[path]
        val parts = path.split('.', limit = 2)
        return if (parts.size == 2) getConfigurationSection(parts[0])?.get(parts[1]) else null
    }
    fun contains(path: String) = get(path) != null
    fun getKeys(deep: Boolean): Set<String> {
        require(!deep) { "Only direct section keys are supported" }
        return values.keys
    }
    @JvmOverloads fun getString(path: String, default: String? = null): String? = get(path)?.takeUnless { it is Map<*, *> || it is List<*> }?.toString() ?: default
    @JvmOverloads fun getBoolean(path: String, default: Boolean = false) = get(path) as? Boolean ?: default
    @JvmOverloads fun getDouble(path: String, default: Double = 0.0) = (get(path) as? Number)?.toDouble() ?: default
    fun getInt(path: String, default: Int = 0) = (get(path) as? Number)?.toInt() ?: default
    fun getLong(path: String, default: Long = 0) = (get(path) as? Number)?.toLong() ?: default
    fun getList(path: String) = get(path) as? List<*>
    fun getStringList(path: String) = getList(path).orEmpty().mapNotNull { value ->
        value?.takeUnless { it is Map<*, *> || it is List<*> }?.toString()
    }
    fun getMapList(path: String) = getList(path).orEmpty().filterIsInstance<Map<*, *>>()
    fun isString(path: String) = get(path) is String
    fun isList(path: String) = get(path) is List<*>
    fun isConfigurationSection(path: String) = get(path) is Map<*, *> || get(path) is ConfigurationSection
    fun getConfigurationSection(path: String): ConfigurationSection? = when (val value = get(path)) {
        is ConfigurationSection -> value
        is Map<*, *> -> ConfigurationSection(value.entries.associateTo(linkedMapOf()) { it.key.toString() to it.value }, childPath(path))
        else -> null
    }
    fun createSection(path: String): ConfigurationSection = ConfigurationSection(currentPath = childPath(path)).also { values[path] = it }
    fun addDefault(path: String, value: Any?) { values.putIfAbsent(path, value) }
    private fun childPath(path: String) = if (currentPath.isEmpty()) path else "$currentPath.$path"
}

class MemoryConfiguration : ConfigurationSection()

class YamlConfiguration(values: MutableMap<String, Any?> = linkedMapOf()) : ConfigurationSection(values) {
    companion object {
        fun loadConfiguration(reader: Reader): YamlConfiguration {
            val options = LoaderOptions().apply { isAllowDuplicateKeys = false }
            val value = Yaml(SafeConstructor(options)).load<Any?>(reader) ?: return YamlConfiguration()
            require(value is Map<*, *>) { "Configuration root must be a mapping" }
            return YamlConfiguration(value.entries.associateTo(linkedMapOf()) { it.key.toString() to it.value })
        }
    }
}
