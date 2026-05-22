package com.hibiscusmc.hmccosmetics.util;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Collection;
import java.util.Locale;

/**
 * Bukkit-native particle rendering that faithfully replicates PlayerParticles'
 * built-in styles using the same default parameters and mathematics.
 *
 * <p>The {@code center} parameter expected by {@link #spawnStyled} should be
 * {@code entity.getLocation()} (feet position). Each style internally applies
 * the same Y offset that PlayerParticles' ConfiguredParticleStyle.locationOffset
 * would produce, so the spawn heights match PP exactly.</p>
 */
public final class NativeParticleUtil {

    // -------------------------------------------------------------------------
    // PP ConfiguredParticleStyle defaults — kept as named constants so that
    // any future tuning is easy to find.
    // -------------------------------------------------------------------------

    // normal – locationOffset = 0  → centre at entity feet
    // (PP uses eye-level ~+1.62 via entity.getEyeLocation(); we approximate
    //  this by always adding EYE_HEIGHT to the entity feet in the style methods)
    private static final double EYE_HEIGHT = 1.62;

    // feet – locationOffset = 0.5, feetOffset = -0.95,
    //        spreadX/Z = 0.4, spreadY = 0.0, speed = 0.0, particlesPerTick = 1
    private static final double FEET_LOC_OFFSET  = 0.5;
    private static final double FEET_OFFSET       = -0.95;
    private static final double FEET_SPREAD_XZ    = 0.4;

    // overhead – locationOffset = -0.5, headOffset = 1.75,
    //            spreadX/Z = 0.4, spreadY = 0.1, speed = 0.0, particlesPerTick = 1
    private static final double OVERHEAD_LOC_OFFSET = -0.5;
    private static final double OVERHEAD_HEAD_OFFSET = 1.75;
    private static final double OVERHEAD_SPREAD_XZ   = 0.4;
    private static final double OVERHEAD_SPREAD_Y    = 0.1;

    // orbit – locationOffset = 0, orbs = 3, steps = 120, radius = 1.0
    private static final int    ORBIT_ORBS    = 3;
    private static final int    ORBIT_STEPS   = 120;
    private static final double ORBIT_RADIUS  = 1.0;

    // halo – locationOffset = -0.5, particleAmount = 16, radius = 0.65,
    //         playerOffset = 1.5, skipNextSpawn alternates each tick
    private static final int    HALO_POINTS        = 16;
    private static final double HALO_RADIUS        = 0.65;
    private static final double HALO_PLAYER_OFFSET = 1.5;
    private static final double HALO_LOC_OFFSET    = -0.5;

    // wings – locationOffset = 0, spawnDelay = 3
    private static final int WINGS_SPAWN_DELAY = 3;

    // spiral – locationOffset = 0, particles = 12,
    //          particlesPerRotation = 90, radius = 0.8
    private static final int    SPIRAL_COUNT        = 12;
    private static final int    SPIRAL_PER_ROTATION = 90;
    private static final double SPIRAL_RADIUS       = 0.8;

    // vortex – locationOffset = 0.5, radius = 2.0, grow = 0.05,
    //          radials = π/16 (config value 16), helices = 4, maxStep = 70
    private static final double VORTEX_RADIUS    = 2.0;
    private static final double VORTEX_GROW      = 0.05;
    private static final double VORTEX_RADIALS   = Math.PI / 16.0;
    private static final int    VORTEX_HELICES   = 4;
    private static final int    VORTEX_MAX_STEP  = 70;
    private static final double VORTEX_LOC_OFFSET = 0.5;

    // quadhelix – locationOffset = 0, orbs = 4, stepsX = 80, stepsY = 60
    private static final int QUADHELIX_ORBS   = 4;
    private static final int QUADHELIX_MAX_X  = 80;
    private static final int QUADHELIX_MAX_Y  = 60;

