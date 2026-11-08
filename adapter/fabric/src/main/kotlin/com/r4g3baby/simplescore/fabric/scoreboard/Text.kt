package com.r4g3baby.simplescore.fabric.scoreboard

import com.r4g3baby.simplescore.core.util.translateColorCodes
import net.kyori.adventure.text.Component as AdventureComponent
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.MutableComponent
import net.minecraft.network.chat.Style
import net.minecraft.network.chat.TextColor

object Text {
    private const val COLOR_CHAR = '§'
    private val legacy = LegacyComponentSerializer.builder().character('§').hexColors().useUnusualXRepeatedCharacterHexFormat().build()
    fun component(input: String): Component {
        val text = translateColorCodes(input)
        val parsed = MiniMessage.miniMessage().deserialize(vanillaToMini(text))
        return convert(parsed)
    }
    fun legacy(component: Component): String = buildString {
        component.visitUnbounded(net.minecraft.network.chat.FormattedText.StyledContentConsumer<Void> { style, text ->
            append("§r")
            style.color?.let { color ->
                append("§x")
                "%06x".format(color.value).forEach { append('§').append(it) }
            }
            if (style.isBold) append("§l")
            if (style.isItalic) append("§o")
            if (style.isUnderlined) append("§n")
            if (style.isStrikethrough) append("§m")
            if (style.isObfuscated) append("§k")
            append(text)
            java.util.Optional.empty()
        }, Style.EMPTY)
    }
    private fun convert(input: AdventureComponent): MutableComponent {
        // Legacy serialization preserves the styles of every child, including RGB and decorations.
        val serialized = legacy.serialize(input)
        val root = Component.empty()
        val buffer = StringBuilder()
        var style = Style.EMPTY
        fun flush() { if (buffer.isNotEmpty()) { root.append(Component.literal(buffer.toString()).setStyle(style)); buffer.setLength(0) } }
        var i = 0
        while (i < serialized.length) {
            if (serialized[i] == '§' && i + 1 < serialized.length) {
                flush()
                val code = serialized[i + 1]
                if (code == 'x' && i + 13 < serialized.length) {
                    val hex = (0..5).map { serialized[i + 3 + it * 2] }.joinToString("").toIntOrNull(16)
                    if (hex != null) { style = Style.EMPTY.withColor(TextColor.fromRgb(hex)); i += 14; continue }
                }
                val formatting = ChatFormatting.getByCode(code)
                if (formatting != null) style = if (formatting == ChatFormatting.RESET) Style.EMPTY else if (code.lowercaseChar() in "0123456789abcdef") Style.EMPTY.applyFormat(formatting) else style.applyFormat(formatting)
                i += 2
            } else buffer.append(serialized[i++])
        }
        flush()
        return root
    }

    private val namedToMiniTag = mapOf(
        '0' to "black", '1' to "dark_blue", '2' to "dark_green", '3' to "dark_aqua", '4' to "dark_red",
        '5' to "dark_purple", '6' to "gold", '7' to "gray", '8' to "dark_gray", '9' to "blue",
        'a' to "green", 'b' to "aqua", 'c' to "red", 'd' to "light_purple", 'e' to "yellow", 'f' to "white",
        'k' to "obf", 'l' to "b", 'm' to "st", 'n' to "u", 'o' to "i", 'r' to "reset"
    )

    private fun vanillaToMini(text: String): String {
        if (text.length <= 1) return text
        val result = StringBuilder()

        var i = 0
        while (i < text.length) {
            val char = text[i]

            // Handle the color character (COLOR_CHAR aka §)
            if (char == COLOR_CHAR && i + 1 < text.length) {
                val nextChar = text[i + 1].lowercaseChar()

                // Hex color detection (§x§F§F§F§F§F§F → <#FFFFFF>)
                if (nextChar == 'x' && i + 14 <= text.length) {
                    val hexCode = StringBuilder()
                    var validHex = true

                    for (j in 3..13 step 2) { // Read the 6 hex digits
                        val char = text[i + j].lowercaseChar()
                        if (char in '0'..'9' || char in 'a'..'f') {
                            hexCode.append(text[i + j])
                        } else {
                            validHex = false
                            break
                        }
                    }

                    if (validHex) {
                        result.append("<#${hexCode}>")

                        i += 14 // Skip over the processed hex code
                        continue
                    }
                }

                // Named color code detection (§a → <green>)
                val tagName = namedToMiniTag[nextChar]
                if (tagName != null) {
                    result.append("<$tagName>")

                    i += 2 // Skip over the processed named code
                    continue
                }
            }

            // Append the current character if no formatting was detected
            result.append(char)
            i++ // And skip to the next character
        }

        return result.toString()
    }
}
