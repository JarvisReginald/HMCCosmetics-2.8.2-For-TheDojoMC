package com.hibiscusmc.hmccosmetics.listener;

import com.hibiscusmc.hmccosmetics.store.StoreArmorStandManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class StoreArmorStandChunkListener implements Listener {

    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent e) {
        StoreArmorStandManager.scanChunkAndRefresh(e.getChunk());
    }
}
