package com.hibiscusmc.hmccosmetics.database.types;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetics;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticSkinType;
import com.hibiscusmc.hmccosmetics.database.UserData;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import org.apache.commons.lang3.EnumUtils;
import org.bukkit.Color;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public abstract class Data {

    public abstract void setup();

    public abstract void save(CosmeticUser user);

    public void saveSync(CosmeticUser user) {
        // Default implementation: just call save (for implementations that don't need sync)
        save(user);
    }

    @Nullable
    public abstract CompletableFuture<UserData> get(UUID uniqueId);

    public abstract void clear(UUID uniqueId);

    // BACKPACK=colorfulbackpack&RRGGBB,HELMET=niftyhat,BALLOON=colorfulballoon,CHESTPLATE=niftychestplate
    @NotNull
    public final String serializeData(@NotNull CosmeticUser user) {
        StringBuilder data = new StringBuilder();
        if (user.isHidden()) {
            boolean firstHidden = true;
            for (CosmeticUser.HiddenReason reason :  user.getHiddenReasons()) {
                if (shouldHiddenSave(reason)) {
                    if (!firstHidden) data.append(",");
                    data.append("HIDDEN=").append(reason);
                    firstHidden = false;
                }
            }
        }
        int cosmeticCount = 0;
        for (Cosmetic cosmetic : user.getCosmetics()) {
            Color color = user.getCosmeticColor(cosmetic.getSlot());

            String key;
            if (cosmetic instanceof CosmeticSkinType skin) {
                String group = skin.getRetextureGroup();
                String skinKey = (group != null && !group.isEmpty()) ? group : ("__skin__" + skin.getId());
                key = "SKIN@" + skinKey;
            } else {
                key = cosmetic.getSlot().toString();
            }

            String input = key + "=" + cosmetic.getId();
            if (color != null) input = input + "&" + color.asRGB();

            if (data.isEmpty()) data.append(input);
            else data.append(",").append(input);
            cosmeticCount++;
        }
        HMCCosmeticsPlugin.getInstance().getLogger().info("[HMCCosmetics] serializeData for " + user.getUniqueId() + ": " + cosmeticCount + " cosmetics -> " + data.toString());
        return data.toString();
    }

    @NotNull
    public final HashMap<CosmeticSlot, Map.Entry<Cosmetic, Integer>> deserializeData(@NotNull String raw) {
        return deserializeAll(raw).cosmetics;
    }

    public static final class ParsedData {
        public final HashMap<CosmeticSlot, Map.Entry<Cosmetic, Integer>> cosmetics = new HashMap<>();
        public final HashMap<String, Map.Entry<CosmeticSkinType, Integer>> skins = new HashMap<>();
        public final ArrayList<CosmeticUser.HiddenReason> hiddenReasons = new ArrayList<>();
    }

    @NotNull
    public final ParsedData deserializeAll(@NotNull String raw) {
        ParsedData out = new ParsedData();
        HMCCosmeticsPlugin.getInstance().getLogger().info("[HMCCosmetics] deserializeAll raw: \"" + raw + "\"");

        String[] rawData = raw.split(",");
        for (String a : rawData) {
            if (a == null || a.isEmpty()) continue;

            String[] splitData = a.split("=", 2);
            if (splitData.length < 2) continue;

            String left = splitData[0];
            String right = splitData[1];

            // HIDDEN=REASON
            if (left.equalsIgnoreCase("HIDDEN")) {
                if (EnumUtils.isValidEnum(CosmeticUser.HiddenReason.class, right)) {
                    if (!Settings.isForceShowOnJoin()) {
                        out.hiddenReasons.add(CosmeticUser.HiddenReason.valueOf(right));
                    }
                }
                continue;
            }

            // valore + colore
            String idPart = right;
            int color = -1;
            if (right.contains("&")) {
                String[] colorSplit = right.split("&", 2);
                idPart = colorSplit[0];
                try { color = Integer.parseInt(colorSplit[1]); } catch (NumberFormatException ignored) { color = -1; }
            }

            // SKIN@group=cosmeticId
            if (left.toUpperCase(Locale.ROOT).startsWith("SKIN@")) {
                String key = left.substring("SKIN@".length());
                if (key.isEmpty()) continue;

                if (!Cosmetics.hasCosmetic(idPart)) {
                    HMCCosmeticsPlugin.getInstance().getLogger().warning("[HMCCosmetics] deserializeAll: SKIN cosmetic not found: " + idPart);
                    continue;
                }
                Cosmetic cosmetic = Cosmetics.getCosmetic(idPart);
                if (!(cosmetic instanceof CosmeticSkinType skin)) continue;

                out.skins.put(key, Map.entry(skin, color));
                continue;
            }

            // slot normale: HELMET=...
            CosmeticSlot slot;
            try {
                slot = CosmeticSlot.valueOf(left);
            } catch (IllegalArgumentException ex) {
                HMCCosmeticsPlugin.getInstance().getLogger().warning("[HMCCosmetics] deserializeAll: invalid slot: " + left);
                continue;
            }

            if (!Cosmetics.hasCosmetic(idPart)) {
                HMCCosmeticsPlugin.getInstance().getLogger().warning("[HMCCosmetics] deserializeAll: cosmetic not found: " + idPart + " (slot=" + slot + ")");
                continue;
            }
            Cosmetic cosmetic = Cosmetics.getCosmetic(idPart);

            out.cosmetics.put(slot, Map.entry(cosmetic, color));
        }
        HMCCosmeticsPlugin.getInstance().getLogger().info("[HMCCosmetics] deserializeAll result: " + out.cosmetics.size() + " cosmetics, " + out.skins.size() + " skins");
        return out;
    }

    private boolean shouldHiddenSave(CosmeticUser.HiddenReason reason) {
        switch (reason) {
            case EMOTE, NONE, GAMEMODE, WORLD, DISABLED, POTION -> {
                return false;
            }
            default -> {
                return true;
            }
        }
    }
}
