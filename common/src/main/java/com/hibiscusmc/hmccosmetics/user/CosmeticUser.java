package com.hibiscusmc.hmccosmetics.user;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.api.events.*;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.config.Wardrobe;
import com.hibiscusmc.hmccosmetics.config.WardrobeSettings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticHolder;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.behavior.CosmeticMovementBehavior;
import com.hibiscusmc.hmccosmetics.cosmetic.behavior.CosmeticUpdateBehavior;
import com.hibiscusmc.hmccosmetics.cosmetic.types.*;
import com.hibiscusmc.hmccosmetics.database.UserData;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.user.manager.UserBackpackManager;
import com.hibiscusmc.hmccosmetics.user.manager.UserBalloonManager;
import com.hibiscusmc.hmccosmetics.user.manager.UserWardrobeManager;
import com.hibiscusmc.hmccosmetics.util.HMCCInventoryUtils;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import com.owen1212055.particlehelper.api.type.ParticleType;
import dev.esophose.playerparticles.api.PlayerParticlesAPI;
import dev.esophose.playerparticles.particles.ParticleEffect;
import dev.esophose.playerparticles.particles.data.ColorTransition;
import dev.esophose.playerparticles.particles.data.NoteColor;
import dev.esophose.playerparticles.particles.data.OrdinaryColor;
import dev.esophose.playerparticles.styles.ParticleStyle;
import lombok.Getter;
import me.lojosho.hibiscuscommons.hooks.Hooks;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import me.lojosho.hibiscuscommons.util.InventoryUtils;
import com.hibiscusmc.hmccosmetics.util.packets.PacketManager;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.logging.Level;

public class CosmeticUser implements CosmeticHolder {
    @Getter
    private final UUID uniqueId;
    private int taskId = -1;
    private final HashMap<CosmeticSlot, Cosmetic> playerCosmetics = new HashMap<>();
    private final Map<String, CosmeticSkinType> equippedSkins = new LinkedHashMap<>();
    private UserWardrobeManager userWardrobeManager;
    private UserBalloonManager userBalloonManager;
    @Getter @Nullable
    private UserBackpackManager userBackpackManager;

    // Cosmetic Settings/Toggles
    private final ArrayList<HiddenReason> hiddenReason = new ArrayList<>();
    private final HashMap<CosmeticSlot, Color> colors = new HashMap<>();

    /**
     * Use {@link #CosmeticUser(UUID)} instead and use {@link #initialize(UserData)} to populate the user with data.
     * @param uuid
     * @param data
     */
    @Deprecated(forRemoval = true, since = "2.7.5")
    public CosmeticUser(UUID uuid, UserData data) {
        this(uuid);
        initialize(data);
    }

    public CosmeticUser(@NotNull UUID uuid) {
        this.uniqueId = uuid;
    }

    private String skinKey(CosmeticSkinType s) {
        return (s.getRetextureGroup() != null && !s.getRetextureGroup().isEmpty())
                ? s.getRetextureGroup()
                : ("__skin__" + s.getId());
    }

    /**
     * Initialize the {@link CosmeticUser}.
     * @param userData the associated {@link UserData}
     * @return the {@link CosmeticUser}
     * @apiNote Initialize is called after {@link CosmeticUserProvider#createCosmeticUser(UUID)} so it is possible to
     * populate an extending version of {@link CosmeticUser} with data then override this method to apply your
     * own state.
     */
    public CosmeticUser initialize(final @Nullable UserData userData) {
        // Clear any particles that PlayerParticles might remember from before restart
        // to prevent duplicates when we re-apply cosmetics
        despawnParticle();
        
        if(userData != null) {
            HMCCosmeticsPlugin.getInstance().getLogger().info("[HMCCosmetics] initialize for " + uniqueId + ": " + userData.getCosmetics().size() + " cosmetics, " + userData.getSkins().size() + " skins");
            // Load regular cosmetics: CosmeticSlot -> Entry<Cosmetic, Integer>
            // Skip permission checks here — DB data is trusted. Permission plugins
            // (e.g. LuckPerms) may not have loaded yet at join time, which would
            // cause cosmetics with a 'permission' node to be wrongly skipped and
            // then permanently lost on the next save.
            for(final Map.Entry<CosmeticSlot, Map.Entry<Cosmetic, Integer>> entry : userData.getCosmetics().entrySet()) {
                Cosmetic cosmetic = entry.getValue().getKey();
                int colorRGBInt = entry.getValue().getValue();

                HMCCosmeticsPlugin.getInstance().getLogger().info("[HMCCosmetics] initialize: loading cosmetic slot=" + entry.getKey() + " id=" + cosmetic.getId());

                Color color = null;
                if (colorRGBInt != -1) color = Color.fromRGB(colorRGBInt);

                this.addCosmetic(cosmetic, color);
            }

            // Load skins: groupKey -> Entry<CosmeticSkinType, Integer>
            for(final Map.Entry<String, Map.Entry<CosmeticSkinType, Integer>> entry : userData.getSkins().entrySet()) {
                CosmeticSkinType skin = entry.getValue().getKey();
                int colorRGBInt = entry.getValue().getValue();

                HMCCosmeticsPlugin.getInstance().getLogger().info("[HMCCosmetics] initialize: loading skin key=" + entry.getKey() + " id=" + skin.getId());

                Color color = null;
                if (colorRGBInt != -1) color = Color.fromRGB(colorRGBInt);

                this.addCosmetic(skin, color);
            }

            this.applyHiddenState(userData.getHiddenReasons());
            HMCCosmeticsPlugin.getInstance().getLogger().info("[HMCCosmetics] initialize complete: " + playerCosmetics.size() + " cosmetics in map");
        } else {
            HMCCosmeticsPlugin.getInstance().getLogger().info("[HMCCosmetics] initialize for " + uniqueId + ": userData is NULL");
        }

        return this;
    }

