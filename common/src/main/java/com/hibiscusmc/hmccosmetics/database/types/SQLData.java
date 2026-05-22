package com.hibiscusmc.hmccosmetics.database.types;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.database.UserData;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import org.bukkit.Bukkit;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public abstract class SQLData extends Data {
    @Override
    @SuppressWarnings({"resource"}) // Duplicate is from deprecated InternalData
    public CompletableFuture<UserData> get(UUID uniqueId) {
        return CompletableFuture.supplyAsync(() -> {
            UserData data = new UserData(uniqueId);

            try (PreparedStatement preparedStatement = preparedStatement("SELECT * FROM COSMETICDATABASE WHERE UUID = ?;")){
                preparedStatement.setString(1, uniqueId.toString());
                try (ResultSet rs = preparedStatement.executeQuery()) {
                    if (rs.next()) {
                        String rawData = rs.getString("COSMETICS");

                        Data.ParsedData parsed = deserializeAll(rawData);

                        data.setCosmetics(parsed.cosmetics);
                        data.setSkins(parsed.skins);
                        data.setHiddenReasons(parsed.hiddenReasons);
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return data;
        });
    }

    @Override
    @SuppressWarnings("resource")
    public void save(CosmeticUser user) {
        save(user, false);
    }

    @Override
    public void saveSync(CosmeticUser user) {
        save(user, true);
    }

    private void save(CosmeticUser user, boolean sync) {
        Runnable run = () -> {
            try (PreparedStatement preparedSt = preparedStatement("REPLACE INTO COSMETICDATABASE(UUID,COSMETICS) VALUES(?,?);")) {
                preparedSt.setString(1, user.getUniqueId().toString());
                preparedSt.setString(2, serializeData(user));
                preparedSt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        };
        if (sync || HMCCosmeticsPlugin.getInstance().isDisabled()) {
            run.run();
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(HMCCosmeticsPlugin.getInstance(), run);
        }
    }

    public abstract PreparedStatement preparedStatement(String query);
}
