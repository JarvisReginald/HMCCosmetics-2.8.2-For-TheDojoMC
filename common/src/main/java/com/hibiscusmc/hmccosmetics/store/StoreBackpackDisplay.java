package com.hibiscusmc.hmccosmetics.store;

import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBackpackType;
import com.hibiscusmc.hmccosmetics.user.manager.UserEntity;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import com.hibiscusmc.hmccosmetics.util.packets.PacketManager;
import me.lojosho.hibiscuscommons.util.ServerUtils;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

public final class StoreBackpackDisplay {

    private final ArmorStand ownerStand;
    private final CosmeticBackpackType backpack;
    private final int backpackEntityId;
    private final UserEntity entityManager;

    public StoreBackpackDisplay(ArmorStand ownerStand, CosmeticBackpackType backpack) {
        this.ownerStand = ownerStand;
        this.backpack = backpack;

        this.backpackEntityId = ServerUtils.getNextEntityId();
        this.entityManager = new UserEntity(ownerStand.getUniqueId());
        this.entityManager.setIds(List.of(backpackEntityId));
    }

    public void spawn() {
        tick(true);
    }

    public void tick() {
        tick(false);
    }

    private void tick(boolean forceSpawn) {
        if (ownerStand == null || !ownerStand.isValid()) return;

        Location base = ownerStand.getLocation();

        entityManager.teleport(base);
        entityManager.setRotation((int) base.getYaw(), false);

        List<Player> newViewers = entityManager.refreshViewers(base);

        // Spawn a chi è nuovo (e anche al primo spawn forzato)
        if (forceSpawn || !newViewers.isEmpty()) {
            List<Player> spawnTargets = forceSpawn ? entityManager.getViewers() : newViewers;

            HMCCPacketManager.spawnInvisibleArmorstand(
                    backpackEntityId,
                    base,
                    UUID.randomUUID(),
                    spawnTargets
            );

            ItemStack displayItem = backpack.getItem();
            if (displayItem != null) {
                PacketManager.equipmentSlotUpdate(
                        backpackEntityId,
                        EquipmentSlot.HEAD,
                        displayItem,
                        spawnTargets
                );
            }

            // mount (riding)
            HMCCPacketManager.sendRidingPacket(ownerStand.getEntityId(), backpackEntityId, spawnTargets);
        }

        // opzionale: se vuoi forzare riding sempre come su player
        if (Settings.isBackpackForceRidingEnabled()) {
            HMCCPacketManager.sendRidingPacket(ownerStand.getEntityId(), backpackEntityId, entityManager.getViewers());
        }
    }

    public void despawn() {
        HMCCPacketManager.sendEntityDestroyPacket(backpackEntityId, entityManager.getViewers());
    }
}
