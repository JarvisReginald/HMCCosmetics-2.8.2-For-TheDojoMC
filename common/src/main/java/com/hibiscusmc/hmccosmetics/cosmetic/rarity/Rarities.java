package com.hibiscusmc.hmccosmetics.cosmetic.rarity;

import me.lojosho.shaded.configurate.ConfigurationNode;

import java.util.*;

public final class Rarities {

    private static final LinkedHashMap<String, Rarity> rarities = new LinkedHashMap<>();
    private static String defaultId = null;
    private static int totalChance = 0;

    private Rarities() {}

    public static void load(ConfigurationNode root) {
        rarities.clear();
        totalChance = 0;
        defaultId = null;

        if (root == null || root.virtual()) return;

        for (Map.Entry<Object, ? extends ConfigurationNode> entry : root.childrenMap().entrySet()) {
            String id = String.valueOf(entry.getKey());
            ConfigurationNode node = entry.getValue();

            int price = node.node("price").getInt(0);
            String name = node.node("name").getString(id);
            int chance = node.node("chance").getInt(0);

            if (defaultId == null) defaultId = id;

            Rarity rarity = new Rarity(id, price, name, chance);
            rarities.put(id.toLowerCase(Locale.ROOT), rarity);
            totalChance += Math.max(0, chance);
        }

        // Validazione: chances devono sommare 100
        if (!rarities.isEmpty() && totalChance != 100) {
            // Non blocco il plugin, ma loggo forte.
            System.out.println("[HMCCosmetics] WARNING: rarities chances sum = " + totalChance + " (should be 100)");
        }
    }

    public static boolean exists(String id) {
        return id != null && rarities.containsKey(id.toLowerCase(Locale.ROOT));
    }

    public static Rarity get(String id) {
        if (id != null) {
            Rarity r = rarities.get(id.toLowerCase(Locale.ROOT));
            if (r != null) return r;
        }
        return getDefault();
    }

    public static Rarity getDefault() {
        if (defaultId == null) return new Rarity("default", 0, "DEFAULT", 0);
        return rarities.get(defaultId.toLowerCase(Locale.ROOT));
    }

    public static Collection<Rarity> values() {
        return rarities.values();
    }

    public static Rarity roll(Random random) {
        if (rarities.isEmpty()) return getDefault();

        int roll = random.nextInt(Math.max(1, totalChance)) + 1; // 1..totalChance
        int acc = 0;

        for (Rarity r : rarities.values()) {
            acc += Math.max(0, r.chance());
            if (roll <= acc) return r;
        }
        return getDefault();
    }
}
