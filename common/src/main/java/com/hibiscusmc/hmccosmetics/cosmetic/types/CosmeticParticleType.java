package com.hibiscusmc.hmccosmetics.cosmetic.types;

import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.behavior.CosmeticUpdateBehavior;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.manager.UserWardrobeManager;
import com.hibiscusmc.hmccosmetics.util.NativeParticleUtil;
import lombok.Getter;
import me.lojosho.shaded.configurate.ConfigurationNode;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;

public class CosmeticParticleType extends Cosmetic implements CosmeticUpdateBehavior {

    private static final double DISPLAY_RANGE = 48.0;

    @Getter private final String particleType;
    @Getter private final String particleStyle;
    @Getter private final String particleData;

    private final Particle particle;
    private final Object particleDataObj;

    public CosmeticParticleType(String id, ConfigurationNode config) {
        super(id, config);

        this.particleType  = config.node("particle", "type").getString("");
        this.particleStyle = config.node("particle", "style").getString("");
        this.particleData  = config.node("particle", "data").getString("");

        this.particle        = NativeParticleUtil.parseParticleType(this.particleType);
        this.particleDataObj = NativeParticleUtil.buildParticleData(this.particle, this.particleData);
    }

    @Override
    public void dispatchUpdate(@NotNull CosmeticUser user) {
        UserWardrobeManager wm = user.getWardrobeManager();
        if (wm != null && wm.isActive()) {
            // CLIENT-SIDE: only the single wardrobe viewer sees the NPC's particles.
            Player viewer = user.getPlayer();
            if (viewer != null) {
                dispatchAtLocation(wm.getNpcLocation(), Collections.singletonList(viewer));
            }
            return;
        }

        Entity entity = Bukkit.getEntity(user.getUniqueId());
        if (entity == null) return;
        dispatchUpdate(entity);
    }

    public void dispatchUpdate(@NotNull Entity entity) {
        // SERVER-SIDE: broadcast to all nearby players.
        // Pass raw feet location; NativeParticleUtil applies eye-height internally.
        Location loc = entity.getLocation();
        dispatchAtLocation(loc, loc.getWorld().getNearbyPlayers(loc, DISPLAY_RANGE));
    }

    private void dispatchAtLocation(@NotNull Location loc, @NotNull Collection<Player> viewers) {
        if (viewers.isEmpty()) return;
        NativeParticleUtil.spawnStyled(particle, particleDataObj, particleStyle, loc, viewers);
    }
}
