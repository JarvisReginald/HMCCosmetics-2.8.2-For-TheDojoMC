package com.hibiscusmc.hmccosmetics.packets;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketContainer;
import com.comphenix.protocol.events.PacketEvent;
import com.comphenix.protocol.wrappers.EnumWrappers;
import com.comphenix.protocol.wrappers.Pair;
import com.comphenix.protocol.wrappers.WrappedWatchableObject;
import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.retexture.RetextureGroupRegistry;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticSkinType;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SkinProtocolLibListener {

    private final ProtocolManager protocolManager;

    public SkinProtocolLibListener(HMCCosmeticsPlugin plugin) {
        this.protocolManager = ProtocolLibrary.getProtocolManager();

        protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.SET_SLOT) {
            @Override public void onPacketSending(PacketEvent event) { handleSetSlot(event); }
        });

        protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.WINDOW_ITEMS) {
            @Override public void onPacketSending(PacketEvent event) { handleWindowItems(event); }
        });

        protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.ENTITY_EQUIPMENT) {
            @Override public void onPacketSending(PacketEvent event) { handleEntityEquipment(event); }
        });

        protocolManager.addPacketListener(new PacketAdapter(plugin, PacketType.Play.Server.ENTITY_METADATA) {
            @Override public void onPacketSending(PacketEvent event) { handleEntityMetadata(event); }
        });
    }

    private void handleSetSlot(PacketEvent event) {
        Player viewer = event.getPlayer();
        CosmeticUser viewerUser = CosmeticUsers.getUser(viewer);
        if (viewerUser == null) return;

        PacketContainer packet = event.getPacket();

        int windowId = packet.getIntegers().read(0);
        int slot = packet.getIntegers().read(1);

        if (slot == -1 && !isSafeCursorMode(viewer)) return;

        ItemStack item = packet.getItemModifier().read(0);
        ItemStack replaced = replaceIfMatchesAnySkin(viewerUser, item);
        if (replaced != item) packet.getItemModifier().write(0, replaced);
    }

    private void handleWindowItems(PacketEvent event) {
        Player viewer = event.getPlayer();
        CosmeticUser viewerUser = CosmeticUsers.getUser(viewer);
        if (viewerUser == null) return;

        PacketContainer packet = event.getPacket();
        List<ItemStack> items = packet.getItemListModifier().read(0);
        if (items == null || items.isEmpty()) return;

        // carried/cursor item
        if (isSafeCursorMode(viewer)) {
            try {
                ItemStack carried = packet.getItemModifier().read(0);
                ItemStack carriedReplaced = replaceIfMatchesAnySkin(viewerUser, carried);
                if (carriedReplaced != carried) {
                    packet.getItemModifier().write(0, carriedReplaced);
                }
            } catch (Exception ignored) {}
        }

        boolean changed = false;
        List<ItemStack> copy = new ArrayList<>(items.size());
        for (ItemStack it : items) {
            ItemStack replaced = replaceIfMatchesAnySkin(viewerUser, it);
            copy.add(replaced);
            if (replaced != it) changed = true;
        }

        if (changed) packet.getItemListModifier().write(0, copy);
    }

    private void handleEntityEquipment(PacketEvent event) {
        Player viewer = event.getPlayer();
        PacketContainer packet = event.getPacket();

        int entityId = packet.getIntegers().read(0);

        Player target = null;
        for (Player p : viewer.getWorld().getPlayers()) {
            if (p.getEntityId() == entityId) { target = p; break; }
        }
        if (target == null) return;

        CosmeticUser targetUser = CosmeticUsers.getUser(target);
        if (targetUser == null) return;

        List<Pair<EnumWrappers.ItemSlot, ItemStack>> pairs = packet.getSlotStackPairLists().read(0);
        if (pairs == null || pairs.isEmpty()) return;

        boolean changed = false;
        List<Pair<EnumWrappers.ItemSlot, ItemStack>> out = new ArrayList<>(pairs.size());

        for (Pair<EnumWrappers.ItemSlot, ItemStack> pair : pairs) {
            EnumWrappers.ItemSlot slot = pair.getFirst();
            ItemStack it = pair.getSecond();

            if (slot == EnumWrappers.ItemSlot.MAINHAND || slot == EnumWrappers.ItemSlot.OFFHAND) {
                ItemStack replaced = replaceIfMatchesAnySkin(targetUser, it);
                out.add(new Pair<>(slot, replaced));
                if (replaced != it) changed = true;
            } else {
                out.add(pair);
            }
        }

        if (changed) packet.getSlotStackPairLists().write(0, out);
    }

    // ===== core =====

    private ItemStack replaceIfMatches(ItemStack realItem, SkinContext ctx) {
        if (realItem == null || realItem.getType().isAir()) return realItem;

        if (!RetextureGroupRegistry.matches(ctx.groupName, realItem.getType())) return realItem;

        var requiredCmds = RetextureGroupRegistry.getCmds(ctx.groupName, realItem.getType());
        ItemMeta rm = realItem.getItemMeta();
        if (rm == null) return realItem;

        if (requiredCmds != null && !requiredCmds.isEmpty()) {
            if (isLikelyCustomItem(realItem, rm)) return realItem;

            int cmd;

            if (!rm.hasCustomModelData()) cmd = 0;
            else cmd = rm.getCustomModelData();

            if (!requiredCmds.contains(cmd)) return realItem;
        } else {
            if (isLikelyCustomItem(realItem, rm)) return realItem;
        }

        ItemStack shown = ctx.template.clone();
        shown.setAmount(realItem.getAmount());

        ItemMeta shownMeta = shown.getItemMeta();

        if (shownMeta != null) {
            shownMeta.setDisplayName(materialPrettyName(realItem.getType()));
            shownMeta.setLore(List.of());

            for (Enchantment enchant : rm.getEnchants().keySet()) {
                shownMeta.addEnchant(enchant, rm.getEnchants().get(enchant), true);
            }

            shown.setItemMeta(shownMeta);
        }

        return shown;
    }

    private String materialPrettyName(Material mat) {
        String[] parts = mat.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder sb = new StringBuilder("§r§f");
        for (int i = 0; i < parts.length; i++) {
            String s = parts[i];
            if (s.isEmpty()) continue;
            sb.append(Character.toUpperCase(s.charAt(0)));
            if (s.length() > 1) sb.append(s.substring(1));
            if (i + 1 < parts.length) sb.append(' ');
        }
        return sb.toString();
    }

    public void resendHands(Player target) {
        CosmeticUser user = CosmeticUsers.getUser(target);
        if (user == null) return;

        ItemStack main = target.getInventory().getItemInMainHand();
        ItemStack off = target.getInventory().getItemInOffHand();

        ItemStack mainOut = replaceIfMatchesAnySkin(user, main);
        ItemStack offOut = replaceIfMatchesAnySkin(user, off);

        PacketContainer pkt = protocolManager.createPacket(PacketType.Play.Server.ENTITY_EQUIPMENT);
        pkt.getIntegers().write(0, target.getEntityId());

        List<Pair<EnumWrappers.ItemSlot, ItemStack>> pairs = new ArrayList<>(2);
        pairs.add(new Pair<>(EnumWrappers.ItemSlot.MAINHAND, mainOut));
        pairs.add(new Pair<>(EnumWrappers.ItemSlot.OFFHAND, offOut));
        pkt.getSlotStackPairLists().write(0, pairs);

        for (Player viewer : target.getWorld().getPlayers()) {
            if (viewer == null || viewer.equals(target)) continue;
            if (!viewer.canSee(target)) continue;
            try { protocolManager.sendServerPacket(viewer, pkt); } catch (Exception ignored) {}
        }
    }

    private boolean isLikelyCustomItem(ItemStack stack, ItemMeta meta) {
        if (isItemsAdderCustomItem(stack)) return true;
        var keys = meta.getPersistentDataContainer().getKeys();
        if (keys == null || keys.isEmpty()) return false;

        String ownNs = HMCCosmeticsPlugin.getInstance().getName().toLowerCase(Locale.ROOT);
        for (NamespacedKey k : keys) {
            String ns = k.getNamespace();
            if (ns == null) continue;
            if (ns.equals("minecraft")) continue;
            if (ns.equals(ownNs)) continue;
            return true;
        }
        return false;
    }

    private boolean isItemsAdderCustomItem(ItemStack stack) {
        try {
            if (!Bukkit.getPluginManager().isPluginEnabled("ItemsAdder")) return false;
            return dev.lone.itemsadder.api.CustomStack.byItemStack(stack) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private ItemStack replaceIfMatchesAnySkin(CosmeticUser user, ItemStack realItem) {
        if (realItem == null || realItem.getType().isAir()) return realItem;

        for (CosmeticSkinType skinType : user.getEquippedSkins().values()) {
            String group = skinType.getRetextureGroup();
            if (group == null || group.isBlank()) continue;

            ItemStack template = skinType.getItem();
            if (template == null || template.getType().isAir()) continue;

            SkinContext ctx = new SkinContext(group, template);
            ItemStack replaced = replaceIfMatches(realItem, ctx);
            if (replaced != realItem) return replaced;
        }

        return realItem;
    }

    private boolean isSafeCursorMode(Player p) {
        switch (p.getGameMode()) {
            case SURVIVAL:
            case ADVENTURE:
                return true;
            default:
                return false; // CREATIVE, SPECTATOR ecc.
        }
    }

    private void handleEntityMetadata(PacketEvent event) {
        Player viewer = event.getPlayer();
        CosmeticUser viewerUser = CosmeticUsers.getUser(viewer);
        if (viewerUser == null) return;

        PacketContainer packet = event.getPacket();
        int entityId = packet.getIntegers().read(0);

        org.bukkit.entity.Entity ent = null;
        for (org.bukkit.entity.Entity e : viewer.getWorld().getEntities()) {
            if (e.getEntityId() == entityId) { ent = e; break; }
        }
        if (!(ent instanceof org.bukkit.entity.Item)) return;

        List<WrappedWatchableObject> list;
        try {
            list = packet.getWatchableCollectionModifier().read(0);
        } catch (Throwable t) {
            return;
        }

        boolean changed = false;
        List<WrappedWatchableObject> out = new ArrayList<>(list.size());

        for (WrappedWatchableObject w : list) {
            Object val = w.getValue();
            if (val instanceof ItemStack stack) {
                ItemStack replaced = replaceIfMatchesAnySkin(viewerUser, stack);
                if (replaced != stack) {
                    out.add(new WrappedWatchableObject(w.getWatcherObject(), replaced));
                    changed = true;
                    continue;
                }
            }
            out.add(w);
        }

        if (changed) {
            try { packet.getWatchableCollectionModifier().write(0, out); }
            catch (Throwable ignored) {}
        }
    }

    private static final class SkinContext {
        final String groupName;
        final ItemStack template;

        SkinContext(String groupName, ItemStack template) {
            this.groupName = groupName;
            this.template = template;
        }
    }
}
