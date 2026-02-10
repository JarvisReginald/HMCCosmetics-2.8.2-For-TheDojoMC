package com.hibiscusmc.hmccosmetics;

import com.hibiscusmc.hmccosmetics.api.events.HMCCosmeticSetupEvent;
import com.hibiscusmc.hmccosmetics.command.CosmeticCommand;
import com.hibiscusmc.hmccosmetics.command.CosmeticCommandTabComplete;
import com.hibiscusmc.hmccosmetics.config.DatabaseSettings;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.config.WardrobeSettings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetics;
import com.hibiscusmc.hmccosmetics.cosmetic.rarity.Rarities;
import com.hibiscusmc.hmccosmetics.cosmetic.retexture.RetextureGroupLoader;
import com.hibiscusmc.hmccosmetics.database.Database;
import com.hibiscusmc.hmccosmetics.database.OutfitsStorage;
import com.hibiscusmc.hmccosmetics.gui.Menu;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.gui.special.DyeMenuProvider;
import com.hibiscusmc.hmccosmetics.gui.special.StoreMenu;
import com.hibiscusmc.hmccosmetics.gui.special.impl.HMCColorDyeMenu;
import com.hibiscusmc.hmccosmetics.hooks.items.HookHMCCosmetics;
import com.hibiscusmc.hmccosmetics.hooks.misc.HookBetterHud;
import com.hibiscusmc.hmccosmetics.hooks.placeholders.HMCPlaceholderExpansion;
import com.hibiscusmc.hmccosmetics.hooks.worldguard.WGHook;
import com.hibiscusmc.hmccosmetics.hooks.worldguard.WGListener;
import com.hibiscusmc.hmccosmetics.listener.*;
import com.hibiscusmc.hmccosmetics.packets.CosmeticPacketInterface;
import com.hibiscusmc.hmccosmetics.packets.SkinProtocolLibListener;
import com.hibiscusmc.hmccosmetics.store.StoreArmorStandManager;
import com.hibiscusmc.hmccosmetics.store.StoreArmorStandPersistListener;
import com.hibiscusmc.hmccosmetics.store.StoreArmorStandStorage;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.PlayerSearchManager;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.TranslationUtil;
import dev.esophose.playerparticles.api.PlayerParticlesAPI;
import lombok.Getter;
import me.lojosho.hibiscuscommons.HibiscusCommonsPlugin;
import me.lojosho.hibiscuscommons.HibiscusPlugin;
import me.lojosho.hibiscuscommons.config.serializer.ItemSerializer;
import me.lojosho.hibiscuscommons.config.serializer.LocationSerializer;
import me.lojosho.hibiscuscommons.hooks.Hooks;
import me.lojosho.shaded.configupdater.common.config.CommentedConfiguration;
import me.lojosho.shaded.configurate.ConfigurateException;
import me.lojosho.shaded.configurate.ConfigurationOptions;
import me.lojosho.shaded.configurate.yaml.NodeStyle;
import me.lojosho.shaded.configurate.yaml.YamlConfigurationLoader;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;

import java.io.File;
import java.nio.file.Path;

import static com.hibiscusmc.hmccosmetics.store.StoreArmorStandManager.initDailyRefreshScheduler;

public final class HMCCosmeticsPlugin extends HibiscusPlugin {

    private static HMCCosmeticsPlugin instance;
    private static YamlConfigurationLoader configLoader;
    private OutfitsStorage outfitsStorage;

    private PlayerParticlesAPI ppAPI;
    private SkinProtocolLibListener skinProtocolLibListener;

    @Getter
    private PlayerSearchManager playerSearchManager;

    public HMCCosmeticsPlugin() {
        super(13873, 1879);
        new HookHMCCosmetics();
        new HookBetterHud();
    }

