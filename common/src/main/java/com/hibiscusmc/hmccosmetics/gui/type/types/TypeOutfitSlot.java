package com.hibiscusmc.hmccosmetics.gui.type.types;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.api.HMCCosmeticsAPI;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticHolder;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetics;
import com.hibiscusmc.hmccosmetics.gui.Menu;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.gui.type.Type;
import com.hibiscusmc.hmccosmetics.gui.util.MenuItemFormatUtil;
import com.hibiscusmc.hmccosmetics.gui.util.MenuMessageUtil;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import me.lojosho.shaded.configurate.ConfigurationNode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class TypeOutfitSlot extends Type {

    private final HMCCosmeticsPlugin plugin = HMCCosmeticsPlugin.getInstance();

    public TypeOutfitSlot(String id) {
        super(id);
    }

    public TypeOutfitSlot() {
        super("outfit_slot");
    }

    private boolean isAllowEmptyOutfits() {
        Menu menu = Menus.getMenu("dojo_outfits");
        if (menu == null || menu.getConfig() == null) return true;
        return menu.getConfig().node("allow-empty-outfits").getBoolean(true);
    }

    private String msgRaw(String key, String def) {
        Menu menu = Menus.getMenu("dojo_outfits");
        if (menu == null || menu.getConfig() == null) return def;

        String raw = menu.getConfig().node("messages", key).getString(def);
        return (raw == null || raw.isBlank()) ? def : raw;
    }

    private void sendMsg(Player p, String key, String def, int slot, int count) {
        String raw = msgRaw(key, def);
        raw = MenuMessageUtil.replace(raw, slot, count);
        p.sendMessage(MenuMessageUtil.format(p, raw));
    }

    private void playSound(Player p, String key) {
        Menu menu = Menus.getMenu("dojo_outfits");
        if (menu == null || menu.getConfig() == null) return;
        String sound = menu.getConfig().node("sounds", key).getString("");
        if (sound != null && !sound.isEmpty()) {
            p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
        }
    }

    @Override
    public void run(Player viewer, CosmeticHolder holder, ConfigurationNode config, ClickType click) {
        if (holder == null) return;

        int outfitSlot = config.node("outfit-slot").getInt(1);

        var storage = plugin.getOutfitsStorage();
        UUID uuid = viewer.getUniqueId();

        String perm = config.node("permission").getString("");
        if (perm != null && !perm.isBlank() && !viewer.hasPermission(perm) && !viewer.isOp()) {
            return;
        }

        boolean hasThis = storage.hasSlot(uuid, outfitSlot);
        int firstFree = getFirstFreeOutfitSlot(viewer);

        if (!hasThis) {
            if (outfitSlot != firstFree) return;

            if (click.isRightClick()) {
                playSound(viewer, "outfit-custom-name");
                String command = "/cosmetic addoutfit ";
                String rawMsg = msgRaw("add_outfit_custom_message",
                        "&eTo add an outfit with a custom name: &f/cosmetic addoutfit <name>");
                rawMsg = MenuMessageUtil.replace(rawMsg, outfitSlot, 0);
                Component message = MenuMessageUtil.format(viewer, rawMsg)
                        .clickEvent(ClickEvent.suggestCommand(command))
                        .hoverEvent(HoverEvent.showText(MenuMessageUtil.format(viewer, "&7Click to suggest")));
                viewer.sendMessage(message);
                viewer.closeInventory();
                return;
            }

            if (click.isLeftClick()) {
                CosmeticUser cosmeticUser = CosmeticUsers.getUser(viewer);
                if (cosmeticUser == null) return;

                var cosmetics = storage.getAllCurrentCosmetics(cosmeticUser);
                int count = (cosmetics == null ? 0 : cosmetics.size());

                if (!isAllowEmptyOutfits() && count == 0) {
                    playSound(viewer, "error");
                    sendMsg(viewer, "cannot_save_empty",
                            "&cYou can't save an empty outfit.",
                            outfitSlot, 0);
                    return;
                }

                if (cosmetics == null) cosmetics = Collections.emptyList();
                storage.setOutfitSlot(uuid, outfitSlot, cosmetics);
                ensureOutfitName(uuid, outfitSlot);

                playSound(viewer, "outfit-saved");
                sendMsg(viewer, "outfit_saved",
                        "&aOutfit saved in slot &f<slot>&a.",
                        outfitSlot, 0);
            }
            return;
        }

        if (click.isRightClick()) {
            storage.deleteOutfitSlot(uuid, outfitSlot);
            storage.deleteOutfitName(uuid, outfitSlot);
            storage.compactSlots(uuid);

            playSound(viewer, "outfit-deleted");
            sendMsg(viewer, "outfit_deleted",
                    "&cOutfit deleted from slot &f<slot>&c.",
                    outfitSlot, 0);

            return;
        }

        if (click.isLeftClick()) {
            CosmeticUser user = CosmeticUsers.getUser(viewer);
            if (user == null) return;

            List<String> ids = storage.getOutfitSlot(uuid, outfitSlot);
            int equipped = 0;

            for (String id : ids) {
                if (id == null || id.isBlank()) continue;
                Cosmetic cosmetic = Cosmetics.getCosmetic(id);
                if (cosmetic == null) continue;

                HMCCosmeticsAPI.equipCosmetic(user, cosmetic);
                equipped++;
            }

            if (equipped > 0) {
                playSound(viewer, "outfit-equipped");
                sendMsg(viewer, "outfit_equipped",
                        "&bOutfit equipped (&f<count>&b items).",
                        outfitSlot, equipped);
            } else {
                playSound(viewer, "error");
                sendMsg(viewer, "outfit_equipped_empty",
                        "&eNo valid cosmetics found in this outfit.",
                        outfitSlot, 0);
            }
        }
    }

    @Override
    public void run(CosmeticUser user, @NotNull ConfigurationNode config, ClickType clickType) {
        run(user.getPlayer(), user, config, clickType);
    }

    @Override
    public ItemStack setItem(CosmeticUser user, ConfigurationNode config, ItemStack itemStack, int slot) {
        return setItem(user.getPlayer(), user, config, itemStack, slot);
    }

    @Override
    public @NotNull ItemStack setItem(Player viewer, CosmeticHolder holder, ConfigurationNode config, ItemStack itemStack, int slot) {
        if (holder == null) return ItemStack.empty();

        int outfitSlot = config.node("outfit-slot").getInt(1);

        String perm = config.node("permission").getString("");
        if (perm != null && !perm.isBlank() && !viewer.hasPermission(perm) && !viewer.isOp()) {
            return MenuItemFormatUtil.buildItem(viewer, config.node("__locked_outfit_item"));
        }

        var storage = plugin.getOutfitsStorage();
        UUID uuid = viewer.getUniqueId();

        boolean hasThis = storage.hasSlot(uuid, outfitSlot);

        if (hasThis) {
            ConfigurationNode outfitNode = config.node("__outfit_item");

            String savedName = storage.getOutfitName(uuid, outfitSlot);
            if (savedName == null || savedName.isBlank()) savedName = getDefaultOutfitName(outfitSlot);

            return buildOutfitDisplayItem(viewer, outfitNode, savedName);
        }

        int firstFree = getFirstFreeOutfitSlot(viewer);
        if (outfitSlot == firstFree) {
            return MenuItemFormatUtil.buildItem(viewer, config.node("__add_outfit_item"));
        }

        return ItemStack.empty();
    }

    private ItemStack buildOutfitDisplayItem(Player viewer, ConfigurationNode outfitNode, String outfitName) {
        ItemStack item = MenuItemFormatUtil.buildItem(viewer, outfitNode);
        if (item == null || item.getType().isAir()) return ItemStack.empty();

        var meta = item.getItemMeta();
        if (meta == null) return item;

        String dn = outfitNode.node("display-name").getString("");
        if (dn == null) dn = outfitNode.node("name").getString("");

        if (dn != null) {
            dn = dn.replace("{outfit_name}", outfitName).replace("<outfit_name>", outfitName);
            meta.displayName(MenuMessageUtil.format(viewer, dn));
        }

        try {
            List<String> loreLines = outfitNode.node("lore").getList(String.class);
            if (loreLines != null && !loreLines.isEmpty()) {
                List<Component> lore = new ArrayList<>(loreLines.size());
                for (String line : loreLines) {
                    if (line == null) continue;
                    line = line.replace("{outfit_name}", outfitName).replace("<outfit_name>", outfitName);
                    lore.add(MenuMessageUtil.format(viewer, line));
                }
                meta.lore(lore);
            }
        } catch (Exception ignored) {}

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Trova il PRIMO slot libero tra gli outfit_slot definiti nel menu dojo_outfits,
     * considerando anche permessi (slot “locked” non conta come libero).
     * Ritorna -1 se non c'è nessuno slot libero.
     */
    private int getFirstFreeOutfitSlot(Player viewer) {
        Menu menu = Menus.getMenu("dojo_outfits");
        if (menu == null) return -1;

        ConfigurationNode root = menu.getConfig();
        if (root == null) return -1;

        var storage = plugin.getOutfitsStorage();
        UUID uuid = viewer.getUniqueId();

        List<Integer> definedSlots = new ArrayList<>();

        ConfigurationNode items = root.node("items");
        for (ConfigurationNode node : items.childrenMap().values()) {
            String type = node.node("type").getString("");
            if (!"outfit_slot".equalsIgnoreCase(type)) continue;

            int os = node.node("outfit-slot").getInt(-1);
            if (os <= 0) continue;

            String perm = node.node("permission").getString("");
            if (perm != null && !perm.isBlank() && !viewer.hasPermission(perm) && !viewer.isOp()) continue;

            definedSlots.add(os);
        }

        Collections.sort(definedSlots);

        for (int os : definedSlots) {
            if (!storage.hasSlot(uuid, os)) {
                return os;
            }
        }
        return -1;
    }

    /**
     * Piccolo hack: duplica node e fa replace sul display-name/lore raw prima del build,
     * così <outfit_name> viene sostituito prima del parse MiniMessage.
     */
    private ConfigurationNode outfitNodeWithReplaced(ConfigurationNode original, String name) {
        try {
            ConfigurationNode copy = original.copy();
            String token = "{outfit_name}";

            String dn = copy.node("display-name").getString("");
            if (dn != null) copy.node("display-name").set(dn.replace(token, name));

            String n = copy.node("name").getString("");
            if (n != null) copy.node("name").set(n.replace(token, name));

            if (!copy.node("lore").virtual()) {
                List<String> lore = copy.node("lore").getList(String.class);
                if (lore != null) {
                    List<String> out = new ArrayList<>(lore.size());
                    for (String line : lore) out.add(line.replace(token, name));
                    copy.node("lore").set(out);
                }
            }
            return copy;
        } catch (Exception e) {
            return original;
        }
    }

    private String getDefaultOutfitName(int slot) {
        Menu menu = Menus.getMenu("dojo_outfits");
        String pattern = "Outfit #<slot>";
        if (menu != null && menu.getConfig() != null) {
            pattern = menu.getConfig().node("default-outfit-name").getString(pattern);
            if (pattern == null || pattern.isBlank()) pattern = "Outfit #<slot>";
        }
        return pattern.replace("<slot>", String.valueOf(slot));
    }

    private void ensureOutfitName(UUID uuid, int slot) {
        var storage = plugin.getOutfitsStorage();
        String current = storage.getOutfitName(uuid, slot);
        if (current == null || current.isBlank()) {
            storage.setOutfitName(uuid, slot, getDefaultOutfitName(slot));
        }
    }

}
