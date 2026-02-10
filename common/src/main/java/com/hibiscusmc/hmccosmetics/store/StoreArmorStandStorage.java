package com.hibiscusmc.hmccosmetics.store;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class StoreArmorStandStorage {

    private static final File FILE = new File(HMCCosmeticsPlugin.getInstance().getDataFolder(), "store-stands.yml");
    private static YamlConfiguration cfg;

    private StoreArmorStandStorage() {}

    public static void init() {
        if (!HMCCosmeticsPlugin.getInstance().getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            HMCCosmeticsPlugin.getInstance().getDataFolder().mkdirs();
        }
        cfg = YamlConfiguration.loadConfiguration(FILE);
        if (!cfg.isConfigurationSection("stands")) {
            cfg.createSection("stands");
            save();
        }
    }

    public static void saveStand(ArmorStand as, int id, int slot) {
        if (as == null || !as.isValid() || as.getWorld() == null) return;

        String key = keyFromLocation(as.getLocation());
        ConfigurationSection s = cfg.getConfigurationSection("stands");
        if (s == null) s = cfg.createSection("stands");

        ConfigurationSection node = s.createSection(key);
        node.set("id", id);
        node.set("slot", slot);

        node.set("yaw", (double) as.getLocation().getYaw());

        save();
    }

    public static void removeStandByLocation(ArmorStand as) {
        if (as == null || as.getWorld() == null) return;
        String key = keyFromLocation(as.getLocation());
        ConfigurationSection s = cfg.getConfigurationSection("stands");
        if (s == null) return;
        s.set(key, null);
        save();
    }

    public static boolean applyIfStored(ArmorStand as) {
        if (as == null || as.getWorld() == null) return false;

        ConfigurationSection s = cfg.getConfigurationSection("stands");
        if (s == null) return false;

        String key = keyFromLocation(as.getLocation());
        ConfigurationSection node = s.getConfigurationSection(key);

        if (node == null) {
            node = findNearestNode(as.getLocation(), s, 0.75);
            if (node == null) return false;
        }

        int id = node.getInt("id", -1);
        int slot = node.getInt("slot", -1);
        if (id < 1 || id > 7) return false;
        if (slot < 10 || slot > 16) return false;

        PersistentDataContainer pdc = as.getPersistentDataContainer();
        pdc.set(StoreArmorStandManager.getArmorstandStoreIdKey(), PersistentDataType.INTEGER, id);
        pdc.set(StoreArmorStandManager.getArmorstandStoreSlotKey(), PersistentDataType.INTEGER, slot);

        StoreArmorStandManager.refreshArmorStand(as);
        return true;
    }

    public static void applyForChunk(Chunk chunk) {
        if (chunk == null) return;
        for (var e : chunk.getEntities()) {
            if (e instanceof ArmorStand as) {
                applyIfStored(as);
            }
        }
    }

    public static void applyForLoadedWorlds() {
        for (World w : Bukkit.getWorlds()) {
            for (ArmorStand as : w.getEntitiesByClass(ArmorStand.class)) {
                applyIfStored(as);
            }
        }
    }

    private static ConfigurationSection findNearestNode(Location loc, ConfigurationSection stands, double radius) {
        String worldName = loc.getWorld().getName();
        double bestDist = Double.MAX_VALUE;
        ConfigurationSection best = null;

        for (String k : stands.getKeys(false)) {
            // k = world;x;y;z
            String[] parts = k.split(";");
            if (parts.length < 4) continue;
            if (!parts[0].equalsIgnoreCase(worldName)) continue;

            int x = parseInt(parts[1], Integer.MIN_VALUE);
            int y = parseInt(parts[2], Integer.MIN_VALUE);
            int z = parseInt(parts[3], Integer.MIN_VALUE);
            if (x == Integer.MIN_VALUE) continue;

            Location cand = new Location(loc.getWorld(), x + 0.5, y, z + 0.5);
            double d = cand.distance(loc);
            if (d <= radius && d < bestDist) {
                bestDist = d;
                best = stands.getConfigurationSection(k);
            }
        }

        return best;
    }

    private static String keyFromLocation(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception ignored) { return def; }
    }

    private static void save() {
        try {
            cfg.save(FILE);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