    /**
     * This method is only called from {@link #initialize(UserData)} and can't be called directly.
     * This is used to help hooking plugins apply custom logic to the user.
     */
    protected boolean applyCosmetic(@NotNull Cosmetic cosmetic, @Nullable Color color) {
        this.addCosmetic(cosmetic, color);
        return true;
    }

    /**
     * This method is only called from {@link #initialize(UserData)} and can't be called directly.
     * This is used to help hooking plugins apply custom logic to the user.
     */
    protected boolean canApplyCosmetic(@NotNull Cosmetic cosmetic) {
        return canEquipCosmetic(cosmetic, false);
    }

    /**
     * This method is only called from {@link #initialize(UserData)} and can't be called directly.
     * This is used to help hooking plugins apply custom logic to the user.
     */
    protected void applyHiddenState(@NotNull List<HiddenReason> hiddenReasons) {
        if(!hiddenReason.isEmpty()) {
            for(final HiddenReason reason : this.hiddenReason) {
                this.silentlyAddHideFlag(reason);
            }
            return;
        }

        Player bukkitPlayer = getPlayer();
        if (bukkitPlayer != null && Settings.isDisabledGamemodesEnabled() && Settings.getDisabledGamemodes().contains(bukkitPlayer.getGameMode().toString())) {
            MessagesUtil.sendDebugMessages("Hiding cosmetics due to gamemode");
            hideCosmetics(HiddenReason.GAMEMODE);
        } else if (this.isHidden(HiddenReason.GAMEMODE)) {
            MessagesUtil.sendDebugMessages("Showing cosmetics for gamemode");
            showCosmetics(HiddenReason.GAMEMODE);
        }

        if (bukkitPlayer != null && Settings.getDisabledWorlds().contains(bukkitPlayer.getLocation().getWorld().getName())) {
            MessagesUtil.sendDebugMessages("Hiding Cosmetics due to world");
            hideCosmetics(CosmeticUser.HiddenReason.WORLD);
        } else if (this.isHidden(HiddenReason.WORLD)) {
            MessagesUtil.sendDebugMessages("Showing Cosmetics due to world");
            showCosmetics(HiddenReason.WORLD);
        }

        if (bukkitPlayer != null && bukkitPlayer.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
            hideCosmetics(HiddenReason.POTION);
        }

        if (Settings.isAllPlayersHidden()) {
            hideCosmetics(HiddenReason.DISABLED);
        }

        for (final HiddenReason reason : hiddenReasons) {
            this.silentlyAddHideFlag(reason);
        }
    }

    /**
     * Start ticking against the {@link CosmeticUser}.
     * @implNote The tick-rate is determined by the tick period specified in the configuration, if it is less-than or equal to 0
     * there will be no {@link BukkitTask} created, and the {@link CosmeticUser#taskId} will be -1
     */
    public final void startTicking() {
        int tickPeriod = Settings.getTickPeriod();
        if(tickPeriod <= 0) {
            MessagesUtil.sendDebugMessages("CosmeticUser tick is disabled.");
            return;
        }

        final BukkitTask task = Bukkit.getScheduler().runTaskTimer(HMCCosmeticsPlugin.getInstance(), this::tick, 0, tickPeriod);
        this.taskId = task.getTaskId();
    }

    /**
     * Dispatch an operation to happen against this {@link CosmeticUser}
     * at a pre-determined tick-rate.
     * The tick-rate is determined by the tick period specified in the configuration.
     */
    protected void tick() {
        MessagesUtil.sendDebugMessages("Tick[uuid=" + uniqueId + "]", Level.INFO);

        // Skip cosmetic updates while in the wardrobe; the wardrobe has its own update loop
        // that properly positions cosmetics on the NPC rather than on the player's real entity.
        if (isInWardrobe()) return;

        if (Hooks.isInvisible(uniqueId)) {
            this.hideCosmetics(HiddenReason.VANISH);
        } else {
            this.showCosmetics(HiddenReason.VANISH);
        }

        this.updateCosmetic();

        if(isHidden() && !playerCosmetics.isEmpty()) {
            MessagesUtil.sendActionBar(getPlayer(), "hidden-cosmetics");
        }
    }

    public void destroy() {
        if(this.taskId != -1) { // ensure we're actually ticking this user.
            Bukkit.getScheduler().cancelTask(taskId);
        }

        despawnBackpack();
        despawnBalloon();
    }

    @Override
    public Cosmetic getCosmetic(@NotNull CosmeticSlot slot) {
        if (slot == CosmeticSlot.SKIN) {
            return equippedSkins.values().stream().findFirst().orElse(null);
        }
        return playerCosmetics.get(slot);
    }

