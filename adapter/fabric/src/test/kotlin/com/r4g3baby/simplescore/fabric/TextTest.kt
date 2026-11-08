package com.r4g3baby.simplescore.fabric

import com.r4g3baby.simplescore.fabric.scoreboard.Text
import net.minecraft.SharedConstants
import net.minecraft.server.Bootstrap
import net.minecraft.network.chat.FormattedText
import net.minecraft.network.chat.Style
import java.util.Optional
import kotlin.test.*
import org.junit.BeforeClass

class TextTest {
    companion object {
        @BeforeClass @JvmStatic fun bootstrap() {
            SharedConstants.tryDetectVersion()
            Bootstrap.bootStrap()
        }
    }
    @Test fun `mixed legacy MiniMessage and RGB formatting renders as styled native text`() {
        val component = Text.component("&aGreen <bold>bold</bold> &#123456RGB")
        assertEquals("Green bold RGB", component.string)
        val runs = mutableListOf<Pair<Style, String>>()
        component.visitUnbounded(FormattedText.StyledContentConsumer<Void> { style, text -> runs += style to text; Optional.empty() }, Style.EMPTY)
        assertTrue(runs.any { (style, text) -> "bold" in text && style.isBold })
        assertTrue(runs.any { (style, text) -> "RGB" in text && style.color?.value == 0x123456 })
    }
    @Test fun `Patbox native components retain text colors and decorations through replacement`() {
        val original = Text.component("<red><bold>Red</bold></red> <#123456>RGB</#123456>")
        val restored = Text.component(Text.legacy(original))
        assertEquals(original.string, restored.string)
        assertEquals(Text.legacy(original), Text.legacy(restored))
    }
}
