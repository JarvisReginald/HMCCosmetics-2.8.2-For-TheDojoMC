package com.hibiscusmc.hmccosmetics.user.manager;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.Wardrobe;
import com.hibiscusmc.hmccosmetics.config.WardrobeLocation;
import com.hibiscusmc.hmccosmetics.config.WardrobeSettings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBalloonType;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticParticleType;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticSkinType;
import com.hibiscusmc.hmccosmetics.gui.Menu;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.HMCCInventoryUtils;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.HMCCServerUtils;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import dev.esophose.playerparticles.api.PlayerParticlesAPI;
import dev.esophose.playerparticles.particles.FixedParticleEffect;
import dev.esophose.playerparticles.particles.ParticleEffect;
import dev.esophose.playerparticles.particles.ParticlePair;
import dev.esophose.playerparticles.particles.data.ColorTransition;
import dev.esophose.playerparticles.particles.data.NoteColor;
import dev.esophose.playerparticles.particles.data.OrdinaryColor;
import dev.esophose.playerparticles.styles.ParticleStyle;
import lombok.Getter;
import lombok.Setter;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import com.hibiscusmc.hmccosmetics.util.packets.PacketManager;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static com.hibiscusmc.hmccosmetics.user.CosmeticUser.getColorTransition;

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

    private String lastWardrobeParticleKey = null;
    private Integer wardrobeFixedEffectId = null;

    private BukkitRunnable wardrobeRunnable;
    private boolean ending = false;

    private List<ParticlePair> savedRealPlayerParticles = new ArrayList<>();
    private boolean realPlayerParticlesTemporarilyCleared = false;

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

        clearRealPlayerParticlesTemporarily(player);

        this.originalGamemode = player.getGameMode();
        if (WardrobeSettings.isReturnLastLocation()) {
            this.exitLocation = player.getLocation().clone();
        }

        user.hidePlayer();
        if (!Bukkit.getServer().getAllowFlight()) player.setAllowFlight(true);
        List<Player> viewer = Collections.singletonList(player);
        List<Player> outsideViewers = HMCCPacketManager.getViewers(viewingLocation);
        outsideViewers.remove(player);

        MessagesUtil.sendMessage(player, "opened-wardrobe");

        Runnable run = () -> {
            if (!player.isOnline()) {
                end();
                return;
            }

            // Armorstand
            HMCCPacketManager.sendEntitySpawnPacket(viewingLocation, ARMORSTAND_ID, EntityType.ARMOR_STAND, UUID.randomUUID(), viewer);
            HMCCPacketManager.sendArmorstandMetadata(ARMORSTAND_ID, viewer);
            NMSHandlers.getHandler().getPacketHandler().sendTeleportPacket(ARMORSTAND_ID, viewingLocation.getX(), viewingLocation.getY(), viewingLocation.getZ(), viewingLocation.getYaw(), viewingLocation.getPitch(), false, viewer);
            //NMSHandlers.getHandler().getPacketHandler().sendLookAtPacket(ARMORSTAND_ID, viewingLocation, viewer);
            HMCCPacketManager.sendRotateHeadPacket(ARMORSTAND_ID, viewingLocation, viewer);

            // Player
            player.teleport(viewingLocation, PlayerTeleportEvent.TeleportCause.PLUGIN);
            player.setInvisible(true);
            HMCCPacketManager.gamemodeChangePacket(player, GameMode.SPECTATOR);
            HMCCPacketManager.sendCameraPacket(ARMORSTAND_ID, viewer);

            // NPC
            npcName = "WardrobeNPC-" + NPC_ID;
            while (npcName.length() > 16) {
                npcName = npcName.substring(16);
            }
            HMCCPacketManager.sendFakePlayerInfoPacket(player, NPC_ID, WARDROBE_UUID, npcName, viewer);

            // NPC 2
            Bukkit.getScheduler().runTaskLater(HMCCosmeticsPlugin.getInstance(), () -> {
                if (!user.isInWardrobe()) return; // If a player exits the wardrobe right away, no need to spawn the NPC
                HMCCPacketManager.sendFakePlayerSpawnPacket(npcLocation, WARDROBE_UUID, NPC_ID, viewer);
                HMCCPacketManager.sendPlayerOverlayPacket(NPC_ID, viewer);
                MessagesUtil.sendDebugMessages("Spawned Fake Player on " + npcLocation);
                NMSHandlers.getHandler().getPacketHandler().sendScoreboardHideNamePacket(player, npcName);
                AttributeInstance scaleAttribute = user.getPlayer().getAttribute(Attribute.GENERIC_SCALE);
                if (scaleAttribute != null) {
                    HMCCPacketManager.sendEntityScalePacket(NPC_ID, scaleAttribute.getValue(), viewer);
                }
            }, 4);

            // Location
            HMCCPacketManager.sendRotateHeadPacket(NPC_ID, npcLocation, viewer);
            HMCCPacketManager.sendRotationPacket(NPC_ID, npcLocation, true, viewer);

            // Misc
            if (user.hasCosmeticInSlot(CosmeticSlot.BACKPACK)) {
                // Maybe null as backpack maybe despawned before entering
                if (user.getUserBackpackManager() == null) user.respawnBackpack();
                if (user.isBackpackSpawned()) {
                    user.getUserBackpackManager().getEntityManager().teleport(npcLocation.clone());
                    PacketManager.equipmentSlotUpdate(user.getUserBackpackManager().getFirstArmorStandId(), EquipmentSlot.HEAD, user.getUserCosmeticItem(user.getCosmetic(CosmeticSlot.BACKPACK)), viewer);
                    HMCCPacketManager.ridingMountPacket(NPC_ID, user.getUserBackpackManager().getFirstArmorStandId(), viewer);
                }
            }

            if (user.hasCosmeticInSlot(CosmeticSlot.BALLOON)) {
                if (user.getBalloonManager() == null) user.respawnBalloon();
                if (user.isBalloonSpawned()) {
                    CosmeticBalloonType cosmetic = (CosmeticBalloonType) user.getCosmetic(CosmeticSlot.BALLOON);
                    user.getBalloonManager().sendRemoveLeashPacket(viewer);
                    user.getBalloonManager().sendLeashPacket(NPC_ID);
                    //PacketManager.sendLeashPacket(VIEWER.getBalloonEntity().getModelId(), NPC_ID, viewer);

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
                //Audience target = BukkitAudiences.create(HMCCosmeticsPlugin.getInstance()).player(player);

                player.showBossBar(bossBar);
            }

            if (WardrobeSettings.isEnterOpenMenu()) {
                Menu menu = Menus.getDefaultMenu();
                if (menu != null) menu.openMenu(user);
            }

            createOrLoadWardrobeFixedEffectOnce(player);

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
        if (ending) return;
        ending = true;

        setWardrobeStatus(WardrobeStatus.STOPPING);

        Player player = user.getPlayer();
        if (player == null) return;

        List<Player> viewer = Collections.singletonList(player);

        // 0) STOP SUBITO: evita che update() continui a mandare packet mentre stai uscendo
        this.active = false;
        // Se hai salvato la runnable, cancellala qui (consigliato)
        if (wardrobeRunnable != null) {
            wardrobeRunnable.cancel();
            wardrobeRunnable = null;
        }

        // 1) PRIMA COSA ASSOLUTA: ripristina subito lo stato “normale” nello stesso tick
        try {
            // camera back
            HMCCPacketManager.sendCameraPacket(player.getEntityId(), viewer);

            // gamemode normale subito
            GameMode gm = WardrobeSettings.isForceExitGamemode()
                    ? WardrobeSettings.getExitGamemode()
                    : this.originalGamemode;

            player.setGameMode(gm);
            HMCCPacketManager.gamemodeChangePacket(player, gm);
            player.setAllowFlight(true);

            // visibilità + show
            player.setInvisible(false);
            user.showPlayer();

            // teleport subito
            Location to = Objects.requireNonNullElseGet(exitLocation, () -> player.getWorld().getSpawnLocation());
            player.teleport(to, PlayerTeleportEvent.TeleportCause.PLUGIN);

        } catch (Throwable ignored) {
        }

        // 2) Subito dopo: togli particles del wardrobe (non del player vero)
        try {
            removeWardrobeFixedParticles(player);
            try {
                // rimuove tutti i fixed effects di quel player (solo se sei ok a farlo mentre sei nel wardrobe)
                PlayerParticlesAPI ppApi = HMCCosmeticsPlugin.getInstance().getPpAPI();
                if (ppApi != null) {
                    for (FixedParticleEffect f : ppApi.getFixedParticleEffects(player)) {
                        // rimuovi SOLO quelli che hai creato tu (id >= 100000) se vuoi essere safe:
                        if (f.getId() >= 100000) {
                            ppApi.removeFixedEffect(player, f.getId());
                        }
                    }
                }
            } catch (Throwable ignored) {
            }
        } catch (Throwable ignored) {
        }

        // Ora che è tornato normale, manda il messaggio (evita glitch font)
        MessagesUtil.sendMessage(player, "closed-wardrobe");

        // 3) Tick dopo: distruggi entità fake e roba “visiva” lato client
        Bukkit.getScheduler().runTask(HMCCosmeticsPlugin.getInstance(), () -> {
            if (!player.isOnline()) return;

            try {
                HMCCPacketManager.sendEntityDestroyPacket(NPC_ID, viewer);
                HMCCPacketManager.sendRemovePlayerPacket(player, WARDROBE_UUID, viewer);
                HMCCPacketManager.sendEntityDestroyPacket(ARMORSTAND_ID, viewer);
            } catch (Throwable ignored) {
            }

            // Bossbar dopo
            if (WardrobeSettings.isEnabledBossbar() && bossBar != null) {
                try {
                    player.hideBossBar(bossBar);
                } catch (Throwable ignored) {
                }
            }

            // 4) Tick dopo ancora: roba “pesante”
            Bukkit.getScheduler().runTask(HMCCosmeticsPlugin.getInstance(), () -> {
                if (!player.isOnline()) return;

                // Ripristina particles del player vero (se le avevi tolte all’entrata)
                try {
                    restoreRealPlayerParticles(player);
                } catch (Throwable ignored) {
                }

                // Re-apply the current particle cosmetic (may have changed during wardrobe)
                try {
                    user.respawnParticle();
                } catch (Throwable ignored) {
                }

                // ripristina armor/equipment al player viewer
                try {
                    HashMap<EquipmentSlot, ItemStack> items = new HashMap<>();
                    for (EquipmentSlot slot : HMCCInventoryUtils.getPlayerArmorSlots()) {
                        items.put(slot, player.getInventory().getItem(slot));
                    }
                    HMCCPacketManager.equipmentSlotUpdate(player.getEntityId(), items, viewer);
                } catch (Throwable ignored) {
                }

                // Respawn cosmetic entities “attorno” (se serve)
                try {
                    if (user.hasCosmeticInSlot(CosmeticSlot.BACKPACK)) {
                        user.respawnBackpack();
                    }
                } catch (Throwable ignored) {
                }

                // Pulisci i cosmetici temporanei (questa parte può essere costosa)
                try {
                    for (Cosmetic cosmetic : user.getCosmetics()) {
                        if (!user.canEquipCosmetic(cosmetic)) {
                            user.removeCosmeticSlot(cosmetic.getSlot());
                        }
                    }
                } catch (Throwable ignored) {
                }

                // Update finale
                try {
                    user.updateCosmetic();
                } catch (Throwable ignored) {
                }
            });
        });
    }

    private void update() {
        final AtomicInteger data = new AtomicInteger();

        wardrobeRunnable = new BukkitRunnable() {
            @Override
            public void run() {
                if (ending) {
                    this.cancel();
                    return;
                }

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

                Location location = npcLocation.clone();
                int yaw = data.get();
                location.setYaw(yaw);

                HMCCPacketManager.sendRotateHeadPacket(NPC_ID, location, viewer);
                user.hidePlayer();
                int rotationSpeed = WardrobeSettings.getRotationSpeed();
                int newYaw = HMCCServerUtils.getNextYaw(yaw - 30, rotationSpeed);
                location.setYaw(newYaw);
                NMSHandlers.getHandler().getPacketHandler().sendRotationPacket(NPC_ID, newYaw, 0, false, viewer);
                HMCCPacketManager.sendRotationPacket(NPC_ID, newYaw, true, viewer);
                int nextyaw = HMCCServerUtils.getNextYaw(yaw, rotationSpeed);
                data.set(nextyaw);

                for (CosmeticSlot slot : CosmeticSlot.values().values()) {
                    HMCCPacketManager.equipmentSlotUpdate(NPC_ID, user, slot, viewer);
                }

                applyWardrobeSkinInHand(viewer);

                if (user.hasCosmeticInSlot(CosmeticSlot.BACKPACK) && user.getUserBackpackManager() != null) {
                    HMCCPacketManager.sendTeleportPacket(user.getUserBackpackManager().getFirstArmorStandId(), location, false, viewer);
                    HMCCPacketManager.ridingMountPacket(NPC_ID, user.getUserBackpackManager().getFirstArmorStandId(), viewer);
                    user.getUserBackpackManager().getEntityManager().setRotation(nextyaw);
                    HMCCPacketManager.sendEntityDestroyPacket(user.getUserBackpackManager().getFirstArmorStandId(), outsideViewers);
                }

                if (user.hasCosmeticInSlot(CosmeticSlot.BALLOON) && user.isBalloonSpawned()) {
                    // The two lines below broke, solved by listening to PlayerCosmeticPostEquipEvent
                    //PacketManager.sendTeleportPacket(user.getBalloonManager().getPufferfishBalloonId(), npcLocation.add(Settings.getBalloonOffset()), false, viewer);
                    //user.getBalloonManager().getModelEntity().teleport(npcLocation.add(Settings.getBalloonOffset()));
                    user.getBalloonManager().sendRemoveLeashPacket(outsideViewers);
                    if (user.getBalloonManager().getBalloonType() != UserBalloonManager.BalloonType.MODELENGINE) {
                        HMCCPacketManager.sendEntityDestroyPacket(user.getBalloonManager().getModelId(), outsideViewers);
                    }
                    user.getBalloonManager().sendLeashPacket(NPC_ID);
                }

                Player sender = user.getPlayer();
                PlayerParticlesAPI ppApi = HMCCosmeticsPlugin.getInstance().getPpAPI();
                if (sender == null || ppApi == null) return;

                if (wardrobeFixedEffectId == null) {
                    // Se il player ha appena equipaggiato una particle, ricrea/ricarica l'effetto
                    if (user.hasCosmeticInSlot(CosmeticSlot.PARTICLE)) {
                        createOrLoadWardrobeFixedEffectOnce(sender);
                    }
                    if (wardrobeFixedEffectId == null) return;
                }

                int id = wardrobeFixedEffectId;
                Location effectLoc = npcLocation.clone().add(0, 1, 0);

// se il player non ha particle cosmetic -> rimuovi il fixed effect e stop
                if (!user.hasCosmeticInSlot(CosmeticSlot.PARTICLE)) {
                    removeWardrobeFixedParticles(sender);
                    return;
                }

                Cosmetic cosmetic = user.getCosmetic(CosmeticSlot.PARTICLE);
                CosmeticParticleType particleType = (CosmeticParticleType) cosmetic;

                String type = safe(particleType.getParticleType());
                String style = safe(particleType.getParticleStyle());
                String pdata = safe(particleType.getParticleData());

                ParticleEffect pe = ParticleEffect.fromName(type);
                ParticleStyle ps = ParticleStyle.fromName(style);
                if (pe == null || ps == null) return;

                Object parsed = parseParticleData(pe, pdata, cosmetic.getId());
                String key = type + "|" + style + "|" + pdata;

// 1) sposta sempre la location
                ppApi.editFixedParticleEffect(sender, id, effectLoc);

// 2) se è cambiato effetto/stile/data -> aggiorna tutto con editFixedParticleEffect(sender, fixedEffect)
                if (!key.equals(lastWardrobeParticleKey)) {
                    ParticlePair pair = buildParticlePair(sender.getUniqueId(), id, pe, ps, parsed);
                    FixedParticleEffect desired = new FixedParticleEffect(sender.getUniqueId(), id, effectLoc, pair, true);

                    FixedParticleEffect edited = ppApi.editFixedParticleEffect(sender, desired);
                    if (edited == null) {
                        // se fallisce, rimuovi e basta (non ricreare qui)
                        removeWardrobeFixedParticles(sender);
                        return;
                    }

                    lastWardrobeParticleKey = key;
                }
            }
        };

        wardrobeRunnable.runTaskTimer(HMCCosmeticsPlugin.getInstance(), 0, 2);
    }

    private Object parseParticleData(ParticleEffect effect, String data, String cosmeticId) {
        if (data == null) return null;
        data = data.trim();
        if (data.isEmpty()) return null;

        // rainbow/random
        if (data.equalsIgnoreCase("rainbow")) {
            return (effect == ParticleEffect.NOTE) ? NoteColor.RAINBOW : OrdinaryColor.RAINBOW;
        }
        if (data.equalsIgnoreCase("random")) {
            return (effect == ParticleEffect.NOTE) ? NoteColor.RANDOM : OrdinaryColor.RANDOM;
        }

        // numeri 0..24: validi solo per NOTE
        if (data.matches("\\d+")) {
            int n = Integer.parseInt(data);
            if (effect == ParticleEffect.NOTE) {
                if (n >= 0 && n <= 24) return new NoteColor(n);
                System.out.println("Invalid note color range for particle " + cosmeticId + ": " + data);
            }
            return null;
        }

        // RGB -> OrdinaryColor (per gli altri colorable)
        String[] parts = data.split("\\s+");
        if (parts.length == 3) {
            try {
                int r = Integer.parseInt(parts[0]);
                int g = Integer.parseInt(parts[1]);
                int b = Integer.parseInt(parts[2]);
                return new OrdinaryColor(r, g, b);
            } catch (NumberFormatException ex) {
                System.out.println("Invalid RGB data for particle " + cosmeticId + ": " + data);
                return null;
            }
        }

        if (parts.length == 6) return getColorTransition(data);

        Material mat = Material.getMaterial(data);
        if (mat != null) return mat;

        System.out.println("Invalid particle data for particle " + cosmeticId + ": " + data);
        return null;
    }

    private void removeWardrobeFixedParticles(CommandSender sender) {
        PlayerParticlesAPI ppApi = HMCCosmeticsPlugin.getInstance().getPpAPI();
        if (ppApi == null) return;

        Integer id = wardrobeFixedEffectId;
        if (sender instanceof Player p) {
            // se per qualche motivo wardrobeFixedEffectId è null, prova con quello deterministico
            if (id == null) id = getWardrobeFixedId(p);
        }

        if (id != null) {
            ppApi.removeFixedEffect(sender, id);
        }

        wardrobeFixedEffectId = null;
        lastWardrobeParticleKey = null;
    }

    private void clearRealPlayerParticlesTemporarily(Player player) {
        PlayerParticlesAPI ppApi = HMCCosmeticsPlugin.getInstance().getPpAPI();
        if (ppApi == null) return;

        // salva solo una volta
        if (realPlayerParticlesTemporarilyCleared) return;

        savedRealPlayerParticles = new ArrayList<>(ppApi.getActivePlayerParticles(player));
        ppApi.resetActivePlayerParticles(player); // rimuove tutte le active particles dal player vero
        realPlayerParticlesTemporarilyCleared = true;
    }

    private void restoreRealPlayerParticles(Player player) {
        PlayerParticlesAPI ppApi = HMCCosmeticsPlugin.getInstance().getPpAPI();
        if (ppApi == null) return;

        if (!realPlayerParticlesTemporarilyCleared) return;

        // rimetti quelle salvate
        for (ParticlePair pair : savedRealPlayerParticles) {
            ppApi.addActivePlayerParticle(player, pair);
        }

        savedRealPlayerParticles.clear();
        realPlayerParticlesTemporarilyCleared = false;
    }

    private String safe(String s) {
        return (s == null) ? "" : s.trim().replaceAll("\\s+", " ");
    }

    private int allocateUniqueFixedId(Player sender) {
        PlayerParticlesAPI ppApi = HMCCosmeticsPlugin.getInstance().getPpAPI();
        if (ppApi == null) return 100000; // fallback

        Set<Integer> used = new HashSet<>();
        for (FixedParticleEffect f : ppApi.getFixedParticleEffects(sender)) {
            used.add(f.getId());
        }

        // scegli id alto per non pestare gli id “normali”
        int id;
        do {
            id = 100000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(900000);
        } while (used.contains(id));

        return id;
    }

    private ParticlePair buildParticlePair(UUID ownerUuid, int id, ParticleEffect effect, ParticleStyle style, Object data) {
        Material itemMaterial = null;
        Material blockMaterial = null;
        OrdinaryColor color = null;
        NoteColor noteColor = null;
        ColorTransition colorTransition = null;
        dev.esophose.playerparticles.particles.data.Vibration vibration = null;

        if (data instanceof Material mat) {
            // Non sappiamo se in config intendi item o block: di solito è ITEM per "item crack"
            // Se vuoi block, cambia qui.
            itemMaterial = mat;
        } else if (data instanceof OrdinaryColor oc) {
            color = oc;
        } else if (data instanceof NoteColor nc) {
            noteColor = nc;
        } else if (data instanceof ColorTransition ct) {
            colorTransition = ct;
        } else if (data instanceof dev.esophose.playerparticles.particles.data.Vibration vib) {
            vibration = vib;
        }

        return new ParticlePair(
                ownerUuid,
                id,
                effect,
                style,
                itemMaterial,
                blockMaterial,
                color,
                noteColor,
                colorTransition,
                vibration
        );
    }

    private int getWardrobeFixedId(Player player) {
        int h = Math.abs(player.getUniqueId().hashCode());
        return 900000 + (h % 10000); // 900000..909999
    }

    private void createOrLoadWardrobeFixedEffectOnce(Player sender) {
        PlayerParticlesAPI ppApi = HMCCosmeticsPlugin.getInstance().getPpAPI();
        if (ppApi == null) return;

        int id = getWardrobeFixedId(sender);
        wardrobeFixedEffectId = id;

        // Se esiste già (cache/DB), non creare: lo editerai in update()
        FixedParticleEffect existing = ppApi.getFixedParticleEffect(sender, id);
        if (existing != null) return;

        // Creo “vuoto” con una particlepair dummy, poi update() lo aggiorna subito
        ParticlePair dummyPair = new ParticlePair(
                sender.getUniqueId(),
                id,
                ParticleEffect.FLAME,          // qualcosa di valido
                ParticleStyle.fromName("quadhelix"),          // qualcosa di valido
                null, null,
                null, null, null, null
        );

        FixedParticleEffect fixed = new FixedParticleEffect(sender.getUniqueId(), id, npcLocation.clone().add(0, 1, 0), dummyPair);

        FixedParticleEffect created = ppApi.createFixedParticleEffectForWardrobe(sender, fixed);
        if (created == null) {
            // se esiste in DB ma non in cache: prova a rimuovere e ricreare
            ppApi.removeFixedEffect(sender, id);
            created = ppApi.createFixedParticleEffectForWardrobe(sender, fixed);
        }

        if (created == null) {
            wardrobeFixedEffectId = null; // così update non prova a editarlo
        }
    }

    private void applyWardrobeSkinInHand(List<Player> viewer) {
        ItemStack hand = new ItemStack(Material.AIR);

        // invece di hasCosmeticInSlot(SKIN) + getCosmetic(SKIN)
        var skins = user.getEquippedSkins();
        if (skins != null && !skins.isEmpty()) {
            CosmeticSkinType last = null;
            for (CosmeticSkinType s : skins.values()) last = s; // ultima in LinkedHashMap

            if (last != null) {
                ItemStack cosmeticItem = last.getItem();
                if (cosmeticItem != null && cosmeticItem.getType() != Material.AIR) {
                    hand = cosmeticItem.clone();
                    hand.setAmount(1);
                }
            }
        }

        PacketManager.equipmentSlotUpdate(NPC_ID, EquipmentSlot.HAND, hand, viewer);
    }

    public enum WardrobeStatus {
        SETUP,
        STARTING,
        RUNNING,
        STOPPING,
    }
}
