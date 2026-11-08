package com.r4g3baby.simplescore.fabric.compass;

// Adapted from Airgalaxie's CompassExpansion; renderer and pixel layout retained.

import com.r4g3baby.simplescore.fabric.config.ConfigurationSection;

/** A deliberately small legacy-text style; formatting is never content. */
record Style(String color, boolean bold, boolean italic, boolean underline, boolean strike) {
    static Style read(ConfigurationSection root, String path) {
        String color = root.getString(path + ".color", "&f");
        if (color.matches("#[0-9a-fA-F]{6}")) {
            StringBuilder hex = new StringBuilder("§x");
            for (char c : color.substring(1).toLowerCase(java.util.Locale.ROOT).toCharArray()) hex.append('§').append(c);
            color = hex.toString();
        } else if (color.matches("&[0-9a-fA-F]")) {
            color = "§" + color.substring(1).toLowerCase(java.util.Locale.ROOT);
        } else throw new IllegalArgumentException(path + ".color: expected &0..&f or #RRGGBB");
        return new Style(color, root.getBoolean(path + ".bold"), root.getBoolean(path + ".italic"),
                root.getBoolean(path + ".underline"), root.getBoolean(path + ".strikethrough"));
    }
    String prefix() {
        return "§r" + color + (bold ? "§l" : "") + (italic ? "§o" : "")
                + (underline ? "§n" : "") + (strike ? "§m" : "");
    }
}
