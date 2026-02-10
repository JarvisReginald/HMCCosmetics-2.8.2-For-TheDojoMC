package com.hibiscusmc.hmccosmetics.store;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class StoreArmorStandPersistListener implements Listener {

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        StoreArmorStandStorage.applyForChunk(e.getChunk());
    }
}

