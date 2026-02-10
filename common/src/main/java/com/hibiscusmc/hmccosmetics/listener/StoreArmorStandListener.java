package com.hibiscusmc.hmccosmetics.listener;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.gui.special.StoreMenu;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class StoreArmorStandListener implements Listener {

    private static final NamespacedKey ARMORSTAND_STORE_SLOT_KEY =
            new NamespacedKey(HMCCosmeticsPlugin.getInstance(), "store_armorstand_slot"); // 10-16

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;

        PersistentDataContainer pdc = stand.getPersistentDataContainer();
        Integer slot = pdc.get(ARMORSTAND_STORE_SLOT_KEY, PersistentDataType.INTEGER);
        if (slot == null) return;

        Player player = event.getPlayer();
        CosmeticUser user = CosmeticUsers.getUser(player);
        if (user == null) return;

        StoreMenu store = Menus.getStoreMenu("store");
        if (store == null) return;

        event.setCancelled(true);
        store.openMenu(user, slot);
    }
}

