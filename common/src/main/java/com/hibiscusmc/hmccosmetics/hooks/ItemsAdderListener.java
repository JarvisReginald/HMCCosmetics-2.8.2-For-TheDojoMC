package com.hibiscusmc.hmccosmetics.hooks;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.gui.special.StoreMenu;
import com.hibiscusmc.hmccosmetics.store.StoreArmorStandManager;
import dev.lone.itemsadder.api.Events.ItemsAdderLoadDataEvent;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ItemsAdderListener implements Listener {

    @EventHandler
    public void onItemsAdderLoaded(ItemsAdderLoadDataEvent event) {
        StoreMenu.setItemsAdderReady(true);

        StoreMenu sm = Menus.getStoreMenu("store");
        if (sm != null) sm.reloadStoreContent();

        Bukkit.getScheduler().runTaskLater(
                HMCCosmeticsPlugin.getInstance(),
                StoreArmorStandManager::refreshArmorStandsOnly,
                2L
        );
    }
}