    private NativeParticleUtil() {}

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Map a PlayerParticles-style effect name to a Bukkit Particle.
     */
    public static Particle parseParticleType(String name) {
        if (name == null || name.isBlank()) return Particle.FLAME;
        String upper = name.trim().toUpperCase(Locale.ROOT);
        try {
            return Particle.valueOf(upper);
        } catch (IllegalArgumentException ignored) {}
        return switch (name.trim().toLowerCase(Locale.ROOT)) {
            case "dust", "redstone"                                -> Particle.DUST;
            case "enchant", "enchantment_table", "enchanting_table" -> Particle.ENCHANT;
            case "soul_fire_flame"                                 -> Particle.SOUL_FIRE_FLAME;
            case "end_rod"                                         -> Particle.END_ROD;
            case "cherry_leaves"                                   -> Particle.CHERRY_LEAVES;
            case "dragon_breath"                                   -> Particle.DRAGON_BREATH;
            case "snowflake"                                       -> Particle.SNOWFLAKE;
            case "firework"                                        -> Particle.FIREWORK;
            case "cloud"                                           -> Particle.CLOUD;
            default                                                -> Particle.FLAME;
        };
    }

    /**
     * Build the particle-data object required by Bukkit's {@code spawnParticle}.
     *
     * <p>Only DUST-family particles are handled explicitly (so custom colours
     * can be parsed from the config string). Every other particle falls through
     * to a runtime {@link Particle#getDataType()} check so the correct data
     * class is always used regardless of server version — avoiding both
     * "missing required data" and "data X should be Void" errors.</p>
     */
    public static Object buildParticleData(Particle particle, String data) {
        if (particle == Particle.DUST) {
            return new Particle.DustOptions(parseColor(data), 1.0f);
        }
        if (particle == Particle.DUST_COLOR_TRANSITION) {
            Color c = parseColor(data);
            return new Particle.DustTransition(c, c, 1.0f);
        }

        // For every other particle: ask the runtime API what data class it needs
        // and supply an appropriate default. Void / null means no data required.
        Class<?> dataClass = particle.getDataType();
        if (dataClass != null && dataClass != Void.class) {
            return safeDefaultForDataClass(dataClass, data);
        }
        return null;
    }

    /**
     * Returns a best-effort safe default value for an unexpected data class so
     * that {@code spawnParticle} does not throw.  Falls back to {@code null}
     * (which will itself throw for mandatory types, but at least the error
     * message will point to the uncovered case clearly).
     */
    private static Object safeDefaultForDataClass(Class<?> cls, String data) {
        if (cls == Float.class || cls == float.class)   return 0.0f;
        if (cls == Integer.class || cls == int.class)   return 0;
        if (cls == Color.class)                         return parseColor(data);
        if (cls == Particle.DustOptions.class)          return new Particle.DustOptions(parseColor(data), 1.0f);
        return null;
    }

    /**
     * Spawn one animation frame of particles for the given PP-compatible style,
     * sending them only to the specified viewers.
     *
     * @param particle Bukkit particle enum
     * @param data     particle data from {@link #buildParticleData}, or {@code null}
     * @param style    style name (normal, feet, orbit, overhead, halo, wings, spiral, vortex, quadhelix)
     * @param center   entity feet location (entity.getLocation())
     * @param viewers  players who should receive the particles
     */
    public static void spawnStyled(Particle particle, Object data,
                                   String style, Location center,
                                   Collection<Player> viewers) {
        if (viewers.isEmpty()) return;
        long tick = System.currentTimeMillis() / 50L; // 1 tick ≈ 50 ms at 20 TPS
        String s = style == null ? "normal" : style.trim().toLowerCase(Locale.ROOT);
        switch (s) {
            case "feet"      -> spawnFeet(particle, data, center, viewers);
            case "overhead"  -> spawnOverhead(particle, data, center, viewers);
            case "orbit"     -> spawnOrbit(particle, data, center, viewers, tick);
            case "halo"      -> spawnHalo(particle, data, center, viewers, tick);
            case "wings"     -> spawnWings(particle, data, center, viewers, tick);
            case "spiral"    -> spawnSpiral(particle, data, center, viewers, tick);
            case "vortex"    -> spawnVortex(particle, data, center, viewers, tick);
            case "quadhelix" -> spawnQuadhelix(particle, data, center, viewers, tick);
            default          -> spawnNormal(particle, data, center, viewers);
        }
    }

    // =========================================================================
    // Style implementations — each method mirrors the PP source directly
    // =========================================================================

