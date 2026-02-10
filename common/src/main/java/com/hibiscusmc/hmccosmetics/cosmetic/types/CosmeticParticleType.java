package com.hibiscusmc.hmccosmetics.cosmetic.types;

import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.behavior.CosmeticUpdateBehavior;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import lombok.Getter;
import me.lojosho.shaded.configurate.ConfigurationNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.NotNull;

public class CosmeticParticleType extends Cosmetic implements CosmeticUpdateBehavior {
    @Getter
    private final String particleType;
    @Getter
    private final String particleStyle;
    @Getter
    private final String particleData;

    public CosmeticParticleType(String id, ConfigurationNode config) {
        super(id, config);

        this.particleType = config.node("particle", "type").getString("");
        this.particleStyle = config.node("particle", "style").getString("");
        this.particleData = config.node("particle", "data").getString("");
    }

    public void dispatchUpdate(@NotNull CosmeticUser user) {
        Entity entity = Bukkit.getEntity(user.getUniqueId());
        if (entity == null) return;
        dispatchUpdate(entity);
    }

    public void dispatchUpdate(@NotNull Entity entity) {

    }
}
