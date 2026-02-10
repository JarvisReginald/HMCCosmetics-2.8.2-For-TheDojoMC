package com.hibiscusmc.hmccosmetics.gui.type.types;

import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticHolder;
import com.hibiscusmc.hmccosmetics.gui.action.Actions;
import com.hibiscusmc.hmccosmetics.gui.type.Type;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import me.lojosho.hibiscuscommons.config.serializer.ItemSerializer;
import me.lojosho.shaded.configurate.ConfigurationNode;
import me.lojosho.shaded.configurate.serialize.SerializationException;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TypePageSwitcher extends Type {

    public TypePageSwitcher() {
        super("page-switcher");
    }

    @Override
    public void run(Player viewer, CosmeticHolder cosmeticHolder, @NotNull ConfigurationNode config, ClickType clickType) {
        // Run configured actions (sounds, commands, etc.)
        List<String> actionStrings = new ArrayList<>();
        ConfigurationNode actionConfig = config.node("actions");

        try {
            if (!actionConfig.node("any").virtual()) actionStrings.addAll(actionConfig.node("any").getList(String.class));

            if (clickType != null) {
                if (clickType.isLeftClick()) {
                    if (!actionConfig.node("left-click").virtual()) actionStrings.addAll(actionConfig.node("left-click").getList(String.class));
                }
                if (clickType.isRightClick()) {
                    if (!actionConfig.node("right-click").virtual()) actionStrings.addAll(actionConfig.node("right-click").getList(String.class));
                }
                if (clickType.equals(ClickType.SHIFT_LEFT)) {
                    if (!actionConfig.node("shift-left-click").virtual()) actionStrings.addAll(actionConfig.node("shift-left-click").getList(String.class));
                }
                if (clickType.equals(ClickType.SHIFT_RIGHT)) {
                    if (!actionConfig.node("shift-right-click").virtual()) actionStrings.addAll(actionConfig.node("shift-right-click").getList(String.class));
                }
            }

            Actions.runActions(viewer, cosmeticHolder, actionStrings);
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
        // Page advancement is handled by Menu directly in the click handler
    }

    @Override
    public void run(CosmeticUser user, ConfigurationNode config, ClickType clickType) {
        final var player = user.getPlayer();
        if (player == null) return;
        run(player, user, config, clickType);
    }

    @Override
    public ItemStack setItem(CosmeticUser user, ConfigurationNode config, ItemStack itemStack, int slot) {
        return setItem(user.getPlayer(), user, config, itemStack, slot);
    }

    @Override
    public ItemStack setItem(Player viewer, CosmeticHolder cosmeticHolder, ConfigurationNode config, @NotNull ItemStack itemStack, int slot) {
        // Read page info injected by Menu
        int currentPage = config.node("__current_page").getInt(1);
        int totalPages = config.node("__total_pages").getInt(1);

        // Choose which config node to use based on page count
        String nodeKey = totalPages <= 1 ? "single-page" : "multi-page";
        ConfigurationNode displayNode = config.node(nodeKey);

        if (displayNode.virtual()) {
            // Fallback: process the base item
            if (itemStack.hasItemMeta()) {
                itemStack.setItemMeta(processItemMeta(viewer, itemStack.getItemMeta()));
            }
            return itemStack;
        }

        // Deserialize the item from the chosen node
        ItemStack result;
        try {
            result = ItemSerializer.INSTANCE.deserialize(ItemStack.class, displayNode);
        } catch (SerializationException e) {
            return itemStack;
        }
        if (result == null || result.getType() == Material.AIR) {
            return itemStack;
        }

        // Replace page placeholders in name and lore
        ItemMeta meta = result.getItemMeta();
        if (meta != null) {
            meta = processItemMeta(viewer, meta);
            
            if (meta.hasDisplayName()) {
                String name = meta.getDisplayName();
                name = name.replace("<current_page>", String.valueOf(currentPage));
                name = name.replace("<total_pages>", String.valueOf(totalPages));
                meta.setDisplayName(name);
            }

            if (meta.hasLore() && meta.getLore() != null) {
                List<String> lore = meta.getLore();
                List<String> processedLore = new ArrayList<>();
                for (String line : lore) {
                    line = line.replace("<current_page>", String.valueOf(currentPage));
                    line = line.replace("<total_pages>", String.valueOf(totalPages));
                    processedLore.add(line);
                }
                meta.setLore(processedLore);
            }

            result.setItemMeta(meta);
        }

        return result;
    }
}
