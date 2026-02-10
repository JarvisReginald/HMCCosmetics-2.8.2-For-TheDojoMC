package com.hibiscusmc.hmccosmetics.gui.special;

import com.hibiscusmc.hmccolor.shaded.jetbrains.annotations.Nullable;
import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.api.events.PlayerMenuCloseEvent;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetics;
import com.hibiscusmc.hmccosmetics.cosmetic.rarity.Rarities;
import com.hibiscusmc.hmccosmetics.cosmetic.rarity.Rarity;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticSkinType;
import com.hibiscusmc.hmccosmetics.util.TranslationUtil;
import com.hibiscusmc.hmccosmetics.gui.MenuItem;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.gui.type.Type;
import com.hibiscusmc.hmccosmetics.gui.type.Types;
import com.hibiscusmc.hmccosmetics.store.StoreArmorStandManager;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.skyboi.dojoEconomy.DojoEconomy;
import com.skyboi.dojoEconomy.managers.BalanceManager;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.guis.Gui;
import lombok.Getter;
import me.clip.placeholderapi.PlaceholderAPI;
import me.lojosho.hibiscuscommons.config.serializer.ItemSerializer;
import me.lojosho.hibiscuscommons.hooks.Hooks;
import me.lojosho.hibiscuscommons.util.AdventureUtils;
import me.lojosho.shaded.configurate.ConfigurationNode;
import me.lojosho.shaded.configurate.serialize.SerializationException;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class StoreMenu {
    @Getter
    private final String id;
    @Getter
    private final String title;
    @Getter
    private final int rows;
    @Getter
    private final Long cooldown;
    private final ConfigurationNode config;
    private final String permissionNode;
    private final HashMap<Integer, List<MenuItem>> items = new HashMap<>();
    private final HashMap<Integer, Cosmetic> randomDailyCosmetics = new HashMap<>();
    private final Map<UUID, PendingConfirm> pending = new HashMap<>();
    
    private List<Integer> backButtonSlots;
    private String backButtonCommand;
    private ItemStack backButtonItem;
    private String backButtonSound;

    private static volatile boolean itemsAdderReady = true;
    private static volatile boolean warmupStarted = false;
    private static final int WARMUP_MAX_TRIES = 60; // 60 * 10 ticks = 30s
    private static final long WARMUP_PERIOD_TICKS = 10L; // 0.5s

    public StoreMenu(String id, @NotNull ConfigurationNode config) {
        this.id = config.node("id").getString(id);
        this.config = config;
        this.title = config.node("title").getString("Store");
        this.rows = config.node("rows").getInt(6);
        this.cooldown = config.node("click-cooldown").getLong(Settings.getDefaultMenuCooldown());
        this.permissionNode = config.node("permission").getString("");

        startItemsAdderWarmupIfNeeded();

        if (canOpenStoreNow()) {
            init();
        }

        Menus.addMenu(this);
    }

    public void init() {
        dbg("init(): START");
        setupDailyCosmetics();
        setupItems();
        setupBackButton();
        dbg("init(): END itemsSlots=" + items.keySet() + " total=" +
                items.values().stream().mapToInt(List::size).sum());
    }

    public void reloadStoreContent() {
        dbg("reloadStoreContent() clearing old data...");
        items.clear();
        randomDailyCosmetics.clear();
        pending.clear();
        init();
    }

    private void setupDailyCosmetics() {
        randomDailyCosmetics.clear();

        List<Cosmetic> list = new ArrayList<>(Cosmetics.values());
        list.removeIf(Objects::isNull);
        list.removeIf(c -> c.getItem() == null);

        long seed = LocalDate.now(ZoneId.of("Europe/Rome")).toEpochDay();
        Random rnd = new Random(seed);

        int amount = Math.min(7, list.size());

        List<Cosmetic> picked = pickDailyWeighted(list, amount, rnd);

        for (int i = 0; i < picked.size(); i++) {
            randomDailyCosmetics.put(10 + i, picked.get(i));
        }

        dbg("setupDailyCosmetics(): picked=" + picked.stream().map(Cosmetic::getId).toList());
    }

    private List<Cosmetic> pickDailyWeighted(List<Cosmetic> source, int k, Random rnd) {
        record Entry(Cosmetic cos, double key) {}

        List<Entry> entries = new ArrayList<>(source.size());
        for (Cosmetic c : source) {
            double w = getRarityChanceWeight(c);
            if (w <= 0) continue;

            double u = Math.max(rnd.nextDouble(), 1e-12);
            double key = Math.pow(u, 1.0 / w);
            entries.add(new Entry(c, key));

            dbg("daily weight: cos=" + c.getId() + " rarity=" + c.getRarityId() + " w=" + w);
        }

        entries.sort(Comparator.comparingDouble(Entry::key).reversed());

        List<Cosmetic> out = new ArrayList<>(Math.min(k, entries.size()));
        for (int i = 0; i < k && i < entries.size(); i++) {
            out.add(entries.get(i).cos());
        }
        return out;
    }

    private double getRarityChanceWeight(Cosmetic cos) {
        if (cos == null) return 1.0;

        Rarity rarity = Rarities.get(cos.getRarityId());
        if (rarity == null) {
            rarity = Rarities.values().stream().findFirst().orElse(null);
        }
        if (rarity == null) return 1.0;

        double chance = rarity.chance();

        if (chance <= 0) return 0;
        return chance;
    }

    public Cosmetic getDailyCosmetic(int slot) {
        return randomDailyCosmetics.get(slot);
    }

    private void setupItems() {
        dbg("setupItems(): START");

        ConfigurationNode itemsNode = config.node("items");
        dbg("setupItems(): itemsNode.virtual=" + itemsNode.virtual()
                + " children=" + itemsNode.childrenMap().size());

        for (Map.Entry<Object, ? extends ConfigurationNode> entry : itemsNode.childrenMap().entrySet()) {
            String key = String.valueOf(entry.getKey());
            ConfigurationNode itemNode = entry.getValue();
            String mat = itemNode.node("item","material").getString();
            dbg("setupItems(): entry=" + key + " material=" + mat);

            ItemStack item = resolveConfigItem(itemNode.node("item"));
            dbg("setupItems(): entry=" + key + " resolved=" +
                    (item == null ? "null" : (item.getType().name())));

            if (item == null || item.getType() == Material.AIR) {
                dbg("setupItems(): SKIP " + key + " (null/AIR)");
                continue;
            }

            List<String> slotStrings = null;
            try {
                slotStrings = itemNode.node("slots").getList(String.class);
            } catch (SerializationException e) {
                throw new RuntimeException(e);
            }
            dbg("setupItems(): " + key + " slotsRaw=" + slotStrings);

            List<Integer> slots = parseSlots(slotStrings).stream().filter(s -> s >= 29 && s <= 33).toList();
            dbg("setupItems(): " + key + " slotsFiltered=" + slots);

            for (int slot : slots) {
                MenuItem menuItem = new MenuItem(slots, item, Types.getDefaultType(), itemNode.node("priority").getInt(1), itemNode);
                items.computeIfAbsent(slot, k -> new ArrayList<>()).add(menuItem);
                dbg("setupItems(): " + key + " REGISTERED into slot=" + slot);
            }
        }

        dbg("setupItems(): END itemsSlots=" + items.keySet());
    }

    private void setupBackButton() {
        dbg("setupBackButton(): START");
        
        // Get back button configuration
        ConfigurationNode backNode = config.node("back-button");
        
        // Get slots (default to 48, 49, 50)
        List<Integer> defaultSlots = List.of(48, 49, 50);
        try {
            List<String> slotStrings = backNode.node("slots").getList(String.class);
            if (slotStrings != null && !slotStrings.isEmpty()) {
                backButtonSlots = parseSlots(slotStrings);
            } else {
                backButtonSlots = defaultSlots;
            }
        } catch (Exception e) {
            backButtonSlots = defaultSlots;
        }
        
        // Get command (default to "cosmeticsv menu")
        backButtonCommand = backNode.node("command").getString("cosmeticsv menu");
        
        // Get ItemsAdder item (default to "guis:blank")
        String itemId = backNode.node("item").getString("guis:blank");
        
        // Resolve the item
        if (isItemsAdderPresent() && itemId.contains(":")) {
            backButtonItem = resolveItemsAdderCustomStack(itemId);
            if (backButtonItem == null || backButtonItem.getType().isAir()) {
                dbg("setupBackButton(): Failed to resolve ItemsAdder item: " + itemId);
                backButtonItem = new ItemStack(Material.BARRIER);
            }
        } else {
            // Fallback to barrier if ItemsAdder not present or invalid format
            backButtonItem = new ItemStack(Material.BARRIER);
        }
        
        // Apply configurable name
        String backButtonName = backNode.node("name").getString("");
        if (!backButtonName.isEmpty()) {
            ItemMeta meta = backButtonItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(cc(backButtonName));
                backButtonItem.setItemMeta(meta);
            }
        }
        
        // Get sound (default to empty string = no sound)
        backButtonSound = backNode.node("sound").getString("");
        
        dbg("setupBackButton(): slots=" + backButtonSlots + " command=" + backButtonCommand + " item=" + backButtonItem.getType() + " sound=" + backButtonSound);
    }

    public void openMenu(@NotNull CosmeticUser user, int focusSlot) {
        Player viewer = user.getPlayer();
        if (viewer == null) return;
        if (!permissionNode.isEmpty() && !viewer.hasPermission(permissionNode) && !viewer.isOp()) {
            MessagesUtil.sendMessage(viewer, "no-permission");
            return;
        }

        dbg("openMenu(): items.size=" + items.size() + " daily=" + randomDailyCosmetics.size());

        if (items.isEmpty()) {
            dbg("openMenu(): items map is empty -> forcing reloadStoreContent()");
            reloadStoreContent();
            dbg("openMenu(): after reload itemsSlots=" + items.keySet());
        }

        dbg("openMenu() player=" + viewer.getName()
                + " iaPresent=" + isItemsAdderPresent()
                + " iaReady=" + itemsAdderReady
                + " itemsLoadedSlots=" + items.keySet()
                + " dailyCount=" + randomDailyCosmetics.size());

        if (!canOpenStoreNow()) {
            dbg("BLOCKED: store not loaded yet (ItemsAdder present but not ready).");
            sendStoreNotLoaded(viewer);
            return;
        }

        String titleRaw = Hooks.processPlaceholders(viewer, this.title);
        titleRaw = legacyToMini(titleRaw);
        Gui gui = Gui.gui()
                .title(AdventureUtils.MINI_MESSAGE.deserialize(titleRaw))
                .rows(this.rows)
                .create();

        gui.setDefaultClickAction(e -> {
            e.setCancelled(true);

            Player v = (Player) e.getWhoClicked();
            UUID uuid = v.getUniqueId();

            PendingConfirm prev = pending.get(uuid);
            if (prev == null) return;

            int raw = e.getRawSlot();
            boolean clickInsideTop = raw >= 0 && raw < gui.getInventory().getSize();

            if (!clickInsideTop) {
                pending.remove(uuid);
                CosmeticUser u = com.hibiscusmc.hmccosmetics.user.CosmeticUsers.getUser(v);
                renderSlot(v, u, gui, prev.slot);
                return;
            }

            if (raw != prev.slot) {
                pending.remove(uuid);
                CosmeticUser u = com.hibiscusmc.hmccosmetics.user.CosmeticUsers.getUser(v);
                renderSlot(v, u, gui, prev.slot);
            }
        });
        gui.setCloseGuiAction(event -> {
            pending.remove(viewer.getUniqueId());
            Bukkit.getPluginManager().callEvent(new PlayerMenuCloseEvent(user, this, event.getReason()));
        });

        updateMenu(viewer, user, gui);
        boolean wasInCosmeticsMenu = viewer.getOpenInventory().getTopInventory().getHolder() instanceof dev.triumphteam.gui.guis.Gui;
        gui.open(viewer);
        if (!wasInCosmeticsMenu) {
            viewer.playSound(viewer.getLocation(), "sounds:menu_open", 1.0f, 1.0f);
        }
        if (focusSlot >= 0) startFlicker(viewer, gui, focusSlot);
    }

    public void openMenu(CosmeticUser user) {
        openMenu(user, -1);
    }

    private void renderSlot(Player viewer, CosmeticUser user, Gui gui, int slot) {
        // Check if this is a back button slot
        if (backButtonSlots != null && backButtonSlots.contains(slot)) {
            ItemStack backItem = backButtonItem != null ? backButtonItem.clone() : new ItemStack(Material.BARRIER);
            backItem = applyViewerFormatting(viewer, backItem);
            gui.updateItem(slot, ItemBuilder.from(backItem).asGuiItem(e -> {
                if (backButtonSound != null && !backButtonSound.isEmpty()) {
                    viewer.playSound(viewer.getLocation(), backButtonSound, 1.0f, 1.0f);
                }
                if (backButtonCommand != null && !backButtonCommand.isEmpty()) {
                    viewer.performCommand(backButtonCommand.replace("%player%", viewer.getName()));
                }
            }));
            return;
        }
        
        if (items.containsKey(slot)) {
            MenuItem it = items.get(slot).get(0);
            ConfigurationNode storeNode = it.itemConfig();

            ItemStack stack = it.item().clone();
            stack = applyViewerFormatting(viewer, stack);

            gui.updateItem(slot, ItemBuilder.from(stack).asGuiItem(e ->
                    handleStoreClick(viewer, user, gui, slot, false, null, storeNode)
            ));
            return;
        }

        if (randomDailyCosmetics.containsKey(slot)) {
            Cosmetic cos = randomDailyCosmetics.get(slot);
            ItemStack stack = getCosmeticMenuItem(viewer, user, cos, slot);
            stack = applyPlaceholders(viewer, stack);

            gui.updateItem(slot, ItemBuilder.from(stack).asGuiItem(e ->
                    handleStoreClick(viewer, user, gui, slot, true, cos, cos.getConfig())
            ));
        }
    }

    private void updateMenu(Player viewer, CosmeticUser user, Gui gui) {
        dbg("updateMenu(): invSize=" + gui.getInventory().getSize()
                + " itemsSlots=" + items.keySet()
                + " dailySlots=" + randomDailyCosmetics.keySet());

        for (int i = 0; i < gui.getInventory().getSize(); i++) {
            if (pending.containsKey(viewer.getUniqueId()) && pending.get(viewer.getUniqueId()).slot == i) continue;

            final int slot = i;

            // Check if this is a back button slot
            if (backButtonSlots != null && backButtonSlots.contains(slot)) {
                ItemStack backItem = backButtonItem != null ? backButtonItem.clone() : new ItemStack(Material.BARRIER);
                backItem = applyViewerFormatting(viewer, backItem);
                gui.setItem(slot, ItemBuilder.from(backItem).asGuiItem(e -> {
                    if (backButtonSound != null && !backButtonSound.isEmpty()) {
                        viewer.playSound(viewer.getLocation(), backButtonSound, 1.0f, 1.0f);
                    }
                    if (backButtonCommand != null && !backButtonCommand.isEmpty()) {
                        viewer.performCommand(backButtonCommand.replace("%player%", viewer.getName()));
                    }
                }));
                continue;
            }

            if (items.containsKey(slot)) {
                MenuItem item = items.get(slot).get(0);

                ConfigurationNode storeNode = item.itemConfig();

                ConfigurationNode itemNode = storeNode.node("item");

                Type type = item.type() != null ? item.type() : Types.getType("default");

                ItemStack stack = applyPlaceholders(viewer, item.item().clone());

                dbg("config slot=" + slot
                        + " materialBefore=" + safe(storeNode.node("item", "material").getString())
                        + " resultType=" + (stack == null ? "null" : stack.getType().name())
                        + " name=" + (stack != null && stack.getItemMeta() != null ? safe(stack.getItemMeta().getDisplayName()) : "no-meta"));

                stack = applyPlaceholders(viewer, stack);

                gui.setItem(slot, ItemBuilder.from(stack).asGuiItem(e ->
                        handleStoreClick(viewer, user, gui, slot, false, null, storeNode)
                ));
            } else if (randomDailyCosmetics.containsKey(slot)) {
                dbg("updateMenu(): slot " + slot + " using daily cosmetic=" + randomDailyCosmetics.get(slot).getId());

                Cosmetic cos = randomDailyCosmetics.get(slot);
                ItemStack stack = getCosmeticMenuItem(viewer, user, cos, slot);

                stack = applyPlaceholders(viewer, stack);

                gui.setItem(slot, ItemBuilder.from(stack).asGuiItem(e ->
                        handleStoreClick(viewer, user, gui, slot, true, cos, cos.getConfig())
                ));
            }
        }
    }

    private void handleStoreClick(Player viewer, CosmeticUser user, Gui gui, int slot, boolean isCosmetic, Cosmetic cos, ConfigurationNode pConfig) {
        dbg("CLICK slot=" + slot
                + " isCosmetic=" + isCosmetic
                + " hasPrev=" + (pending.get(viewer.getUniqueId()) != null)
                + " prevSlot=" + (pending.get(viewer.getUniqueId()) != null ? pending.get(viewer.getUniqueId()).slot : "null")
                + " pConfigVirtual=" + (pConfig == null || pConfig.virtual()));

        UUID uuid = viewer.getUniqueId();
        PendingConfirm prev = pending.get(uuid);

        if (Settings.isMenuClickCooldown()) {
            if (System.currentTimeMillis() - Menus.getCooldown(uuid) < cooldown) {
                MessagesUtil.sendMessage(viewer, "on-click-cooldown");
                return;
            }
            Menus.addCooldown(uuid, System.currentTimeMillis());
        }

        if (prev != null && prev.slot != slot) {
            pending.remove(uuid);

            renderSlot(viewer, user, gui, prev.slot);
        }

        if (isCosmetic && cos != null && user.canEquipCosmetic(cos, true)) return;

        if (prev != null && prev.slot == slot) {
            double cost = isCosmetic ? getRarity(cos).price() : pConfig.node("cost").getDouble(0.0);
            if (!tryPurchase(viewer, cost)) {
                playConfiguredSound(viewer, "fail");
                viewer.sendMessage(cc(config.node("messages", "not-enough-currency").getString("&cYou don't have enough money!").replace("<currency>", cc(config.node("economy", "currency-name").getString("gems")))));
                pending.remove(uuid);
                updateMenu(viewer, user, gui);
                Bukkit.getScheduler().runTaskLater(HMCCosmeticsPlugin.getInstance(), () -> {
                    if (!viewer.isOnline()) return;

                    if (gui.getInventory().getViewers().contains(viewer)) {
                        updateMenu(viewer, user, gui);
                    }
                }, 5L);

                return;
            }

            String itemName = "";

            playConfiguredSound(viewer, "success");
            if (isCosmetic && cos != null) {
                String perm = cos.getPermission();
                if (perm != null) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + viewer.getName() + " permission set " + perm + " true");
                    itemName = cc(Objects.requireNonNull(cos.getItem()).getItemMeta().getDisplayName());
                }
            }
            if (!isCosmetic) {
                try {
                    itemName = cc(ChatColor.translateAlternateColorCodes('&', pConfig.node("item").node("name").getString()));
                } catch (Exception ex) {
                    MessagesUtil.sendDebugMessages(cc("&c[STORE] Unexpected error while giving item: " + ex.getMessage()));
                }
            }
            try {
                List<String> cmds = pConfig.node("commands").getList(String.class, List.of());
                cmds.forEach(cmd -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", viewer.getName())));
            } catch (SerializationException ignored) {
            }

            viewer.sendMessage(cc(config.node("messages", "item-purchased").getString("&aYou successfully purchased <item>!").replace("<item>", itemName.isEmpty() ? "an item" : itemName)));

            pending.remove(uuid);
            updateMenu(viewer, user, gui);
            pending.remove(uuid);
            updateMenu(viewer, user, gui);

            Bukkit.getScheduler().runTask(HMCCosmeticsPlugin.getInstance(), () -> {
                if (!viewer.isOnline()) return;
                viewer.closeInventory();
                openMenu(user);
            });
            return;
        }

        ItemStack current = gui.getInventory().getItem(slot);
        if (current == null) return;

        // Save the original name from the current item
        ItemMeta cm = current.getItemMeta();
        String originalName = (cm != null && cm.hasDisplayName()) ? cm.getDisplayName() : null;

        ItemStack confirmIcon = buildTemplateFromIcon(viewer, "confirm", current.clone());

        ItemMeta im = confirmIcon.getItemMeta();
        if (cm != null && im != null) {
            // Restore the original cosmetic name
            if (originalName != null) {
                im.setDisplayName(originalName);
            }

            // Copy lore but replace the last line (price) with configurable confirm text
            List<String> lore = (cm.hasLore() && cm.getLore() != null) ? new ArrayList<>(cm.getLore()) : new ArrayList<>();
            if (!lore.isEmpty()) {
                lore.set(lore.size() - 1, cc(config.node("messages", "confirm-line").getString("&6&lCONFIRM")));
            } else {
                lore.add(cc(config.node("messages", "confirm-line").getString("&6&lCONFIRM")));
            }
            im.setLore(lore);

            confirmIcon.setItemMeta(im);
        }

        pending.put(uuid, new PendingConfirm(slot));
        gui.updateItem(slot, ItemBuilder.from(confirmIcon).asGuiItem(e ->
                handleStoreClick(viewer, user, gui, slot, isCosmetic, cos, pConfig)
        ));
        playConfiguredSound(viewer, "confirm");
    }

    private Rarity getRarity(Cosmetic cos) {
        if (cos == null) return Rarities.values().stream().findFirst().orElse(null);

        Rarity rarity = Rarities.get(cos.getRarityId());
        if (rarity == null) {
            return Rarities.values().stream().findFirst().orElse(null);
        }
        return rarity;
    }

    private String getCosmeticTypeName(Cosmetic cos) {
        // For weapon skins with a retexture group, use the retexture group's friendly name
        if (cos instanceof CosmeticSkinType skinType) {
            String group = skinType.getRetextureGroup();
            if (group != null && !group.isEmpty()) {
                return TranslationUtil.getTranslation("cosmetic-type", group);
            }
        }
        // Otherwise, use the slot's friendly name
        String slotName = cos.getSlot() != null ? cos.getSlot().getName() : "NONE";
        return TranslationUtil.getTranslation("cosmetic-type", slotName);
    }

    private ItemStack getCosmeticMenuItem(Player viewer, CosmeticUser user, Cosmetic cos, int slot) {
        ItemStack item = cos.getItem().clone();
        Type cosType = Types.getType("cosmetic");
        if (cosType != null) item = cosType.setItem(viewer, user, cos.getConfig(), item, slot);

        // Apply glint if configured
        boolean glint = cos.getConfig().node("glint").getBoolean(false);
        if (glint) {
            ItemMeta glintMeta = item.getItemMeta();
            if (glintMeta != null) {
                glintMeta.addEnchant(Enchantment.PROTECTION, 1, true);
                glintMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(glintMeta);
            }
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : meta.getLore();

            // Add cosmetic type line at the top
            String cosmeticTypeName = getCosmeticTypeName(cos);
            String typeLineFormat = config.node("messages", "type-line").getString("&8<type>");
            lore.add(cc(typeLineFormat.replace("<type>", cosmeticTypeName)));

            Rarity rarity = getRarity(cos);

            lore.add(cc(config.node("messages", "rarity-line").getString("&7Rarity: <rarity>").replace("<rarity>", rarity != null ? rarity.displayName() : "Normal")));
            
            // Check if user already owns the cosmetic
            boolean isPurchased = user.canEquipCosmetic(cos, true);
            if (isPurchased) {
                // Add "PURCHASED" text instead of price
                lore.add(cc(config.node("messages", "purchased-line").getString("&a&lPURCHASED")));
            } else {
                // Add price line
                lore.add(cc(config.node("messages", "price-line").getString("&7Price: &b<price> <currency>").replace("<price>", String.valueOf(rarity != null ? rarity.price() : 0)).replace("<currency>", cc(config.node("economy", "currency-name").getString("gems")))));
            }
            
            meta.setLore(lore);
            item.setItemMeta(meta);

            if (isPurchased) {
                // Save the original cosmetic name before template
                String originalName = meta.hasDisplayName() ? meta.getDisplayName() : null;
                
                // Build the purchased template (changes to checkmark icon)
                ItemStack purchased = buildTemplateFromIcon(viewer, "purchased", item);
                
                // Restore the cosmetic's original name and the lore we built
                ItemMeta pm = purchased.getItemMeta();
                if (pm != null) {
                    if (originalName != null) {
                        pm.setDisplayName(originalName);
                    }
                    pm.setLore(lore);
                    purchased.setItemMeta(pm);
                }
                
                return purchased;
            }
        }
        return item;
    }

    private boolean tryPurchase(Player player, double cost) {
        if (cost <= 0) return true;
        Plugin plugin = Bukkit.getPluginManager().getPlugin("DojoEconomy");
        if (!(plugin instanceof DojoEconomy economy)) return false;
        BalanceManager bm = economy.getBalanceManager();
        String currency = config.node("economy", "currency").getString("gems");
        if (!bm.hasBalance(player.getUniqueId(), currency, cost)) return false;
        bm.subtractBalance(player.getUniqueId(), currency, cost);
        return true;
    }

    private void startFlicker(Player viewer, Gui gui, int slot) {
        ItemStack shown = gui.getInventory().getItem(slot);
        if (shown == null || shown.getType().isAir()) return;

        CosmeticUser user = com.hibiscusmc.hmccosmetics.user.CosmeticUsers.getUser(viewer);

        final boolean isDaily = randomDailyCosmetics.containsKey(slot);
        final Cosmetic cos = isDaily ? randomDailyCosmetics.get(slot) : null;

        final ConfigurationNode storeNode =
                isDaily ? (cos != null ? cos.getConfig() : null)
                        : (items.containsKey(slot) ? items.get(slot).get(0).itemConfig() : null);

        ItemStack realSnap = shown.clone();

        ItemStack invBase = buildTemplateFromIcon(viewer, "invisible", new ItemStack(Material.AIR));
        if (invBase == null) invBase = new ItemStack(Material.AIR);

        ItemStack invFrame  = mergeVisual(invBase.clone(), realSnap);
        ItemStack realFrame = mergeVisual(realSnap.clone(), realSnap);

        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (!viewer.isOnline() || gui.getInventory().getViewers().isEmpty() || count >= 8) {
                    cancel();
                    CosmeticUser u = CosmeticUsers.getUser(viewer);

                    pending.remove(viewer.getUniqueId());
                    renderSlot(viewer, u, gui, slot);

                    return;
                }

                ItemStack frame = (count % 2 == 0) ? invFrame : realFrame;

                PendingConfirm pc = pending.get(viewer.getUniqueId());
                if (pc != null && pc.slot == slot) {
                    cancel();
                    return;
                }

                gui.updateItem(slot, ItemBuilder.from(frame).asGuiItem(e ->
                        handleStoreClick(viewer, user, gui, slot, isDaily, cos, storeNode)
                ));

                count++;
            }
        }.runTaskTimer(HMCCosmeticsPlugin.getInstance(), 0L, 4L);
    }

    private ItemStack mergeVisual(ItemStack target, ItemStack textSource) {
        if (target == null || textSource == null) return target;

        ItemMeta src = textSource.getItemMeta();
        if (src == null) return target;

        ItemMeta tm = target.getItemMeta();
        if (tm == null) return target;

        tm.setDisplayName(null);
        tm.setLore(null);

        tm.displayName(src.displayName());
        tm.lore(src.lore());

        tm.addItemFlags(src.getItemFlags().toArray(new ItemFlag[0]));
        src.getEnchants().forEach((en, lvl) -> tm.addEnchant(en, lvl, true));

        target.setItemMeta(tm);
        return target;
    }

    private List<Integer> parseSlots(List<String> strings) {
        List<Integer> slots = new ArrayList<>();
        for (String s : strings) {
            try {
                if (s.contains("-")) {
                    String[] p = s.split("-");
                    for (int i = Integer.parseInt(p[0]); i <= Integer.parseInt(p[1]); i++) slots.add(i);
                } else slots.add(Integer.parseInt(s));
            } catch (NumberFormatException ignored) {
            }
        }
        return slots;
    }

    private void applyBottomLore(Player viewer, ItemStack item, String rawLine) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<Component> lore = meta.lore();
        if (lore == null) lore = new ArrayList<>();

        lore.add(Component.empty());
        lore.add(mm(viewer, rawLine));

        meta.lore(lore);
        item.setItemMeta(meta);
    }

    private void playConfiguredSound(Player p, String key) {
        ConfigurationNode n = config.node("sounds", key);
        if (n.virtual()) return;

        String raw = n.node("sound").getString("UI_BUTTON_CLICK");
        float volume = (float) n.node("volume").getDouble(1.0);
        float pitch = (float) n.node("pitch").getDouble(1.0);

        if (raw == null || raw.trim().isEmpty()) return;

        String soundName = raw.trim();

        try {
            Sound s = Sound.valueOf(soundName.toUpperCase(Locale.ROOT));
            p.playSound(p.getLocation(), s, volume, pitch);
            return;
        } catch (IllegalArgumentException ignored) {
        }

        try {
            p.playSound(p.getLocation(), soundName, volume, pitch);
        } catch (Exception ex) {
            MessagesUtil.sendDebugMessages(cc("&c[STORE] Invalid sound '" + soundName + "' for key '" + key + "': " + ex.getMessage()));
        }
    }

    private String cc(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private record PendingConfirm(int slot) {
    }

    public void refreshDailyCosmetics() {
        randomDailyCosmetics.clear();
        setupDailyCosmetics();
    }

    private boolean isPapiAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    private String applyPlaceholders(Player player, String text) {
        if (text == null) return null;

        String out = Hooks.processPlaceholders(player, text);

        if (isPapiAvailable()) {
            out = PlaceholderAPI.setPlaceholders(player, out);
        }
        return out;
    }

    private ItemStack applyPlaceholders(Player player, ItemStack item) {
        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // DisplayName
        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            meta.setDisplayName(applyPlaceholders(player, name));
        }

        // Lore
        if (meta.hasLore() && meta.getLore() != null) {
            List<String> lore = meta.getLore();
            List<String> newLore = new ArrayList<>(lore.size());
            for (String line : lore) {
                newLore.add(applyPlaceholders(player, line));
            }
            meta.setLore(newLore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack buildTemplateFromIcon(Player viewer, String iconKey, ItemStack fallback) {
        ConfigurationNode node = config.node("icons", iconKey);
        if (node == null || node.virtual()) return fallback;

        ItemStack built = resolveConfigItem(node); // no-player
        if (built == null || built.getType().isAir()) return fallback;

        built = applyViewerFormatting(viewer, built);

        return built;
    }

    public static void setItemsAdderReady(boolean ready) {
        itemsAdderReady = ready;
        Bukkit.getLogger().info("[HMCC Store] ItemsAdder ready = " + ready);
    }

    private boolean isItemsAdderPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("ItemsAdder");
    }

    private boolean canOpenStoreNow() {
        if (!isItemsAdderPresent()) return true;

        return itemsAdderReady;
    }

    private void sendStoreNotLoaded(Player viewer) {
        String msg = config.node("messages", "store-not-loaded")
                .getString("&cThe store is not loaded yet. Please try again in a moment.");
        viewer.sendMessage(cc(msg));
    }

    private boolean debugEnabled() {
        return config.node("debug").getBoolean(false);
    }

    private void dbg(String msg) {
        if (!debugEnabled()) return;
        MessagesUtil.sendDebugMessages(cc("&e[STORE-DEBUG] " + msg));
    }

    private String safe(Object o) {
        return o == null ? "null" : String.valueOf(o);
    }

    private ItemStack resolveConfigItem(ConfigurationNode itemNode) {
        if (itemNode == null) return null;

        String mat = itemNode.node("material").getString();
        if (mat == null || mat.isBlank()) return null;
        mat = mat.trim();

        if (isItemsAdderPresent() && mat.contains(":") && !mat.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            ItemStack ia = resolveItemsAdderCustomStack(mat);

            if ((ia == null || ia.getType().isAir()) && mat.contains(":")) {
                String noNs = mat.split(":", 2)[1];
                dbg("resolveConfigItem(): IA try no-namespace id=" + noNs);
                ia = resolveItemsAdderCustomStack(noNs);
            }

            if (ia != null && !ia.getType().isAir()) {
                applyBasicMetaFromNodeLegacy(ia, itemNode);
                return ia;
            }

            dbg("resolveConfigItem(): IA failed for " + mat);
            return null;
        }

        // Vanilla/serializer
        try {
            ItemStack built = ItemSerializer.INSTANCE.deserialize(ItemStack.class, itemNode);
            return built;
        } catch (Exception ex) {
            dbg("resolveConfigItem(): serializer exception: " + ex.getMessage());
            return null;
        }
    }

    /**
     * Applica amount/name/lore ma come STRING legacy (non Component),
     * senza placeholder e senza MiniMessage. Poi lo “upgradei” con applyViewerFormatting(...)
     */
    private void applyBasicMetaFromNodeLegacy(ItemStack base, ConfigurationNode itemNode) {
        if (base == null) return;

        int amount = itemNode.node("amount").getInt(base.getAmount());
        if (amount > 0) base.setAmount(amount);

        ItemMeta meta = base.getItemMeta();
        if (meta == null) return;

        // name (string)
        ConfigurationNode nameNode = itemNode.node("name");
        String name = nameNode.virtual() ? null : nameNode.getString();
        if (name != null && !name.isBlank()) {
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        }

        // lore (string list)
        ConfigurationNode loreNode = itemNode.node("lore");
        if (!loreNode.virtual()) {
            try {
                List<String> lore = loreNode.getList(String.class);
                if (lore != null) {
                    List<String> out = new ArrayList<>(lore.size());
                    for (String line : lore) out.add(ChatColor.translateAlternateColorCodes('&', line));
                    meta.setLore(out);
                }
            } catch (Exception ignored) {}
        }

        base.setItemMeta(meta);
    }

    private ItemStack applyViewerFormatting(Player viewer, ItemStack item) {
        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // DisplayName (legacy § / & + placeholders + minimessage)
        if (meta.hasDisplayName()) {
            String raw = meta.getDisplayName();
            raw = Hooks.processPlaceholders(viewer, raw);
            raw = legacyToMini(raw);
            meta.displayName(AdventureUtils.MINI_MESSAGE.deserialize("<!italic>" + raw));
        }

        List<String> legacyLore = meta.getLore();
        if (legacyLore != null && !legacyLore.isEmpty()) {
            List<Component> lore = new ArrayList<>(legacyLore.size());
            for (String line : legacyLore) {
                String s = Hooks.processPlaceholders(viewer, line);
                s = legacyToMini(s);
                lore.add(AdventureUtils.MINI_MESSAGE.deserialize("<!italic>" + s));
            }
            meta.lore(lore);
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack resolveItemsAdderCustomStack(String id) {
        try {
            dev.lone.itemsadder.api.CustomStack cs = dev.lone.itemsadder.api.CustomStack.getInstance(id);
            if (cs == null) {
                dbg("resolveItemsAdderCustomStack(): NOT FOUND " + id);
                return null;
            }
            ItemStack stack = cs.getItemStack();
            dbg("resolveItemsAdderCustomStack(): FOUND " + id + " -> " + (stack == null ? "null" : stack.getType()));
            return stack == null ? null : stack.clone();
        } catch (Throwable t) {
            dbg("resolveItemsAdderCustomStack(): ERROR " + id + " -> " + t.getClass().getSimpleName() + ": " + t.getMessage());
            return null;
        }
    }

    public void startItemsAdderWarmupIfNeeded() {
        if (!isItemsAdderPresent()) {
            setItemsAdderReady(true);
            return;
        }

        if (warmupStarted) return;
        warmupStarted = true;

        setItemsAdderReady(false);

        String probeId = findFirstMaterialIdFromConfig();
        if (probeId == null) {
            dbg("Warmup: no probeId found in config items.*.item.material -> allow open");
            setItemsAdderReady(true);
            return;
        }

        dbg("Warmup: starting. probeId=" + probeId);

        new BukkitRunnable() {
            int tries = 0;

            @Override
            public void run() {
                tries++;

                ItemStack probe = resolveItemsAdderCustomStack(probeId);
                boolean ok = (probe != null && probe.getType() != Material.AIR);

                dbg("Warmup try " + tries + "/" + WARMUP_MAX_TRIES
                        + " probeId=" + probeId
                        + " ok=" + ok
                        + " type=" + (probe == null ? "null" : probe.getType().name()));

                if (ok) {
                    setItemsAdderReady(true);
                    dbg("Warmup: ItemsAdder ready (probe resolved). Reloading store content...");

                    this.cancel();

                    Bukkit.getScheduler().runTask(HMCCosmeticsPlugin.getInstance(), () -> {
                        try {
                            reloadStoreContent();
                            StoreArmorStandManager.refreshArmorStandsOnly();
                        } catch (Exception ex) {
                            dbg("Warmup reloadStoreContent() failed: " + ex.getMessage());
                            ex.printStackTrace();
                        }
                    });
                    return;
                }

                if (tries >= WARMUP_MAX_TRIES) {
                    dbg("Warmup: FAILED after max tries. Store will stay blocked (store-not-loaded).");
                    cancel();
                }
            }
        }.runTaskTimer(HMCCosmeticsPlugin.getInstance(), 1L, WARMUP_PERIOD_TICKS);
    }

    private String findFirstMaterialIdFromConfig() {
        ConfigurationNode itemsNode = config.node("items");
        if (itemsNode.virtual()) return null;

        for (Map.Entry<Object, ? extends ConfigurationNode> e : itemsNode.childrenMap().entrySet()) {
            ConfigurationNode node = e.getValue();
            String mat = node.node("item", "material").getString();
            if (mat != null && !mat.isBlank()) return mat.trim();
        }
        return null;
    }

    private Component mm(Player viewer, @Nullable String raw) {
        if (raw == null) return Component.empty();

        String s = Hooks.processPlaceholders(viewer, raw);

        s = legacyToMini(s);

        return AdventureUtils.MINI_MESSAGE.deserialize(s);
    }

    private String legacyToMini(String s) {
        if (s == null) return null;

        s = s.replace('§', '&');

        return legacyAmpersandToMini(s);
    }

    private String legacyAmpersandToMini(String s) {
        if (s.indexOf('&') == -1) return s;

        StringBuilder out = new StringBuilder(s.length() + 16);

        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);

            if (c == '&' && i + 1 < s.length() && s.charAt(i + 1) == '&') {
                out.append('&');
                i++;
                continue;
            }

            if (c != '&' || i + 1 >= s.length()) {
                out.append(c);
                continue;
            }

            char n = s.charAt(i + 1);

            // &#RRGGBB
            if (n == '#' && i + 7 < s.length()) {
                String hex = s.substring(i + 2, i + 8);
                if (hex.matches("(?i)[0-9a-f]{6}")) {
                    out.append("<#").append(hex).append(">");
                    i += 7;
                    continue;
                }
            }

            // &x&1&2&3&4&5&6
            if ((n == 'x' || n == 'X') && i + 13 < s.length()) {
                StringBuilder hex = new StringBuilder(6);
                boolean ok = true;
                int j = i + 2;
                for (int k = 0; k < 6; k++) {
                    if (j + 1 >= s.length() || s.charAt(j) != '&') { ok = false; break; }
                    char h = s.charAt(j + 1);
                    if (!isHex(h)) { ok = false; break; }
                    hex.append(h);
                    j += 2;
                }
                if (ok) {
                    out.append("<#").append(hex).append(">");
                    i = j - 1;
                    continue;
                }
            }

            String tag = legacyCodeToMiniTag(n);
            if (tag != null) {
                out.append(tag);
                i++;
            } else {
                out.append('&').append(n);
                i++;
            }
        }

        return out.toString();
    }

    private boolean isHex(char c) {
        c = Character.toLowerCase(c);
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
    }

    private String legacyCodeToMiniTag(char code) {
        switch (Character.toLowerCase(code)) {
            case '0': return "<black>";
            case '1': return "<dark_blue>";
            case '2': return "<dark_green>";
            case '3': return "<dark_aqua>";
            case '4': return "<dark_red>";
            case '5': return "<dark_purple>";
            case '6': return "<gold>";
            case '7': return "<gray>";
            case '8': return "<dark_gray>";
            case '9': return "<blue>";
            case 'a': return "<green>";
            case 'b': return "<aqua>";
            case 'c': return "<red>";
            case 'd': return "<light_purple>";
            case 'e': return "<yellow>";
            case 'f': return "<white>";

            case 'k': return "<obfuscated>";
            case 'l': return "<bold>";
            case 'm': return "<strikethrough>";
            case 'n': return "<underlined>";
            case 'o': return "<italic>";
            case 'r': return "<reset>";
            default: return null;
        }
    }

    private ItemStack resolveBaseItemForGive(ConfigurationNode itemNode) {
        if (itemNode == null) return null;

        String mat = itemNode.node("material").getString();
        if (mat == null || mat.isBlank()) return null;
        mat = mat.trim();

        ItemStack base = null;

        // ItemsAdder
        if (isItemsAdderPresent() && mat.contains(":") && !mat.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            base = resolveItemsAdderCustomStack(mat);

            if ((base == null || base.getType().isAir()) && mat.contains(":")) {
                String noNs = mat.split(":", 2)[1];
                base = resolveItemsAdderCustomStack(noNs);
            }
        } else {
            // Vanilla
            try {
                base = ItemSerializer.INSTANCE.deserialize(ItemStack.class, itemNode);
            } catch (Exception ignored) {}
        }

        if (base == null || base.getType().isAir()) return null;

        int amount = itemNode.node("amount").getInt(base.getAmount());
        if (amount > 0) base.setAmount(amount);

        return base;
    }
}