    /**
     * ParticleStyleNormal – particle-type-dependent scatter around the centre.
     * Replicates PP's switch on {@code particleEffect} with matching offsets/speed.
     */
    @SuppressWarnings("unchecked")
    private static void spawnNormal(Particle particle, Object data,
                                    Location center, Collection<Player> viewers) {
        double baseY = center.getY() + EYE_HEIGHT;
        double x = center.getX(), z = center.getZ();

        double oX, oY, oZ, speed;
        int count;

        switch (particle) {
            case ANGRY_VILLAGER, BLOCK, DRIPPING_LAVA, DRIPPING_WATER,
                 HEART, ITEM, NOTE, SPIT, SQUID_INK, TOTEM_OF_UNDYING -> {
                oX = 0.6; oY = 0.6; oZ = 0.6; speed = 0; count = 1;
            }
            case DUST, HAPPY_VILLAGER -> {
                oX = 0.5; oY = 0.5; oZ = 0.5; speed = 0; count = 1;
            }
            case ENCHANT -> {
                oX = 0.6; oY = 0.6; oZ = 0.6; speed = 1.0; count = 1;
            }
            case FLAME, CLOUD -> {
                oX = 0.1; oY = 0.1; oZ = 0.1; speed = 0.05; count = 1;
            }
            case NAUTILUS, PORTAL -> {
                oX = 0.5; oY = 0.5; oZ = 0.5; speed = 1.0; count = 1;
            }
            case UNDERWATER -> {
                oX = 0.5; oY = 0.5; oZ = 0.5; speed = 0; count = 5;
            }
            default -> {
                oX = 0.4; oY = 0.4; oZ = 0.4; speed = 0; count = 1;
            }
        }

        for (Player p : viewers) {
            p.spawnParticle(particle, x, baseY, z, count, oX, oY, oZ, speed, data);
        }
    }

    /**
     * ParticleStyleFeet – one particle per tick at feet level.
     * PP defaults: feetOffset=-0.95, locationOffset=0.5, spreadXZ=0.4, spreadY=0.0.
     */
    @SuppressWarnings("unchecked")
    private static void spawnFeet(Particle particle, Object data,
                                  Location center, Collection<Player> viewers) {
        double spawnY = center.getY() + EYE_HEIGHT + FEET_LOC_OFFSET + FEET_OFFSET;
        double x = center.getX(), z = center.getZ();
        for (Player p : viewers) {
            p.spawnParticle(particle, x, spawnY, z, 1,
                    FEET_SPREAD_XZ, 0.0, FEET_SPREAD_XZ, 0.0, data);
        }
    }

    /**
     * ParticleStyleOverhead – one particle per tick above the player's head.
     * PP defaults: locationOffset=-0.5, headOffset=1.75, spreadXZ=0.4, spreadY=0.1.
     */
    @SuppressWarnings("unchecked")
    private static void spawnOverhead(Particle particle, Object data,
                                      Location center, Collection<Player> viewers) {
        double spawnY = center.getY() + EYE_HEIGHT + OVERHEAD_LOC_OFFSET + OVERHEAD_HEAD_OFFSET;
        double x = center.getX(), z = center.getZ();
        for (Player p : viewers) {
            p.spawnParticle(particle, x, spawnY, z, 1,
                    OVERHEAD_SPREAD_XZ, OVERHEAD_SPREAD_Y, OVERHEAD_SPREAD_XZ, 0.0, data);
        }
    }

    /**
     * ParticleStyleOrbit – 3 orbs circling the player.
     * PP: orbs=3, steps=120, radius=1.0, locationOffset=0.
     * Each call advances the orbit by the current tick step.
     */
    @SuppressWarnings("unchecked")
    private static void spawnOrbit(Particle particle, Object data,
                                   Location center, Collection<Player> viewers, long tick) {
        int step = (int)(tick % ORBIT_STEPS);
        double baseY = center.getY() + EYE_HEIGHT;
        double x = center.getX(), z = center.getZ();
        for (int i = 0; i < ORBIT_ORBS; i++) {
            double angle = (step / (double) ORBIT_STEPS) * (Math.PI * 2)
                    + ((Math.PI * 2) / ORBIT_ORBS) * i;
            double dx = -Math.cos(angle) * ORBIT_RADIUS;
            double dz = -Math.sin(angle) * ORBIT_RADIUS;
            double px = x + dx, py = baseY, pz = z + dz;
            for (Player p : viewers) {
                p.spawnParticle(particle, px, py, pz, 1, 0, 0, 0, 0, data);
            }
        }
    }

