package com.hibiscusmc.hmccosmetics.cosmetic.retexture;

import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RetextureGroupRegistry {

    private static final Map<String, Map<Material, List<Integer>>> GROUPS = new ConcurrentHashMap<>();

    private RetextureGroupRegistry() {}

    public static void clear() {
        GROUPS.clear();
    }

    public static void registerGroup(String name, Map<Material, List<Integer>> materialMap) {
        if (name == null) return;
        GROUPS.put(normalize(name), Map.copyOf(materialMap));
    }

    public static boolean matches(String groupName, Material material) {
        if (groupName == null || material == null) return false;
        Map<Material, List<Integer>> map = GROUPS.get(normalize(groupName));
        return map != null && map.containsKey(material);
    }

    public static @Nullable List<Integer> getCmds(String groupName, Material material) {
        if (groupName == null || material == null) return null;
        Map<Material, List<Integer>> map = GROUPS.get(normalize(groupName));
        if (map == null) return null;
        return map.get(material);
    }

    // opzionale: compatibilità col vecchio getCmd (ritorna il primo)
    public static @Nullable Integer getCmd(String groupName, Material material) {
        List<Integer> cmds = getCmds(groupName, material);
        if (cmds == null || cmds.isEmpty()) return null;
        return cmds.getFirst();
    }

    private static String normalize(String s) {
        return s.trim().toLowerCase();
    }

    public static @Nullable Map<Material, List<Integer>> getGroupMap(String groupName) {
        if (groupName == null) return null;
        return GROUPS.get(normalize(groupName));
    }
}
