package com.hibiscusmc.hmccosmetics.store;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBackpackType;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticSkinType;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.gui.special.StoreMenu;
import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StoreArmorStandManager {

    private StoreArmorStandManager() {
    }

    private static final NamespacedKey ARMORSTAND_STORE_ID_KEY =
            new NamespacedKey(HMCCosmeticsPlugin.getInstance(), "store_armorstand_id"); // 1-7

    private static final NamespacedKey ARMORSTAND_STORE_SLOT_KEY =
            new NamespacedKey(HMCCosmeticsPlugin.getInstance(), "store_armorstand_slot"); // 10-16

    private static final Map<UUID, StoreBackpackDisplay> BACKPACK_DISPLAYS = new ConcurrentHashMap<>();
    private static boolean backpackTickerStarted = false;

    /**
     * Chiama questo in onEnable()
     */
    public static void initDailyRefreshScheduler() {
        ensureBackpackTicker();
        ZoneId zone = ZoneId.of("Europe/Rome");

        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(zone);

        long delayTicks = Duration.between(now, nextMidnight).getSeconds() * 20L;
        long periodTicks = 24L * 60L * 60L * 20L;

        Bukkit.getScheduler().runTaskTimer(
                HMCCosmeticsPlugin.getInstance(),
                StoreArmorStandManager::refreshDailyAndArmorStands,
                delayTicks,
                periodTicks
        );

        Bukkit.getScheduler().runTask(HMCCosmeticsPlugin.getInstance(), StoreArmorStandManager::refreshArmorStandsOnly);
    }

    /**
     * Rigenera i daily cosmetics e aggiorna gli armorstand
     */
    public static void refreshDailyAndArmorStands() {
        StoreMenu store = Menus.getStoreMenu("store");
        if (store != null) {
            store.refreshDailyCosmetics();
        }
        refreshArmorStandsOnly();
        Bukkit.getLogger().info("[HMCCosmetics] Daily store refreshed + store armorstands updated.");
    }

    /**
     * Aggiorna solo gli armorstand (senza rigenerare daily)
     */
    public static void refreshArmorStandsOnly() {
        StoreMenu store = Menus.getStoreMenu("store");
        if (store == null) return;

        for (World w : Bukkit.getWorlds()) {
            for (ArmorStand as : w.getEntitiesByClass(ArmorStand.class)) {
                PersistentDataContainer pdc = as.getPersistentDataContainer();

                Integer storeSlot = pdc.get(ARMORSTAND_STORE_SLOT_KEY, PersistentDataType.INTEGER);
                if (storeSlot == null) continue;

                if (storeSlot >= 1 && storeSlot <= 7) storeSlot = storeSlot + 9;

                if (storeSlot < 10 || storeSlot > 16) continue;

                updateArmorStandDisplay(store, as, storeSlot);
            }
        }
    }

    /**
     * Aggiorna un singolo armorstand
     */
    public static void refreshArmorStand(ArmorStand armorStand) {
        if (armorStand == null) return;

        StoreMenu store = Menus.getStoreMenu("store");
        if (store == null) return;

        PersistentDataContainer pdc = armorStand.getPersistentDataContainer();
        Integer storeSlot = pdc.get(ARMORSTAND_STORE_SLOT_KEY, PersistentDataType.INTEGER);
        if (storeSlot == null) return;

        if (storeSlot >= 1 && storeSlot <= 7) storeSlot = storeSlot + 9;
        if (storeSlot < 10 || storeSlot > 16) return;

        updateArmorStandDisplay(store, armorStand, storeSlot);
    }

    private static void updateArmorStandDisplay(StoreMenu store, ArmorStand armorStand, int storeSlot) {
        if (store == null) return;

        Cosmetic cosmetic = store.getDailyCosmetic(storeSlot);
        if (cosmetic == null) return;

        removeBackpackDisplay(armorStand);

        if (armorStand.getEquipment() != null) {
            armorStand.getEquipment().clear();
        }

        if (cosmetic instanceof CosmeticBackpackType backpackType) {
            StoreBackpackDisplay display = new StoreBackpackDisplay(armorStand, backpackType);
            BACKPACK_DISPLAYS.put(armorStand.getUniqueId(), display);
            display.spawn();
            return;
        }

        if (cosmetic instanceof CosmeticSkinType) {
            applySkinToArmorStand(armorStand, cosmetic);
            return;
        }

        applyCosmeticToArmorStand(armorStand, cosmetic);
    }

    private static void applySkinToArmorStand(ArmorStand as, Cosmetic cosmetic) {
        if (as.getEquipment() == null) return;

        ItemStack base = Objects.requireNonNull(cosmetic.getItem()).clone();
        as.getEquipment().setItemInMainHand(base);
    }

    private static void applyCosmeticToArmorStand(ArmorStand as, Cosmetic cosmetic) {
        if (as == null || cosmetic == null) return;
        if (as.getEquipment() == null) return;

        as.getEquipment().setHelmet(null);
        as.getEquipment().setChestplate(null);
        as.getEquipment().setLeggings(null);
        as.getEquipment().setBoots(null);
        as.getEquipment().setItemInMainHand(null);
        as.getEquipment().setItemInOffHand(null);

        if (cosmetic.getSlot() == CosmeticSlot.SKIN && cosmetic instanceof CosmeticSkinType skinType) {
            ItemStack template = skinType.getItem(); // questo è già l'item "reskinnato"
            if (template != null && !template.getType().isAir()) {
                as.getEquipment().setItemInMainHand(template);
            }
            return;
        }

        ItemStack display = cosmetic.getItem();
        if (display == null || display.getType().isAir()) return;

        CosmeticSlot slot = cosmetic.getSlot();

        if (slot == CosmeticSlot.HELMET) {
            as.getEquipment().setHelmet(display);
        } else if (slot == CosmeticSlot.CHESTPLATE) {
            as.getEquipment().setChestplate(display);
        } else if (slot == CosmeticSlot.LEGGINGS) {
            as.getEquipment().setLeggings(display);
        } else if (slot == CosmeticSlot.BOOTS) {
            as.getEquipment().setBoots(display);
        } else if (slot == CosmeticSlot.OFFHAND) {
            as.getEquipment().setItemInOffHand(display);
        } else if (slot == CosmeticSlot.MAINHAND) {
            as.getEquipment().setItemInMainHand(display);
        } else {
        }
    }

    /**
     * Chiama questo in onEnable() per riallineare gli armorstand dopo restart + registrare i listener
     */
    public static void initPersistentArmorStandSupport() {
        ensureBackpackTicker();
        Bukkit.getScheduler().runTask(HMCCosmeticsPlugin.getInstance(), StoreArmorStandManager::refreshArmorStandsOnly);
    }

    public static void scanChunkAndRefresh(Chunk chunk) {
        if (chunk == null) return;

        for (Entity ent : chunk.getEntities()) {
            if (ent instanceof ArmorStand as) {
                tryRefreshIfTagged(as);
            }
        }
    }

    public static void tryRefreshIfTagged(ArmorStand as) {
        if (as == null) return;

        PersistentDataContainer pdc = as.getPersistentDataContainer();
        Integer storeSlot = pdc.get(ARMORSTAND_STORE_SLOT_KEY, PersistentDataType.INTEGER);
        if (storeSlot == null) return;

        if (storeSlot >= 1 && storeSlot <= 7) storeSlot += 9;
        if (storeSlot < 10 || storeSlot > 16) return;

        refreshArmorStand(as);
        Bukkit.getScheduler().runTaskLater(HMCCosmeticsPlugin.getInstance(), () -> refreshArmorStand(as), 2L);
    }

    public static void removeBackpackDisplay(ArmorStand armorStand) {
        if (armorStand == null) return;
        StoreBackpackDisplay display = BACKPACK_DISPLAYS.remove(armorStand.getUniqueId());
        if (display != null) {
            display.despawn();
        }
    }

    private static void ensureBackpackTicker() {
        if (backpackTickerStarted) return;
        backpackTickerStarted = true;

        long period = Settings.getTickPeriod() > 0 ? Settings.getTickPeriod() : 20L; // fallback 1s
        Bukkit.getScheduler().runTaskTimer(
                HMCCosmeticsPlugin.getInstance(),
                () -> {
                    BACKPACK_DISPLAYS.entrySet().removeIf(entry -> {
                        UUID standId = entry.getKey();
                        StoreBackpackDisplay display = entry.getValue();
                        Entity e = Bukkit.getEntity(standId);
                        if (!(e instanceof ArmorStand as) || !as.isValid()) {
                            display.despawn();
                            return true;
                        }
                        display.tick();
                        return false;
                    });
                },
                1L,
                period
        );
    }

    public static @NotNull NamespacedKey getArmorstandStoreIdKey() {
        return ARMORSTAND_STORE_ID_KEY;
    }

    public static @NotNull NamespacedKey getArmorstandStoreSlotKey() {
        return ARMORSTAND_STORE_SLOT_KEY;
    }
}