    @Override
    public @NotNull ImmutableCollection<Cosmetic> getCosmetics() {
        List<Cosmetic> all = new ArrayList<>(playerCosmetics.values());
        all.addAll(equippedSkins.values());
        return ImmutableList.copyOf(all);
    }

    public boolean isEquipped(@NotNull Cosmetic cosmetic) {
        if (cosmetic instanceof CosmeticSkinType skin) {
            CosmeticSkinType equipped = equippedSkins.get(skinKey(skin));
            return equipped != null && equipped.getId().equalsIgnoreCase(skin.getId());
        }
        Cosmetic equipped = playerCosmetics.get(cosmetic.getSlot());
        return equipped != null && equipped.getId().equalsIgnoreCase(cosmetic.getId());
    }

    public void removeCosmetic(@NotNull Cosmetic cosmetic) {
        if (cosmetic instanceof CosmeticSkinType skin) {
            String key = skinKey(skin);
            CosmeticSkinType removed = equippedSkins.remove(key);
            if (removed == null) return;

            PlayerCosmeticRemoveEvent event = new PlayerCosmeticRemoveEvent(this, removed);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                equippedSkins.put(key, removed);
                return;
            }

            updateCosmetic();

            Player p = getPlayer();
            if (p != null) {
                HMCCosmeticsPlugin.getInstance().getSkinProtocolLibListener().resendHands(p);
                p.updateInventory();
            }

            return;
        }

