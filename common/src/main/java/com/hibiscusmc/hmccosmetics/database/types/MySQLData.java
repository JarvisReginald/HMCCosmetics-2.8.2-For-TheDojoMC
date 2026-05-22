package com.hibiscusmc.hmccosmetics.database.types;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.DatabaseSettings;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;

public class MySQLData extends SQLData {

    // Connection Information
    private String host;
    private String user;
    private String database;
    private String password;
    private int port;

    @Nullable
    private Connection connection;

    @Override
    public void setup() {
        host = DatabaseSettings.getHost();
        user = DatabaseSettings.getUsername();
        database = DatabaseSettings.getDatabase();
        password = DatabaseSettings.getPassword();
        port = DatabaseSettings.getPort();

        HMCCosmeticsPlugin plugin = HMCCosmeticsPlugin.getInstance();
        try {
            openConnection();
            if (connection == null) throw new IllegalStateException("Connection is null");
            try (PreparedStatement preparedStatement =  connection.prepareStatement("CREATE TABLE IF NOT EXISTS `COSMETICDATABASE` " +
                    "(UUID varchar(36) PRIMARY KEY, " +
                    "COSMETICS MEDIUMTEXT " +
                    ");")) {
                preparedStatement.execute();
            }
            try (PreparedStatement preparedStatement = connection.prepareStatement("CREATE TABLE IF NOT EXISTS `STORE_DAILY_PICKS` " +
                    "(`DATE` VARCHAR(10) PRIMARY KEY, " +
                    "`PICKS` TEXT NOT NULL" +
                    ");")) {
                preparedStatement.execute();
            }
        } catch (SQLException | IllegalStateException e) {
            plugin.getLogger().severe("");
            plugin.getLogger().severe("");
            plugin.getLogger().severe("MySQL DATABASE CAN NOT BE REACHED.");
            plugin.getLogger().severe("CHECK CONFIG FOR ERRORS");
            plugin.getLogger().severe("");
            plugin.getLogger().severe("SAFETY SHUTTING DOWN SERVER");
            plugin.getLogger().severe("");
            plugin.getLogger().severe("");
            Bukkit.shutdown();
            throw new RuntimeException(e);
        }
    }

    @Override
    public void clear(UUID uniqueId) {
        Bukkit.getScheduler().runTaskAsynchronously(HMCCosmeticsPlugin.getInstance(), () -> {
            try (PreparedStatement preparedSt = preparedStatement("DELETE FROM COSMETICDATABASE WHERE UUID=?;")) {
                preparedSt.setString(1, uniqueId.toString());
                preparedSt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }

    private void openConnection() throws SQLException {
        // Connection isn't null AND Connection isn't closed :: return
        try {
            if (isConnectionOpen()) return;
            if (connection != null) close(); // Close connection if still active
        } catch (RuntimeException e) {
            e.printStackTrace(); // If isConnectionOpen() throws error
        }

        // Connect to database host
        try {
            Class.forName("com.mysql.jdbc.Driver");
            connection = DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database, setupProperties());
        } catch (SQLException | ClassNotFoundException e) {
            System.out.println(e.getMessage());
        }
    }

    public void close() {
        Bukkit.getScheduler().runTaskAsynchronously(HMCCosmeticsPlugin.getInstance(), () -> {
            try {
                if (connection == null) throw new IllegalStateException("Connection is null");
                connection.close();
            } catch (SQLException | NullPointerException e) {
                System.out.println(e.getMessage());
            }
        });
    }

    @NotNull
    private Properties setupProperties() {
        Properties props = new Properties();
        props.setProperty("user", user);
        props.setProperty("password", password);
        return props;
    }

    private boolean isConnectionOpen() {
        try {
            return connection != null && !connection.isClosed() && connection.isValid(2);
        } catch (SQLException e) {
            return false;
        }
    }

    /**
     * Returns the stored cosmetic IDs for the given date, or null if not yet written.
     * Safe to call on any thread.
     */
    @Nullable
    public List<String> loadDailyPicks(String date) {
        try (PreparedStatement ps = preparedStatement("SELECT `PICKS` FROM `STORE_DAILY_PICKS` WHERE `DATE` = ?;")) {
            if (ps == null) return null;
            ps.setString(1, date);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String raw = rs.getString("PICKS");
                    if (raw == null || raw.isBlank()) return Collections.emptyList();
                    return Arrays.asList(raw.split(",", -1));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Deletes the daily picks row for the given date, allowing a fresh recompute.
     * Used when stored picks contain cosmetics that are no longer eligible.
     */
    public void deleteDailyPicks(String date) {
        try (PreparedStatement ps = preparedStatement(
                "DELETE FROM `STORE_DAILY_PICKS` WHERE `DATE` = ?;")) {
            if (ps == null) return;
            ps.setString(1, date);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Writes daily picks for the given date using INSERT IGNORE so the first server
     * to write wins and subsequent servers' writes are safely discarded.
     */
    public void saveDailyPicks(String date, List<String> pickedIds) {
        String picks = String.join(",", pickedIds);
        try (PreparedStatement ps = preparedStatement(
                "INSERT IGNORE INTO `STORE_DAILY_PICKS` (`DATE`, `PICKS`) VALUES (?, ?);")) {
            if (ps == null) return;
            ps.setString(1, date);
            ps.setString(2, picks);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public PreparedStatement preparedStatement(String query) {
        PreparedStatement ps = null;

        if (!isConnectionOpen()) {
            MessagesUtil.sendDebugMessages("The MySQL database connection is not open (Could the database been idle for to long?). Reconnecting...", Level.WARNING);
            try {
                openConnection();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }

        try {
            if (connection == null) throw new IllegalStateException("Connection is null");
            ps = connection.prepareStatement(query);
        } catch (SQLException | IllegalStateException e) {
            e.printStackTrace();
        }

        return ps;
    }
}