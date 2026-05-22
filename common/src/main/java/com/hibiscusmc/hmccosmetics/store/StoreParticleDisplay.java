package com.hibiscusmc.hmccosmetics.store;

import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticParticleType;
import com.hibiscusmc.hmccosmetics.util.NativeParticleUtil;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Displays a particle cosmetic at a store armor stand each tick.
 * Uses Bukkit's native particle API — no PlayerParticles dependency.
 * Particles are spawned server-side and are visible to all nearby players.
 */
public final class StoreParticleDisplay {

    private static final double DISPLAY_RANGE = 48.0;

    private final ArmorStand ownerStand;
    private final Particle particle;
    private final Object particleData;
    private final String style;

    public StoreParticleDisplay(ArmorStand ownerStand, CosmeticParticleType particleType) {
        this.ownerStand = ownerStand;
        this.particle = NativeParticleUtil.parseParticleType(particleType.getParticleType());
        this.particleData = NativeParticleUtil.buildParticleData(this.particle, particleType.getParticleData());
        this.style = particleType.getParticleStyle();
    }

    public void tick() {
        if (ownerStand == null || !ownerStand.isValid()) return;

        // Pass raw feet location; NativeParticleUtil applies eye-height internally.
        Location loc = ownerStand.getLocation();
        Collection<Player> nearby = loc.getWorld().getNearbyPlayers(loc, DISPLAY_RANGE);
        if (nearby.isEmpty()) return;

        NativeParticleUtil.spawnStyled(particle, particleData, style, loc, nearby);
    }

    public void despawn() {
        // Particles are transient — nothing to clean up.
    }
}
