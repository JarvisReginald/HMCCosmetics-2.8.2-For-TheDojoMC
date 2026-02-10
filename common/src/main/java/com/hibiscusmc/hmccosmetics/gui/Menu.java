package com.hibiscusmc.hmccosmetics.gui;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.api.events.PlayerMenuCloseEvent;
import com.hibiscusmc.hmccosmetics.api.events.PlayerMenuOpenEvent;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticHolder;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetics;
import com.hibiscusmc.hmccosmetics.gui.type.Type;
import com.hibiscusmc.hmccosmetics.gui.type.Types;
import com.hibiscusmc.hmccosmetics.gui.type.types.TypeCosmetic;
import com.hibiscusmc.hmccosmetics.gui.type.types.TypePageSwitcher;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import dev.triumphteam.gui.builder.item.ItemBuilder;
import dev.triumphteam.gui.components.GuiType;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import lombok.Getter;
import me.lojosho.hibiscuscommons.config.serializer.ItemSerializer;
import me.lojosho.hibiscuscommons.hooks.Hooks;
import me.lojosho.hibiscuscommons.util.AdventureUtils;
import me.lojosho.shaded.configurate.ConfigurationNode;
import me.lojosho.shaded.configurate.serialize.SerializationException;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Menu {

    @Getter
    private final String id;
    @Getter
    private final String title;
    @Getter
    private final int rows;
    @Getter
    private final Long cooldown;
    @Getter
    private final ConfigurationNode config;
    @Getter
    private final String permissionNode;
    private HashMap<Integer, List<MenuItem>> items;
    private final List<MenuItem> cosmeticItems = new ArrayList<>();
    private List<Integer> freeSlots = new ArrayList<>();
    private final Map<UUID, Integer> viewerPages = new HashMap<>();
    @Getter
    private final int refreshRate;
    @Getter
    private final boolean shading;

    public Menu(String id, @NotNull ConfigurationNode config) {
        this.id = config.node("id").getString(id);
        this.config = config;

        title = config.node("title").getString("chest");
        rows = config.node("rows").getInt(1);
        cooldown = config.node("click-cooldown").getLong(Settings.getDefaultMenuCooldown());
        permissionNode = config.node("permission").getString("");
        refreshRate = config.node("refresh-rate").getInt(-1);
        shading = config.node("shading").getBoolean(Settings.isDefaultShading());

        items = new HashMap<>();
        setupItems();

        Menus.addMenu(this);
    }

    private void setupItems() {
        cosmeticItems.clear();

        for (ConfigurationNode config : config.node("items").childrenMap().values()) {

            // Determine the type first so we know if this is a cosmetic
            Type type = Types.getDefaultType();
            if (!config.node("type").virtual()) {
                String typeId = config.node("type").getString("");
                if (Types.isType(typeId)) type = Types.getType(typeId);
            }

            // Deserialize the item
            ItemStack item;

            boolean isOutfitSlot = "dojo_outfits".equalsIgnoreCase(this.id)
                    && "outfit_slot".equalsIgnoreCase(config.node("type").getString(""));

            boolean isPageSwitcher = type instanceof TypePageSwitcher;

            if ((isOutfitSlot || isPageSwitcher) && config.node("item").virtual()) {
                item = new ItemStack(Material.AIR);
            } else {
                try {
                    item = ItemSerializer.INSTANCE.deserialize(ItemStack.class, config.node("item"));
                } catch (SerializationException e) {
                    MessagesUtil.sendDebugMessages("Unable to get valid item for " + config.key().toString() + " " + e.getMessage());
                    continue;
                }
                if (item == null) {
                    MessagesUtil.sendDebugMessages("Something went wrong with the item creation for " + config.key().toString());
                    continue;
                }
            }

            if (isOutfitSlot) {
                try {
                    config.node("__add_outfit_item").set(this.config.node("add_outfit_item").raw());
                    config.node("__outfit_item").set(this.config.node("outfit_item").raw());
                    config.node("__locked_outfit_item").set(this.config.node("locked_outfit_item").raw());
                    config.node("__add_outfit_custom_message").set(this.config.node("add_outfit_custom_message").getString(""));
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            int priority = config.node("priority").getInt(1);

            // For cosmetic items: collect into cosmeticItems list, do NOT assign a slot
            if (type instanceof TypeCosmetic) {
                MenuItem menuItem = new MenuItem(List.of(), item, type, priority, config);
                cosmeticItems.add(menuItem);
                continue;
            }

            // For static items (empty, page-switcher, outfit, etc.): assign slots as before
            List<String> slotString = null;
            try {
                slotString = config.node("slots").getList(String.class);
            } catch (SerializationException ignored) {
            }

            List<Integer> slots = (slotString == null) ? new ArrayList<>() : getSlots(slotString);

            if (slots == null || slots.isEmpty()) {
                Integer firstAvailable = getFirstAvailableSlot();
                if (firstAvailable == null) {
                    MessagesUtil.sendDebugMessages("No slot available for " + config.key().toString());
                    continue;
                }
                slots = List.of(firstAvailable);
                MessagesUtil.sendDebugMessages("Slots not specified for " + config.key().toString()
                        + ", use the first available: " + firstAvailable);
            }

            for (Integer slot : slots) {
                MenuItem menuItem = new MenuItem(slots, item, type, priority, config);

                if (items.containsKey(slot)) {
                    List<MenuItem> menuItems = items.get(slot);
                    menuItems.add(menuItem);
                    menuItems.sort(priorityCompare);
                    items.put(slot, menuItems);
                } else {
                    items.put(slot, new ArrayList<>(List.of(menuItem)));
                }
            }
        }

        // Compute free slots: all slots not occupied by static items
        freeSlots = new ArrayList<>();
        int size = getRows() * 9;
        for (int i = 0; i < size; i++) {
            if (!items.containsKey(i)) {
                freeSlots.add(i);
            }
        }
    }

    private Integer getFirstAvailableSlot() {
        int size = getRows() * 9;
        for (int i = 0; i < size; i++) {
            if (!items.containsKey(i) || items.get(i) == null || items.get(i).isEmpty()) {
                return i;
            }
        }
        return null;
    }

    private int getTotalPages() {
        if (freeSlots.isEmpty()) return 1;
        return Math.max(1, (int) Math.ceil((double) cosmeticItems.size() / freeSlots.size()));
    }

    public void openMenu(CosmeticUser user) {
        openMenu(user, false);
    }

    public void openMenu(@NotNull CosmeticUser user, boolean ignorePermission) {
        Player player = user.getPlayer();
        if (player == null) return;
        openMenu(player, user, ignorePermission);
    }

    public void openMenu(@NotNull Player viewer, @NotNull CosmeticHolder cosmeticHolder) {
        openMenu(viewer, cosmeticHolder, false);
    }

    public void openMenu(@NotNull Player viewer, @NotNull CosmeticHolder cosmeticHolder, boolean ignorePermission) {
        if (!ignorePermission && !permissionNode.isEmpty()) {
            if (!viewer.hasPermission(permissionNode) && !viewer.isOp()) {
                MessagesUtil.sendMessage(viewer, "no-permission");
                return;
            }
        }

        // Initialize page state for this viewer
        viewerPages.put(viewer.getUniqueId(), 0);

        final Component component = AdventureUtils.MINI_MESSAGE.deserialize(Hooks.processPlaceholders(viewer, this.title));
        Gui gui = Gui.gui()
                .title(component)
                .type(GuiType.CHEST)
                .inventory((title, owner, type) -> Bukkit.createInventory(owner, rows * 9, title))
                .create();

        gui.setDefaultClickAction(event -> event.setCancelled(true));

        AtomicInteger taskid = new AtomicInteger(-1);
        gui.setOpenGuiAction(event -> {
            Runnable run = () -> {
                if (gui.getInventory().getViewers().isEmpty() && taskid.get() != -1) {
                    Bukkit.getScheduler().cancelTask(taskid.get());
                }

                updateMenu(viewer, cosmeticHolder, gui);
            };

            if (refreshRate != -1) {
                taskid.set(Bukkit.getScheduler().scheduleSyncRepeatingTask(HMCCosmeticsPlugin.getInstance(), run, 0, refreshRate));
            } else {
                run.run();
            }
        });

        gui.setCloseGuiAction(event -> {
            if (cosmeticHolder instanceof CosmeticUser user) {
                PlayerMenuCloseEvent closeEvent = new PlayerMenuCloseEvent(user, this, event.getReason());
                Bukkit.getScheduler().runTask(HMCCosmeticsPlugin.getInstance(), () -> Bukkit.getPluginManager().callEvent(closeEvent));
            }

            // Clean up page state
            viewerPages.remove(viewer.getUniqueId());

            if (taskid.get() != -1) Bukkit.getScheduler().cancelTask(taskid.get());
        });

        Runnable openGuiTask = () -> {
            boolean wasInCosmeticsMenu = viewer.getOpenInventory().getTopInventory().getHolder() instanceof Gui;
            gui.open(viewer);
            if (!wasInCosmeticsMenu) {
                viewer.playSound(viewer.getLocation(), "sounds:menu_open", 1.0f, 1.0f);
            }
            updateMenu(viewer, cosmeticHolder, gui); // fixes shading? I know I do this twice but it's easier than writing a whole new class to deal with this shit
        };
        
        // API
        if (cosmeticHolder instanceof CosmeticUser user) {
            PlayerMenuOpenEvent event = new PlayerMenuOpenEvent(user, this);
            Bukkit.getScheduler().runTask(HMCCosmeticsPlugin.getInstance(), () -> {
                Bukkit.getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    openGuiTask.run();
                }
            });
        }
        // Internal
        else {
            Bukkit.getScheduler().runTask(HMCCosmeticsPlugin.getInstance(), openGuiTask);
        }
    }

    private void updateMenu(Player viewer, CosmeticHolder cosmeticHolder, Gui gui) {
        // Compute pagination
        int totalPages = getTotalPages();
        int currentPage = viewerPages.getOrDefault(viewer.getUniqueId(), 0) % totalPages;
        int slotsPerPage = freeSlots.size();

        // Compute the slice of cosmetics for this page
        int startIndex = currentPage * slotsPerPage;
        int endIndex = Math.min(startIndex + slotsPerPage, cosmeticItems.size());
        List<MenuItem> pageSlice = (startIndex < cosmeticItems.size())
                ? cosmeticItems.subList(startIndex, endIndex)
                : Collections.emptyList();

        // Build a map of slot -> cosmetic MenuItem for this page
        Map<Integer, MenuItem> pageCosmeticMap = new HashMap<>();
        for (int i = 0; i < freeSlots.size(); i++) {
            if (i < pageSlice.size()) {
                pageCosmeticMap.put(freeSlots.get(i), pageSlice.get(i));
            }
        }

        // Inject page info into page-switcher items
        injectPageInfo(currentPage + 1, totalPages);

        StringBuilder title = new StringBuilder(this.title);

        int row = 0;
        if (shading) {
            for (int i = 0; i < gui.getInventory().getSize(); i++) {
                // Handles the title
                if (i % 9 == 0) {
                    if (row == 0) {
                        title.append(Settings.getFirstRowShift());
                    } else {
                        title.append(Settings.getSequentRowShift());
                    }
                    row += 1;
                } else {
                    title.append(Settings.getIndividualColumnShift());
                }

                boolean occupied = false;

                if (pageCosmeticMap.containsKey(i)) {
                    // This free slot has a cosmetic on this page
                    MenuItem cosItem = pageCosmeticMap.get(i);
                    Cosmetic cosmetic = Cosmetics.getCosmetic(cosItem.itemConfig().node("cosmetic").getString(""));
                    if (cosmetic != null && cosmeticHolder.canEquipCosmetic(cosmetic)) {
                        if (cosmeticHolder.hasCosmeticInSlot(cosmetic)) {
                            title.append(Settings.getEquippedCosmeticColor());
                        } else {
                            if (cosmeticHolder.canEquipCosmetic(cosmetic, true)) {
                                title.append(Settings.getEquipableCosmeticColor());
                            } else {
                                title.append(Settings.getLockedCosmeticColor());
                            }
                        }
                        occupied = true;
                    }
                    updateCosmeticSlot(viewer, cosmeticHolder, gui, i, cosItem);
                } else if (freeSlots.contains(i)) {
                    // This free slot is empty on this page (past end of cosmetics)
                    gui.updateItem(i, ItemBuilder.from(new ItemStack(Material.AIR)).asGuiItem());
                } else if (items.containsKey(i)) {
                    // Static item
                    updateItem(viewer, cosmeticHolder, gui, i);
                }

                if (occupied) {
                    title.append(Settings.getBackground().replaceAll("<row>", String.valueOf(row)));
                } else {
                    title.append(Settings.getClearBackground().replaceAll("<row>", String.valueOf(row)));
                }
            }
            MessagesUtil.sendDebugMessages("Updated menu with title " + title);
            gui.updateTitle(AdventureUtils.MINI_MESSAGE.deserialize(Hooks.processPlaceholders(viewer, title.toString())));
        } else {
            for (int i = 0; i < gui.getInventory().getSize(); i++) {
                if (pageCosmeticMap.containsKey(i)) {
                    MenuItem cosItem = pageCosmeticMap.get(i);
                    Cosmetic cosmetic = Cosmetics.getCosmetic(cosItem.itemConfig().node("cosmetic").getString(""));
                    if (cosmetic != null && cosmeticHolder.canEquipCosmetic(cosmetic)) {
                        updateCosmeticSlot(viewer, cosmeticHolder, gui, i, cosItem);
                    } else {
                        gui.updateItem(i, ItemBuilder.from(new ItemStack(Material.AIR)).asGuiItem());
                    }
                } else if (freeSlots.contains(i)) {
                    gui.updateItem(i, ItemBuilder.from(new ItemStack(Material.AIR)).asGuiItem());
                } else if (items.containsKey(i)) {
                    updateItem(viewer, cosmeticHolder, gui, i);
                }
            }
        }
    }

    /**
     * Injects __current_page and __total_pages into any page-switcher item configs.
     */
    private void injectPageInfo(int currentPage, int totalPages) {
        for (List<MenuItem> menuItems : items.values()) {
            for (MenuItem menuItem : menuItems) {
                if (menuItem.type() instanceof TypePageSwitcher) {
                    try {
                        menuItem.itemConfig().node("__current_page").set(currentPage);
                        menuItem.itemConfig().node("__total_pages").set(totalPages);
                    } catch (SerializationException ignored) {
                    }
                }
            }
        }
    }

    /**
     * Renders a cosmetic MenuItem into a specific slot and binds its click handler.
     */
    private void updateCosmeticSlot(Player viewer, CosmeticHolder cosmeticHolder, Gui gui, int slot, MenuItem cosItem) {
        Type type = cosItem.type();
        ItemStack modifiedItem = getMenuItem(viewer, cosmeticHolder, type, cosItem.itemConfig(), cosItem.item().clone(), slot);
        GuiItem guiItem = ItemBuilder.from(modifiedItem).asGuiItem();
        guiItem.setAction(event -> {
            UUID uuid = viewer.getUniqueId();
            if (Settings.isMenuClickCooldown()) {
                Long userCooldown = Menus.getCooldown(uuid);
                if (userCooldown != 0 && (System.currentTimeMillis() - Menus.getCooldown(uuid) <= getCooldown())) {
                    MessagesUtil.sendDebugMessages("Cooldown for " + viewer.getUniqueId() + " System time: " + System.currentTimeMillis() + " Cooldown: " + Menus.getCooldown(viewer.getUniqueId()) + " Difference: " + (System.currentTimeMillis() - Menus.getCooldown(viewer.getUniqueId())));
                    MessagesUtil.sendMessage(viewer, "on-click-cooldown");
                    return;
                } else {
                    Menus.addCooldown(uuid, System.currentTimeMillis());
                }
            }
            MessagesUtil.sendDebugMessages("Updated Menu Item in slot number " + slot);
            MenuClickContext.set(event.getSlot(), event.getCurrentItem());
            try {
                if (type != null) {
                    type.run(viewer, cosmeticHolder, cosItem.itemConfig(), event.getClick());
                }
            } finally {
                MenuClickContext.clear();
            }
            updateMenu(viewer, cosmeticHolder, gui);
        });

        MessagesUtil.sendDebugMessages("Set a cosmetic item in slot " + slot + " in the menu of " + getId());
        gui.updateItem(slot, guiItem);
    }

    private void updateItem(Player viewer, CosmeticHolder cosmeticHolder, Gui gui, int slot) {
        if (!items.containsKey(slot)) return;
        List<MenuItem> menuItems = items.get(slot);
        if (menuItems.isEmpty()) return;

        for (MenuItem item : menuItems) {
            Type type = item.type();
            ItemStack modifiedItem = getMenuItem(viewer, cosmeticHolder, type, item.itemConfig(), item.item().clone(), slot);
            GuiItem guiItem = ItemBuilder.from(modifiedItem).asGuiItem();
            guiItem.setAction(event -> {
                UUID uuid = viewer.getUniqueId();
                if (Settings.isMenuClickCooldown()) {
                    Long userCooldown = Menus.getCooldown(uuid);
                    if (userCooldown != 0 && (System.currentTimeMillis() - Menus.getCooldown(uuid) <= getCooldown())) {
                        MessagesUtil.sendDebugMessages("Cooldown for " + viewer.getUniqueId() + " System time: " + System.currentTimeMillis() + " Cooldown: " + Menus.getCooldown(viewer.getUniqueId()) + " Difference: " + (System.currentTimeMillis() - Menus.getCooldown(viewer.getUniqueId())));
                        MessagesUtil.sendMessage(viewer, "on-click-cooldown");
                        return;
                    } else {
                        Menus.addCooldown(uuid, System.currentTimeMillis());
                    }
                }
                MessagesUtil.sendDebugMessages("Updated Menu Item in slot number " + slot);

                // Handle page-switcher click: run actions, advance page, and re-render
                if (type instanceof TypePageSwitcher) {
                    MenuClickContext.set(event.getSlot(), event.getCurrentItem());
                    try {
                        type.run(viewer, cosmeticHolder, item.itemConfig(), event.getClick());
                    } finally {
                        MenuClickContext.clear();
                    }
                    int totalPages = getTotalPages();
                    if (totalPages > 1) {
                        int current = viewerPages.getOrDefault(uuid, 0);
                        viewerPages.put(uuid, (current + 1) % totalPages);
                    }
                    updateMenu(viewer, cosmeticHolder, gui);
                    return;
                }

                MenuClickContext.set(event.getSlot(), event.getCurrentItem());
                try {
                    if (type != null) {
                        type.run(viewer, cosmeticHolder, item.itemConfig(), event.getClick());
                    }
                } finally {
                    MenuClickContext.clear();
                }
                updateMenu(viewer, cosmeticHolder, gui);
            });

            MessagesUtil.sendDebugMessages("Set an item in slot " + slot + " in the menu of " + getId());
            gui.updateItem(slot, guiItem);
            break;
        }
    }

    @NotNull
    private List<Integer> getSlots(@NotNull List<String> slotString) {
        List<Integer> slots = new ArrayList<>();

        for (String a : slotString) {
            if (a.contains("-")) {
                String[] split = a.split("-");
                int min = Integer.parseInt(split[0]);
                int max = Integer.parseInt(split[1]);
                slots.addAll(getSlots(min, max));
            } else {
                slots.add(Integer.valueOf(a));
            }
        }

        return slots;
    }

    @NotNull
    private List<Integer> getSlots(int small, int max) {
        List<Integer> slots = new ArrayList<>();

        for (int i = small; i <= max; i++) slots.add(i);
        return slots;
    }

    @Contract("_, _, _, _, _, _ -> param4")
    @NotNull
    private ItemStack getMenuItem(Player viewer, CosmeticHolder cosmeticHolder, Type type, ConfigurationNode config, ItemStack itemStack, int slot) {
        ItemStack item = type.setItem(viewer, cosmeticHolder, config, itemStack, slot);
        
        // Apply glint if configured
        boolean glint = config.node("glint").getBoolean(false);
        if (glint) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(Enchantment.PROTECTION, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
        }
        
        return item;
    }

    public boolean canOpen(Player player) {
        if (permissionNode.isEmpty()) return true;
        return player.isOp() || player.hasPermission(permissionNode);
    }

    public static Comparator<MenuItem> priorityCompare = Comparator.comparing(MenuItem::priority).reversed();

    private String cc(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public int getMenuAmount(CosmeticUser user, boolean unlockedOnly) {
        int amount = 0;

        // Count cosmetics from the paginated cosmeticItems list
        for (MenuItem menuItem : cosmeticItems) {
            String cosmeticName = menuItem.itemConfig().node("cosmetic").getString("");
            Cosmetic cosmetic = Cosmetics.getCosmetic(cosmeticName);

            if (cosmetic != null) {
                if (unlockedOnly) {
                    if (user.canEquipCosmetic(cosmetic)) {
                        amount++;
                    }
                } else {
                    amount++;
                }
            }
        }

        // Also count any cosmetic items that might still be in the static items map (backward compat)
        for (List<MenuItem> menuItemList : items.values()) {
            MenuItem menuItem = menuItemList.get(0);
            if (menuItem.type() instanceof TypeCosmetic) {
                String cosmeticName = menuItem.itemConfig().node("cosmetic").getString("");
                Cosmetic cosmetic = Cosmetics.getCosmetic(cosmeticName);

                if (cosmetic != null) {
                    if (unlockedOnly) {
                        if (user.canEquipCosmetic(cosmetic)) {
                            amount++;
                        }
                    } else {
                        amount++;
                    }
                }
            }
        }

        return amount;
    }
}
