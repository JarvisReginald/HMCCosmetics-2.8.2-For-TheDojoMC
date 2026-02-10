package com.hibiscusmc.hmccosmetics.command;

import com.hibiscusmc.hmccolor.HMCColorConfig;
import com.hibiscusmc.hmccolor.HMCColorContextKt;
import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.config.Wardrobe;
import com.hibiscusmc.hmccosmetics.config.WardrobeLocation;
import com.hibiscusmc.hmccosmetics.config.WardrobeSettings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetics;
import com.hibiscusmc.hmccosmetics.database.Database;
import com.hibiscusmc.hmccosmetics.database.OutfitsStorage;
import com.hibiscusmc.hmccosmetics.gui.Menu;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.gui.special.DyeMenuProvider;
import com.hibiscusmc.hmccosmetics.gui.special.StoreMenu;
import com.hibiscusmc.hmccosmetics.gui.util.MenuMessageUtil;
import com.hibiscusmc.hmccosmetics.store.StoreArmorStandManager;
import com.hibiscusmc.hmccosmetics.store.StoreArmorStandStorage;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.HMCCServerUtils;
import me.lojosho.hibiscuscommons.hooks.Hooks;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class CosmeticCommand implements CommandExecutor {

    private static final NamespacedKey ARMORSTAND_STORE_ID_KEY =
            new NamespacedKey(HMCCosmeticsPlugin.getInstance(), "store_armorstand_id"); // 1-7

    private static final NamespacedKey ARMORSTAND_STORE_SLOT_KEY =
            new NamespacedKey(HMCCosmeticsPlugin.getInstance(), "store_armorstand_slot"); // 10-16

    // cosmetics apply cosmetics playerName
    //             0      1        2

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        boolean silent = false;
        boolean console = false;

        if (!(sender instanceof Player)) {
            console = true;
        }

        if (args.length == 0) {
            if (console) {
                return true;
            }
            if (!sender.hasPermission("hmccosmetics.cmd.default")) {
                MessagesUtil.sendMessage(sender, "no-permission");
                return true;
            }

            CosmeticUser user = CosmeticUsers.getUser(((Player) sender).getUniqueId());
            Menu menu = Menus.getDefaultMenu();

            if (user == null) {
                MessagesUtil.sendMessage(sender, "invalid-player");
                return true;
            }

            if (menu == null) {
                MessagesUtil.sendMessage(sender, "invalid-menu");
                return true;
            }

            menu.openMenu(user);
            return true;
        }
        Player player = sender instanceof Player ? (Player) sender : null;

        String firstArgs = args[0].toLowerCase();

        if (sender.hasPermission("HMCCosmetics.cmd.silent") || sender.isOp()) {
            for (String singleArg : args) {
                if (singleArg.equalsIgnoreCase("-s")) {
                    silent = true;
                    break;
                }
            }
        }

        switch (firstArgs) {
            case ("reload") -> {
                if (!sender.hasPermission("HMCCosmetics.cmd.reload") && !sender.isOp()) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                HMCCosmeticsPlugin.setup();
                if (!silent) MessagesUtil.sendMessage(sender, "reloaded");
                return true;
            }
            case ("apply") -> {
                if (!sender.hasPermission("hmccosmetics.cmd.apply")) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                Cosmetic cosmetic;
                Color color = null;

                if (sender instanceof Player) player = ((Player) sender).getPlayer();
                if (sender.hasPermission("hmccosmetics.cmd.apply.other")) {
                    if (args.length >= 3) player = Bukkit.getPlayer(args[2]);
                }

                if (sender.hasPermission("hmccosmetics.cmd.apply.color")) {
                    if (args.length >= 4) {
                        // TODO: Add sub-color support somehow... (and make this neater)
                        String textColor = args[3];
                        if (!textColor.contains("#") && Hooks.isActiveHook("HMCColor")) {
                            HMCColorConfig.Colors colors = HMCColorContextKt.getHmcColor().getConfig().getColors().get(textColor);
                            if (colors != null) {
                                color = colors.getBaseColor().getColor();
                            }
                        } else {
                            color = HMCCServerUtils.hex2Rgb(textColor);
                        }
                    }
                }

                if (args.length == 1) {
                    if (!silent) MessagesUtil.sendMessage(player, "not-enough-args");
                    return true;
                }

                cosmetic = Cosmetics.getCosmetic(args[1]);

                if (cosmetic == null) {
                    if (!silent) MessagesUtil.sendMessage(sender, "invalid-cosmetic");
                    return true;
                }

                if (player == null) {
                    if (!silent) MessagesUtil.sendMessage(sender, "invalid-player");
                    return true;
                }

                CosmeticUser user = CosmeticUsers.getUser(player);

                if (user == null) {
                    if (!silent) MessagesUtil.sendMessage(sender, "invalid-player");
                    return true;
                }

                if (!user.canEquipCosmetic(cosmetic) && !console) {
                    if (!silent) MessagesUtil.sendMessage(player, "no-cosmetic-permission");
                    return true;
                }

                TagResolver placeholders =
                        TagResolver.resolver(Placeholder.parsed("cosmetic", cosmetic.getId()),
                                TagResolver.resolver(Placeholder.parsed("player", player.getName())),
                                TagResolver.resolver(Placeholder.parsed("cosmeticslot", cosmetic.getSlot().toString())));

                if (!silent) MessagesUtil.sendMessage(player, "equip-cosmetic", placeholders);

                user.addCosmetic(cosmetic, color);
                user.updateCosmetic(cosmetic.getSlot());
                return true;
            }
            case ("unapply") -> {
                if (!sender.hasPermission("hmccosmetics.cmd.unapply")) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                if (args.length == 1) {
                    if (!silent) MessagesUtil.sendMessage(player, "not-enough-args");
                    return true;
                }

                if (sender instanceof Player) player = ((Player) sender).getPlayer();
                if (sender.hasPermission("hmccosmetics.cmd.unapply.other")) {
                    if (args.length >= 3) player = Bukkit.getPlayer(args[2]);
                }

                if (player == null) {
                    if (!silent) MessagesUtil.sendMessage(sender, "invalid-player");
                    return true;
                }

                CosmeticUser user = CosmeticUsers.getUser(player);

                Set<CosmeticSlot> cosmeticSlots;

                if (args[1].equalsIgnoreCase("all")) {
                    cosmeticSlots = user.getSlotsWithCosmetics();
                } else {
                    String rawSlot = args[1].toUpperCase();
                    if (!CosmeticSlot.contains(rawSlot)) {
                        if (!silent) MessagesUtil.sendMessage(sender, "invalid-slot");
                        return true;
                    }
                    cosmeticSlots = Set.of(CosmeticSlot.valueOf(rawSlot));
                }

                for (CosmeticSlot cosmeticSlot : cosmeticSlots) {
                    if (user.getCosmetic(cosmeticSlot) == null) {
                        if (!silent) MessagesUtil.sendMessage(sender, "no-cosmetic-slot");
                        continue;
                    }

                    TagResolver placeholders =
                            TagResolver.resolver(Placeholder.parsed("cosmetic", user.getCosmetic(cosmeticSlot).getId()),
                                    TagResolver.resolver(Placeholder.parsed("player", player.getName())),
                                    TagResolver.resolver(Placeholder.parsed("cosmeticslot", cosmeticSlot.toString())));

                    if (!silent) MessagesUtil.sendMessage(player, "unequip-cosmetic", placeholders);

                    user.removeCosmeticSlot(cosmeticSlot);
                    user.updateCosmetic(cosmeticSlot);
                }
                return true;
            }
            case ("wardrobe") -> {
                if (sender instanceof Player) player = ((Player) sender).getPlayer();

                if (args.length == 1) {
                    if (!silent) MessagesUtil.sendMessage(player, "not-enough-args");
                    return true;
                }

                if (sender.hasPermission("hmccosmetics.cmd.wardrobe.other")) {
                    if (args.length >= 3) player = Bukkit.getPlayer(args[2]);
                }

                if (!sender.hasPermission("hmccosmetics.cmd.wardrobe")) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }

                if (player == null) {
                    if (!silent) MessagesUtil.sendMessage(sender, "invalid-player");
                    return true;
                }

                if (!WardrobeSettings.getWardrobeNames().contains(args[1])) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-wardrobes");
                    return true;
                }
                Wardrobe wardrobe = WardrobeSettings.getWardrobe(args[1]);

                CosmeticUser user = CosmeticUsers.getUser(player);

                if (user.isInWardrobe()) {
                    user.leaveWardrobe(false);
                } else {
                    user.enterWardrobe(wardrobe, false);
                }
                return true;
            }
            // cosmetic menu exampleMenu playerName
            case ("menu") -> {
                if (!sender.hasPermission("hmccosmetics.cmd.menu")) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                Menu menu = null;
                StoreMenu storeMenu = null;
                if (args.length == 1) {
                    menu = Menus.getDefaultMenu();
                } else {
                    if(args[1].equalsIgnoreCase("store")) {
                        storeMenu = Menus.getStoreMenu(args[1]);
                    } else {
                        menu = Menus.getMenu(args[1]);
                    }
                }

                if (sender instanceof Player) player = ((Player) sender).getPlayer();
                if (sender.hasPermission("hmccosmetics.cmd.menu.other")) {
                    if (args.length >= 3) player = Bukkit.getPlayer(args[2]);
                }
                CosmeticUser user = CosmeticUsers.getUser(player);

                if (user == null) {
                    if (!silent) MessagesUtil.sendMessage(sender, "invalid-player");
                    return true;
                }

                if (menu == null && storeMenu == null) {
                    if (!silent) MessagesUtil.sendMessage(sender, "invalid-menu");
                    return true;
                }

                if(menu == null) {
                    storeMenu.openMenu(user);
                } else {
                    menu.openMenu(user);
                }

                return true;
            }
            case ("dataclear") -> {
                if (args.length == 1) return true;
                OfflinePlayer selectedPlayer = Bukkit.getOfflinePlayer(args[1]);
                if (!sender.hasPermission("hmccosmetics.cmd.dataclear") && !sender.isOp()) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                Database.clearData(selectedPlayer.getUniqueId());
                sender.sendMessage("Cleared data for " + selectedPlayer.getName());
                return true;
            }
            case ("dye") -> {
                if (player == null) return true;
                CosmeticUser user = CosmeticUsers.getUser(player);
                if (user == null) return true;
                if (!sender.hasPermission("hmccosmetics.cmd.dye") && !sender.isOp()) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }

                if (args.length == 1) {
                    if (!silent) MessagesUtil.sendMessage(player, "not-enough-args");
                    return true;
                }

                final String rawSlot = args[1];
                if (!CosmeticSlot.contains(rawSlot)) {
                    if (!silent) MessagesUtil.sendMessage(player, "invalid-slot");
                    return true;
                }
                final CosmeticSlot slot = CosmeticSlot.valueOf(rawSlot); // This is checked above. While IDEs may say the slot might be null, it will not be.
                final Cosmetic cosmetic = user.getCosmetic(slot);
                if (cosmetic == null) {
                    if (!silent) MessagesUtil.sendMessage(player, "invalid-slot");
                    return true;
                }

                if (args.length >= 3) {
                    if (args[2].isEmpty()) {
                        if (!silent) MessagesUtil.sendMessage(player, "invalid-color");
                        return true;
                    }
                    Color color = HMCCServerUtils.hex2Rgb(args[2]);
                    if (color == null) {
                        if (!silent) MessagesUtil.sendMessage(player, "invalid-color");
                        return true;
                    }
                    user.addCosmetic(cosmetic, color); // #FFFFFF
                } else {
                    if (DyeMenuProvider.hasMenuProvider()) {
                        DyeMenuProvider.openMenu(player, user, cosmetic);
                    } else {
                        if (!silent) MessagesUtil.sendMessage(player, "invalid-color");
                    }
                }
            }
            case "store" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.store") && !sender.isOp()) {
                    MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }

                Player target = (sender instanceof Player p) ? p : null;
                int focusSlot = -1;

                // Logica parsing migliorata
                if (args.length == 2) {
                    Player p2 = Bukkit.getPlayer(args[1]);
                    if (p2 != null) target = p2;
                    else {
                        try { focusSlot = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
                    }
                } else if (args.length >= 3) {
                    target = Bukkit.getPlayer(args[1]);
                    try { focusSlot = Integer.parseInt(args[2]); } catch (NumberFormatException ignored) {}
                }

                if (target == null) {
                    MessagesUtil.sendMessage(sender, "invalid-player");
                    return true;
                }

                // Traslazione slot per i daily (se l'utente mette 1-7, noi vogliamo 10-16)
                if (focusSlot >= 1 && focusSlot <= 7) {
                    focusSlot = focusSlot + 9;
                }

                StoreMenu store = Menus.getStoreMenu("store");
                CosmeticUser user = CosmeticUsers.getUser(target);
                if (store != null && user != null) {
                    store.openMenu(user, focusSlot);
                }
                return true;
            }
            case ("setarmorstand") -> {
                if (!(sender instanceof Player p)) return true;

                if (!sender.hasPermission("hmccosmetics.cmd.setarmorstand") && !sender.isOp()) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }

                if (args.length < 2) {
                    if (!silent) MessagesUtil.sendMessage(sender, "not-enough-args");
                    return true;
                }

                int id;
                try {
                    id = Integer.parseInt(args[1]);
                } catch (NumberFormatException ex) {
                    if (!silent) MessagesUtil.sendMessage(sender, "invalid-args");
                    return true;
                }

                if (id < 1 || id > 7) {
                    if (!silent) MessagesUtil.sendMessage(sender, "invalid-args");
                    return true;
                }

                // 1 -> 10, 2 -> 11, ... 7 -> 16
                int slot = 9 + id;

                Entity target = p.getTargetEntity(6);
                if (!(target instanceof ArmorStand armorStand)) {
                    if (!silent) p.sendMessage("You need to be looking at an armor stand");
                    return true;
                }

                PersistentDataContainer pdc = armorStand.getPersistentDataContainer();
                pdc.set(ARMORSTAND_STORE_ID_KEY, PersistentDataType.INTEGER, id);
                pdc.set(ARMORSTAND_STORE_SLOT_KEY, PersistentDataType.INTEGER, slot);

                armorStand.setPersistent(true);
                armorStand.setRemoveWhenFarAway(false);

                if (!silent) p.sendMessage("ArmorStand configured: id=" + id + " -> slot=" + slot);
                StoreArmorStandStorage.saveStand(armorStand, id, slot);
                StoreArmorStandManager.refreshArmorStand(armorStand);
                return true;
            }
            case ("unsetarmorstand") -> {
                if (!(sender instanceof Player p)) return true;

                if (!sender.hasPermission("hmccosmetics.cmd.unsetarmorstand") && !sender.isOp()) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }

                Entity target = p.getTargetEntity(6);
                if (!(target instanceof ArmorStand armorStand)) {
                    if (!silent) p.sendMessage("You need to be looking at an armor stand");
                    return true;
                }

                PersistentDataContainer pdc = armorStand.getPersistentDataContainer();
                pdc.remove(ARMORSTAND_STORE_ID_KEY);
                pdc.remove(ARMORSTAND_STORE_SLOT_KEY);

                armorStand.getEquipment().clear();
                StoreArmorStandStorage.removeStandByLocation(armorStand);
                StoreArmorStandManager.removeBackpackDisplay(armorStand);

                if (!silent) p.sendMessage("Meta removed from ArmorStand.");
                return true;
            }
            case ("setwardrobesetting") -> {
                if (!sender.hasPermission("hmccosmetics.cmd.setwardrobesetting")) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }

                if (player == null) return true;

                if (args.length < 3) {
                    if (!silent) MessagesUtil.sendMessage(player, "not-enough-args");
                    return true;
                }
                Wardrobe wardrobe = WardrobeSettings.getWardrobe(args[1]);
                if (wardrobe == null) {
                    wardrobe = new Wardrobe(args[1], new WardrobeLocation(null, null, null), null, -1, null);
                    WardrobeSettings.addWardrobe(wardrobe);
                    //MessagesUtil.sendMessage(player, "no-wardrobes");
                    //return true;
                }

                if (args[2].equalsIgnoreCase("npclocation")) {
                    WardrobeSettings.setNPCLocation(wardrobe, player.getLocation());
                    if (!silent) MessagesUtil.sendMessage(player, "set-wardrobe-location");
                    return true;
                }

                if (args[2].equalsIgnoreCase("viewerlocation")) {
                    WardrobeSettings.setViewerLocation(wardrobe, player.getEyeLocation());
                    if (!silent) MessagesUtil.sendMessage(player, "set-wardrobe-viewing");
                    return true;
                }

                if (args[2].equalsIgnoreCase("leavelocation")) {
                    WardrobeSettings.setLeaveLocation(wardrobe, player.getLocation());
                    if (!silent) MessagesUtil.sendMessage(player, "set-wardrobe-leaving");
                    return true;
                }

                if (args.length >= 4) {
                    if (args[2].equalsIgnoreCase("permission")) {
                        WardrobeSettings.setWardrobePermission(wardrobe, args[3]);
                        if (!silent) MessagesUtil.sendMessage(player, "set-wardrobe-permission");
                        return true;
                    }
                    if (args[2].equalsIgnoreCase("distance")) {
                        WardrobeSettings.setWardrobeDistance(wardrobe, Integer.parseInt(args[3]));
                        if (!silent) MessagesUtil.sendMessage(player, "set-wardrobe-distance");
                        return true;
                    }
                    if (args[2].equalsIgnoreCase("defaultmenu")) {
                        WardrobeSettings.setWardrobeDefaultMenu(wardrobe, args[3]);
                        if (!silent) MessagesUtil.sendMessage(player, "set-wardrobe-menu");
                        return true;
                    }
                }
            }
            case ("dump") -> {
                if (player == null) return true;
                CosmeticUser user = CosmeticUsers.getUser(player);
                if (user == null) return true;
                if (!sender.hasPermission("HMCCosmetic.cmd.dump") && !sender.isOp()) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                player.sendMessage("Passengers -> " + player.getPassengers());
                if (user.hasCosmeticInSlot(CosmeticSlot.BACKPACK)) {
                    player.sendMessage("Backpack Location -> " + user.getUserBackpackManager().getEntityManager().getLocation());
                }
                player.sendMessage("Cosmetic Passengers -> " + user.getUserBackpackManager().getAreaEffectEntityId());
                player.sendMessage("Cosmetics -> " + user.getCosmetics());
                player.sendMessage("EntityId -> " + player.getEntityId());
                return true;
            }
            case ("hide") -> {
                if (sender instanceof Player) player = ((Player) sender).getPlayer();
                if (sender.hasPermission("hmccosmetics.cmd.hide.other")) {
                    if (args.length >= 2) player = Bukkit.getPlayer(args[1]);
                }

                if (!sender.hasPermission("hmccosmetics.cmd.hide")) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }

                if (player == null) {
                    if (!silent) MessagesUtil.sendMessage(sender, "invalid-player");
                    return true;
                }

                CosmeticUser user = CosmeticUsers.getUser(player);
                if (!silent) MessagesUtil.sendMessage(sender, "hide-cosmetic");
                user.hideCosmetics(CosmeticUser.HiddenReason.COMMAND);
                return true;
            }
            case ("show") -> {
                if (sender instanceof Player) player = ((Player) sender).getPlayer();
                if (sender.hasPermission("hmccosmetics.cmd.show.other")) {
                    if (args.length >= 2) player = Bukkit.getPlayer(args[1]);
                }

                if (!sender.hasPermission("hmccosmetics.cmd.show")) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }

                if (player == null) {
                    if (!silent) MessagesUtil.sendMessage(sender, "invalid-player");
                    return true;
                }

                CosmeticUser user = CosmeticUsers.getUser(player);

                if (!silent) MessagesUtil.sendMessage(sender, "show-cosmetic");
                user.showCosmetics(CosmeticUser.HiddenReason.COMMAND);
                return true;
            }
            case ("debug") -> {
                if (!sender.hasPermission("hmccosmetics.cmd.debug")) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }

                if (Settings.isDebugMode()) {
                    Settings.setDebugMode(false);
                    if (!silent) MessagesUtil.sendMessage(sender, "debug-disabled");
                } else {
                    Settings.setDebugMode(true);
                    if (!silent) MessagesUtil.sendMessage(sender, "debug-enabled");
                }
            }
            case "disableall" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.disableall")) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                if (args.length == 1) {
                    if (!silent) MessagesUtil.sendMessage(player, "not-enough-args");
                    return true;
                }
                if (args[1].equalsIgnoreCase("true")) {
                    Settings.setAllPlayersHidden(true);
                    for (CosmeticUser user : CosmeticUsers.values()) user.hideCosmetics(CosmeticUser.HiddenReason.DISABLED);
                    if (!silent) MessagesUtil.sendMessage(sender, "disabled-all");
                } else if (args[1].equalsIgnoreCase("false")) {
                    Settings.setAllPlayersHidden(false);
                    for (CosmeticUser user : CosmeticUsers.values()) user.showCosmetics(CosmeticUser.HiddenReason.DISABLED);
                    if (!silent) MessagesUtil.sendMessage(sender, "enabled-all");
                } else {
                    if (!silent) MessagesUtil.sendMessage(sender, "invalid-args");
                }
                return true;
            }

            case "hiddenreasons" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.hiddenreasons")) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                if (args.length >= 2) {
                    player = Bukkit.getPlayer(args[1]);
                }
                if (player == null) {
                    if (!silent) MessagesUtil.sendMessage(sender, "invalid-player");
                    return true;
                }
                CosmeticUser user = CosmeticUsers.getUser(player);
                sender.sendMessage(user.getHiddenReasons().toString());
                return true;
            }

            case "clearhiddenreasons" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.clearhiddenreasons")) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                if (args.length >= 2) {
                    player = Bukkit.getPlayer(args[1]);
                }
                if (player == null) {
                    if (!silent) MessagesUtil.sendMessage(sender, "invalid-player");
                    return true;
                }
                CosmeticUser user = CosmeticUsers.getUser(player);
                user.clearHiddenReasons();
                return true;
            }
            case ("addoutfit") -> {
                if (!(sender instanceof Player p)) return true;

                if (!sender.hasPermission("hmccosmetics.cmd.addoutfit") && !sender.isOp()) {
                    if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }

                // nome outfit: se l'utente passa argomenti => custom name
                String customName = null;
                if (args.length >= 2) {
                    customName = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
                    if (customName.isBlank()) customName = null;
                }

                CosmeticUser user = CosmeticUsers.getUser(p);
                if (user == null) return true;

                OutfitsStorage storage = HMCCosmeticsPlugin.getInstance().getOutfitsStorage();
                if (storage == null) {
                    if (!silent) p.sendMessage(MenuMessageUtil.format(p,
                            msgFromDojo("outfits_storage_unavailable", "&cOutfits storage not available.")));
                    return true;
                }

                UUID uuid = p.getUniqueId();

                // Leggi settings da dojo_outfits.yml
                Menu dojo = Menus.getMenu("dojo_outfits");
                boolean allowEmpty = true;

                String noCosmeticsMsg = "&cYou have no cosmetics equipped.";
                String defaultNamePattern = "Outfit #<slot>";
                String savedMsg = "&aOutfit saved in slot &f<slot>&a as: &f<name>&a.";
                String maxReachedMsg = "&cYou already have the maximum number of outfits.";

                int maxSlots = 5; // TODO: se vuoi renderlo configurabile, leggilo dal config

                if (dojo != null && dojo.getConfig() != null) {
                    allowEmpty = dojo.getConfig().node("allow-empty-outfits").getBoolean(true);

                    noCosmeticsMsg = dojo.getConfig().node("messages", "no_cosmetics_equipped").getString(noCosmeticsMsg);
                    defaultNamePattern = dojo.getConfig().node("default-outfit-name").getString(defaultNamePattern);

                    savedMsg = dojo.getConfig().node("messages", "outfit_saved_command").getString(savedMsg);
                    maxReachedMsg = dojo.getConfig().node("messages", "outfit_max_reached").getString(maxReachedMsg);

                    if (defaultNamePattern == null || defaultNamePattern.isBlank()) defaultNamePattern = "Outfit #<slot>";
                    if (noCosmeticsMsg == null || noCosmeticsMsg.isBlank()) noCosmeticsMsg = "&cYou have no cosmetics equipped.";
                    if (savedMsg == null || savedMsg.isBlank()) savedMsg = "&aOutfit saved in slot &f<slot>&a as: &f<name>&a.";
                    if (maxReachedMsg == null || maxReachedMsg.isBlank()) maxReachedMsg = "&cYou already have the maximum number of outfits.";
                }

                // trova primo slot libero: IMPORTANTISSIMO per outfit vuoti -> usa hasSlot()
                int freeSlot = -1;
                for (int s = 1; s <= maxSlots; s++) {
                    if (!storage.hasSlot(uuid, s)) {
                        freeSlot = s;
                        break;
                    }
                }

                if (freeSlot == -1) {
                    if (!silent) p.sendMessage(MenuMessageUtil.format(p, maxReachedMsg));
                    return true;
                }

                // cosmetics equipaggiati
                List<String> current = storage.getAllCurrentCosmetics(user);
                if (current == null) current = new ArrayList<>();

                if (!allowEmpty && current.isEmpty()) {
                    if (!silent) p.sendMessage(MenuMessageUtil.format(p, noCosmeticsMsg));
                    return true;
                }

                // salva (anche vuoto se allowEmpty=true)
                storage.setOutfitSlot(uuid, freeSlot, current);

                // nome default se non custom
                String defaultName = defaultNamePattern.replace("<slot>", String.valueOf(freeSlot));
                String nameToSet = (customName != null) ? customName : defaultName;
                storage.setOutfitName(uuid, freeSlot, nameToSet);

                storage.compactSlots(uuid, allowEmpty);

                if (!silent) {
                    String out = savedMsg.replace("<slot>", String.valueOf(freeSlot))
                            .replace("<name>", nameToSet);
                    p.sendMessage(MenuMessageUtil.format(p, out));
                }

                // Reopen the outfits menu after saving
                Bukkit.getScheduler().runTaskLater(HMCCosmeticsPlugin.getInstance(), () -> {
                    if (!p.isOnline()) return;
                    Menu outfitsMenu = Menus.getMenu("dojo_outfits");
                    if (outfitsMenu != null) {
                        CosmeticUser outfitUser = CosmeticUsers.getUser(p);
                        if (outfitUser != null) outfitsMenu.openMenu(outfitUser);
                    }
                }, 2);
                return true;
            }
        }
        return true;
    }

    public static void refreshAllStoreArmorStands() {
        for (World w : Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity e : w.getEntitiesByClass(ArmorStand.class)) {
                ArmorStand as = (ArmorStand) e;

                PersistentDataContainer pdc = as.getPersistentDataContainer();
                Integer storeSlot = pdc.get(ARMORSTAND_STORE_SLOT_KEY, PersistentDataType.INTEGER);
                if (storeSlot == null) continue;

                // storeSlot deve essere 10..16, ma se vuoi accetta anche 1..7 e converti
                if (storeSlot >= 1 && storeSlot <= 7) storeSlot = storeSlot + 9;

                StoreArmorStandManager.refreshArmorStand(as);
            }
        }
    }

    private String msgFromDojo(String key, String def) {
        Menu dojo = Menus.getMenu("dojo_outfits");
        if (dojo == null || dojo.getConfig() == null) return def;
        String s = dojo.getConfig().node("messages", key).getString(def);
        return (s == null || s.isBlank()) ? def : s;
    }
}
