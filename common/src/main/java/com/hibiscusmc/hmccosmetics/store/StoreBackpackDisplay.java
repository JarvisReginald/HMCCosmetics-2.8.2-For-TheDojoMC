package com.hibiscusmc.hmccosmetics.store;

import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBackpackType;
import com.hibiscusmc.hmccosmetics.user.manager.UserEntity;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import me.lojosho.hibiscuscommons.packets.wrapper.PacketWrapper;
import me.lojosho.hibiscuscommons.util.ServerUtils;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        List<Player> allViewers = entityManager.getViewers();

        if (forceSpawn) {
            sendSpawnBundle(base, allViewers);

            if (Settings.isBackpackForceRidingEnabled()) {
                HMCCPacketManager.sendRidingPacket(ownerStand.getEntityId(), backpackEntityId, allViewers);
            }
        } else if (!newViewers.isEmpty()) {
            sendSpawnBundle(base, newViewers);

            if (Settings.isBackpackForceRidingEnabled()) {
                HMCCPacketManager.sendRidingPacket(ownerStand.getEntityId(), backpackEntityId, newViewers);
            }
        }
    }

    private void sendSpawnBundle(Location base, List<Player> spawnTargets) {
        List<PacketWrapper> bundle = new ArrayList<>(
                HMCCPacketManager.getInvisibleArmorStand(backpackEntityId, base, UUID.randomUUID())
        );

        ItemStack displayItem = backpack.getItem();
        if (displayItem != null) {
            bundle.add(NMSHandlers.getHandler().getPacketBuilder()
                    .buildEntityEquipmentSlotUpdatePacket(backpackEntityId, Map.of(EquipmentSlot.HEAD, displayItem)));
        }

        bundle.add(NMSHandlers.getHandler().getPacketBuilder()
                .buildEntityMountPacket(ownerStand.getEntityId(), new int[]{backpackEntityId}));

        NMSHandlers.getHandler().getPacketSender().sendBundle(bundle, spawnTargets);
    }

    public void despawn() {
        HMCCPacketManager.sendEntityDestroyPacket(backpackEntityId, entityManager.getViewers());
    }
}