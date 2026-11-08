package com.r4g3baby.simplescore.fabric

import com.r4g3baby.simplescore.api.Platform
import com.r4g3baby.simplescore.api.Manager
import com.r4g3baby.simplescore.api.scoreboard.VarReplacer
import com.r4g3baby.simplescore.api.scoreboard.data.Provider
import com.r4g3baby.simplescore.fabric.config.MainConfig
import com.r4g3baby.simplescore.fabric.config.YamlConfiguration
import java.io.File
import java.nio.file.Files
import java.util.logging.Logger
import kotlin.test.*

class ConfigurationTest {
    private class TestPlatform(private val folder: File) : Platform<Unit> {
        override val provider = Provider("test")
        override val manager: Manager<Unit> get() = error("Not used by configuration parsing")
        override fun getDataFolder() = folder
        override fun getLogger() = Logger.getLogger("configuration-test")
    }
    private fun withConfig(scoreboards: String? = null, conditions: String? = null, test: (MainConfig<Unit>) -> Unit) {
        val folder = Files.createTempDirectory("simplescore-config-test").toFile()
        try {
            scoreboards?.let { File(folder, "scoreboards.yml").writeText(it.trimIndent()) }
            conditions?.let { File(folder, "conditions.yml").writeText(it.trimIndent()) }
            val config = MainConfig(TestPlatform(folder)) { _, permission -> permission == "allowed" }
            config.loadConfig()
            test(config)
        } finally { folder.deleteRecursively() }
    }
    @Test fun `all supplied configurations load and default sidebar retains all lines`() = withConfig { config ->
        assertEquals(4, config.version)
        assertTrue(config.scoreboards.keys.containsAll(listOf("default", "worldTheEnd", "pvp")))
        assertEquals(15, config.scoreboards.getValue("default").scores.size)
        val compass = config.scoreboards.getValue("default").scores.minBy { it.value.toInt() }
        assertEquals("-1", compass.value)
        val compassLine = compass.lines.single() as com.r4g3baby.simplescore.core.scoreboard.line.StaticLine<Unit>
        assertEquals("%compass_bar%", compassLine.text)
        assertEquals(1, compassLine.renderEvery)
        assertTrue(config.scoreboards.getValue("default").scores.all { it.hideNumber })
        assertTrue(config.conditions.keys.containsAll(listOf("hasPermission", "hasWorldPermission", "inOverworld", "inTheEnd", "isLowHealth", "inCreativeMode")))
        assertTrue(config.worlds.keys.any { it.test("modded:custom_dimension") })
    }
    @Test fun `numeric YAML keys and scalar condition values preserve their string representation`() {
        val section = YamlConfiguration.loadConfiguration("scores:\n  12: hello\nvalue: 10\n".reader())
        assertEquals("hello", section.getConfigurationSection("scores")!!.getString("12"))
        assertEquals("10", section.getString("value"))
    }
    @Test fun `regex world keys containing dots are not treated as paths`() {
        val section = YamlConfiguration.loadConfiguration("worlds:\n  '^minecraft:.*$': [default]\n".reader()).getConfigurationSection("worlds")!!
        assertEquals(listOf("default"), section.getStringList("^minecraft:.*$"))
    }
    @Test fun `reject duplicate keys and invalid roots rather than silently changing configuration`() {
        assertFails { YamlConfiguration.loadConfiguration("key: a\nkey: b".reader()) }
        assertFailsWith<IllegalArgumentException> { YamlConfiguration.loadConfiguration("- a\n- b".reader()) }
    }
    @Test fun `all line forms conditions score placeholders and number hiding survive parsing`() = withConfig("""
        test:
          titles:
            text: 'Title %value%'
            renderEvery: 1
          scores:
            - score: '%value%'
              hideNumber: true
              lines:
                - text: 'denied'
                  conditions: '!allowed'
                - frames:
                    - text: 'first'
                      visibleFor: 1
                      renderEvery: 1
                    - text: 'second'
                      visibleFor: 1
                      renderEvery: 1
                  conditions: allowed
            - score: 2
              lines: ''
            - score: 1
              lines:
                text: 'section line'
    """, """
        allowed:
          type: HasPermission
          permission: allowed
    """) { config ->
        val replacer = VarReplacer<Unit> { text, _ -> text.replace("%value%", "7") }
        val board = config.scoreboards.getValue("test")
        assertEquals("Title 7", board.getTitle(Unit, replacer)!!.currentText(Unit, replacer))
        val scores = board.getScores(Unit, replacer)
        assertEquals(7, scores[0].getValueAsInteger(Unit, replacer))
        assertTrue(scores[0].hideNumber)
        val animated = scores[0].getLine(Unit, replacer)!!
        val frames = mutableSetOf<String>()
        repeat(6) { board.tick(); frames += animated.currentText(Unit, replacer) }
        assertEquals(setOf("first", "second"), frames)
        assertEquals("", scores[1].getLine(Unit, replacer)!!.currentText(Unit, replacer))
        assertEquals("section line", scores[2].getLine(Unit, replacer)!!.currentText(Unit, replacer))
    }
    @Test fun `all comparison condition types use the shared core and support negation`() = withConfig("""
        test:
          titles: ['one', 'two']
          conditions: [equals, contains, starts, ends, greater, less, '!denied']
          scores: {1: 'ok'}
    """, """
        equals: {type: Equals, input: '%value%', value: 7}
        contains: {type: Contains, input: abc, value: b}
        starts: {type: StartsWith, input: abc, value: a}
        ends: {type: EndsWith, input: abc, value: c}
        greater: {type: GreaterThan, input: '%value%', value: 6}
        less: {type: LessThan, input: '%value%', value: 8}
        denied: {type: HasPermission, permission: denied}
    """) { config ->
        val board = config.scoreboards.getValue("test")
        assertTrue(board.canSee(Unit, VarReplacer { text, _ -> text.replace("%value%", "7") }))
        assertFalse(board.canSee(Unit, VarReplacer { text, _ -> text.replace("%value%", "9") }))
    }
}
