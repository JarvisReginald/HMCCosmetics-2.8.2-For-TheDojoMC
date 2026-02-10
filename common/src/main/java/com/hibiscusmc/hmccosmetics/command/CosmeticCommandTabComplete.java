package com.hibiscusmc.hmccosmetics.command;

import com.hibiscusmc.hmccolor.HMCColorContextKt;
import com.hibiscusmc.hmccosmetics.config.Wardrobe;
import com.hibiscusmc.hmccosmetics.config.WardrobeSettings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetics;
import com.hibiscusmc.hmccosmetics.gui.Menu;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import me.lojosho.hibiscuscommons.hooks.Hooks;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CosmeticCommandTabComplete implements TabCompleter {

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String alias,
                                      @NotNull String @NotNull [] args) {

        List<String> completions = new ArrayList<>();
        List<String> finalCompletions = new ArrayList<>();

        // /cosmetics <sub>
        if (args.length == 1) {
            if (hasPermission(sender, "hmccosmetics.cmd.apply")) completions.add("apply");
            if (hasPermission(sender, "hmccosmetics.cmd.unapply")) completions.add("unapply");
            if (hasPermission(sender, "hmccosmetics.cmd.menu")) completions.add("menu");
            if (hasPermission(sender, "HMCCosmetics.cmd.reload")) completions.add("reload");
            if (hasPermission(sender, "hmccosmetics.cmd.wardrobe")) completions.add("wardrobe");
            if (hasPermission(sender, "hmccosmetics.cmd.dataclear")) completions.add("dataclear");
            if (hasPermission(sender, "hmccosmetics.cmd.dye")) completions.add("dye");
            if (hasPermission(sender, "hmccosmetics.cmd.store")) completions.add("store");
            if (hasPermission(sender, "hmccosmetics.cmd.setarmorstand")) completions.add("setarmorstand");
            if (hasPermission(sender, "hmccosmetics.cmd.unsetarmorstand")) completions.add("unsetarmorstand");
            if (hasPermission(sender, "hmccosmetics.cmd.setwardrobesetting")) completions.add("setwardrobesetting");
            if (hasPermission(sender, "hmccosmetics.cmd.hide")) completions.add("hide");
            if (hasPermission(sender, "hmccosmetics.cmd.show")) completions.add("show");
            if (hasPermission(sender, "hmccosmetics.cmd.debug")) completions.add("debug");
            if (hasPermission(sender, "hmccosmetics.cmd.disableall")) completions.add("disableall");
            if (hasPermission(sender, "hmccosmetics.cmd.hiddenreasons")) completions.add("hiddenreasons");
            if (hasPermission(sender, "hmccosmetics.cmd.clearhiddenreasons")) completions.add("clearhiddenreasons");
            if (hasPermission(sender, "HMCCosmetic.cmd.dump")) completions.add("dump");
            if (hasPermission(sender, "hmccosmetics.cmd.addoutfit")) completions.add("addoutfit");

            StringUtil.copyPartialMatches(args[0], completions, finalCompletions);
            Collections.sort(finalCompletions);
            return finalCompletions;
        }

        // Da qui in poi, se non è player, limita (es: console può fare reload ecc, ma niente completions player-specific)
        if (!(sender instanceof Player p)) {
            return Collections.emptyList();
        }

        CosmeticUser user = CosmeticUsers.getUser(p.getUniqueId());
        if (user == null) return Collections.emptyList();

        String sub = args[0].toLowerCase(Locale.ROOT);

        // /cosmetics <sub> <arg2>
        if (args.length == 2) {
            switch (sub) {
                case "apply" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.apply")) break;
                    completions.addAll(applyCommandComplete(user, args));
                }
                case "unapply" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.unapply")) break;
                    for (Cosmetic cosmetic : user.getCosmetics()) {
                        completions.add(cosmetic.getSlot().toString().toUpperCase(Locale.ROOT));
                    }
                    completions.add("ALL");
                }
                case "menu" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.menu")) break;
                    // il tuo comando supporta "store" come speciale + menu normali
                    completions.add("store");
                    for (Menu menu : Menus.getMenu()) {
                        if (menu.canOpen(user.getPlayer())) completions.add(menu.getId());
                    }
                }
                case "dataclear" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.dataclear")) break;
                    for (Player pl : Bukkit.getOnlinePlayers()) completions.add(pl.getName());
                }
                case "hide" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.hide")) break;
                    // ha anche hide.other
                    if (hasPermission(sender, "hmccosmetics.cmd.hide.other")) {
                        for (Player pl : Bukkit.getOnlinePlayers()) completions.add(pl.getName());
                    }
                }
                case "show" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.show")) break;
                    if (hasPermission(sender, "hmccosmetics.cmd.show.other")) {
                        for (Player pl : Bukkit.getOnlinePlayers()) completions.add(pl.getName());
                    }
                }
                case "hiddenreasons" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.hiddenreasons")) break;
                    for (Player pl : Bukkit.getOnlinePlayers()) completions.add(pl.getName());
                }
                case "clearhiddenreasons" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.clearhiddenreasons")) break;
                    for (Player pl : Bukkit.getOnlinePlayers()) completions.add(pl.getName());
                }
                case "disableall" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.disableall")) break;
                    completions.add("true");
                    completions.add("false");
                }
                case "wardrobe" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.wardrobe")) break;
                    for (Wardrobe wardrobe : WardrobeSettings.getWardrobes()) {
                        if (wardrobe.hasPermission()) {
                            if (user.getPlayer().hasPermission(wardrobe.getPermission())) completions.add(wardrobe.getId());
                        } else {
                            completions.add(wardrobe.getId());
                        }
                    }
                }
                case "dye" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.dye")) break;
                    for (CosmeticSlot slot : user.getDyeableSlots()) {
                        completions.add(slot.toString());
                    }
                }
                case "setwardrobesetting" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.setwardrobesetting")) break;
                    for (Wardrobe wardrobe : WardrobeSettings.getWardrobes()) {
                        completions.add(wardrobe.getId());
                    }
                }
                case "store" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.store")) break;
                    // /cosmetics store [player|slot]
                    // suggerisco player online + 1..7
                    for (Player pl : Bukkit.getOnlinePlayers()) completions.add(pl.getName());
                    for (int i = 1; i <= 7; i++) completions.add(String.valueOf(i));
                }
                case "setarmorstand" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.setarmorstand")) break;
                    // id 1..7
                    for (int i = 1; i <= 7; i++) completions.add(String.valueOf(i));
                }
                case "addoutfit" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.addoutfit")) break;
                    // nome outfit libero: non suggerisco nulla
                }
            }

            StringUtil.copyPartialMatches(args[1], completions, finalCompletions);
            Collections.sort(finalCompletions);
            return finalCompletions;
        }

        // /cosmetics <sub> <arg2> <arg3>
        if (args.length == 3) {
            switch (sub) {
                case "dye" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.dye")) break;
                    completions.add("#FFFFFF");
                }
                case "menu" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.menu")) break;
                    // menu other
                    if (hasPermission(sender, "hmccosmetics.cmd.menu.other")) {
                        for (Player pl : Bukkit.getOnlinePlayers()) completions.add(pl.getName());
                    }
                }
                case "wardrobe" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.wardrobe")) break;
                    if (hasPermission(sender, "hmccosmetics.cmd.wardrobe.other")) {
                        for (Player pl : Bukkit.getOnlinePlayers()) completions.add(pl.getName());
                    }
                }
                case "apply" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.apply")) break;
                    if (hasPermission(sender, "hmccosmetics.cmd.apply.other")) {
                        for (Player pl : Bukkit.getOnlinePlayers()) completions.add(pl.getName());
                    }
                }
                case "unapply" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.unapply")) break;
                    if (hasPermission(sender, "hmccosmetics.cmd.unapply.other")) {
                        for (Player pl : Bukkit.getOnlinePlayers()) completions.add(pl.getName());
                    }
                }
                case "store" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.store")) break;
                    // /cosmetics store <player> <slot>
                    for (int i = 1; i <= 7; i++) completions.add(String.valueOf(i));
                }
                case "setwardrobesetting" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.setwardrobesetting")) break;
                    completions.add("npclocation");
                    completions.add("viewerlocation");
                    completions.add("leavelocation");
                    completions.add("permission");
                    completions.add("distance");
                    completions.add("defaultmenu");
                }
            }

            StringUtil.copyPartialMatches(args[2], completions, finalCompletions);
            Collections.sort(finalCompletions);
            return finalCompletions;
        }

        // /cosmetics <sub> <arg2> <arg3> <arg4>
        if (args.length == 4) {
            switch (sub) {
                case "apply" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.apply")) break;
                    if (hasPermission(sender, "hmccosmetics.cmd.apply.color")) {
                        if (Hooks.isActiveHook("HMCColor")) {
                            completions.addAll(HMCColorContextKt.getHmcColor().getConfig().getColors().keySet());
                        }
                        completions.add("#FFFFFF");
                    }
                }
                case "setwardrobesetting" -> {
                    if (!hasPermission(sender, "hmccosmetics.cmd.setwardrobesetting")) break;
                    if (args[2].equalsIgnoreCase("defaultmenu")) {
                        completions.addAll(Menus.getMenuNames());
                    }
                }
            }

            StringUtil.copyPartialMatches(args[3], completions, finalCompletions);
            Collections.sort(finalCompletions);
            return finalCompletions;
        }

        return Collections.emptyList();
    }

    @NotNull
    private static List<String> applyCommandComplete(CosmeticUser user, String @NotNull [] args) {
        List<String> completitions = new ArrayList<>();
        // args.length==2 => /cosmetics apply <cosmeticId>
        if (args.length == 2) {
            for (Cosmetic cosmetic : Cosmetics.values()) {
                if (!user.canEquipCosmetic(cosmetic)) continue;
                completitions.add(cosmetic.getId());
            }
        }
        return completitions;
    }

    private boolean hasPermission(@NotNull CommandSender sender, String permission) {
        return sender.isOp() || sender.hasPermission(permission);
    }
}