    /**
     * ParticleStyleHalo – static ring above the player's head.
     * PP: particleAmount=16, radius=0.65, playerOffset=1.5, locationOffset=-0.5.
     * skipNextSpawn: only renders on even ticks.
     */
    @SuppressWarnings("unchecked")
    private static void spawnHalo(Particle particle, Object data,
                                  Location center, Collection<Player> viewers, long tick) {
        if (tick % 2 != 0) return; // skipNextSpawn
        double haloY = center.getY() + EYE_HEIGHT + HALO_LOC_OFFSET + HALO_PLAYER_OFFSET;
        double slice = 2 * Math.PI / HALO_POINTS;
        double x = center.getX(), z = center.getZ();
        for (int i = 0; i < HALO_POINTS; i++) {
            double angle = slice * i;
            double px = x + HALO_RADIUS * Math.cos(angle);
            double pz = z + HALO_RADIUS * Math.sin(angle);
            for (Player p : viewers) {
                p.spawnParticle(particle, px, haloY, pz, 1, 0, 0, 0, 0, data);
            }
        }
    }

    /**
     * ParticleStyleWings – butterfly-curve wings oriented by the entity's yaw.
     * PP: spawnDelay=3 (only spawns every 3rd tick).
     * Butterfly curve formula from PP source (Esophose).
     */
    @SuppressWarnings("unchecked")
    private static void spawnWings(Particle particle, Object data,
                                   Location center, Collection<Player> viewers, long tick) {
        if (tick % WINGS_SPAWN_DELAY != 0) return;
        double baseY = center.getY() + EYE_HEIGHT;
        double yawDeg = center.getYaw();
        for (double t = 0; t < Math.PI * 2; t += Math.PI / 48.0) {
            double cosT = Math.cos(t);
            double sinT = Math.sin(t);
            double offset = (Math.pow(Math.E, cosT)
                    - 2.0 * Math.cos(t * 4)
                    - Math.pow(sinT / 12.0, 5)) / 2.0;
            double vx = sinT * offset;
            double vy = cosT * offset;
            // rotate around Y axis by -yaw (PP uses VectorUtils.rotateAroundAxisY)
            Vector v = rotateAroundAxisY(new Vector(vx, vy, -0.3),
                    -Math.toRadians(yawDeg));
            double px = center.getX() + v.getX();
            double py = baseY + v.getY();
            double pz = center.getZ() + v.getZ();
            for (Player p : viewers) {
                p.spawnParticle(particle, px, py, pz, 1, 0, 0, 0, 0, data);
            }
        }
    }

    /**
     * ParticleStyleSpiral – 12-particle double helix that animates upward.
     * PP: particles=12, particlesPerRotation=90, radius=0.8, locationOffset=0.
     * stepX increments each tick.
     */
    @SuppressWarnings("unchecked")
    private static void spawnSpiral(Particle particle, Object data,
                                    Location center, Collection<Player> viewers, long tick) {
        int stepX = (int)(tick & 0x7FFFFFFFL); // always positive
        double baseY = center.getY() + EYE_HEIGHT;
        double x = center.getX(), z = center.getZ();
        for (double stepY = -60; stepY < 60; stepY += 120.0 / SPIRAL_COUNT) {
            double angle = ((stepX + stepY) / (double) SPIRAL_PER_ROTATION) * Math.PI * 2;
            double dx = -Math.cos(angle) * SPIRAL_RADIUS;
            double dy = stepY / SPIRAL_PER_ROTATION / 2.0;
            double dz = -Math.sin(angle) * SPIRAL_RADIUS;
            double px = x + dx, py = baseY + dy, pz = z + dz;
            for (Player p : viewers) {
                p.spawnParticle(particle, px, py, pz, 1, 0, 0, 0, 0, data);
            }
        }
    }

