package com.hibiscusmc.hmccosmetics.gui;

import org.bukkit.inventory.ItemStack;

public final class MenuClickContext {
    private static final ThreadLocal<Ctx> CTX = new ThreadLocal<>();

    private MenuClickContext() {}

    public static void set(int slot, ItemStack currentItem) {
        CTX.set(new Ctx(slot, currentItem));
    }

    public static Ctx get() {
        return CTX.get();
    }

    public static void clear() {
        CTX.remove();
    }

    public record Ctx(int slot, ItemStack currentItem) {}
}
