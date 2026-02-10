package com.hibiscusmc.hmccosmetics.cosmetic.retexture;

import me.lojosho.shaded.configurate.ConfigurationNode;
import me.lojosho.shaded.configurate.serialize.SerializationException;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.*;

public final class RetextureGroupLoader {

    private RetextureGroupLoader() {}

    public static void load(ConfigurationNode root) {
        RetextureGroupRegistry.clear();
        if (root == null) return;

        ConfigurationNode groupsNode = root.node("retexture_groups");
        if (groupsNode.virtual()) return;

        for (Map.Entry<Object, ? extends ConfigurationNode> groupEntry : groupsNode.childrenMap().entrySet()) {
            String groupName = String.valueOf(groupEntry.getKey());
            ConfigurationNode groupNode = groupEntry.getValue();
            if (groupNode == null || groupNode.virtual()) continue;

            Map<Material, List<Integer>> map = new HashMap<>();

            for (Map.Entry<Object, ? extends ConfigurationNode> matEntry : groupNode.childrenMap().entrySet()) {
                String matKey = String.valueOf(matEntry.getKey());
                Material mat = Material.matchMaterial(matKey);
                if (mat == null) continue;

                ConfigurationNode valueNode = matEntry.getValue();
                if (valueNode == null || valueNode.virtual()) continue;

                List<Integer> cmds;

                if (!valueNode.childrenList().isEmpty() || valueNode.isList()) {
                    try {
                        cmds = valueNode.getList(Integer.class, List.of());
                    } catch (SerializationException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    int single = valueNode.getInt(0);
                    cmds = List.of(single);
                }

                if (!cmds.isEmpty()) {
                    map.put(mat, List.copyOf(cmds));
                }
            }

            if (!map.isEmpty()) {
                RetextureGroupRegistry.registerGroup(groupName, map);
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.updateInventory();
        }
    }
}
