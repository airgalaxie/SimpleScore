package com.r4g3baby.simplescore.fabric.compass;

// Adapted from Airgalaxie's CompassExpansion; renderer and pixel layout retained.

import java.util.HashMap;
import java.util.Map;
import com.r4g3baby.simplescore.fabric.config.ConfigurationSection;

/** Vanilla default ASCII advances (glyph width + one pixel tracking). */
final class FontMetrics {
    private final Map<Integer, Integer> advances = new HashMap<>();
    FontMetrics(ConfigurationSection config) {
        for (int c = 32; c <= 126; c++) advances.put(c, 6);
        set(" ", 4); set("!.,:;i|l'", 2); set("`", 3); set("It[](){}*\"", 4);
        set("<>fk", 5); set("@~", 7);
        advances.put(0x200c, 0);
        ConfigurationSection overrides = config.getConfigurationSection("font.advances");
        if (overrides != null) for (String key : overrides.getKeys(false)) {
            if (key.codePointCount(0, key.length()) != 1) throw new IllegalArgumentException("Font key must be one character: " + key);
            if (!(overrides.get(key) instanceof Number number) || !Double.isFinite(number.doubleValue())
                    || number.doubleValue() != Math.rint(number.doubleValue()))
                throw new IllegalArgumentException("Font advance must be an integer: " + key);
            int value = number.intValue();
            if (number.doubleValue() < 1 || number.doubleValue() > 32) throw new IllegalArgumentException("Font advance must be 1..32: " + key);
            advances.put(key.codePointAt(0), value);
        }
    }
    private void set(String chars, int width) { chars.codePoints().forEach(c -> advances.put(c, width)); }
    int advance(int codePoint, Style style) {
        Integer width = advances.get(codePoint);
        if (width == null) throw new IllegalArgumentException("No font advance for U+" + Integer.toHexString(codePoint));
        return width + (style.bold() ? 1 : 0);
    }
    int width(String text, Style style) { return text.codePoints().map(c -> advance(c, style)).sum(); }
}
