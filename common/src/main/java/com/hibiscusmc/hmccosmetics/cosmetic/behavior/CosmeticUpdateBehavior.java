package com.hibiscusmc.hmccosmetics.cosmetic.behavior;

import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import org.jetbrains.annotations.NotNull;

/**
 * Generic updates that happen every tick or when manually requested to be dispatched.
 */
public interface CosmeticUpdateBehavior {
    void dispatchUpdate(
            @NotNull final CosmeticUser user
    );
}