        removeCosmeticSlot(cosmetic.getSlot());
    }

    @Override
    public void addCosmetic(@NotNull Cosmetic cosmetic, @Nullable Color color) {
        // API
        PlayerCosmeticEquipEvent event = new PlayerCosmeticEquipEvent(this, cosmetic);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        cosmetic = event.getCosmetic();
        // Internal
        if (playerCosmetics.containsKey(cosmetic.getSlot()) && cosmetic.getSlot() != CosmeticSlot.SKIN) {
            removeCosmeticSlot(cosmetic.getSlot());
        }

        if (cosmetic instanceof CosmeticSkinType skin) {
            String key = skinKey(skin);

            equippedSkins.remove(key);
            equippedSkins.put(key, skin);

            if (color != null) colors.put(CosmeticSlot.SKIN, color);

            Player p = getPlayer();
            if (p != null) {
                HMCCosmeticsPlugin.getInstance().getSkinProtocolLibListener().resendHands(p);
                p.updateInventory();
            }

            return;
        }

        playerCosmetics.put(cosmetic.getSlot(), cosmetic);
        if (color != null) colors.put(cosmetic.getSlot(), color);
        MessagesUtil.sendDebugMessages("addPlayerCosmetic[id=" + cosmetic.getId() + "]");
        if (!isHidden()) {
            if (cosmetic.getSlot() == CosmeticSlot.BACKPACK) {
                CosmeticBackpackType backpackType = (CosmeticBackpackType) cosmetic;
                spawnBackpack(backpackType);
                MessagesUtil.sendDebugMessages("addPlayerCosmetic[spawnBackpack,id=" + cosmetic.getId() + "]");
            }
            if (cosmetic.getSlot() == CosmeticSlot.BALLOON) {
                CosmeticBalloonType balloonType = (CosmeticBalloonType) cosmetic;
                spawnBalloon(balloonType);
            }
            if (cosmetic.getSlot() == CosmeticSlot.PARTICLE && isInWardrobe()) {
                return;
            }
            if (cosmetic.getSlot() == CosmeticSlot.PARTICLE) {
                try {
                    spawnParticle((CosmeticParticleType) cosmetic);
                } catch (Throwable ignored) {
                    // PP API may not be ready yet (e.g. during initialization);
                    // the cosmetic is already stored in playerCosmetics above,
                    // the delayed updateCosmetic / respawnParticle will retry.
                }
            }
        }
        // API
        PlayerCosmeticPostEquipEvent postEquipEvent = new PlayerCosmeticPostEquipEvent(this, cosmetic);
        Bukkit.getPluginManager().callEvent(postEquipEvent);
    }

    public static @NotNull ColorTransition getColorTransition(String data) {
        String[] split = data.split(" ");
        int r = Integer.parseInt(split[0]);
        int g = Integer.parseInt(split[1]);
        int b = Integer.parseInt(split[2]);

        OrdinaryColor startColor = new OrdinaryColor(r, g, b);

        r = Integer.parseInt(split[3]);
        g = Integer.parseInt(split[4]);
        b = Integer.parseInt(split[5]);

        OrdinaryColor endColor = new OrdinaryColor(r, g, b);

        ColorTransition colorTransition = new ColorTransition(startColor, endColor);
        return colorTransition;
    }

    /**
     * @deprecated Use {@link #addCosmetic(Cosmetic)} instead
     */
    @Deprecated(since = "2.7.7", forRemoval = true)
    public void addPlayerCosmetic(@NotNull Cosmetic cosmetic) {
        addCosmetic(cosmetic);
    }

    /**
     * @deprecated Use {@link #addCosmetic(Cosmetic, Color)} instead
     */
    @Deprecated(since = "2.7.7", forRemoval = true)
    public void addPlayerCosmetic(@NotNull Cosmetic cosmetic, @Nullable Color color) {
        addCosmetic(cosmetic, color);
    }

    @Override
    public void removeCosmeticSlot(@NotNull CosmeticSlot slot) {

        if (slot == CosmeticSlot.SKIN) {
            if (equippedSkins.isEmpty()) return;

            var copy = new HashMap<>(equippedSkins);

            for (var e : copy.entrySet()) {
                String key = e.getKey();
                CosmeticSkinType skin = e.getValue();

                PlayerCosmeticRemoveEvent event = new PlayerCosmeticRemoveEvent(this, skin);
                Bukkit.getPluginManager().callEvent(event);
                if (event.isCancelled()) continue;

                equippedSkins.remove(key);
            }

            colors.remove(CosmeticSlot.SKIN);
            updateCosmetic(); // refresh visual
            Player p = getPlayer();
            if (p != null) {
                HMCCosmeticsPlugin.getInstance().getSkinProtocolLibListener().resendHands(p);
                p.updateInventory();
            }

            return;
        }

        PlayerCosmeticRemoveEvent event = new PlayerCosmeticRemoveEvent(this, getCosmetic(slot));
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) return;

        if (slot == CosmeticSlot.BACKPACK) despawnBackpack();
        if (slot == CosmeticSlot.BALLOON) despawnBalloon();
        if (slot == CosmeticSlot.PARTICLE) despawnParticle();

        colors.remove(slot);
        playerCosmetics.remove(slot);
        removeArmor(slot);
    }

    @Override
    public boolean hasCosmeticInSlot(@NotNull CosmeticSlot slot) {
        if (slot == CosmeticSlot.SKIN) {
            return !equippedSkins.isEmpty();
        }
        return playerCosmetics.containsKey(slot);
    }

    @Override
    public boolean hasCosmeticInSlot(@NotNull Cosmetic cosmetic) {
        if (cosmetic instanceof CosmeticSkinType skin) {
            CosmeticSkinType equipped = equippedSkins.get(skinKey(skin));
            return equipped != null && equipped.getId().equalsIgnoreCase(skin.getId());
        }
        return CosmeticHolder.super.hasCosmeticInSlot(cosmetic);
    }

    public Set<CosmeticSlot> getSlotsWithCosmetics() {
        Set<CosmeticSlot> out = new HashSet<>(playerCosmetics.keySet());
        if (!equippedSkins.isEmpty()) out.add(CosmeticSlot.SKIN);
        return out;
    }

    public Map<String, CosmeticSkinType> getEquippedSkins() {
        return java.util.Collections.unmodifiableMap(equippedSkins);
    }

    @Override
    public boolean updateCosmetic(@NotNull CosmeticSlot slot) {

        if (slot == CosmeticSlot.SKIN) {
            boolean updated = false;

            for (CosmeticSkinType skin : equippedSkins.values()) {
                if (skin instanceof CosmeticUpdateBehavior behavior) {
                    behavior.dispatchUpdate(this);
                    updated = true;
                }
            }
            return updated;
        }

        final Cosmetic cosmetic = playerCosmetics.get(slot);
        if (cosmetic == null) return false;

        if (!(cosmetic instanceof CosmeticUpdateBehavior behavior)) {
            MessagesUtil.sendDebugMessages("Attempted to update cosmetic that does not implement CosmeticUpdateBehavior");
            return false;
        }

        behavior.dispatchUpdate(this);
        return true;
    }

    @Override
    public boolean updateMovementCosmetic(@NotNull CosmeticSlot slot,
                                          @NotNull Location from,
                                          @NotNull Location to) {

        if (slot == CosmeticSlot.SKIN) {
            boolean updated = false;

            for (CosmeticSkinType skin : equippedSkins.values()) {
                if (skin instanceof CosmeticMovementBehavior behavior) {
                    behavior.dispatchMove(this, from, to);
                    updated = true;
                }
            }
            return updated;
        }

        final Cosmetic cosmetic = playerCosmetics.get(slot);
        if (cosmetic == null) return false;

        if (!(cosmetic instanceof CosmeticMovementBehavior behavior)) {
            MessagesUtil.sendDebugMessages("Attempted to update cosmetic that does not implement CosmeticMovementBehavior");
            return false;
        }

        behavior.dispatchMove(this, from, to);
        return true;
    }

    public boolean updateCosmetic(@NotNull final Cosmetic cosmetic) {
        return updateCosmetic(cosmetic.getSlot());
    }

    public void updateCosmetic() {
        MessagesUtil.sendDebugMessages("updateCosmetic (All) - start");
        final HashMap<EquipmentSlot, ItemStack> items = new HashMap<>();

        for(final Cosmetic cosmetic : getCosmetics()) {
            if(!(cosmetic instanceof CosmeticUpdateBehavior behavior)) {
                continue;
            }

            if(cosmetic instanceof CosmeticParticleType) {
                if (isInWardrobe()) return;
            }

            // defers item updates to end of operation
            if(cosmetic instanceof CosmeticArmorType armorType) {
                if (isInWardrobe()) return;
                if (!(getEntity() instanceof HumanEntity humanEntity)) return;

                boolean requireEmpty = Settings.getSlotOption(armorType.getEquipSlot()).isRequireEmpty();
                boolean isAir = humanEntity.getInventory().getItem(armorType.getEquipSlot()).getType().isAir();
                MessagesUtil.sendDebugMessages("updateCosmetic (All) - " + armorType.getId() + " - " + requireEmpty + " - " + isAir);
                if (requireEmpty && !isAir) continue;

                items.put(HMCCInventoryUtils.getEquipmentSlot(armorType.getSlot()), armorType.getItem(this));
            } else {
                behavior.dispatchUpdate(this);
            }
        }

        final Entity entity = this.getEntity();
        if(!items.isEmpty() && entity != null) {
            PacketManager.equipmentSlotUpdate(
                entity.getEntityId(),
                items,
                HMCCPacketManager.getViewers(entity.getLocation())
            );
            MessagesUtil.sendDebugMessages("updateCosmetic (All) - end - " + items.size());
        }
    }

    public ItemStack getUserCosmeticItem(@NotNull CosmeticSlot slot) {
        Cosmetic cosmetic = getCosmetic(slot);
        if (cosmetic == null) return new ItemStack(Material.AIR);
        return getUserCosmeticItem(cosmetic);
    }

    public ItemStack getUserCosmeticItem(@NotNull Cosmetic cosmetic) {
        ItemStack item = null;
        if (!hiddenReason.isEmpty()) {
            if (cosmetic instanceof CosmeticBackpackType || cosmetic instanceof CosmeticBalloonType) return new ItemStack(Material.AIR);
            return getPlayer().getInventory().getItem(HMCCInventoryUtils.getEquipmentSlot(cosmetic.getSlot()));
        }
        if (cosmetic instanceof CosmeticArmorType armorType) {
            item = armorType.getItem(this, cosmetic.getItem());
        }
        if (cosmetic instanceof CosmeticBackpackType) {
            item = cosmetic.getItem();
        }
        if (cosmetic instanceof CosmeticBalloonType) {
            if (cosmetic.getItem() == null) {
                item = new ItemStack(Material.LEATHER_HORSE_ARMOR);
            } else {
                item = cosmetic.getItem();
            }
        }
        return getUserCosmeticItem(cosmetic, item);
    }

    @SuppressWarnings("deprecation")
    public ItemStack getUserCosmeticItem(@NotNull Cosmetic cosmetic, @Nullable ItemStack item) {
        if (item == null) {
            //MessagesUtil.sendDebugMessages("GetUserCosemticUser Item is null");
            return new ItemStack(Material.AIR);
        }
        if (item.hasItemMeta()) {
            ItemMeta itemMeta = item.getItemMeta();

            if (item.getType() == Material.PLAYER_HEAD) {
                SkullMeta skullMeta = (SkullMeta) itemMeta;
                if (skullMeta.getPersistentDataContainer().has(InventoryUtils.getSkullOwner(), PersistentDataType.STRING)) {
                    String owner = skullMeta.getPersistentDataContainer().get(InventoryUtils.getSkullOwner(), PersistentDataType.STRING);

                    owner = Hooks.processPlaceholders(getPlayer(), owner);

                    skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(owner));
                    //skullMeta.getPersistentDataContainer().remove(InventoryUtils.getSkullOwner()); // Don't really need this?
                }
                if (skullMeta.getPersistentDataContainer().has(InventoryUtils.getSkullTexture(), PersistentDataType.STRING)) {
                    String texture = skullMeta.getPersistentDataContainer().get(InventoryUtils.getSkullTexture(), PersistentDataType.STRING);

                    texture = Hooks.processPlaceholders(getPlayer(), texture);

                    Bukkit.getUnsafe().modifyItemStack(item, "{SkullOwner:{Id:[I;0,0,0,0],Properties:{textures:[{Value:\""
                            + texture + "\"}]}}}");
                    //skullMeta.getPersistentDataContainer().remove(InventoryUtils.getSkullTexture()); // Don't really need this?
                }

                itemMeta = skullMeta;
            }

            if (Settings.isItemProcessingDisplayName()) {
                if (itemMeta.hasDisplayName()) {
                    String displayName = itemMeta.getDisplayName();
                    itemMeta.setDisplayName(Hooks.processPlaceholders(getPlayer(), displayName));
                }
            }
            if (Settings.isItemProcessingLore()) {
                List<String> processedLore = new ArrayList<>();
                if (itemMeta.hasLore()) {
                    for (String loreLine : itemMeta.getLore()) {
                        processedLore.add(Hooks.processPlaceholders(getPlayer(), loreLine));
                    }
                }
                itemMeta.setLore(processedLore);
            }


            itemMeta.getPersistentDataContainer().set(HMCCInventoryUtils.getCosmeticKey(), PersistentDataType.STRING, cosmetic.getId());
            itemMeta.getPersistentDataContainer().set(InventoryUtils.getOwnerKey(), PersistentDataType.STRING, getEntity().getUniqueId().toString());

            item.setItemMeta(itemMeta);

            if (colors.containsKey(cosmetic.getSlot())) {
                Color color = colors.get(cosmetic.getSlot());
                item = NMSHandlers.getHandler().getUtilHandler().setColor(item, color);
            }
        }
        return item;
    }

    public UserBalloonManager getBalloonManager() {
        return this.userBalloonManager;
    }

    public UserWardrobeManager getWardrobeManager() {
        return userWardrobeManager;
    }

    /**
     * Use {@link #enterWardrobe(Wardrobe, boolean)} instead.
     * @param ignoreDistance
     * @param wardrobe
     */
    @Deprecated(forRemoval = true, since = "2.7.5")
    public void enterWardrobe(boolean ignoreDistance, @NotNull Wardrobe wardrobe) {
        enterWardrobe(wardrobe, ignoreDistance);
    }

    /**
     * This method is used to enter a wardrobe. You can listen to the {@link PlayerWardrobeEnterEvent} to cancel the event or modify any data.
     * @param wardrobe The wardrobe to enter. Use {@link WardrobeSettings#getWardrobe(String)} to get pre-existing wardrobe or use your own by {@link Wardrobe}.
     * @param ignoreDistance If true, the player can enter the wardrobe from any distance. If false, the player must be within the distance set in the wardrobe (If wardrobe has a distance of 0 or lower, the player can enter from any distance).
     */
    public void enterWardrobe(@NotNull Wardrobe wardrobe, boolean ignoreDistance) {
        if (wardrobe.hasPermission() && !getPlayer().hasPermission(wardrobe.getPermission())) {
            MessagesUtil.sendMessage(getPlayer(), "no-permission");
            return;
        }
        if (!wardrobe.canEnter(this) && !ignoreDistance) {
            MessagesUtil.sendMessage(getPlayer(), "not-near-wardrobe");
            return;
        }
        if (!wardrobe.getLocation().hasAllLocations()) {
            MessagesUtil.sendMessage(getPlayer(), "wardrobe-not-setup");
            return;
        }
        PlayerWardrobeEnterEvent event = new PlayerWardrobeEnterEvent(this, wardrobe);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        wardrobe = event.getWardrobe();

        if (userWardrobeManager == null) {
            userWardrobeManager = new UserWardrobeManager(this, wardrobe);
            userWardrobeManager.start();
        }
    }

    /**
     * Use {@link #leaveWardrobe(boolean)} instead.
     */
    @Deprecated(forRemoval = true, since = "2.7.5")
    public void leaveWardrobe() {
        leaveWardrobe(false);
    }

    /**
     * Causes the player to leave the wardrobe. If a player is not in the wardrobe, this will do nothing, use (@{@link #isInWardrobe()} to check if they are).
     * @param ejected If true, the player was ejected from the wardrobe (Skips transition). If false, the player left the wardrobe normally.
     */
    public void leaveWardrobe(boolean ejected) {
        PlayerWardrobeLeaveEvent event = new PlayerWardrobeLeaveEvent(this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        MessagesUtil.sendDebugMessages("Leaving Wardrobe");
        if (!getWardrobeManager().getWardrobeStatus().equals(UserWardrobeManager.WardrobeStatus.RUNNING)) return;

        getWardrobeManager().setWardrobeStatus(UserWardrobeManager.WardrobeStatus.STOPPING);
        getWardrobeManager().setLastOpenMenu(Menus.getDefaultMenu());

        userWardrobeManager.end();
        userWardrobeManager = null;
    }

    /**
     * This checks if the player is in a wardrobe. If they are, it will return true, else false. See {@link #getWardrobeManager()} to get the wardrobe manager.
     * @return If the player is in a wardrobe.
     */
    public boolean isInWardrobe() {
        return userWardrobeManager != null;
    }

    public void spawnBackpack(CosmeticBackpackType cosmeticBackpackType) {
        if (this.userBackpackManager != null) return;
        this.userBackpackManager = new UserBackpackManager(this);
        userBackpackManager.spawnBackpack(cosmeticBackpackType);
    }

    public void despawnBackpack() {
        if (userBackpackManager == null) return;
        userBackpackManager.despawnBackpack();
        userBackpackManager = null;
    }

    public boolean isBackpackSpawned() {
        return this.userBackpackManager != null;
    }

    public boolean isBalloonSpawned() {
        return this.userBalloonManager != null;
    }

    public void spawnBalloon(CosmeticBalloonType cosmeticBalloonType) {
        if (this.userBalloonManager != null) return;

        org.bukkit.entity.Entity entity = getEntity();

        UserBalloonManager userBalloonManager1 = new UserBalloonManager(this, entity.getLocation());
        userBalloonManager1.getModelEntity().teleport(entity.getLocation().add(cosmeticBalloonType.getBalloonOffset()));

        userBalloonManager1.spawnModel(cosmeticBalloonType, getCosmeticColor(cosmeticBalloonType.getSlot()));
        userBalloonManager1.addPlayerToModel(this, cosmeticBalloonType, getCosmeticColor(cosmeticBalloonType.getSlot()));

        this.userBalloonManager = userBalloonManager1;
        //this.userBalloonManager = NMSHandlers.getHandler().spawnBalloon(this, cosmeticBalloonType);
    }

    public void despawnBalloon() {
        if (this.userBalloonManager == null) return;
        this.userBalloonManager.remove();
        this.userBalloonManager = null;
    }

    public void respawnBackpack() {
        if (!hasCosmeticInSlot(CosmeticSlot.BACKPACK)) return;
        final Cosmetic cosmetic = getCosmetic(CosmeticSlot.BACKPACK);
        despawnBackpack();
        if (!hiddenReason.isEmpty()) return;
        spawnBackpack((CosmeticBackpackType) cosmetic);
        MessagesUtil.sendDebugMessages("Respawned Backpack for " + getEntity().getName());
    }

    public void respawnBalloon() {
        if (!hasCosmeticInSlot(CosmeticSlot.BALLOON)) return;
        final Cosmetic cosmetic = getCosmetic(CosmeticSlot.BALLOON);
        despawnBalloon();
        if (!hiddenReason.isEmpty()) return;
        spawnBalloon((CosmeticBalloonType) cosmetic);
        MessagesUtil.sendDebugMessages("Respawned Balloon for " + getEntity().getName());
    }

    public void removeArmor(CosmeticSlot slot) {
        EquipmentSlot equipmentSlot = HMCCInventoryUtils.getEquipmentSlot(slot);
        if (equipmentSlot == null) return;
        if (getPlayer() != null) {
            PacketManager.equipmentSlotUpdate(getEntity().getEntityId(), equipmentSlot, getPlayer().getInventory().getItem(equipmentSlot), HMCCPacketManager.getViewers(getEntity().getLocation()));
        } else {
            HMCCPacketManager.equipmentSlotUpdate(getEntity().getEntityId(), this, slot, HMCCPacketManager.getViewers(getEntity().getLocation()));
        }
    }

    public void despawnParticle() {
        PlayerParticlesAPI playerParticlesAPI = HMCCosmeticsPlugin.getInstance().getPpAPI();
        if (playerParticlesAPI == null) return;
        playerParticlesAPI.resetActivePlayerParticles(uniqueId);
    }

    /**
     * Re-applies the current particle cosmetic's PlayerParticles active effect.
     * Clears any stale PP particles first, then adds the current cosmetic's effect.
     */
    public void respawnParticle() {
        if (!hasCosmeticInSlot(CosmeticSlot.PARTICLE)) return;
        Cosmetic cosmetic = getCosmetic(CosmeticSlot.PARTICLE);
        if (!(cosmetic instanceof CosmeticParticleType particleType)) return;

        despawnParticle();
        spawnParticle(particleType);
    }

    /**
     * Adds the given particle cosmetic's effect to PlayerParticles as an active player particle.
     */
    private void spawnParticle(CosmeticParticleType particleType) {
        PlayerParticlesAPI ppApi = HMCCosmeticsPlugin.getInstance().getPpAPI();
        if (ppApi == null) return;

        String effect = particleType.getParticleType();
        String style = particleType.getParticleStyle();
        String data = particleType.getParticleData();

        ParticleEffect particleEffect = ParticleEffect.fromName(effect);
        ParticleStyle particleStyle = ParticleStyle.fromName(style);

        if (particleEffect == null || particleStyle == null) return;

        // ColorTransition
        if (data.split(" ").length == 6) {
            ColorTransition colorTransition = getColorTransition(data);
            ppApi.addActivePlayerParticle(uniqueId, particleEffect, particleStyle, colorTransition);
            return;
        }

        if (data.equalsIgnoreCase("rainbow")) {
            if (particleEffect == ParticleEffect.NOTE) {
                ppApi.addActivePlayerParticle(uniqueId, particleEffect, particleStyle, NoteColor.RAINBOW);
            } else {
                ppApi.addActivePlayerParticle(uniqueId, particleEffect, particleStyle, OrdinaryColor.RAINBOW);
            }
            return;
        }

        if (data.equalsIgnoreCase("random")) {
            if (particleEffect == ParticleEffect.NOTE) {
                ppApi.addActivePlayerParticle(uniqueId, particleEffect, particleStyle, NoteColor.RANDOM);
            } else {
                ppApi.addActivePlayerParticle(uniqueId, particleEffect, particleStyle, OrdinaryColor.RANDOM);
            }
            return;
        }

        if (data.split(" ").length == 3) {
            String[] split = data.split(" ");
            try {
                int r = Integer.parseInt(split[0]);
                int g = Integer.parseInt(split[1]);
                int b = Integer.parseInt(split[2]);
                ppApi.addActivePlayerParticle(uniqueId, particleEffect, particleStyle, new OrdinaryColor(r, g, b));
            } catch (NumberFormatException ex) {
                System.out.println("Invalid RGB data for particle " + particleType.getId() + ": " + data);
            }
            return;
        }

        if (data.matches("\\d+")) {
            int n = Integer.parseInt(data);
            if (particleEffect == ParticleEffect.NOTE) {
                if (n >= 0 && n <= 24) {
                    ppApi.addActivePlayerParticle(uniqueId, particleEffect, particleStyle, new NoteColor(n));
                }
            }
            return;
        }

        Material mat = Material.getMaterial(data);
        if (mat != null) {
            ppApi.addActivePlayerParticle(uniqueId, particleEffect, particleStyle, mat);
            return;
        }

        ppApi.addActivePlayerParticle(uniqueId, particleEffect, particleStyle);
    }

    /**
     * This returns the player associated with the user. Some users may not have a player attached, ie, they are npcs
     * wearing cosmetics through an addon. If you need to get locations, use getEntity instead.
     * @return Player
     */
    @Nullable
    public Player getPlayer() {
        return Bukkit.getPlayer(uniqueId);
    }

    /**
     * This gets the entity associated with the user.
     * @return Entity
     */
    public Entity getEntity() {
        return getPlayer();
    }

    public Color getCosmeticColor(CosmeticSlot slot) {
        return colors.get(slot);
    }

    public List<CosmeticSlot> getDyeableSlots() {
        ArrayList<CosmeticSlot> dyeableSlots = new ArrayList<>();

        for (Cosmetic cosmetic : playerCosmetics.values()) {
            if (cosmetic.isDyeable()) dyeableSlots.add(cosmetic.getSlot());
        }

        return dyeableSlots;
    }

    @Override
    public boolean canEquipCosmetic(@NotNull Cosmetic cosmetic, boolean ignoreWardrobe) {
        if (!cosmetic.requiresPermission()) return true;
        if (isInWardrobe() && !ignoreWardrobe) {
            if (WardrobeSettings.isTryCosmeticsInWardrobe() && userWardrobeManager.getWardrobeStatus().equals(UserWardrobeManager.WardrobeStatus.RUNNING)) return true;
        }
        final Player player = getPlayer();
        if (player != null) return player.hasPermission(cosmetic.getPermission());
        // This sucks, but basically if we can find a player, use that. If not, try to find the entity. If it can't find the entity, just return false.
        final Entity entity = getEntity();
        if (entity != null) return entity.hasPermission(cosmetic.getPermission());
        return false;
    }

    public void hidePlayer() {
        Player player = getPlayer();
        if (player == null) return;
        for (final Player p : Bukkit.getOnlinePlayers()) {
            p.hidePlayer(HMCCosmeticsPlugin.getInstance(), player);
            player.hidePlayer(HMCCosmeticsPlugin.getInstance(), p);
        }
    }

    public void showPlayer() {
        Player player = getPlayer();
        if (player == null) return;
        for (final Player p : Bukkit.getOnlinePlayers()) {
            p.showPlayer(HMCCosmeticsPlugin.getInstance(), player);
            player.showPlayer(HMCCosmeticsPlugin.getInstance(), p);
        }
    }

    public void hideCosmetics(HiddenReason reason) {
        PlayerCosmeticHideEvent event = new PlayerCosmeticHideEvent(this, reason);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        if (!hiddenReason.contains(reason)) hiddenReason.add(reason);
        if (hasCosmeticInSlot(CosmeticSlot.BALLOON)) {
            despawnBalloon();
            //getBalloonManager().removePlayerFromModel(getPlayer());
            //getBalloonManager().sendRemoveLeashPacket();
        }
        if (hasCosmeticInSlot(CosmeticSlot.BACKPACK)) {
            despawnBackpack();
        }
        updateCosmetic();
        MessagesUtil.sendDebugMessages("HideCosmetics");
    }

    /**
     * This is used to silently add a hidden flag to the user. This will not trigger any events or checks, nor do anything else
     * @param reason
     */
    public void silentlyAddHideFlag(HiddenReason reason) {
        if (!hiddenReason.contains(reason)) hiddenReason.add(reason);
    }

    public void showCosmetics(HiddenReason reason) {
        if (hiddenReason.isEmpty()) return;

        PlayerCosmeticShowEvent event = new PlayerCosmeticShowEvent(this);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        hiddenReason.remove(reason);
        if (isHidden()) return;
        if (hasCosmeticInSlot(CosmeticSlot.BALLOON)) {
            if (!isBalloonSpawned()) respawnBalloon();
            CosmeticBalloonType balloonType = (CosmeticBalloonType) getCosmetic(CosmeticSlot.BALLOON);
            getBalloonManager().addPlayerToModel(this, balloonType);
            List<Player> viewer = HMCCPacketManager.getViewers(getEntity().getLocation());
            HMCCPacketManager.sendLeashPacket(getBalloonManager().getPufferfishBalloonId(), getPlayer().getEntityId(), viewer);
        }
        if (hasCosmeticInSlot(CosmeticSlot.BACKPACK)) {
            if (!isBackpackSpawned()) respawnBackpack();
            CosmeticBackpackType cosmeticBackpackType = (CosmeticBackpackType) getCosmetic(CosmeticSlot.BACKPACK);
            ItemStack item = getUserCosmeticItem(cosmeticBackpackType);
            userBackpackManager.setItem(item);
        }
        updateCosmetic();
        MessagesUtil.sendDebugMessages("ShowCosmetics");
    }


    /**
     * This method is deprecated and will be removed in the future. Use {@link #isHidden()} instead.
     * @return
     */
    @Deprecated(since = "2.7.2-DEV", forRemoval = true)
    public boolean getHidden() {
        return !hiddenReason.isEmpty();
    }

    public boolean isHidden() {
        return !hiddenReason.isEmpty();
    }

    public boolean isHidden(HiddenReason reason) {
        return hiddenReason.contains(reason);
    }

    public List<HiddenReason> getHiddenReasons() {
        return hiddenReason;
    }

    public void clearHiddenReasons() {
        hiddenReason.clear();
    }

    public enum HiddenReason {
        NONE,
        WORLDGUARD,
        PLUGIN,
        VANISH,
        POTION,
        ACTION,
        COMMAND,
        EMOTE,
        GAMEMODE,
        WORLD,
        DISABLED
    }
}
