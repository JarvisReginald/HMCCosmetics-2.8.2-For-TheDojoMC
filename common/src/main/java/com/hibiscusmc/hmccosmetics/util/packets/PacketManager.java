package com.hibiscusmc.hmccosmetics.util.packets;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import me.lojosho.hibiscuscommons.nms.NMSPacketBuilder;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Compatibility layer over the HibiscusCommons NMSPacketBuilder API.
 * All callers retain their existing signatures; only the internal implementation
 * changed to use the new builder + sendPacket pattern.
 */
public class PacketManager {

    private static NMSPacketBuilder builder() {
        return NMSHandlers.getHandler().getPacketBuilder();
    }

    public static void sendEntitySpawnPacket(@NotNull Location location, int entityId, EntityType entityType, UUID uuid, @NotNull List<Player> sendTo) {
        builder().buildEntitySpawnPacket(entityId, uuid, entityType, location).sendPacket(sendTo);
    }

    public static void gamemodeChangePacket(Player player, GameMode gamemode) {
        builder().buildPlayerGamemodeChangePacket(gamemode).sendPacket(List.of(player));
    }

    public static void ridingMountPacket(int mountId, int passengerId, @NotNull List<Player> sendTo) {
        builder().buildEntityMountPacket(mountId, new int[]{passengerId}).sendPacket(sendTo);
    }

    public static void sendRotateHeadPacket(int entityId, @NotNull Location location, @NotNull List<Player> sendTo) {
        builder().buildEntityRotateHeadPacket(entityId, location.getYaw()).sendPacket(sendTo);
    }

    public static void sendRotationPacket(int entityId, @NotNull Location location, boolean onGround, @NotNull List<Player> sendTo) {
        builder().buildEntityRotatePacket(entityId, location.getYaw(), location.getPitch(), onGround).sendPacket(sendTo);
    }

    public static void sendRidingPacket(int mountId, int passengerId, @NotNull List<Player> sendTo) {
        sendRidingPacket(mountId, new int[]{passengerId}, sendTo);
    }

    public static void sendRidingPacket(int mountId, int[] passengerIds, @NotNull List<Player> sendTo) {
        builder().buildEntityMountPacket(mountId, passengerIds).sendPacket(sendTo);
    }

    public static void sendEntityDestroyPacket(int entityId, @NotNull List<Player> sendTo) {
        builder().buildEntityDestroyPacket(IntList.of(entityId)).sendPacket(sendTo);
    }

    public static void sendEntityDestroyPacket(List<Integer> ids, @NotNull List<Player> sendTo) {
        builder().buildEntityDestroyPacket(new IntArrayList(ids)).sendPacket(sendTo);
    }

    public static void sendCameraPacket(int entityId, @NotNull List<Player> sendTo) {
        builder().buildEntityCameraPacket(entityId).sendPacket(sendTo);
    }

    public static void sendLeashPacket(int leashedEntity, int entityId, @NotNull List<Player> sendTo) {
        builder().buildEntityLeashPacket(leashedEntity, entityId).sendPacket(sendTo);
    }

    public static void sendTeleportPacket(int entityId, @NotNull Location location, boolean onGround, @NotNull List<Player> sendTo) {
        builder().buildEntityTeleportPacket(
                entityId,
                location.getX(), location.getY(), location.getZ(),
                location.getYaw(), location.getPitch(),
                onGround
        ).sendPacket(sendTo);
    }

    /** No direct equivalent in the new API; kept for source compatibility. */
    public static void slotUpdate(Player player, int slot) {}

    public static void equipmentSlotUpdate(int entityId, EquipmentSlot slot, ItemStack item, List<Player> sendTo) {
        builder().buildEntityEquipmentSlotUpdatePacket(entityId, Map.of(slot, item)).sendPacket(sendTo);
    }

    public static void equipmentSlotUpdate(int entityId, HashMap<EquipmentSlot, ItemStack> equipment, List<Player> sendTo) {
        builder().buildEntityEquipmentSlotUpdatePacket(entityId, equipment).sendPacket(sendTo);
    }

    public static void sendFakePlayerSpawnPacket(@NotNull Location location, UUID uuid, int entityId, @NotNull List<Player> sendTo) {
        builder().buildEntitySpawnPacket(entityId, uuid, EntityType.PLAYER, location).sendPacket(sendTo);
    }

    @NotNull
    public static List<Player> getViewers(Location location, int distance) {
        ArrayList<Player> viewers = new ArrayList<>();
        if (distance <= 0) {
            viewers.addAll(location.getWorld().getPlayers());
        } else {
            viewers.addAll(getNearbyPlayers(location, distance));
        }
        return viewers;
    }

    private static List<Player> getNearbyPlayers(Location location, int distance) {
        List<Player> players = new ArrayList<>();
        for (Entity entity : location.getWorld().getNearbyEntities(location, distance, distance, distance)) {
            if (entity instanceof Player p) {
                players.add(p);
            }
        }
        return players;
    }
}
