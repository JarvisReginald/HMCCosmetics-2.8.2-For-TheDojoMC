package com.hibiscusmc.hmccosmetics.gui.special;

import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticHolder;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public interface DyeMenu {

    /**
     * Overrideable method that is called when a player wishing to dye a menu.
     * After the method is called, the menu plugin itself is supposed to handle everything else, including adding the cosmetic to the CosmeticHolder
     *
     * @param viewer The player that is viewing the menu
     * @param cosmeticHolder The CosmeticHolder that is being dyed (Can be different from the viewer)
     * @param cosmetic (The cosmetic that is wished to be dyed)
     */
    void openMenu(@NotNull Player viewer, @NotNull CosmeticHolder cosmeticHolder, @NotNull Cosmetic cosmetic);

}
