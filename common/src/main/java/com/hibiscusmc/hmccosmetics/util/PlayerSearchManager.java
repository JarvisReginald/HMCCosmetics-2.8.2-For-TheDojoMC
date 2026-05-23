package com.hibiscusmc.hmccosmetics.util;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerSearchManager implements Listener {
    private final Map<UUID, Octree<Player>> worldOctrees = new HashMap<>();
    private final Map<UUID, Octree.Point3D> playerPositions = new HashMap<>();
    /**
     * Players whose join grace period is still active.
     * During this window, move/teleport events do NOT update their octree
     * position — preventing the backpack ticker from detecting them as new
     * viewers before the vanilla entity tracker has sent nearby entities.
     */
    private final Set<UUID> joiningPlayers = ConcurrentHashMap.newKeySet();

    //private static final double WORLD_HALF_SIZE = 30_000_000; // Previous built in value
    private final double WORLD_HALF_SIZE;

    public PlayerSearchManager(@NotNull HMCCosmeticsPlugin plugin) {
        WORLD_HALF_SIZE = (double) plugin.getServer().getMaxWorldSize() / 2;
    }

    private Octree<Player> getOrCreateOctree(World world) {
        return worldOctrees.computeIfAbsent(world.getUID(), $ -> {
            Octree.BoundingBox worldBoundary = new Octree.BoundingBox(
                new Octree.Point3D(0, 160, 0), WORLD_HALF_SIZE
            );
            return new Octree<>(worldBoundary);
        });
    }

    private Octree.Point3D toPoint3D(Location location) {
        return new Octree.Point3D(location.getX(), location.getY(), location.getZ());
    }

    public boolean addPlayer(Player player) {
        Octree<Player> octree = getOrCreateOctree(player.getWorld());
        Octree.Point3D point = toPoint3D(player.getLocation());

        if(octree.insert(point, player)) {
            playerPositions.put(player.getUniqueId(), point);
            return true;
        }
        return false;
    }

    public boolean removePlayer(Player player) {
        Octree<Player> octree = worldOctrees.get(player.getWorld().getUID());
        if (octree == null) return false;

        Octree.Point3D point = playerPositions.remove(player.getUniqueId());
        if (point != null) return octree.remove(point, player);

        return false;
    }

    public void updatePlayerPosition(Player player) {
        removePlayer(player);
        addPlayer(player);
    }

    public List<Player> getPlayersInRange(Location location, double range) {
        Octree<Player> octree = worldOctrees.get(location.getWorld().getUID());
        if (octree == null) return Collections.emptyList();

        Octree.Point3D point = toPoint3D(location);
        Octree.BoundingBox searchArea = new Octree.BoundingBox(point, range);

        return octree.queryRange(searchArea)
            .stream()
            .filter(Objects::nonNull)
            .toList();
    }

    public void clear() {
        worldOctrees.clear();
        playerPositions.clear();
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (joiningPlayers.contains(event.getPlayer().getUniqueId())) return;
        if (event.hasChangedBlock()) updatePlayerPosition(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Ignore teleports during the join grace period — Paper fires a
        // PlayerTeleportEvent on join when placing the player at spawn,
        // which would bypass our delayed insertion.
        if (joiningPlayers.contains(event.getPlayer().getUniqueId())) return;
        updatePlayerPosition(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        joiningPlayers.remove(event.getPlayer().getUniqueId());
        removePlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // Mark as joining so move/teleport events don't insert them early.
        joiningPlayers.add(player.getUniqueId());

        // Delay adding to the spatial index until the vanilla entity tracker
        // has had time to send nearby entity spawn packets (armor stands, etc.)
        // to the client. This prevents the backpack ticker from sending mount
        // packets before the client knows about the owner entity.
        Bukkit.getScheduler().runTaskLater(
                HMCCosmeticsPlugin.getInstance(),
                () -> {
                    if (!player.isOnline()) return;
                    joiningPlayers.remove(player.getUniqueId());
                    addPlayer(player);
                },
                40L  // 2 seconds — covers initial chunk + entity-tracker flush
        );
    }
}