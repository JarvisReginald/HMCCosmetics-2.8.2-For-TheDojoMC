package com.hibiscusmc.hmccosmetics.gui.type.types;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticHolder;
import com.hibiscusmc.hmccosmetics.gui.type.Type;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import me.lojosho.shaded.configurate.ConfigurationNode;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class TypeOutfitSlotAdd extends Type {

    private final HMCCosmeticsPlugin plugin = HMCCosmeticsPlugin.getInstance();

    public TypeOutfitSlotAdd(String id) {
        super(id);
    }

    public TypeOutfitSlotAdd() {
        super("outfit_slot_add");
    }

    @Override
    public void run(Player viewer, CosmeticHolder cosmeticHolder, ConfigurationNode config, ClickType clickType) {
        MessagesUtil.sendDebugMessages("Running Outfit Slot Add Click Type");
        Collection<Cosmetic> cosmetics = cosmeticHolder.getCosmetics();

        if(!cosmetics.isEmpty()) {
            List<String> cosmeticsList = new ArrayList<>();

            for(Cosmetic c : cosmetics) {
                cosmeticsList.add(c.getId());
            }

            int slots = plugin.getOutfitsStorage().getExistingSlots(viewer.getUniqueId()).size();
            plugin.getOutfitsStorage().setOutfitSlot(viewer.getUniqueId(), slots + 1, cosmeticsList);
        }

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
    public ItemStack setItem(@NotNull Player viewer, @NotNull CosmeticHolder cosmeticHolder, @NotNull ConfigurationNode config, @NotNull ItemStack itemStack, int slot) {
        return itemStack;
    }
}
