package com.hibiscusmc.hmccosmetics.gui.type.types;

import com.hibiscusmc.hmccosmetics.gui.action.Actions;
import com.hibiscusmc.hmccosmetics.gui.type.Type;
import com.hibiscusmc.hmccosmetics.gui.util.MenuItemFormatUtil;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import me.lojosho.shaded.configurate.ConfigurationNode;
import me.lojosho.shaded.configurate.serialize.SerializationException;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class TypePermissionReq extends Type {

    public TypePermissionReq() {
        super("permission_req");
    }

    @Override
    public void run(CosmeticUser user, ConfigurationNode config, ClickType clickType) {
        Player viewer = user.getPlayer();
        if (viewer == null) return;

        String permission = config.node("permission").getString("");
        boolean hasPermission = permission.isBlank() || viewer.hasPermission(permission);
        ConfigurationNode branch = hasPermission ? config.node("has_permission") : config.node("no_permission");

        List<String> actionStrings = new ArrayList<>();
        ConfigurationNode actionConfig = branch.node("actions");

        try {
            if (!actionConfig.node("any").virtual())
                actionStrings.addAll(actionConfig.node("any").getList(String.class));

            if (clickType != null) {
                if (clickType.isLeftClick()) {
                    if (!actionConfig.node("left-click").virtual())
                        actionStrings.addAll(actionConfig.node("left-click").getList(String.class));
                }
                if (clickType.isRightClick()) {
                    if (!actionConfig.node("right-click").virtual())
                        actionStrings.addAll(actionConfig.node("right-click").getList(String.class));
                }
                if (clickType.equals(ClickType.SHIFT_LEFT)) {
                    if (!actionConfig.node("shift-left-click").virtual())
                        actionStrings.addAll(actionConfig.node("shift-left-click").getList(String.class));
                }
                if (clickType.equals(ClickType.SHIFT_RIGHT)) {
                    if (!actionConfig.node("shift-right-click").virtual())
                        actionStrings.addAll(actionConfig.node("shift-right-click").getList(String.class));
                }
            }

            Actions.runActions(viewer, user, actionStrings);
        } catch (SerializationException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ItemStack setItem(CosmeticUser user, ConfigurationNode config, ItemStack itemStack, int slot) {
        Player viewer = user.getPlayer();
        if (viewer == null) return itemStack;

        String permission = config.node("permission").getString("");
        boolean hasPermission = permission.isBlank() || viewer.hasPermission(permission);
        ConfigurationNode branch = hasPermission ? config.node("has_permission") : config.node("no_permission");

        if (branch.virtual()) return itemStack;

        ItemStack built = MenuItemFormatUtil.buildItem(viewer, branch);
        return (built == null || built.getType().isAir()) ? itemStack : built;
    }
}
