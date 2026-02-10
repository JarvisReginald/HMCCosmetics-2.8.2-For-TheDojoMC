package com.hibiscusmc.hmccosmetics.gui.type.types;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticHolder;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetics;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticArmorType;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticSkinType;
import com.hibiscusmc.hmccosmetics.gui.action.Actions;
import com.hibiscusmc.hmccosmetics.gui.special.DyeMenu;
import com.hibiscusmc.hmccosmetics.gui.special.DyeMenuProvider;
import com.hibiscusmc.hmccosmetics.gui.type.Type;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import me.lojosho.hibiscuscommons.config.serializer.ItemSerializer;
import me.lojosho.hibiscuscommons.hooks.Hooks;
import me.lojosho.shaded.configurate.ConfigurationNode;
import me.lojosho.shaded.configurate.serialize.SerializationException;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TypeCosmetic extends Type {

    public TypeCosmetic(String id) {
        super(id);
    }

    public TypeCosmetic() {
        super("cosmetic");
    }

    @Override
    public void run(Player viewer, CosmeticHolder cosmeticHolder, ConfigurationNode config, ClickType clickType) {
        MessagesUtil.sendDebugMessages("Running Cosmetic Click Type");
        if (config.node("cosmetic").virtual()) {
            MessagesUtil.sendDebugMessages("Cosmetic Config Field Virtual");
            return;
        }
        String cosmeticName = config.node("cosmetic").getString();
        Cosmetic cosmetic = Cosmetics.getCosmetic(cosmeticName);
        if (cosmetic == null) {
            MessagesUtil.sendDebugMessages("No Cosmetic Found");
            MessagesUtil.sendMessage(viewer, "invalid-cosmetic");
            return;
        }

        if (!cosmeticHolder.canEquipCosmetic(cosmetic)) {
            MessagesUtil.sendDebugMessages("No Cosmetic Permission");
            MessagesUtil.sendMessage(viewer, "no-cosmetic-permission");
            return;
        }

        boolean isUnEquippingCosmetic = cosmeticHolder.hasCosmeticInSlot(cosmetic);

        String dyeClick = Settings.getCosmeticDyeClickType();
        String requiredClick;
        if (isUnEquippingCosmetic) requiredClick = Settings.getCosmeticUnEquipClickType();
        else requiredClick = Settings.getCosmeticEquipClickType();

        MessagesUtil.sendDebugMessages("Required click type: " + requiredClick);
        MessagesUtil.sendDebugMessages("Click type: " + clickType.name());

        final boolean isRequiredClick = requiredClick.equalsIgnoreCase("ANY") || requiredClick.equalsIgnoreCase(clickType.name());
        final boolean isDyeClick = dyeClick.equalsIgnoreCase("ANY") || dyeClick.equalsIgnoreCase(clickType.name());

        if (!isRequiredClick) isUnEquippingCosmetic = false;

        List<String> actionStrings = new ArrayList<>();
        ConfigurationNode actionConfig = config.node("actions");

        MessagesUtil.sendDebugMessages("Running Actions");

        try {
            if (!actionConfig.node("any").virtual())
                actionStrings.addAll(actionConfig.node("any").getList(String.class));

            if (clickType != null) {
                if (clickType.isLeftClick()) {
                    if (!actionConfig.node("left-click").virtual())
                        actionStrings.addAll(actionConfig.node("left-click").getList(String.class));
                }
                if (clickType.isRightClick()) {
                    if (!actionConfig.node("right-click").virtual())
                        actionStrings.addAll(actionConfig.node("right-click").getList(String.class));
                }
                if (clickType.equals(ClickType.SHIFT_LEFT)) {
                    if (!actionConfig.node("shift-left-click").virtual())
                        actionStrings.addAll(actionConfig.node("shift-left-click").getList(String.class));
                }
                if (clickType.equals(ClickType.SHIFT_RIGHT)) {
                    if (!actionConfig.node("shift-right-click").virtual())
                        actionStrings.addAll(actionConfig.node("shift-right-click").getList(String.class));
                }
            }

            if (isUnEquippingCosmetic) {
                if (!actionConfig.node("on-unequip").virtual())
                    actionStrings.addAll(actionConfig.node("on-unequip").getList(String.class));

                MessagesUtil.sendDebugMessages("on-unequip");

                if (cosmetic instanceof CosmeticSkinType) {
                    if (cosmeticHolder instanceof CosmeticUser cu) {
                        cu.removeCosmetic(cosmetic);
                    } else {
                        cosmeticHolder.removeCosmeticSlot(CosmeticSlot.SKIN);
                    }
                } else {
                    cosmeticHolder.removeCosmeticSlot(cosmetic.getSlot());
                }
            } else {
                if (!actionConfig.node("on-equip").virtual())
                    actionStrings.addAll(actionConfig.node("on-equip").getList(String.class));
                MessagesUtil.sendDebugMessages("on-equip");
                MessagesUtil.sendDebugMessages("Preparing for on-equip with the following checks:");
                MessagesUtil.sendDebugMessages("CosmeticDyeable? " + cosmetic.isDyeable() + " / isDyeClick? " + isDyeClick + " / isHMCColorActive? " + Hooks.isActiveHook("HMCColor"));
                // TODO: Redo this
                if (cosmetic.isDyeable() && isDyeClick && DyeMenuProvider.hasMenuProvider()) {
                    DyeMenuProvider.openMenu(viewer, cosmeticHolder, cosmetic);
                } else if (isRequiredClick) {
                    if (cosmetic instanceof CosmeticSkinType skinType) {
                        for (Cosmetic c : cosmeticHolder.getCosmetics()) {
                            if (c instanceof CosmeticSkinType skinType1) {
                                if (skinType.getRetextureGroup() != null && skinType1.getRetextureGroup() != null) {
                                    if (skinType.getRetextureGroup().equals(skinType1.getRetextureGroup())) {
                                        if (cosmeticHolder instanceof CosmeticUser cu) {
                                            cu.removeCosmetic(c);
                                        } else {
                                            cosmeticHolder.removeCosmeticSlot(CosmeticSlot.SKIN);
                                        }
                                    }
                                }
                            }
                        }
                    }

                    if (cosmetic.getSlot() == CosmeticSlot.PARTICLE) {
                        for (Cosmetic c : cosmeticHolder.getCosmetics()) {
                            if (c.getSlot() == CosmeticSlot.PARTICLE) {
                                cosmeticHolder.removeCosmeticSlot(c);
                            }
                        }
                    }

                    cosmeticHolder.addCosmetic(cosmetic);
                }
            }

            Actions.runActions(viewer, cosmeticHolder, actionStrings);

        } catch (SerializationException e) {
            e.printStackTrace();
        }
        // Fixes issue with offhand cosmetics not appearing. Yes, I know this is dumb
        Runnable run = () -> cosmeticHolder.updateCosmetic(cosmetic.getSlot());
        if (cosmetic instanceof CosmeticArmorType) {
            if (((CosmeticArmorType) cosmetic).getEquipSlot().equals(EquipmentSlot.OFF_HAND)) {
                Bukkit.getScheduler().runTaskLater(HMCCosmeticsPlugin.getInstance(), run, 1);
            }
        }
        run.run();
        MessagesUtil.sendDebugMessages("Finished Type Click Run");
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
    public ItemStack setItem(@NotNull Player viewer,
                             @NotNull CosmeticHolder cosmeticHolder,
                             @NotNull ConfigurationNode config,
                             @NotNull ItemStack itemStack,
                             int slot) {

        MessagesUtil.sendDebugMessages("=== setItem START ===");
        MessagesUtil.sendDebugMessages("slot=" + slot
                + " viewer=" + viewer.getName()
                + " item=" + itemStack.getType()
                + " amount=" + itemStack.getAmount());

        // Base meta processing
        MessagesUtil.sendDebugMessages("Checking base ItemMeta...");
        if (itemStack.hasItemMeta()) {
            MessagesUtil.sendDebugMessages("Base ItemStack HAS meta -> processing...");
            itemStack.setItemMeta(processItemMeta(viewer, itemStack.getItemMeta()));
            MessagesUtil.sendDebugMessages("Base meta processed.");
        } else {
            MessagesUtil.sendDebugMessages("Base ItemStack has NO ItemMeta?");
        }

        // Cosmetic node virtual?
        boolean cosmeticVirtual = config.node("cosmetic").virtual();
        MessagesUtil.sendDebugMessages("config.cosmetic virtual=" + cosmeticVirtual);

        if (cosmeticVirtual) {
            MessagesUtil.sendDebugMessages("Branch: cosmetic node is VIRTUAL -> returning current itemStack");
            MessagesUtil.sendDebugMessages("=== setItem END (virtual cosmetic) ===");
            return itemStack;
        }

        // Cosmetic load
        String cosmeticName = config.node("cosmetic").getString();
        MessagesUtil.sendDebugMessages("cosmeticName=" + cosmeticName);

        Cosmetic cosmetic = Cosmetics.getCosmetic(cosmeticName);
        MessagesUtil.sendDebugMessages("Cosmetics.getCosmetic(...) result=" + (cosmetic == null ? "null" : cosmetic.toString()));

        if (cosmetic == null) {
            MessagesUtil.sendDebugMessages("Branch: cosmetic is NULL -> returning current itemStack");
            MessagesUtil.sendDebugMessages("=== setItem END (cosmetic null) ===");
            return itemStack;
        }

        boolean hasCosmeticInSlot = cosmeticHolder.hasCosmeticInSlot(cosmetic);
        boolean equippedItemVirtual = config.node("equipped-item").virtual();
        boolean lockedEquippedItemVirtual = config.node("locked-equipped-item").virtual();

        MessagesUtil.sendDebugMessages("hasCosmeticInSlot=" + hasCosmeticInSlot
                + " equipped-item virtual=" + equippedItemVirtual
                + " locked-equipped-item virtual=" + lockedEquippedItemVirtual);

        // Equipped branch
        if (hasCosmeticInSlot && (!equippedItemVirtual || !lockedEquippedItemVirtual)) {
            MessagesUtil.sendDebugMessages("Branch: EQUIPPED item logic entered.");

            boolean canEquipCosmetic = cosmeticHolder.canEquipCosmetic(cosmetic, true);
            MessagesUtil.sendDebugMessages("canEquipCosmetic=" + canEquipCosmetic);

            String chosenNodeName = (canEquipCosmetic && !equippedItemVirtual) ? "equipped-item" : "locked-equipped-item";
            MessagesUtil.sendDebugMessages("chosen equipped node=" + chosenNodeName);

            ConfigurationNode equippedItem = config.node(chosenNodeName);

            // Ensure material fallback
            try {
                boolean equippedMaterialVirtual = equippedItem.node("material").virtual();
                MessagesUtil.sendDebugMessages("equippedItem.material virtual=" + equippedMaterialVirtual);

                if (equippedMaterialVirtual) {
                    String fallbackMaterial = config.node("item", "material").getString();
                    MessagesUtil.sendDebugMessages("equippedItem.material is virtual -> fallback material from item.material=" + fallbackMaterial);
                    equippedItem.node("material").set(fallbackMaterial);
                    MessagesUtil.sendDebugMessages("equippedItem.material set to fallback.");
                }
            } catch (SerializationException e) {
                MessagesUtil.sendDebugMessages("SerializationException while setting equippedItem.material fallback: " + e.getMessage());
            }

            // Deserialize equipped item
            try {
                MessagesUtil.sendDebugMessages("Deserializing equipped item from node '" + chosenNodeName + "'...");
                itemStack = ItemSerializer.INSTANCE.deserialize(ItemStack.class, equippedItem);
                MessagesUtil.sendDebugMessages("Deserialize OK -> item=" + itemStack.getType() + " amount=" + itemStack.getAmount());
            } catch (SerializationException e) {
                MessagesUtil.sendDebugMessages("Deserialize FAILED for equipped item: " + e.getMessage());
                throw new RuntimeException(e);
            }

            // Process meta after deserialize
            MessagesUtil.sendDebugMessages("Checking ItemMeta after equipped deserialize...");
            if (itemStack.hasItemMeta()) {
                MessagesUtil.sendDebugMessages("Equipped ItemStack HAS meta -> processing...");
                itemStack.setItemMeta(processItemMeta(viewer, itemStack.getItemMeta()));
                MessagesUtil.sendDebugMessages("Equipped meta processed.");
            } else {
                MessagesUtil.sendDebugMessages("ItemStack has NO ItemMeta in equipped item?");
            }

            MessagesUtil.sendDebugMessages("=== setItem END (equipped branch) ===");
            return itemStack;
        }

        if (hasCosmeticInSlot && config.node("enchant").getBoolean(true)) {
            // Glow effect
            MessagesUtil.sendDebugMessages("Applying glow enchant + hide flags...");
            ItemMeta meta = itemStack.getItemMeta();
            if (meta == null) {
                MessagesUtil.sendDebugMessages("WARNING: itemStack.getItemMeta() returned NULL while applying glow!");
            } else {
                meta.addEnchant(Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                itemStack.setItemMeta(meta);
                MessagesUtil.sendDebugMessages("Glow applied.");
            }

            MessagesUtil.sendDebugMessages("=== setItem END (equipped branch) ===");
            return itemStack;
        }

        // Locked branch
        boolean canEquipCosmetic = cosmeticHolder.canEquipCosmetic(cosmetic, true);
        boolean lockedItemVirtual = config.node("locked-item").virtual();

        MessagesUtil.sendDebugMessages("Locked-check: canEquipCosmetic=" + canEquipCosmetic
                + " locked-item virtual=" + lockedItemVirtual);

        if (!canEquipCosmetic && !lockedItemVirtual) {
            MessagesUtil.sendDebugMessages("Branch: LOCKED item logic entered.");

            ConfigurationNode lockedItem = config.node("locked-item");

            // Ensure material fallback
            try {
                boolean lockedMaterialVirtual = lockedItem.node("material").virtual();
                MessagesUtil.sendDebugMessages("lockedItem.material virtual=" + lockedMaterialVirtual);

                if (lockedMaterialVirtual) {
                    String fallbackMaterial = config.node("item", "material").getString();
                    MessagesUtil.sendDebugMessages("lockedItem.material is virtual -> fallback material from item.material=" + fallbackMaterial);
                    lockedItem.node("material").set(fallbackMaterial);
                    MessagesUtil.sendDebugMessages("lockedItem.material set to fallback.");
                }
            } catch (SerializationException e) {
                MessagesUtil.sendDebugMessages("SerializationException while setting lockedItem.material fallback: " + e.getMessage());
            }

            // Deserialize locked item
            try {
                MessagesUtil.sendDebugMessages("Deserializing locked item from node 'locked-item'...");
                itemStack = ItemSerializer.INSTANCE.deserialize(ItemStack.class, lockedItem);
                MessagesUtil.sendDebugMessages("Deserialize OK -> item=" + itemStack.getType() + " amount=" + itemStack.getAmount());
            } catch (SerializationException e) {
                MessagesUtil.sendDebugMessages("Deserialize FAILED for locked item: " + e.getMessage());
                throw new RuntimeException(e);
            }

            // Process meta after deserialize
            MessagesUtil.sendDebugMessages("Checking ItemMeta after locked deserialize...");
            if (itemStack.hasItemMeta()) {
                MessagesUtil.sendDebugMessages("Locked ItemStack HAS meta -> processing...");
                itemStack.setItemMeta(processItemMeta(viewer, itemStack.getItemMeta()));
                MessagesUtil.sendDebugMessages("Locked meta processed.");
            } else {
                MessagesUtil.sendDebugMessages("ItemStack has NO ItemMeta in locked item?");
            }

            MessagesUtil.sendDebugMessages("=== setItem END (locked branch) ===");
            return itemStack;
        }

        MessagesUtil.sendDebugMessages("Branch: DEFAULT -> returning current itemStack (no changes beyond base meta).");
        MessagesUtil.sendDebugMessages("=== setItem END (default) ===");
        return itemStack;
    }
}
