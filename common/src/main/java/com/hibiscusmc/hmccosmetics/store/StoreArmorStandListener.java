package com.hibiscusmc.hmccosmetics.store;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class StoreArmorStandListener implements Listener {

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        Bukkit.getScheduler().runTaskLater(HMCCosmeticsPlugin.getInstance(), () -> {
            StoreArmorStandManager.scanChunkAndRefresh(e.getChunk());
        }, 2L);

    }
}

