package com.hibiscusmc.hmccosmetics.user.manager;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.Wardrobe;
import com.hibiscusmc.hmccosmetics.config.WardrobeLocation;
import com.hibiscusmc.hmccosmetics.config.WardrobeSettings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBalloonType;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticSkinType;
import com.hibiscusmc.hmccosmetics.gui.Menu;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.HMCCInventoryUtils;
import com.hibiscusmc.hmccosmetics.util.HMCCServerUtils;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import lombok.Getter;
import lombok.Setter;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import me.lojosho.hibiscuscommons.nms.NMSPacketBuilder;
import me.lojosho.hibiscuscommons.nms.NMSPacketSender;
import me.lojosho.hibiscuscommons.packets.wrapper.PacketWrapper;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class UserWardrobeManager {

    @Getter
    private final int NPC_ID;
    @Getter
    private final int ARMORSTAND_ID;
    @Getter
    private final UUID WARDROBE_UUID;
    @Getter
    private String npcName;
    @Getter
    private GameMode originalGamemode;
    @Getter
    private final CosmeticUser user;
    @Getter
    private final Wardrobe wardrobe;
    @Getter
    private final WardrobeLocation wardrobeLocation;
    @Getter
    private final Location viewingLocation;
    @Getter
    private final Location npcLocation;
    @Getter
    private Location exitLocation;
    @Getter
    private BossBar bossBar;
    @Getter
    private boolean active;
    @Setter
    @Getter
    private WardrobeStatus wardrobeStatus;
    @Getter
    @Setter
    private Menu lastOpenMenu;

    private final NMSPacketBuilder packetBuilder = NMSHandlers.getHandler().getPacketBuilder();
    private final NMSPacketSender packetSender = NMSHandlers.getHandler().getPacketSender();

    /** The skin item currently previewed in the NPC's mainhand. */
    private ItemStack previewSkinItem = null;
    /** Snapshot of the equipped skins (groupKey → cosmeticId) from the previous tick for change detection. */
    private Map<String, String> lastEquippedSkinSnapshot = new HashMap<>();


    public UserWardrobeManager(CosmeticUser user, Wardrobe wardrobe) {
        NPC_ID = me.lojosho.hibiscuscommons.util.ServerUtils.getNextEntityId();
        ARMORSTAND_ID = me.lojosho.hibiscuscommons.util.ServerUtils.getNextEntityId();
        WARDROBE_UUID = UUID.randomUUID();
        this.user = user;

        this.wardrobe = wardrobe;
        this.wardrobeLocation = wardrobe.getLocation();

        this.exitLocation = wardrobeLocation.getLeaveLocation();
        this.viewingLocation = wardrobeLocation.getViewerLocation();
        this.npcLocation = wardrobeLocation.getNpcLocation();

        String defaultMenu = wardrobe.getDefaultMenu();
        if (defaultMenu != null && Menus.hasMenu(defaultMenu)) this.lastOpenMenu = Menus.getMenu(defaultMenu);
        else this.lastOpenMenu = Menus.getDefaultMenu();

        wardrobeStatus = WardrobeStatus.SETUP;
    }

    public void start() {
        setWardrobeStatus(WardrobeStatus.STARTING);
        Player player = user.getPlayer();

        this.originalGamemode = player.getGameMode();
        if (WardrobeSettings.isReturnLastLocation()) {
            this.exitLocation = player.getLocation().clone();
        }

        user.hidePlayer();
        player.setAllowFlight(true);
        List<Player> viewer = Collections.singletonList(player);
        List<Player> outsideViewers = HMCCPacketManager.getViewers(viewingLocation);
        outsideViewers.remove(player);

        MessagesUtil.sendMessage(player, "opened-wardrobe");

        Runnable run = () -> {
            if (!player.isOnline()) {
                end();
                return;
            }

            List<PacketWrapper> viewerPackets = new ArrayList<>();

            // Armorstand
            viewerPackets.add(packetBuilder.buildEntitySpawnPacket(ARMORSTAND_ID, UUID.randomUUID(), EntityType.ARMOR_STAND, viewingLocation));
            viewerPackets.add(packetBuilder.buildEntityMetadataPacket(ARMORSTAND_ID, HMCCPacketManager.getInvisibleArmorStandData()));
            viewerPackets.add(packetBuilder.buildEntityTeleportPacket(ARMORSTAND_ID, viewingLocation.getX(), viewingLocation.getY(), viewingLocation.getZ(), viewingLocation.getYaw(), viewingLocation.getPitch(), false));
            viewerPackets.add(packetBuilder.buildEntityRotateHeadPacket(ARMORSTAND_ID, viewingLocation.getYaw()));

            // Player
            player.teleport(viewingLocation, PlayerTeleportEvent.TeleportCause.PLUGIN);
            player.setInvisible(true);
            viewerPackets.add(packetBuilder.buildPlayerGamemodeChangePacket(GameMode.SPECTATOR));
            viewerPackets.add(packetBuilder.buildEntityCameraPacket(ARMORSTAND_ID));

            // NPC
            npcName = "Mannequin";
            if (npcName.length() >= 16) {
                npcName = npcName.substring(0, 15);
            }
            viewerPackets.add(packetBuilder.buildPlayerInfoAddPacket(player, NPC_ID, WARDROBE_UUID, npcName));
            viewerPackets.add(packetBuilder.buildEntitySpawnPacket(NPC_ID, WARDROBE_UUID, EntityType.PLAYER, npcLocation));
            viewerPackets.add(packetBuilder.buildEntityMetadataPacket(NPC_ID, HMCCPacketManager.getPlayerOverlayMetaData()));
            viewerPackets.add(packetBuilder.buildPlayerScoreboardRemovePacket(player, npcName));
            viewerPackets.add(packetBuilder.buildPlayerScoreboardCreatePacket(player, npcName));
            viewerPackets.add(packetBuilder.buildPlayerScoreboardAddPlayersPacket(player, npcName));
            AttributeInstance scaleAttribute = user.getPlayer().getAttribute(Attribute.GENERIC_SCALE);
            if (scaleAttribute != null) {
                viewerPackets.add(packetBuilder.buildEntityAttributePacket(NPC_ID, Attribute.GENERIC_SCALE, scaleAttribute.getValue()));
            }

            // Location
            viewerPackets.add(packetBuilder.buildEntityRotateHeadPacket(NPC_ID, npcLocation.getYaw()));
            viewerPackets.add(packetBuilder.buildEntityRotatePacket(NPC_ID, npcLocation.getYaw(), npcLocation.getPitch(), true));

            // Misc
            if (user.hasCosmeticInSlot(CosmeticSlot.BACKPACK)) {
                if (user.getUserBackpackManager() == null) user.respawnBackpack();
                if (user.isBackpackSpawned()) {
                    user.getUserBackpackManager().getEntityManager().teleport(npcLocation.clone().add(0, 2, 0));

                    viewerPackets.add(packetBuilder.buildEntityEquipmentSlotUpdatePacket(user.getUserBackpackManager().getFirstArmorStandId(), Map.of(EquipmentSlot.HEAD, user.getUserCosmeticItem(user.getCosmetic(CosmeticSlot.BACKPACK)))));
                    viewerPackets.add(packetBuilder.buildEntityMountPacket(NPC_ID, new int[]{user.getUserBackpackManager().getFirstArmorStandId()}));
                }
            }

            // Weapon skin preview
            initPreviewSkin();
            if (previewSkinItem != null) {
                viewerPackets.add(packetBuilder.buildEntityEquipmentSlotUpdatePacket(NPC_ID, Map.of(EquipmentSlot.HAND, previewSkinItem)));
            }

            packetSender.sendBundle(viewerPackets, viewer);

            if (user.hasCosmeticInSlot(CosmeticSlot.BALLOON)) {
                if (user.getBalloonManager() == null) user.respawnBalloon();
                if (user.isBalloonSpawned()) {
                    CosmeticBalloonType cosmetic = (CosmeticBalloonType) user.getCosmetic(CosmeticSlot.BALLOON);
                    user.getBalloonManager().sendRemoveLeashPacket(viewer);
                    user.getBalloonManager().sendLeashPacket(NPC_ID);

                    Location balloonLocation = npcLocation.clone().add(cosmetic.getBalloonOffset());
                    HMCCPacketManager.sendTeleportPacket(user.getBalloonManager().getPufferfishBalloonId(), balloonLocation, false, viewer);
                    user.getBalloonManager().getModelEntity().teleport(balloonLocation);
                    user.getBalloonManager().setLocation(balloonLocation);
                }
            }

            if (WardrobeSettings.isEnabledBossbar()) {
                float progress = WardrobeSettings.getBossbarProgress();
                Component message = MessagesUtil.processStringNoKey(player, WardrobeSettings.getBossbarMessage());

                bossBar = BossBar.bossBar(message, progress, WardrobeSettings.getBossbarColor(), WardrobeSettings.getBossbarOverlay());
                player.showBossBar(bossBar);
            }

            if (WardrobeSettings.isEnterOpenMenu()) {
                Menu menu = Menus.getDefaultMenu();
                if (menu != null) menu.openMenu(user);
            }

            this.active = true;
            update();
            setWardrobeStatus(WardrobeStatus.RUNNING);
        };

        if (WardrobeSettings.isEnabledTransition()) {
            MessagesUtil.sendTitle(
                    user.getPlayer(),
                    WardrobeSettings.getTransitionText(),
                    WardrobeSettings.getTransitionFadeIn(),
                    WardrobeSettings.getTransitionStay(),
                    WardrobeSettings.getTransitionFadeOut()
            );
            Bukkit.getScheduler().runTaskLater(HMCCosmeticsPlugin.getInstance(), run, WardrobeSettings.getTransitionDelay());
        } else {
            run.run();
        }
    }

    public void end() {
        setWardrobeStatus(WardrobeStatus.STOPPING);
        Player player = user.getPlayer();

        List<Player> viewer = Collections.singletonList(player);

        if (player == null) return;
        MessagesUtil.sendMessage(player, "closed-wardrobe");

        Runnable run = () -> {
            this.active = false;

            for (Cosmetic cosmetic : user.getCosmetics()) {
                MessagesUtil.sendDebugMessages("Checking... " + cosmetic.getId());
                if (!user.canEquipCosmetic(cosmetic)) {
                    MessagesUtil.sendDebugMessages("Unable to keep " + cosmetic.getId());
                    user.removeCosmeticSlot(cosmetic.getSlot());
                }
            }

            // NPC
            if (user.isBalloonSpawned()) user.getBalloonManager().sendRemoveLeashPacket();
            HMCCPacketManager.sendEntityDestroyPacket(NPC_ID, viewer);
            HMCCPacketManager.sendRemovePlayerPacket(player, WARDROBE_UUID, viewer);

            // Player
            packetBuilder.buildEntityCameraPacket(player.getEntityId()).sendPacket(viewer);
            user.getPlayer().setInvisible(false);

            // Armorstand
            HMCCPacketManager.sendEntityDestroyPacket(ARMORSTAND_ID, viewer);

            if (WardrobeSettings.isForceExitGamemode()) {
                MessagesUtil.sendDebugMessages("Force Exit Gamemode " + WardrobeSettings.getExitGamemode());
                player.setGameMode(WardrobeSettings.getExitGamemode());
                packetBuilder.buildPlayerGamemodeChangePacket(WardrobeSettings.getExitGamemode()).sendPacket(viewer);
            } else {
                MessagesUtil.sendDebugMessages("Original Gamemode " + this.originalGamemode);
                player.setGameMode(this.originalGamemode);
                packetBuilder.buildPlayerGamemodeChangePacket(this.originalGamemode).sendPacket(viewer);
            }
            player.setAllowFlight(true);
            user.showPlayer();

            if (user.hasCosmeticInSlot(CosmeticSlot.BACKPACK)) {
                user.respawnBackpack();
            }

            player.teleport(Objects.requireNonNullElseGet(exitLocation, () -> player.getWorld().getSpawnLocation()), PlayerTeleportEvent.TeleportCause.PLUGIN);

            HashMap<EquipmentSlot, ItemStack> items = new HashMap<>();
            for (EquipmentSlot slot : HMCCInventoryUtils.getPlayerArmorSlots()) {
                ItemStack item = player.getInventory().getItem(slot);
                items.put(slot, item);
            }
            packetBuilder.buildEntityEquipmentSlotUpdatePacket(player.getEntityId(), items).sendPacket(viewer);

            if (WardrobeSettings.isEnabledBossbar()) {
                player.hideBossBar(bossBar);
            }

            user.updateCosmetic();
        };
        run.run();
    }

    private void update() {
        final AtomicInteger data = new AtomicInteger();

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                Player player = user.getPlayer();
                if (!active || player == null) {
                    MessagesUtil.sendDebugMessages("WardrobeEnd[user=" + user.getUniqueId() + ",reason=Active is false]");
                    this.cancel();
                    return;
                }
                MessagesUtil.sendDebugMessages("WardrobeUpdate[user=" + user.getUniqueId() + ",status=" + getWardrobeStatus() + "]");
                List<Player> viewer = Collections.singletonList(player);
                List<Player> outsideViewers = HMCCPacketManager.getViewers(viewingLocation);
                outsideViewers.remove(player);

                Location location = npcLocation;
                int yaw = data.get();
                location.setYaw(yaw);

                HMCCPacketManager.sendRotateHeadPacket(NPC_ID, location, viewer);
                user.hidePlayer();
                int rotationSpeed = WardrobeSettings.getRotationSpeed();
                int newYaw = HMCCServerUtils.getNextYaw(yaw - 30, rotationSpeed);
                location.setYaw(newYaw);
                packetBuilder.buildEntityRotatePacket(NPC_ID, newYaw, 0, false).sendPacket(viewer);
                int nextyaw = HMCCServerUtils.getNextYaw(yaw, rotationSpeed);
                data.set(nextyaw);

                for (CosmeticSlot slot : CosmeticSlot.values().values()) {
                    HMCCPacketManager.equipmentSlotUpdate(NPC_ID, user, slot, viewer);
                }

                // Dispatch particle cosmetics — CosmeticParticleType.dispatchUpdate
                // detects the active wardrobe and renders at the NPC location.
                user.updateCosmetic(CosmeticSlot.PARTICLE);

                // Weapon skin preview — sole controller of the NPC's mainhand slot
                tickPreviewSkin(viewer);

                if (user.hasCosmeticInSlot(CosmeticSlot.BACKPACK) && user.getUserBackpackManager() != null) {
                    HMCCPacketManager.sendTeleportPacket(user.getUserBackpackManager().getFirstArmorStandId(), location, false, viewer);
                    packetBuilder.buildEntityMountPacket(NPC_ID, new int[]{user.getUserBackpackManager().getFirstArmorStandId()}).sendPacket(viewer);
                    user.getUserBackpackManager().getEntityManager().setRotation(nextyaw);
                    HMCCPacketManager.sendEntityDestroyPacket(user.getUserBackpackManager().getFirstArmorStandId(), outsideViewers);
                }

                if (user.hasCosmeticInSlot(CosmeticSlot.BALLOON) && user.isBalloonSpawned()) {
                    user.getBalloonManager().sendRemoveLeashPacket(outsideViewers);
                    if (user.getBalloonManager().getBalloonType() != UserBalloonManager.BalloonType.MODELENGINE) {
                        HMCCPacketManager.sendEntityDestroyPacket(user.getBalloonManager().getModelId(), outsideViewers);
                    }
                    user.getBalloonManager().sendLeashPacket(NPC_ID);
                }

                if (WardrobeSettings.isEquipPumpkin()) {
                    HMCCPacketManager.equipmentSlotUpdate(user.getPlayer().getEntityId(), EquipmentSlot.HEAD, new ItemStack(Material.CARVED_PUMPKIN), viewer);
                } else {
                    HMCCPacketManager.equipmentSlotUpdate(user.getPlayer(), true, viewer);
                }
            }
        };

        runnable.runTaskTimer(HMCCosmeticsPlugin.getInstance(), 0, 2);
    }

    /**
     * Snapshots the equipped skins and picks a random one to preview on entry.
     * Called once when the wardrobe opens.
     */
    private void initPreviewSkin() {
        Map<String, CosmeticSkinType> equippedSkins = user.getEquippedSkins();
        MessagesUtil.sendDebugMessages("WardrobeSkinPreview[user=" + user.getUniqueId() + ",skins=" + equippedSkins.size() + "]");
        lastEquippedSkinSnapshot = buildSnapshot(equippedSkins);
        if (!equippedSkins.isEmpty()) {
            List<CosmeticSkinType> skins = new ArrayList<>(equippedSkins.values());
            CosmeticSkinType chosen = skins.get(ThreadLocalRandom.current().nextInt(skins.size()));
            ItemStack item = chosen.getItem();
            MessagesUtil.sendDebugMessages("WardrobeSkinPreview[chosen=" + chosen.getId() + ",item=" + (item == null ? "null" : item.getType()) + "]");
            if (item != null) previewSkinItem = item;
        }
    }

    /**
     * Detects skin changes each tick (new skin equipped or same group replaced by a different one)
     * and switches the preview to the most recently equipped skin.
     * Sole controller of the NPC's mainhand slot.
     */
    private void tickPreviewSkin(List<Player> viewer) {
        Map<String, CosmeticSkinType> equippedSkins = user.getEquippedSkins();

        if (equippedSkins.isEmpty()) {
            previewSkinItem = null;
            lastEquippedSkinSnapshot = new HashMap<>();
        } else {
            // Find any group key whose skin has changed since the last tick
            CosmeticSkinType mostRecent = null;
            for (Map.Entry<String, CosmeticSkinType> entry : equippedSkins.entrySet()) {
                String prevId = lastEquippedSkinSnapshot.get(entry.getKey());
                if (!entry.getValue().getId().equals(prevId)) {
                    mostRecent = entry.getValue();
                    break;
                }
            }
            if (mostRecent != null && mostRecent.getItem() != null) {
                previewSkinItem = mostRecent.getItem();
            }
            lastEquippedSkinSnapshot = buildSnapshot(equippedSkins);
        }

        MessagesUtil.sendDebugMessages("WardrobeSkinTick[user=" + user.getUniqueId() + ",item=" + (previewSkinItem == null ? "null" : previewSkinItem.getType()) + "]");
        if (previewSkinItem != null) {
            HMCCPacketManager.equipmentSlotUpdate(NPC_ID, EquipmentSlot.HAND, previewSkinItem, viewer);
        }
    }

    private static Map<String, String> buildSnapshot(Map<String, CosmeticSkinType> skins) {
        Map<String, String> snapshot = new HashMap<>();
        for (Map.Entry<String, CosmeticSkinType> e : skins.entrySet()) {
            snapshot.put(e.getKey(), e.getValue().getId());
        }
        return snapshot;
    }

    public enum WardrobeStatus {
        SETUP,
        STARTING,
        RUNNING,
        STOPPING,
    }
}
