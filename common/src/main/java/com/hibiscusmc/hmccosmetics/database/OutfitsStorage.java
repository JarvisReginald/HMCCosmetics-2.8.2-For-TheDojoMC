package com.hibiscusmc.hmccosmetics.database;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class OutfitsStorage {

    private final JavaPlugin plugin;
    private final File file;
    @Getter
    private FileConfiguration config;

    public OutfitsStorage() {
        this.plugin = HMCCosmeticsPlugin.getInstance();
        this.file = new File(plugin.getDataFolder(), "outfits.yml");
    }

    /** Crea la cartella del plugin + outfits.yml se non esiste, e carica in memoria */
    public void load() {
        if (!plugin.getDataFolder().exists()) {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
        }

        if (!file.exists()) {
            try {
                //noinspection ResultOfMethodCallIgnored
                file.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Impossibile creare outfits.yml: " + e.getMessage());
            }
        }

        this.config = YamlConfiguration.loadConfiguration(file);
    }

    /** Salva su disco quello che hai in memoria */
    public void save() {
        if (config == null) return;
        try {
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Impossibile salvare outfits.yml: " + e.getMessage());
        }
    }

    /** Ricarica da disco */
    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
    }

    // ===== PATHS =====

    private String playerBase(UUID uuid) {
        return "players." + uuid.toString();
    }

    private String outfitsBase(UUID uuid) {
        return playerBase(uuid) + ".outfits";
    }

    /** Slot come key numerica (ma in YAML sarà "1", "2", etc.) */
    private String slotPath(UUID uuid, int slot) {
        return outfitsBase(uuid) + "." + slot;
    }

    // ===== API: SLOT LISTE =====

    public List<String> getOutfitSlot(UUID uuid, int slot) {
        ensureLoaded();
        List<String> list = config.getStringList(slotItemsPath(uuid, slot));
        return (list == null) ? new ArrayList<>() : new ArrayList<>(list);
    }

    public void setOutfitSlot(UUID uuid, int slot, List<String> items) {
        ensureLoaded();
        config.set(slotItemsPath(uuid, slot), items == null ? new ArrayList<>() : new ArrayList<>(items));
        save();
    }

    public void deleteOutfitSlot(UUID uuid, int slot) {
        ensureLoaded();
        config.set(slotBase(uuid, slot), null); // elimina tutto lo slot (name+items)
        save();
    }

    /** Aggiunge un valore allo slot (evita duplicati) */
    public boolean addToOutfitSlot(UUID uuid, int slot, String value) {
        ensureLoaded();
        if (value == null || value.isBlank()) return false;

        List<String> list = getOutfitSlot(uuid, slot);
        if (list.contains(value)) return false;

        list.add(value);
        config.set(slotItemsPath(uuid, slot), list);
        save();
        return true;
    }

    /** Rimuove un valore dallo slot */
    public boolean removeFromOutfitSlot(UUID uuid, int slot, String value) {
        ensureLoaded();
        if (value == null || value.isBlank()) return false;

        List<String> list = getOutfitSlot(uuid, slot);
        boolean removed = list.remove(value);
        if (!removed) return false;

        config.set(slotItemsPath(uuid, slot), list);
        save();
        return true;
    }

    /** Elimina completamente il player (tutti gli slot) */
    public void deletePlayer(UUID uuid) {
        ensureLoaded();
        config.set(playerBase(uuid), null);
        save();
    }

    public Set<Integer> getExistingSlots(UUID uuid) {
        ensureLoaded();
        ConfigurationSection sec = config.getConfigurationSection(outfitsBase(uuid));
        if (sec == null) return Collections.emptySet();

        Set<Integer> out = new HashSet<>();
        for (String key : sec.getKeys(false)) {
            int slot;
            try { slot = Integer.parseInt(key); } catch (NumberFormatException ignored) { continue; }

            List<String> items = config.getStringList(slotItemsPath(uuid, slot));
            String name = config.getString(slotNamePath(uuid, slot), null);

            boolean hasItems = items != null && !items.isEmpty();
            boolean hasName = name != null && !name.isBlank();

            if (hasItems || hasName) out.add(slot);
        }
        return out;
    }

    public int countFilledSlots(UUID uuid) {
        ensureLoaded();
        ConfigurationSection sec = config.getConfigurationSection(outfitsBase(uuid));
        if (sec == null) return 0;

        int count = 0;
        for (String key : sec.getKeys(false)) {
            int slot;
            try { slot = Integer.parseInt(key); } catch (NumberFormatException ignored) { continue; }

            List<String> items = config.getStringList(slotItemsPath(uuid, slot));
            if (items != null && !items.isEmpty()) count++;
        }
        return count;
    }

    /** (Opzionale) true se lo slot esiste */
    public boolean hasSlot(UUID uuid, int slot) {
        ensureLoaded();
        return config.contains(slotPath(uuid, slot));
    }

    // ===== UTILS =====

    private void ensureLoaded() {
        if (config == null) {
            // fallback: evita NPE se ti dimentichi di chiamare load() in onEnable
            load();
        }
    }

    public void compactSlots(UUID uuid) {
        ensureLoaded();

        ConfigurationSection outfitsSec = config.getConfigurationSection(outfitsBase(uuid));
        if (outfitsSec == null) return;

        // raccogli slot numerici, ordinati
        List<Integer> oldSlots = new ArrayList<>();
        for (String key : outfitsSec.getKeys(false)) {
            try {
                oldSlots.add(Integer.parseInt(key));
            } catch (NumberFormatException ignored) {}
        }
        Collections.sort(oldSlots);
        if (oldSlots.isEmpty()) return;

        // copia in memoria: items + name
        record SlotData(List<String> items, String name) {}
        List<SlotData> data = new ArrayList<>();

        for (int s : oldSlots) {
            List<String> items = config.getStringList(slotItemsPath(uuid, s));
            if (items == null || items.isEmpty()) continue; // slot vuoto => lo eliminiamo

            String name = config.getString(slotNamePath(uuid, s), null);
            data.add(new SlotData(new ArrayList<>(items), name));
        }

        // pulisci tutta la sezione outfits
        config.set(outfitsBase(uuid), null);

        // riscrivi 1..N
        int newSlot = 1;
        for (SlotData sd : data) {
            config.set(slotItemsPath(uuid, newSlot), sd.items());
            if (sd.name() != null && !sd.name().isBlank()) {
                config.set(slotNamePath(uuid, newSlot), sd.name());
            }
            newSlot++;
        }

        save();
    }

    public void compactSlots(UUID uuid, boolean keepEmpty) {
        ensureLoaded();

        ConfigurationSection outfitsSec = config.getConfigurationSection(outfitsBase(uuid));
        if (outfitsSec == null) return;

        // raccogli slot numerici, ordinati
        List<Integer> oldSlots = new ArrayList<>();
        for (String key : outfitsSec.getKeys(false)) {
            try {
                oldSlots.add(Integer.parseInt(key));
            } catch (NumberFormatException ignored) {
            }
        }
        Collections.sort(oldSlots);
        if (oldSlots.isEmpty()) return;

        // copia in memoria: items + name
        record SlotData(List<String> items, String name) {
        }
        List<SlotData> data = new ArrayList<>();

        for (int s : oldSlots) {
            List<String> items = config.getStringList(slotItemsPath(uuid, s));
            if (!keepEmpty && (items == null || items.isEmpty())) continue;

            String name = config.getString(slotNamePath(uuid, s), null);
            data.add(new SlotData(new ArrayList<>(items), name));
        }

        // pulisci tutta la sezione outfits
        config.set(outfitsBase(uuid), null);

        // riscrivi 1..N
        int newSlot = 1;
        for (SlotData sd : data) {
            config.set(slotItemsPath(uuid, newSlot), sd.items());
            if (sd.name() != null && !sd.name().isBlank()) {
                config.set(slotNamePath(uuid, newSlot), sd.name());
            }
            newSlot++;
        }

        save();
    }

    public List<String> getAllCurrentCosmetics(CosmeticUser user) {
        if (user == null) return new ArrayList<>();

        List<String> out = new ArrayList<>();

        // Usa gli slot realmente equipaggiati (include PARTICLE se presente nella mappa)
        for (CosmeticSlot slot : user.getSlotsWithCosmetics()) {

            // SKIN: può averne più di una
            if (slot == CosmeticSlot.SKIN) {
                for (var skin : user.getEquippedSkins().values()) {
                    if (skin == null) continue;
                    String id = skin.getId();
                    if (id != null && !id.isBlank()) out.add(id);
                }
                continue;
            }

            Cosmetic c = user.getCosmetic(slot);
            if (c == null) continue;

            String id = c.getId();
            if (id != null && !id.isBlank()) out.add(id);
        }

        HMCCosmeticsPlugin.getInstance().getLogger().info("[Outfits] slotsWith=" + user.getSlotsWithCosmetics());

        Cosmetic particle = user.getCosmetic(CosmeticSlot.PARTICLE);
        HMCCosmeticsPlugin.getInstance().getLogger().info("[Outfits] particleSlot=" + (particle == null ? "null" : particle.getId()));

        HMCCosmeticsPlugin.getInstance().getLogger().info("[Outfits] cosmeticsIter=" +
                user.getCosmetics().stream().map(c -> c.getSlot() + ":" + c.getId()).toList());

        return out;
    }

    private String outfitNamePath(UUID uuid, int slot) {
        return outfitsBase(uuid) + "." + slot + ".__name";
    }

    public String getOutfitName(UUID uuid, int slot) {
        ensureLoaded();
        String name = config.getString(slotNamePath(uuid, slot), null);
        return (name == null || name.isBlank()) ? null : name;
    }

    public void setOutfitName(UUID uuid, int slot, String name) {
        ensureLoaded();
        if (name == null || name.isBlank()) {
            config.set(slotNamePath(uuid, slot), null);
            save();
            return;
        }
        if (name.length() > 32) name = name.substring(0, 32);
        config.set(slotNamePath(uuid, slot), name);
        save();
    }

    public void deleteOutfitName(UUID uuid, int slot) {
        ensureLoaded();
        config.set(outfitNamePath(uuid, slot), null);
        save();
    }

    private String slotBase(UUID uuid, int slot) {
        return outfitsBase(uuid) + "." + slot;
    }

    private String slotItemsPath(UUID uuid, int slot) {
        return slotBase(uuid, slot) + ".items";
    }

    private String slotNamePath(UUID uuid, int slot) {
        return slotBase(uuid, slot) + ".__name";
    }
}