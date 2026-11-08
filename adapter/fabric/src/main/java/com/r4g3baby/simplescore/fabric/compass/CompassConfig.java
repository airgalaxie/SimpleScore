package com.r4g3baby.simplescore.fabric.compass;

// Adapted from Airgalaxie's CompassExpansion; renderer and pixel layout retained.

import java.util.List;
import com.r4g3baby.simplescore.fabric.config.ConfigurationSection;

public record CompassConfig(List<String> directions, String scale, int width, int spacing, int tickDegrees,
                     int margin, int frames, boolean highlight, double activeWithin,
                     Style normal, Style active, Style ticks, FontMetrics font) {
    static final List<String> NAMES = List.of("N", "NE", "E", "SE", "S", "SW", "W", "NW");
    public static CompassConfig read(ConfigurationSection c) {
        Style normal = Style.read(c, "styles.direction"), active = Style.read(c, "styles.active"), ticks = Style.read(c, "styles.scale");
        FontMetrics font = new FontMetrics(c);
        List<String> labels = NAMES.stream().map(n -> text(c.getString("content.directions." + n, n))).toList();
        String scale = text(c.getString("content.scale", "|"));
        int width = integer(c, "layout.width-pixels", 160, 40, 512);
        int spacing = integer(c, "layout.pixels-per-45-degrees", 64, 24, 256);
        int tick = integer(c, "layout.scale-every-degrees", 5, 1, 45);
        int margin = integer(c, "layout.label-margin-pixels", 6, 0, 16);
        if (360 % tick != 0) throw new IllegalArgumentException("scale-every-degrees must divide 360");
        double resolution = c.getDouble("behavior.resolution-degrees", 0.5);
        double count = 360 / resolution;
        if (!Double.isFinite(count) || count < 8 || count > 1440 || Math.abs(count - Math.rint(count)) > 0.000001)
            throw new IllegalArgumentException("resolution-degrees must divide 360 and be between 0.25 and 45");
        double range = c.getDouble("behavior.active-within-degrees", 15);
        if (!Double.isFinite(range) || range < 0 || range > 22.5) throw new IllegalArgumentException("active-within-degrees must be 0..22.5");
        for (String label : labels) {
            int max = Math.max(font.width(label, normal), font.width(label, active));
            if (max + 2 * margin > spacing) throw new IllegalArgumentException("Direction label exceeds its fixed angle slot: " + label);
        }
        if (font.width(scale, ticks) > spacing * tick / 45.0) throw new IllegalArgumentException("Scale text overlaps adjacent ticks");
        return new CompassConfig(labels, scale, width, spacing, tick, margin, (int)Math.rint(count),
                c.getBoolean("behavior.highlight", true), range, normal, active, ticks, font);
    }
    private static int integer(ConfigurationSection c, String key, int fallback, int min, int max) {
        double number = c.getDouble(key, fallback);
        if (!Double.isFinite(number) || number != Math.rint(number) || number < min || number > max)
            throw new IllegalArgumentException(key + " must be an integer in " + min + ".." + max);
        return (int)number;
    }
    private static String text(String text) {
        if (text.codePointCount(0, text.length()) > 32 || text.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.LINE_SEPARATOR
                || Character.getType(c) == Character.PARAGRAPH_SEPARATOR || c == '§' || c == '&'))
            throw new IllegalArgumentException("Content must be one line, max 32 characters, without formatting codes");
        return text;
    }
}
