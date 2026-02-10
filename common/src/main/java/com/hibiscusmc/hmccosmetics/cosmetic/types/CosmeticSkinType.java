package com.hibiscusmc.hmccosmetics.cosmetic.types;

import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.behavior.CosmeticUpdateBehavior;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import me.lojosho.shaded.configurate.ConfigurationNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class CosmeticSkinType extends Cosmetic implements CosmeticUpdateBehavior {

    private final @Nullable String retextureGroup;

    public CosmeticSkinType(String id, ConfigurationNode config) {
        super(id, config);

        String g = config.node("retexture_group").getString("");
        this.retextureGroup = g.isBlank() ? null : g;
    }

    public @Nullable String getRetextureGroup() {
        return retextureGroup;
    }

    @Override
    public void dispatchUpdate(@NotNull CosmeticUser user) {
        // no-op
    }
}
