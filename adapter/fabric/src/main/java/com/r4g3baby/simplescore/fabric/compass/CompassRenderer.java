package com.r4g3baby.simplescore.fabric.compass;

// Adapted from Airgalaxie's CompassExpansion; renderer and pixel layout retained.

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Immutable, eagerly rendered frames. The hot path normalizes yaw and indexes an array. */
public final class CompassRenderer {
    private record Glyph(int x, int codePoint, Style style, int advance) {}
    private final CompassConfig config;
    private final String[] cache;
    public CompassRenderer(CompassConfig config) {
        this.config = config;
        cache = new String[config.frames()];
        for (int i = 0; i < cache.length; i++) cache[i] = render(i * 360.0 / cache.length);
    }
    public String atYaw(float yaw) {
        if (!Float.isFinite(yaw)) return "";
        double normalized = ((double)yaw % 360 + 360) % 360;
        int index = (int)Math.round(normalized * cache.length / 360) % cache.length;
        return cache[index];
    }
    static double bearing(double yaw) { return (yaw + 180) % 360; }
    static double distance(double a, double b) { return Math.abs(((a - b + 540) % 360) - 180); }
    private String render(double yaw) {
        double bearing = bearing(yaw);
        int circumference = config.spacing() * 8;
        int camera = (int)Math.round(bearing / 360 * circumference);
        List<Glyph> glyphs = new ArrayList<>();
        int[] reserved = new int[8];
        for (int i = 0; i < 8; i++) reserved[i] = Math.max(config.font().width(config.directions().get(i), config.normal()),
                config.font().width(config.directions().get(i), config.active()));
        // Three neighboring revolutions also cover viewports wider than one revolution.
        int repeats = config.width() / circumference + 2;
        for (int turn = -repeats; turn <= repeats; turn++) {
            for (int i = 0; i < 8; i++) {
                Style style = config.highlight() && distance(bearing, i * 45) <= config.activeWithin() ? config.active() : config.normal();
                int anchor = turn * circumference + i * config.spacing() - camera + config.width() / 2;
                add(glyphs, config.directions().get(i), style, anchor);
            }
            for (int degree = 0; degree < 360; degree += config.tickDegrees()) {
                int nearest = ((int)Math.round(degree / 45.0)) % 8;
                double deltaPixels = distance(degree, nearest * 45) * config.spacing() / 45;
                double occupied = reserved[nearest] / 2.0 + config.margin() + config.font().width(config.scale(), config.ticks()) / 2.0;
                if (deltaPixels <= occupied) continue;
                int anchor = turn * circumference + (int)Math.round(degree * circumference / 360.0) - camera + config.width() / 2;
                add(glyphs, config.scale(), config.ticks(), anchor);
            }
        }
        glyphs.sort(Comparator.comparingInt(Glyph::x));
        StringBuilder out = new StringBuilder();
        int cursor = 0;
        Style last = null;
        for (Glyph glyph : glyphs) {
            if (glyph.x() < 0 || glyph.x() + glyph.advance() > config.width()) continue;
            if (glyph.x() < cursor) continue;
            int gap = glyph.x() - cursor;
            if (gap > 0) { pad(out, gap); last = null; }
            if (!glyph.style().equals(last)) { out.append(glyph.style().prefix()); last = glyph.style(); }
            out.appendCodePoint(glyph.codePoint());
            cursor = glyph.x() + glyph.advance();
        }
        pad(out, config.width() - cursor);
        out.append("§r");
        return out.toString();
    }
    private void pad(StringBuilder out, int pixels) {
        if (pixels == 0) return;
        // Vanilla's space provider: space advances 4px; U+200C advances 0px.
        // Bold adds 1px even to an empty glyph, giving invisible 1px remainders.
        int space = config.font().advance(' ', new Style("", false, false, false, false));
        out.append("§r").append(" ".repeat(pixels / space));
        if (pixels % space != 0) out.append("§l").append("\u200c".repeat(pixels % space));
    }
    private void add(List<Glyph> list, String text, Style style, int center) {
        int x = center - config.font().width(text, style) / 2;
        for (int codePoint : text.codePoints().toArray()) {
            int advance = config.font().advance(codePoint, style);
            list.add(new Glyph(x, codePoint, style, advance));
            x += advance;
        }
    }
}
