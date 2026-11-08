package com.r4g3baby.simplescore.fabric.config

import com.r4g3baby.simplescore.api.Platform
import com.r4g3baby.simplescore.core.config.BaseMainConfig
import java.io.Reader
import java.util.function.Predicate
import java.util.regex.Pattern

class MainConfig<V : Any>(plugin: Platform<V>, hasPermission: (V, String) -> Boolean) : BaseMainConfig<V, YamlConfiguration>(plugin.getDataFolder()) {
    override val conditionsConfig = ConditionsConfig(plugin, hasPermission)
    override val scoreboardsConfig = ScoreboardsConfig(plugin, this)

    override val resourceName: String = "configs/main.yml"

    var taskUpdateTime: Long = 1
    var scoreboardTaskAsync: Boolean = true

    private val _worlds = LinkedHashMap<Predicate<String>, List<String>>()
    val worlds: Map<Predicate<String>, List<String>> get() = _worlds

    override fun parseConfigFile(reader: Reader?): YamlConfiguration {
        return if (reader != null) {
            YamlConfiguration.loadConfiguration(reader)
        } else YamlConfiguration()
    }

    override fun loadVariables(config: YamlConfiguration) {
        version = config.getInt("version", version)
        language = config.getString("language", language) ?: language
        checkForUpdates = config.getBoolean("checkForUpdates", checkForUpdates)
        taskUpdateTime = config.getLong("taskUpdateTime", taskUpdateTime)
        scoreboardTaskAsync = config.getBoolean("scoreboardTaskAsync", scoreboardTaskAsync)

        if (config.isConfigurationSection("worlds")) {
            val worldsSec = config.getConfigurationSection("worlds") ?: return
            worldsSec.getKeys(false).forEach { worldName ->
                val pattern = Pattern.compile(worldName, Pattern.CASE_INSENSITIVE)
                _worlds[pattern.asPredicate()] = when {
                    worldsSec.isString(worldName) -> listOfNotNull(worldsSec.getString(worldName))
                    worldsSec.isList(worldName) -> worldsSec.getStringList(worldName)
                    else -> emptyList()
                }
            }
        }
    }
}