    /**
     * ParticleStyleVortex – 4 helices contracting inward as they rise.
     * PP: radius=2.0, grow=0.05, radials=π/16, helices=4, maxStep=70, locationOffset=0.5.
     */
    @SuppressWarnings("unchecked")
    private static void spawnVortex(Particle particle, Object data,
                                    Location center, Collection<Player> viewers, long tick) {
        int step = (int)(tick % VORTEX_MAX_STEP);
        double baseY = center.getY() + EYE_HEIGHT + VORTEX_LOC_OFFSET;
        double radius = VORTEX_RADIUS * (1.0 - (double) step / VORTEX_MAX_STEP);
        double x = center.getX(), z = center.getZ();
        for (int i = 0; i < VORTEX_HELICES; i++) {
            double angle = step * VORTEX_RADIALS + (2 * Math.PI * i / VORTEX_HELICES);
            double px = x + Math.cos(angle) * radius;
            double py = baseY + step * VORTEX_GROW - 1.0;
            double pz = z + Math.sin(angle) * radius;
            for (Player p : viewers) {
                p.spawnParticle(particle, px, py, pz, 1, 0, 0, 0, 0, data);
            }
        }
    }

    /**
     * ParticleStyleQuadhelix – 4 orbs tracing a figure-8 helix.
     * PP: orbs=4, stepsX=80, stepsY=60, locationOffset=0.
     * stepX increments each tick; stepY oscillates -60..+60 and reverses.
     */
    @SuppressWarnings("unchecked")
    private static void spawnQuadhelix(Particle particle, Object data,
                                       Location center, Collection<Player> viewers, long tick) {
        // Replicate the independent stepX and reversing stepY counters.
        // stepX wraps at QUADHELIX_MAX_X; stepY ping-pongs between -MAX_Y and +MAX_Y.
        int stepX = (int)(tick % (QUADHELIX_MAX_X + 1));
        int fullCycle = QUADHELIX_MAX_Y * 2; // one full ping-pong cycle
        int phase = (int)(tick % fullCycle);
        int stepY = (phase <= QUADHELIX_MAX_Y) ? (phase - QUADHELIX_MAX_Y) : (QUADHELIX_MAX_Y - (phase - QUADHELIX_MAX_Y));

        double baseY = center.getY() + EYE_HEIGHT;
        double x = center.getX(), z = center.getZ();
        for (int i = 0; i < QUADHELIX_ORBS; i++) {
            double angle = (stepX / (double) QUADHELIX_MAX_X) * (Math.PI * 2)
                    + ((Math.PI * 2) / QUADHELIX_ORBS) * i;
            double radiusFactor = (QUADHELIX_MAX_Y - Math.abs(stepY)) / (double) QUADHELIX_MAX_Y;
            double dx = -Math.cos(angle) * radiusFactor;
            double dy = (stepY / (double) QUADHELIX_MAX_Y) * 1.5;
            double dz = -Math.sin(angle) * radiusFactor;
            double px = x + dx, py = baseY + dy, pz = z + dz;
            for (Player p : viewers) {
                p.spawnParticle(particle, px, py, pz, 1, 0, 0, 0, 0, data);
            }
        }
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Mirrors VectorUtils.rotateAroundAxisY from PlayerParticles. */
    private static Vector rotateAroundAxisY(Vector v, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = v.getX() * cos + v.getZ() * sin;
        double z = v.getX() * -sin + v.getZ() * cos;
        return v.setX(x).setZ(z);
    }

    /**
     * Parse a color string: "rainbow" / "random" for a cycling hue,
     * "R G B" for explicit values, or white as fallback.
     */
    private static Color parseColor(String data) {
        if (data == null || data.isBlank()) return Color.WHITE;

        if (data.equalsIgnoreCase("rainbow") || data.equalsIgnoreCase("random")) {
            float hue = (System.currentTimeMillis() % 5000) / 5000.0f;
            java.awt.Color awt = java.awt.Color.getHSBColor(hue, 1.0f, 1.0f);
            return Color.fromRGB(awt.getRed(), awt.getGreen(), awt.getBlue());
        }

        String[] parts = data.trim().split("\\s+");
        if (parts.length >= 3) {
            try {
                int r = clamp(Integer.parseInt(parts[0]), 0, 255);
                int g = clamp(Integer.parseInt(parts[1]), 0, 255);
                int b = clamp(Integer.parseInt(parts[2]), 0, 255);
                return Color.fromRGB(r, g, b);
            } catch (NumberFormatException ignored) {}
        }
        return Color.WHITE;
    }

    private static int clamp(int val, int min, int max) {
        return Math.max(min, Math.min(max, val));
    }
}