    @Override
    public void onStart() {
        // Plugin startup logic
        instance = this;

        // Search Service
        this.playerSearchManager = new PlayerSearchManager(this);

        // File setup
        saveDefaultConfig();
        if (!Path.of(getDataFolder().getPath(), "messages.yml").toFile().exists()) saveResource("messages.yml", false);
        if (!Path.of(getDataFolder().getPath(), "translations.yml").toFile().exists()) saveResource("translations.yml", false);
        if (!Path.of(getDataFolder().getPath() + "/cosmetics/").toFile().exists()) saveResource("cosmetics/defaultcosmetics.yml", false);
        if (!Path.of(getDataFolder().getPath() + "/menus/").toFile().exists()) {
            saveResource("menus/defaultmenu_hats.yml", false);
            saveResource("menus/defaultmenu_balloons.yml", false);
            saveResource("menus/defaultmenu_hands.yml", false);
            saveResource("menus/defaultmenu_backpacks.yml", false);
        }


        // Configuration Sync
        final File configFile = Path.of(getInstance().getDataFolder().getPath(), "config.yml").toFile();
        final File messageFile = Path.of(getInstance().getDataFolder().getPath(), "messages.yml").toFile();
        final File translationFile = Path.of(getInstance().getDataFolder().getPath(), "translations.yml").toFile();
        outfitsStorage = new OutfitsStorage();
        try {
            CommentedConfiguration.loadConfiguration(configFile).syncWithConfig(configFile, getInstance().getResource("config.yml"),
                    "database-settings", "wardrobe.wardrobes", "debug-mode", "wardrobe.viewer-location", "wardrobe.npc-location", "wardrobe.wardrobe-location", "wardrobe.leave-location");
            CommentedConfiguration.loadConfiguration(messageFile).syncWithConfig(messageFile, getInstance().getResource("messages.yml"));
            CommentedConfiguration.loadConfiguration(translationFile).syncWithConfig(translationFile, getInstance().getResource("translations.yml"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        outfitsStorage.load();

        // Move this over to Hibiscus Commons later
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) new HMCPlaceholderExpansion().register();

        // Setup
        setup();
        setPacketInterface(new CosmeticPacketInterface());
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") != null) {
            skinProtocolLibListener = new SkinProtocolLibListener(this);
        }

        // Commands
        getServer().getPluginCommand("cosmetic").setExecutor(new CosmeticCommand());
        getServer().getPluginCommand("cosmetic").setTabCompleter(new CosmeticCommandTabComplete());

        // Listener
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerGameListener(), this);
        getServer().getPluginManager().registerEvents(new ServerListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerMovementListener(), this);
        getServer().getPluginManager().registerEvents(new StoreArmorStandListener(), this);
        Bukkit.getPluginManager().registerEvents(new StoreArmorStandChunkListener(), this);
        getServer().getPluginManager().registerEvents(playerSearchManager, this);

        if (HibiscusCommonsPlugin.isOnPaper()) {
            getServer().getPluginManager().registerEvents(new PaperPlayerGameListener(), this);
        }
        // Database
        new Database();

        // WorldGuard
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null && Settings.isWorldGuardMoveCheck()) {
            getServer().getPluginManager().registerEvents(new WGListener(), this);
        }

        // HMCColor
        if (Hooks.isActiveHook("HMCColor")) {
            try {
                DyeMenuProvider.setDyeMenuProvider(new HMCColorDyeMenu());
            } catch (IllegalStateException e) {
                getLogger().warning("Unable to set HMCColor as the dye menu. There is likely another plugin registering another dye menu.");
            }
        }

        Bukkit.getPluginManager().registerEvents(new StoreArmorStandChunkListener(), this);
        Bukkit.getPluginManager().registerEvents(new StoreArmorStandListener(), this);
        Bukkit.getPluginManager().registerEvents(new StoreArmorStandListener(), this);
        StoreArmorStandManager.initDailyRefreshScheduler();
        StoreArmorStandManager.initPersistentArmorStandSupport();

        StoreArmorStandStorage.init();
        Bukkit.getPluginManager().registerEvents(new StoreArmorStandPersistListener(), this);

        Bukkit.getScheduler().runTask(this, () -> {
            StoreMenu store = Menus.getStoreMenu("store");
            if (store != null) store.refreshDailyCosmetics();
            CosmeticCommand.refreshAllStoreArmorStands();
        });

