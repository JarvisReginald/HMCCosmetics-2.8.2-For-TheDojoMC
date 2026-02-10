package com.hibiscusmc.hmccosmetics.util.packets;

import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.HMCCInventoryUtils;
import com.sk89q.worldedit.world.entity.EntityTypes;
import com.hibiscusmc.hmccosmetics.util.packets.PacketManager;
import me.lojosho.hibiscuscommons.nms.MinecraftVersion;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class HMCCPacketManager extends PacketManager {

    // The cloud effect map, in case it gets lost: Map<Integer, Number> dataValues = Map.of(0, (byte) 0x20, 8, 0f);
    private static final List<CosmeticSlot> EQUIPMENT_SLOTS = List.of(CosmeticSlot.HELMET, CosmeticSlot.CHESTPLATE, CosmeticSlot.LEGGINGS, CosmeticSlot.BOOTS, CosmeticSlot.MAINHAND, CosmeticSlot.OFFHAND);

    public static void sendEntitySpawnPacket(
            final @NotNull Location location,
            final int entityId,
            final EntityType entityType,
            final UUID uuid
            ) {
        sendEntitySpawnPacket(location, entityId, entityType, uuid, getViewers(location));
    }

    public static void sendEntitySpawnPacket(
            final @NotNull Location location,
            final int entityId,
            final EntityType entityType,
            final UUID uuid,
            final @NotNull List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketHandler().sendSpawnEntityPacket(entityId, uuid, entityType, location, sendTo);
    }

    public static void equipmentSlotUpdate(
            Player player,
            boolean empty,
            List<Player> sendTo
    ) {
        HashMap<EquipmentSlot, ItemStack> items = new HashMap<>();
        for (EquipmentSlot slot : HMCCInventoryUtils.getPlayerArmorSlots()) {
            ItemStack item = player.getInventory().getItem(slot);
            if (empty) item = new ItemStack(Material.AIR);
            items.put(slot, item);
        }
        equipmentSlotUpdate(player.getEntityId(), items, sendTo);
    }
    public static void equipmentSlotUpdate(
            @NotNull Player player,
            CosmeticSlot cosmetic,
            List<Player> sendTo
    ) {
        CosmeticUser user = CosmeticUsers.getUser(player.getUniqueId());
        equipmentSlotUpdate(player.getEntityId(), user, cosmetic, sendTo);
    }
    public static void equipmentSlotUpdate(
            CosmeticUser user,
            CosmeticSlot cosmeticSlot,
            List<Player> sendTo
    ) {
        equipmentSlotUpdate(user.getEntity().getEntityId(), user, cosmeticSlot, sendTo);
    }

    public static void equipmentSlotUpdate(
            int entityId,
            CosmeticUser user,
            CosmeticSlot cosmeticSlot,
            List<Player> sendTo
    ) {
        if (!EQUIPMENT_SLOTS.contains(cosmeticSlot)) return;
        equipmentSlotUpdate(entityId, HMCCInventoryUtils.getEquipmentSlot(cosmeticSlot), user.getUserCosmeticItem(cosmeticSlot), sendTo);
    }

    public static void sendInvisibilityPacket(
            int entityId,
            List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketHandler().sendSharedEntityData(entityId, Map.of(0, (byte) 0x20), sendTo);
    }

    public static void spawnCloudAndHandleEffect(
            int entityId,
            Location location,
            UUID uuid,
            List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketHandler().sendInvisibleParticleCloud(entityId, location, uuid, sendTo);
    }

    /**
     * This handles both spawn + metadata in a bundle packet
     * @param entityId
     * @param location
     * @param uuid
     * @param sendTo
     */
    public static void spawnInvisibleArmorstand(
            int entityId,
            Location location,
            UUID uuid,
            List<Player> sendTo
    ) {
        byte mask = getMask();
        NMSHandlers.getHandler().getPacketHandler().sendInvisibleArmorstand(entityId, location, uuid, mask, sendTo);
    }

    /**
     * This is just a normal meta data packet (non-bundled)
     * @param entityId
     * @param sendTo
     */
    public static void sendArmorstandMetadata(
            int entityId,
            List<Player> sendTo
    ) {
        byte mask = getMask();
        Map<Integer, Number> dataValues = Map.of(0, mask, 15, (byte) 0x10);
        NMSHandlers.getHandler().getPacketHandler().sendSharedEntityData(entityId, dataValues, sendTo);
    }

    private static byte getMask() {
        return (byte) (Settings.isBackpackPreventDarkness() ? 0x21 : 0x20);
    }

    public static void sendRotationPacket(
            int entityId,
            Location location,
            boolean onGround
    ) {
        sendRotationPacket(entityId, location, onGround, getViewers(location));
    }

    public static void sendRotationPacket(
            int entityId,
            @NotNull Location location,
            boolean onGround,
            @NotNull List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketHandler().sendRotationPacket(entityId, location.getYaw(), location.getPitch(), onGround, sendTo);
    }

    public static void sendRotationPacket(
            int entityId,
            int yaw,
            boolean onGround,
            @NotNull List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketHandler().sendRotationPacket(entityId, yaw, 0, onGround, sendTo);
    }

    /**
     * Mostly to deal with backpacks, this deals with entities riding other entities.
     * @param mountId The entity that is the "mount", ex. a player
     * @param passengerId The entity that is riding the mount, ex. a armorstand for a backpack
     */
    public static void sendRidingPacket(
            final int mountId,
            final int passengerId,
            final Location location
    ) {
        sendRidingPacket(mountId, passengerId, getViewers(location));
    }

    /**
     * Mostly to deal with backpacks, this deals with entities riding other entities.
     * @param mountId The entity that is the "mount", ex. a player
     * @param passengerIds The entities that are riding the mount, ex. a armorstand for a backpack
     * @param sendTo Whom to send the packet to
     */
    public static void sendRidingPacket(
            final int mountId,
            final int[] passengerIds,
            final @NotNull List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketHandler().sendMountPacket(mountId, passengerIds, sendTo);
    }

    /**
     * Mostly to deal with backpacks, this deals with entities riding other entities.
     * @param mountId The entity that is the "mount", ex. a player
     * @param passengerId The entity that is riding the mount, ex. a armorstand for a backpack
     * @param sendTo Whom to send the packet to
     */
    public static void sendRidingPacket(
            final int mountId,
            final int passengerId,
            final @NotNull List<Player> sendTo
    ) {
        sendRidingPacket(mountId, new int[] {passengerId}, sendTo);
    }

    /**
     * Creates a fake player entity.
     * @param skinnedPlayer The original player it bases itself off of.
     * @param uuid UUID of the fake entity.
     * @param sendTo Whom to send the packet to
     */
    public static void sendFakePlayerInfoPacket(
            final Player skinnedPlayer,
            final int entityId,
            final UUID uuid,
            final String npcName,
            final List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketHandler().sendFakePlayerInfoPacket(skinnedPlayer, entityId, uuid, npcName, sendTo);
    }

    /**
     * Generates the overlay packet for entities.
     * @param playerId The entity the packet is about
     * @param sendTo Whom is sent the packet.
     */
    public static void sendPlayerOverlayPacket(
            final int playerId,
            final @NotNull List<Player> sendTo
    ) {
        // https://minecraft.wiki/w/Java_Edition_protocol/Entity_metadata#Avatar
        final byte mask = 0x01 | 0x02 | 0x04 | 0x08 | 0x010 | 0x020 | 0x40;
        if (NMSHandlers.getVersion().isLowerOrEqual(MinecraftVersion.v1_21_8)) NMSHandlers.getHandler().getPacketHandler().sendSharedEntityData(playerId, Map.of(17, mask), sendTo);
        else NMSHandlers.getHandler().getPacketHandler().sendSharedEntityData(playerId, Map.of(16, mask), sendTo);
    }

    /**
     * Removes a fake player from being seen by players.
     * @param player Which gameprofile to wrap for removing the player.
     * @param uuid What is the fake player UUID
     * @param sendTo Whom to send the packet to
     */
    @SuppressWarnings("deprecation")
    public static void sendRemovePlayerPacket(
            final Player player,
            final UUID uuid,
            final List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketHandler().sendPlayerInfoRemovePacket(uuid, sendTo);
    }

    public static void sendLeashPacket(
            final int leashedEntity,
            final int entityId,
            final Location location
    ) {
        sendLeashPacket(leashedEntity, entityId, getViewers(location));
    }

    /**
     * Sends a movement packet from one location to another
     * @param entityId Entity this will affect
     * @param from Previous location
     * @param to New location
     * @param onGround If the movement is on the ground
     * @param sendTo Whom to send the packet to
     */
    public static void sendMovePacket(
            final int entityId,
            final @NotNull Location from,
            final @NotNull Location to,
            final boolean onGround,
            @NotNull List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketHandler().sendMovePacket(entityId, from, to, onGround, sendTo);
    }

    public static void sendEntityScalePacket(
        int entityId,
        double scale,
        List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketHandler().sendEntityScalePacket(entityId, scale, sendTo);
    }

    // For future transition to display entities
    public static void sendDisplayEntityMetadataPacket(
            int entityid,
            ItemStack backpackItem,
            List<Player> sendTo) {
        // TODO: Make the default values adjustable
        Vector3f translation = new Vector3f(0, 3, 0);
        Vector3f scale = new Vector3f(1, 1, 1);
        Quaternionf rotationLeft = new Quaternionf();
        Quaternionf rotationRight = new Quaternionf();
        Display.Billboard billboard = Display.Billboard.FIXED;
        int blockLight = 15;
        int skylight = 15;
        int viewRange = Settings.getViewDistance();
        int width = 0;
        int height = 0;
        ItemDisplay.ItemDisplayTransform transform = ItemDisplay.ItemDisplayTransform.HEAD;

        NMSHandlers.getHandler().getPacketHandler().sendItemDisplayMetadata(
                entityid,
                translation,
                scale,
                rotationLeft,
                rotationRight,
                billboard,
                blockLight,
                skylight,
                viewRange,
                width,
                height,
                transform,
                backpackItem,
                sendTo
        );
    }

    /**
     * Gets the nearby players (or viewers) of a location through the view distance set in the config. If the view distance is 0, it will return all players in the world.
     * @param location
     * @return
     */
    @NotNull
    public static List<Player> getViewers(@NotNull Location location) {
        return PacketManager.getViewers(location, Settings.getViewDistance());
    }
}
