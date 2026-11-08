package com.r4g3baby.simplescore.fabric.compass

import com.r4g3baby.simplescore.fabric.config.ConfigurationSection
import com.r4g3baby.simplescore.fabric.config.YamlConfiguration
import com.r4g3baby.simplescore.fabric.scoreboard.Text
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style as NativeStyle
import java.nio.file.Files
import java.util.Optional
import kotlin.test.*
import org.junit.BeforeClass

class CompassTest {
    companion object {
        @BeforeClass @JvmStatic fun bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap() }
    }
    private fun config(vararg overrides: Pair<String, Any>): ConfigurationSection {
        val defaults = checkNotNull(javaClass.getResourceAsStream("/configs/compass.yml"))
            .bufferedReader().use(YamlConfiguration::loadConfiguration)
        val values = overrides.toMap()
        return object : ConfigurationSection() { override fun get(path: String): Any? = values[path] ?: defaults.get(path) }
    }
    private fun plain(text: String) = text.replace(Regex("§[0-9a-fk-orx]"), "")
    private fun measured(text: String, font: FontMetrics): Int {
        var width = 0
        var bold = false
        var i = 0
        while (i < text.length) {
            val code = text.codePointAt(i)
            i += Character.charCount(code)
            if (code == '§'.code) {
                when (val format = text[i++]) {
                    'l' -> bold = true
                    'r', 'x' -> bold = false
                    in "0123456789abcdef" -> bold = false
                }
            } else width += font.advance(code, Style("", bold, false, false, false))
        }
        return width
    }
    @Test fun `all eight bearings retain original labels and pixel centering`() {
        val configuration = config()
        val settings = CompassConfig.read(configuration)
        val font = FontMetrics(configuration)
        val renderer = CompassRenderer(settings)
        listOf("[S]", "SW", "[W]", "NW", "[N]", "NE", "[E]", "SE").forEachIndexed { index, label ->
            val output = renderer.atYaw(index * 45f)
            assertTrue(label in output, plain(output))
            val left = measured(output.substring(0, output.indexOf(label)), font)
            assertTrue(kotlin.math.abs(80 - (left + font.width(label, settings.active()) / 2)) <= 2)
        }
    }
    @Test fun `negative and repeated rotations wrap and nonfinite angles are empty`() {
        val renderer = CompassRenderer(CompassConfig.read(config()))
        for (yaw in -720..720) assertEquals(renderer.atYaw(yaw.toFloat()), renderer.atYaw(yaw + 360f))
        assertEquals(renderer.atYaw(0f), renderer.atYaw(359.9f))
        assertEquals("", renderer.atYaw(Float.NaN))
        assertEquals("", renderer.atYaw(Float.POSITIVE_INFINITY))
    }
    @Test fun `turning right moves north left without changing angle spacing`() {
        val configuration = config("behavior.highlight" to false)
        val font = FontMetrics(configuration)
        val renderer = CompassRenderer(CompassConfig.read(configuration))
        val first = renderer.atYaw(180f)
        val next = renderer.atYaw(185f)
        val left = measured(first.substring(0, first.indexOf("[N]")), font)
        val moved = measured(next.substring(0, next.indexOf("[N]")), font)
        assertTrue(left - moved in 6..10)
        val east = measured(first.substring(0, first.indexOf("NE")), font)
        assertEquals(64, east + 6 - (left + 7))
    }
    @Test fun `every frame retains exact viewport width after native Fabric text conversion`() {
        for (width in listOf(90, 160)) {
            val configuration = config("layout.width-pixels" to width)
            val font = FontMetrics(configuration)
            val renderer = CompassRenderer(CompassConfig.read(configuration))
            val outputs = mutableSetOf<String>()
            for (frame in 0 until 720) {
                val legacy = renderer.atYaw(frame / 2f)
                assertEquals(width, measured(legacy, font), "Original frame $frame")
                assertFalse('\n' in legacy || '\r' in legacy)
                val native = Text.component(legacy)
                assertEquals(plain(legacy), native.string, "Whitespace and U+200C at frame $frame")
                var nativeWidth = 0
                native.visitUnbounded(FormattedText.StyledContentConsumer<Void> { style, text ->
                    text.codePoints().forEach { nativeWidth += font.advance(it, Style("", style.isBold, false, false, false)) }
                    Optional.empty()
                }, NativeStyle.EMPTY)
                assertEquals(width, nativeWidth, "Native frame $frame")
                outputs += native.string
            }
            assertTrue(outputs.size > 100)
        }
    }
    @Test fun `invalid layout content colors and resolution fail validation`() {
        val invalid = listOf(
            "layout.width-pixels" to 0, "layout.width-pixels" to 160.5,
            "layout.pixels-per-45-degrees" to 0, "layout.scale-every-degrees" to 7,
            "behavior.resolution-degrees" to 0.7, "behavior.resolution-degrees" to 0,
            "behavior.active-within-degrees" to 23, "styles.active.color" to "red",
            "content.directions.N" to "N\nS", "content.directions.N" to "&aN",
            "content.directions.N" to "北", "content.scale" to "----"
        )
        for ((path, value) in invalid) assertFailsWith<IllegalArgumentException>(path) { CompassConfig.read(config(path to value)) }
    }
    @Test fun `styles and custom glyph metrics retain original behavior`() {
        val configuration = config("styles.active.italic" to true, "styles.active.underline" to true, "styles.active.strikethrough" to true)
        val renderer = CompassRenderer(CompassConfig.read(configuration))
        assertTrue("§l§o§n§m" in renderer.atYaw(180f))
        val font = FontMetrics(configuration)
        assertEquals(0, font.advance(0x200c, Style("", false, false, false, false)))
        assertEquals(1, font.advance(0x200c, Style("", true, false, false, false)))
        val custom = config("font.advances" to mapOf("北" to 9), "content.directions.N" to "北")
        assertTrue("北" in CompassRenderer(CompassConfig.read(custom)).atYaw(180f))
    }
    @Test fun `enabled defaults support disabling and reload applies independent immutable snapshots`() {
        val folder = Files.createTempDirectory("simplescore-compass").toFile()
        try {
            val initial = Compass.load(folder)
            assertTrue(initial.enabled)
            assertTrue("[N]" in initial.atYaw(180f))
            val file = folder.resolve("compass.yml")
            assertTrue(file.exists())
            file.writeText("enabled: false\n")
            val disabled = Compass.load(folder)
            assertFalse(disabled.enabled)
            assertEquals("", disabled.atYaw(180f))
            file.writeText("enabled: true\nlayout: {width-pixels: 90}\n")
            val enabled = Compass.load(folder)
            assertTrue(enabled.enabled)
            assertTrue("[N]" in enabled.atYaw(180f))
            assertEquals(90, measured(enabled.atYaw(180f), FontMetrics(config())))
            file.writeText("enabled: false\nlayout: {width-pixels: -1}\n")
            assertFalse(Compass.load(folder).enabled)
            assertTrue("[N]" in enabled.atYaw(180f))
            file.writeText("enabled: true\nlayout: {width-pixels: -1}\n")
            assertFailsWith<IllegalArgumentException> { Compass.load(folder) }
            assertTrue(enabled.enabled)
            file.writeText("enabled: invalid\n")
            assertFailsWith<IllegalArgumentException> { Compass.load(folder) }
        } finally { folder.deleteRecursively() }
    }
}
