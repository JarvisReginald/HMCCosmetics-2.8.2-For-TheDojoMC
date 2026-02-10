package com.hibiscusmc.hmccosmetics.gui.util;

import dev.lone.itemsadder.api.CustomStack;
import me.lojosho.hibiscuscommons.config.serializer.ItemSerializer;
import me.lojosho.hibiscuscommons.hooks.Hooks;
import me.lojosho.hibiscuscommons.util.AdventureUtils;
import me.lojosho.shaded.configurate.ConfigurationNode;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MenuItemFormatUtil {

    private MenuItemFormatUtil() {}

    public static ItemStack buildItem(Player viewer, ConfigurationNode itemNode) {
        if (itemNode == null || itemNode.virtual()) return ItemStack.empty();

        ItemStack item = resolveConfigItem(itemNode);
        if (item == null || item.getType().isAir()) return ItemStack.empty();

        // Apply placeholders + minimessage + <!italic>
        return applyViewerFormatting(viewer, item);
    }

    /** Come nello StoreMenu: supporta material ItemsAdder e fallback serializer */
    private static ItemStack resolveConfigItem(ConfigurationNode itemNode) {
        String mat = itemNode.node("material").getString();
        if (mat == null || mat.isBlank()) return null;
        mat = mat.trim();

        if (isItemsAdderPresent() && mat.contains(":") && !mat.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            ItemStack ia = resolveItemsAdderCustomStack(mat);

            if ((ia == null || ia.getType().isAir()) && mat.contains(":")) {
                String noNs = mat.split(":", 2)[1];
                ia = resolveItemsAdderCustomStack(noNs);
            }

            if (ia != null && !ia.getType().isAir()) {
                applyBasicMetaFromNodeLegacy(ia, itemNode);
                return ia;
            }
            return null;
        }

        // Vanilla / ItemSerializer
        try {
            ItemStack item = ItemSerializer.INSTANCE.deserialize(ItemStack.class, itemNode);
            if (item == null) return null;

            applyBasicMetaFromNodeLegacy(item, itemNode);

            return item;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static ItemStack resolveItemsAdderCustomStack(String id) {
        try {
            CustomStack cs = CustomStack.getInstance(id);
            if (cs == null) return null;
            ItemStack stack = cs.getItemStack();
            return stack == null ? null : stack.clone();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean isItemsAdderPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    /**
     * Supporta sia `name` che `display-name`.
     * Applica name/lore come legacy strings (con &), poi li “upgradeiamo” con MiniMessage nel pass successivo.
     */
    private static void applyBasicMetaFromNodeLegacy(ItemStack base, ConfigurationNode itemNode) {
        if (base == null) return;

        int amount = itemNode.node("amount").getInt(base.getAmount());
        if (amount > 0) base.setAmount(amount);

        ItemMeta meta = base.getItemMeta();
        if (meta == null) return;

        // name / display-name
        String name = firstString(itemNode, "name", "display-name");
        if (name != null && !name.isBlank()) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }

        // lore
        ConfigurationNode loreNode = itemNode.node("lore");
        if (!loreNode.virtual()) {
            try {
                List<String> lore = loreNode.getList(String.class);
                if (lore != null) {
                    List<String> out = new ArrayList<>(lore.size());
                    for (String line : lore) out.add(ChatColor.translateAlternateColorCodes('&', line));
                    meta.setLore(out);
                }
            } catch (Exception ignored) {}
        }

        base.setItemMeta(meta);
    }

    private static String firstString(ConfigurationNode node, String... keys) {
        for (String k : keys) {
            ConfigurationNode n = node.node(k);
            if (!n.virtual()) {
                String s = n.getString();
                if (s != null) return s;
            }
        }
        return null;
    }

    /** Placeholder + legacy->Mini + <!italic> su name e lore */
    private static ItemStack applyViewerFormatting(Player viewer, ItemStack item) {
        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        if (meta.hasDisplayName()) {
            String raw = meta.getDisplayName();
            raw = Hooks.processPlaceholders(viewer, raw);
            raw = legacyToMini(raw);
            raw = raw.replace("<reset>", "<reset><!italic>");
            meta.displayName(AdventureUtils.MINI_MESSAGE.deserialize("<!italic>" + raw));
        }

        List<String> legacyLore = meta.getLore();
        if (legacyLore != null && !legacyLore.isEmpty()) {
            List<Component> lore = new ArrayList<>(legacyLore.size());
            for (String line : legacyLore) {
                String s = Hooks.processPlaceholders(viewer, line);
                s = legacyToMini(s);
                s = s.replace("<reset>", "<reset><!italic>");
                lore.add(AdventureUtils.MINI_MESSAGE.deserialize("<!italic>" + s));
            }
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private static String legacyToMini(String s) {
        if (s == null) return null;
        s = s.replace('§', '&');
        return legacyAmpersandToMini(s);
    }

    /** Conversione & -> MiniMessage (presa dallo stile StoreMenu) */
    private static String legacyAmpersandToMini(String s) {
        if (s.indexOf('&') == -1) return s;

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
                out.append(c);
            }
        }

        return out.toString();
    }
}
