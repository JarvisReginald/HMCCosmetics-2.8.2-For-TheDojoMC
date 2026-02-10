package com.hibiscusmc.hmccosmetics.util.packets;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import me.lojosho.hibiscuscommons.util.MessagesUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class PacketManager {
    public static void sendEntitySpawnPacket(@NotNull Location location, int entityId, EntityType entityType, UUID uuid, @NotNull List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketHandler().sendSpawnEntityPacket(entityId, uuid, entityType, location, sendTo);
    }

    public static void gamemodeChangePacket(Player player, GameMode gamemode) {
        NMSHandlers.getHandler().getPacketHandler().sendGamemodeChange(player, gamemode);
    }

    public static void ridingMountPacket(int mountId, int passengerId, @NotNull List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketHandler().sendMountPacket(mountId, new int[]{passengerId}, sendTo);
    }

    public static void sendRotateHeadPacket(int entityId, @NotNull Location location, @NotNull List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketHandler().sendRotateHeadPacket(entityId, location, sendTo);
    }

    public static void sendRotationPacket(int entityId, @NotNull Location location, boolean onGround, @NotNull List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketHandler().sendRotationPacket(entityId, location, onGround, sendTo);
    }

    public static void sendRidingPacket(int mountId, int passengerId, @NotNull List<Player> sendTo) {
        sendRidingPacket(mountId, new int[]{passengerId}, sendTo);
    }

    public static void sendRidingPacket(int mountId, int[] passengerIds, @NotNull List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketHandler().sendMountPacket(mountId, passengerIds, sendTo);
    }

    public static void sendEntityDestroyPacket(int entityId, @NotNull List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketHandler().sendEntityDestroyPacket(IntList.of(entityId), sendTo);
    }

    public static void sendEntityDestroyPacket(List<Integer> ids, @NotNull List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketHandler().sendEntityDestroyPacket(new IntArrayList(ids), sendTo);
    }

    public static void sendCameraPacket(int entityId, @NotNull List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketHandler().sendCameraPacket(entityId, sendTo);
        String var10000 = String.valueOf(sendTo);
        MessagesUtil.sendDebugMessages(var10000 + " | " + entityId + " has had a camera packet on them!");
    }

    public static void sendLeashPacket(int leashedEntity, int entityId, @NotNull List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketHandler().sendLeashPacket(leashedEntity, entityId, sendTo);
    }

    public static void sendTeleportPacket(int entityId, @NotNull Location location, boolean onGround, @NotNull List<Player> sendTo) {
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        float yaw = location.getYaw();
        float pitch = location.getPitch();
        NMSHandlers.getHandler().getPacketHandler().sendTeleportPacket(entityId, x, y, z, yaw, pitch, onGround, sendTo);
    }

    public static @NotNull List<Player> getViewers(Location location, int distance) {
        ArrayList<Player> viewers = new ArrayList();
        if (distance <= 0) {
            viewers.addAll(location.getWorld().getPlayers());
        } else {
            viewers.addAll(getNearbyPlayers(location, distance));
        }

        return viewers;
    }

    public static void slotUpdate(Player player, int slot) {
        NMSHandlers.getHandler().getPacketHandler().sendSlotUpdate(player, slot);
    }

    public static void equipmentSlotUpdate(int entityId, EquipmentSlot slot, ItemStack item, List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketHandler().sendEquipmentSlotUpdate(entityId, slot, item, sendTo);
    }

    public static void equipmentSlotUpdate(int entityId, HashMap<EquipmentSlot, ItemStack> equipment, List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketHandler().sendEquipmentSlotUpdate(entityId, equipment, sendTo);
    }

    public static void sendFakePlayerSpawnPacket(@NotNull Location location, UUID uuid, int entityId, @NotNull List<Player> sendTo) {
        sendEntitySpawnPacket(location, entityId, EntityType.PLAYER, uuid, sendTo);
    }

    private static List<Player> getNearbyPlayers(Location location, int distance) {
        List<Player> players = new ArrayList();

        for(Entity entity : location.getWorld().getNearbyEntities(location, (double)distance, (double)distance, (double)distance)) {
            if (entity instanceof Player) {
                players.add((Player)entity);
            }
        }

        return players;
    }
}