        if (Bukkit.getPluginManager().isPluginEnabled("PlayerParticles")) {
            this.ppAPI = PlayerParticlesAPI.getInstance();
        }
    }

    @Override
    public void onLoad() {
        // WorldGuard
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            new WGHook();
        }

        // ItemsAdder
        if (getServer().getPluginManager().isPluginEnabled("ItemsAdder")) {
            com.hibiscusmc.hmccosmetics.gui.special.StoreMenu.setItemsAdderReady(false);
            getServer().getPluginManager().registerEvents(new com.hibiscusmc.hmccosmetics.hooks.ItemsAdderListener(), this);
        } else {
            com.hibiscusmc.hmccosmetics.gui.special.StoreMenu.setItemsAdderReady(true);
        }
    }

    @Override
    public void onEnd() {
        // Plugin shutdown logic
        // Save any remaining users from CosmeticUsers map.
        // During restart, PlayerQuitEvent fires and saves users synchronously, removing them from the map.
        // During normal stop, players may stay connected, so PlayerQuitEvent doesn't fire,
        // and we need to save them here as a fallback.
        int remainingUsers = CosmeticUsers.values().size();
        if (remainingUsers > 0) {
            getLogger().info("[HMCCosmetics] Saving cosmetics for " + remainingUsers + " remaining users during shutdown...");
            for (CosmeticUser user : CosmeticUsers.values()) {
                if (user == null) continue;
                if (user.isInWardrobe()) {
                    user.leaveWardrobe(true);
                }
                Database.saveSync(user);
            }
            getLogger().info("[HMCCosmetics] Finished saving cosmetics during shutdown");
        }

        if (outfitsStorage != null) {
            outfitsStorage.save();
        }
    }

    public static HMCCosmeticsPlugin getInstance() {
        return instance;
    }

    public static void setup() {
        getInstance().reloadConfig();

        // Configuration setup
        final File file = Path.of(getInstance().getDataFolder().getPath(), "config.yml").toFile();
        final YamlConfigurationLoader loader = YamlConfigurationLoader.
                builder().
                path(file.toPath()).
                defaultOptions(opts ->
                        opts.serializers(build -> {
                            build.register(Location.class, LocationSerializer.INSTANCE);
                            build.register(ItemStack.class, ItemSerializer.INSTANCE);
                        }))
                .nodeStyle(NodeStyle.BLOCK)
                .build();
        try {
            var root = loader.load(ConfigurationOptions.defaults());

            Settings.load(root);
            Rarities.load(root.node("rarities"));
            WardrobeSettings.load(root.node("wardrobe"));
            DatabaseSettings.load(root.node("database-settings"));

            RetextureGroupLoader.load(root);

            configLoader = loader;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        // Messages setup
        final File messagesFile = Path.of(getInstance().getDataFolder().getPath(), "messages.yml").toFile();
        final YamlConfigurationLoader messagesLoader = YamlConfigurationLoader.
                builder().
                path(messagesFile.toPath()).
                defaultOptions(opts ->
                        opts.serializers(build -> {
                            build.register(Location.class, LocationSerializer.INSTANCE);
                            build.register(ItemStack.class, ItemSerializer.INSTANCE);
                        }))
                .nodeStyle(NodeStyle.BLOCK)
                .build();
        try {
            MessagesUtil.setup(messagesLoader.load());
        } catch (ConfigurateException e) {
            throw new RuntimeException(e);
        }

        // Translation setup
        final File translationFile = Path.of(getInstance().getDataFolder().getPath(), "translations.yml").toFile();
        final YamlConfigurationLoader translationLoader = YamlConfigurationLoader.
                builder().
                path(translationFile.toPath())
                .nodeStyle(NodeStyle.BLOCK)
                .build();
        try {
            TranslationUtil.setup(translationLoader.load());
        } catch (ConfigurateException e) {
            throw new RuntimeException(e);
        }

        // Cosmetics setup
        Cosmetics.setup();

        // Menus setup
        Menus.setup();

        // For reloads
        /*
        for (Player player : Bukkit.getOnlinePlayers()) {
            CosmeticUser user = CosmeticUsers.getUser(player.getUniqueId());
            if (user == null) continue;
            for (Cosmetic cosmetic : user.getCosmetic()) {
                Color color = user.getCosmeticColor(cosmetic.getSlot());
                Cosmetic newCosmetic = Cosmetics.getCosmetic(cosmetic.getId());
                user.removeCosmeticSlot(cosmetic);

                if (newCosmetic == null) continue;
                user.addPlayerCosmetic(newCosmetic, color);
            }
            user.updateCosmetic();
        }
         */
        for (Cosmetic cosmetic : Cosmetics.values()) {
            if (cosmetic.getPermission() != null) {
                if (getInstance().getServer().getPluginManager().getPermission(cosmetic.getPermission()) != null) continue;
                getInstance().getServer().getPluginManager().addPermission(new Permission(cosmetic.getPermission()));
            }
        }
        for (Menu menu : Menus.values()) {
            if (menu.getPermissionNode() != null) {
                if (getInstance().getServer().getPluginManager().getPermission(menu.getPermissionNode()) != null) continue;
                getInstance().getServer().getPluginManager().addPermission(new Permission(menu.getPermissionNode()));
            }
        }

        getInstance().getLogger().info("Successfully Enabled HMCCosmetics");
        getInstance().getLogger().info(Cosmetics.values().size() + " Cosmetics Successfully Setup");
        getInstance().getLogger().info(Menus.getMenuNames().size() + " Menus Successfully Setup");
        getInstance().getLogger().info(WardrobeSettings.getWardrobes().size() + " Wardrobes Successfully Setup");
        getInstance().getLogger().info("Data storage is set to " + DatabaseSettings.getDatabaseType());

        Bukkit.getPluginManager().callEvent(new HMCCosmeticSetupEvent());
    }

    public OutfitsStorage getOutfitsStorage() {
        return outfitsStorage;
    }

    public PlayerParticlesAPI getPpAPI() {
        return ppAPI;
    }

    public SkinProtocolLibListener getSkinProtocolLibListener() { return skinProtocolLibListener; }
}
