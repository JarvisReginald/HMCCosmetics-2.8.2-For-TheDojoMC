package com.hibiscusmc.hmccosmetics.cosmetic.types;

import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.behavior.CosmeticUpdateBehavior;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import me.lojosho.shaded.configurate.ConfigurationNode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class CosmeticNoneType extends Cosmetic implements CosmeticUpdateBehavior {

    public CosmeticNoneType(String id, ConfigurationNode config) {
        super(id, config);
    }

    @Override
    public void dispatchUpdate(@NotNull CosmeticUser user) {
        // Do nothing
    }

    public ItemStack getItem(@NotNull CosmeticUser user) {
        return getItem(user, user.getUserCosmeticItem(this));
    }

    public ItemStack getItem(@NotNull CosmeticUser user, ItemStack cosmeticItem) {
        Player player = user.getPlayer();
        if (player == null) return null;

        return cosmeticItem;
    }
}
