package com.hibiscusmc.hmccosmetics.cosmetic.behavior;

import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;

/**
 * Updates cosmetics whenever a player moves.
 */
public interface CosmeticMovementBehavior {
    void dispatchMove(
        @NotNull final CosmeticUser user,
        @NotNull final Location from,
        @NotNull final Location to
    );
}
