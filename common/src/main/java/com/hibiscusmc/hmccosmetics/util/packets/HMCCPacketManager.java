package com.hibiscusmc.hmccosmetics.util.packets;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.HMCCInventoryUtils;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import me.lojosho.hibiscuscommons.nms.MinecraftVersion;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import me.lojosho.hibiscuscommons.nms.NMSPacketBuilder;
import me.lojosho.hibiscuscommons.packets.wrapper.PacketWrapper;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class HMCCPacketManager {

    private static final Map<Integer, Number> CLOUD_EFFECT_INVISIBLE_DATA_VALUES;
    private static final Map<Integer, Number> GENERIC_INVISIBLE_DATA_VALUES;

    static {
        Map<Integer, Number> cloud = new LinkedHashMap<>();
        cloud.put(0, (byte) 0x20);
        cloud.put(8, 0f);
        CLOUD_EFFECT_INVISIBLE_DATA_VALUES = Collections.unmodifiableMap(cloud);

        Map<Integer, Number> generic = new LinkedHashMap<>();
        generic.put(0, (byte) 0x20);
        GENERIC_INVISIBLE_DATA_VALUES = Collections.unmodifiableMap(generic);
    }
    private static final List<CosmeticSlot> EQUIPMENT_SLOTS = List.of(CosmeticSlot.HELMET, CosmeticSlot.CHESTPLATE, CosmeticSlot.LEGGINGS, CosmeticSlot.BOOTS, CosmeticSlot.OFFHAND);

    public static void sendEntitySpawnPacket(
            final @NotNull Location location,
            final int entityId,
            final EntityType entityType,
            final UUID uuid,
            final @NotNull List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntitySpawnPacket(entityId, uuid, entityType, location).sendPacket(sendTo);
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
        NMSHandlers.getHandler().getPacketBuilder().buildEntityEquipmentSlotUpdatePacket(player.getEntityId(), items).sendPacket(sendTo);
    }

    public static void equipmentSlotUpdate(
            int entityId,
            CosmeticUser user,
            CosmeticSlot cosmeticSlot,
            List<Player> sendTo
    ) {
        if (!EQUIPMENT_SLOTS.contains(cosmeticSlot)) return;
        NMSHandlers.getHandler().getPacketBuilder().buildEntityEquipmentSlotUpdatePacket(entityId, Map.of(HMCCInventoryUtils.getEquipmentSlot(cosmeticSlot), user.getUserCosmeticItem(cosmeticSlot))).sendPacket(sendTo);
    }

    public static void equipmentSlotUpdate(
            int entityId,
            EquipmentSlot equipmentSlot,
            ItemStack itemStack,
            List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntityEquipmentSlotUpdatePacket(entityId, Map.of(equipmentSlot, itemStack)).sendPacket(sendTo);
    }

    public static void sendInvisibilityPacket(
            int entityId,
            List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntityMetadataPacket(entityId, Map.of(0, (byte) 0x20)).sendPacket(sendTo);
    }

    public static void spawnInvisibleEntity(
            int entityId,
            EntityType entityType,
            Location location,
            UUID uuid,
            List<Player> sendTo
    ) {
        NMSPacketBuilder packetBuilder = NMSHandlers.getHandler().getPacketBuilder();
        List<PacketWrapper> packets = new ArrayList<>();
        packets.add(packetBuilder.buildEntitySpawnPacket(entityId, uuid, entityType, location));
        packets.add(packetBuilder.buildEntityMetadataPacket(entityId, GENERIC_INVISIBLE_DATA_VALUES));
        NMSHandlers.getHandler().getPacketSender().sendBundle(packets, sendTo);
    }

    public static List<PacketWrapper> getCloudHandleEffect(
            int entityId,
            Location location,
            UUID uuid
    ) {
        NMSPacketBuilder packetBuilder = NMSHandlers.getHandler().getPacketBuilder();
        List<PacketWrapper> packets = new ArrayList<>();
        packets.add(packetBuilder.buildEntitySpawnPacket(entityId, uuid, EntityType.AREA_EFFECT_CLOUD, location));
        packets.add(packetBuilder.buildEntityMetadataPacket(entityId, CLOUD_EFFECT_INVISIBLE_DATA_VALUES));
        return packets;
    }

    public static List<PacketWrapper> getInvisibleArmorStand(
            int entityId,
            Location location,
            UUID uuid
    ) {
        NMSPacketBuilder packetBuilder = NMSHandlers.getHandler().getPacketBuilder();
        List<PacketWrapper> packets = new ArrayList<>();
        packets.add(packetBuilder.buildEntitySpawnPacket(entityId, uuid, EntityType.ARMOR_STAND, location));
        packets.add(packetBuilder.buildEntityMetadataPacket(entityId, getInvisibleArmorStandData()));
        return packets;
    }

    /**
     * Non-bundled metadata packet (send armorstand metadata after the fact).
     */
    public static void sendArmorstandMetadata(
            int entityId,
            List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntityMetadataPacket(entityId, getInvisibleArmorStandData()).sendPacket(sendTo);
    }

    public static Map<Integer, Number> getInvisibleArmorStandData() {
        Map<Integer, Number> m = new LinkedHashMap<>();
        m.put(0, getMask());
        m.put(15, (byte) 0x10);
        return m;
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
        NMSHandlers.getHandler().getPacketBuilder().buildEntityRotatePacket(entityId, location.getYaw(), location.getPitch(), onGround).sendPacket(sendTo);
    }

    public static void sendRotationPacket(
            int entityId,
            int yaw,
            boolean onGround,
            @NotNull List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntityRotatePacket(entityId, yaw, 0, onGround).sendPacket(sendTo);
    }

    public static void sendTeleportPacket(int entityId, Location location, boolean onGround, List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntityTeleportPacket(
                entityId,
                location.x(), location.y(), location.z(),
                location.getYaw(), location.getPitch(),
                onGround
        ).sendPacket(sendTo);
    }

    public static void sendRidingPacket(
            final int mountId,
            final int passengerId,
            final Location location
    ) {
        sendRidingPacket(mountId, passengerId, getViewers(location));
    }

    public static void sendRidingPacket(
            final int mountId,
            final int[] passengerIds,
            final @NotNull List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntityMountPacket(mountId, passengerIds).sendPacket(sendTo);
    }

    public static void sendRidingPacket(
            final int mountId,
            final int passengerId,
            final @NotNull List<Player> sendTo
    ) {
        sendRidingPacket(mountId, new int[]{passengerId}, sendTo);
    }

    public static void sendFakePlayerInfoPacket(
            final Player skinnedPlayer,
            final int entityId,
            final UUID uuid,
            final String npcName,
            final List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketBuilder().buildPlayerInfoAddPacket(skinnedPlayer, entityId, uuid, npcName).sendPacket(sendTo);
    }

    public static void sendPlayerOverlayPacket(
            final int playerId,
            final @NotNull List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntityMetadataPacket(playerId, getPlayerOverlayMetaData()).sendPacket(sendTo);
    }

    public static Map<Integer, Number> getPlayerOverlayMetaData() {
        final byte mask = 0x01 | 0x02 | 0x04 | 0x08 | 0x010 | 0x020 | 0x40;
        Map<Integer, Number> m = new LinkedHashMap<>();
        if (NMSHandlers.getVersion().isLowerOrEqual(MinecraftVersion.v1_21_8)) m.put(17, mask);
        else m.put(16, mask);
        return m;
    }

    @SuppressWarnings("deprecation")
    public static void sendRemovePlayerPacket(
            final Player player,
            final UUID uuid,
            final List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketBuilder().buildPlayerInfoRemovePacket(List.of(uuid)).sendPacket(sendTo);
    }

    public static void sendLeashPacket(
            final int leashedEntity,
            final int entityId,
            final Location location
    ) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntityLeashPacket(leashedEntity, entityId).sendPacket(getViewers(location));
    }

    public static void sendLeashPacket(
            final int leashedEntity,
            final int entityId,
            final List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntityLeashPacket(leashedEntity, entityId).sendPacket(sendTo);
    }

    public static void sendMovePacket(
            final int entityId,
            final @NotNull Location from,
            final @NotNull Location to,
            final boolean onGround,
            @NotNull List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntityMovePacket(entityId, from, to, onGround).sendPacket(sendTo);
    }

    public static void sendEntityScalePacket(
            int entityId,
            double scale,
            List<Player> sendTo
    ) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntityAttributePacket(entityId, Attribute.GENERIC_SCALE, scale).sendPacket(sendTo);
    }

    public static void sendEntityDestroyPacket(int entityId, List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntityDestroyPacket(IntList.of(entityId)).sendPacket(sendTo);
    }

    public static void sendEntityDestroyPacket(List<Integer> entities, List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntityDestroyPacket(new IntArrayList(entities)).sendPacket(sendTo);
    }

    public static void sendRotateHeadPacket(int entityId, Location location, List<Player> sendTo) {
        NMSHandlers.getHandler().getPacketBuilder().buildEntityRotateHeadPacket(entityId, location.getYaw()).sendPacket(sendTo);
    }

    @NotNull
    public static List<Player> getViewers(@NotNull Location location) {
        return new ArrayList<>(HMCCosmeticsPlugin.getInstance().getPlayerSearchManager().getPlayersInRange(location, Settings.getViewDistance()));
    }
}
