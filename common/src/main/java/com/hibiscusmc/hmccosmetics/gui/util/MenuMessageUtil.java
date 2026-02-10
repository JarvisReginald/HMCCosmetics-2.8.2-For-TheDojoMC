package com.hibiscusmc.hmccosmetics.gui.util;

import me.lojosho.hibiscuscommons.hooks.Hooks;
import me.lojosho.hibiscuscommons.util.AdventureUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

public final class MenuMessageUtil {
    private MenuMessageUtil() {}

    public static Component format(Player viewer, String raw) {
        if (raw == null) raw = "";

        // PlaceholderAPI/Hook placeholders
        raw = Hooks.processPlaceholders(viewer, raw);

        raw = legacyAmpersandToMini(raw);

        // After <reset>, italic defaults back on in lore, so re-disable it after every reset
        raw = raw.replace("<reset>", "<reset><!italic>");
        return AdventureUtils.MINI_MESSAGE.deserialize("<!italic>" + raw);
    }

    public static String replace(String raw, int slot, int count) {
        if (raw == null) return "";
        return raw.replace("<slot>", String.valueOf(slot))
                .replace("<count>", String.valueOf(count));
    }

    private static String legacyAmpersandToMini(String s) {
        if (s == null || s.indexOf('&') == -1) return s;

        StringBuilder out = new StringBuilder(s.length() + 16);

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '&' && i + 1 < s.length() && s.charAt(i + 1) == '&') {
                out.append('&');
                i++;
                continue;
            }

            if (c != '&' || i + 1 >= s.length()) {
                out.append(c);
                continue;
            }

            char n = s.charAt(i + 1);

            // &#RRGGBB
            if (n == '#' && i + 7 < s.length()) {
                String hex = s.substring(i + 2, i + 8);
                if (hex.matches("(?i)[0-9a-f]{6}")) {
                    out.append("<#").append(hex).append(">");
                    i += 7;
                    continue;
                }
            }

            String tag = switch (Character.toLowerCase(n)) {
                case '0' -> "<black>";
                case '1' -> "<dark_blue>";
                case '2' -> "<dark_green>";
                case '3' -> "<dark_aqua>";
                case '4' -> "<dark_red>";
                case '5' -> "<dark_purple>";
                case '6' -> "<gold>";
                case '7' -> "<gray>";
                case '8' -> "<dark_gray>";
                case '9' -> "<blue>";
                case 'a' -> "<green>";
                case 'b' -> "<aqua>";
                case 'c' -> "<red>";
                case 'd' -> "<light_purple>";
                case 'e' -> "<yellow>";
                case 'f' -> "<white>";
                case 'l' -> "<bold>";
                case 'm' -> "<strikethrough>";
                case 'n' -> "<underlined>";
                case 'o' -> "<italic>";
                case 'r' -> "<reset>";
                default -> null;
            };

            if (tag != null) {
                out.append(tag);
                i++;
            } else {
                out.append('&');
            }
        }

        return out.toString();
    }
}
