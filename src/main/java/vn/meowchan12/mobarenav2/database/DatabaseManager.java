package vn.meowchan12.mobarenav2.database;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.shop.model.PlayerShopData;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * High-performance database manager for MobArenav2 v8.0.0.
 * Supports SQLite (default, zero configuration) and MySQL/MariaDB (multi-server networks).
 * Powered by standard JDBC connection management with automatic legacy YAML data migration.
 */
public class DatabaseManager {

    private final MobArenav2 plugin;
    private String databaseType = "SQLITE";
    private String jdbcUrl;
    private String jdbcUser;
    private String jdbcPass;
    private boolean initialized = false;

    // Memory cache for active player stats
    private final Map<UUID, CachedStats> statsCache = new ConcurrentHashMap<>();

    public record CachedStats(
            int kills,
            int wins,
            int gamesPlayed,
            int highestWave,
            String lastClass,
            int bossKills,
            double damageDealt,
            double damageTaken,
            long totalPlaytimeSeconds,
            long fastestClearSeconds
    ) {}

    public DatabaseManager(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    public void init() {
        try {
            var config = plugin.getConfigManager().getSettings();
            this.databaseType = config.getString("database.type", "SQLITE").toUpperCase();

            if (databaseType.equals("MYSQL") || databaseType.equals("MARIADB")) {
                String host = config.getString("database.mysql.host", "localhost");
                int port = config.getInt("database.mysql.port", 3306);
                String database = config.getString("database.mysql.database", "mobarena");
                this.jdbcUser = config.getString("database.mysql.username", "root");
                this.jdbcPass = config.getString("database.mysql.password", "");
                boolean ssl = config.getBoolean("database.mysql.ssl", false);

                this.jdbcUrl = "jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=" + ssl + "&characterEncoding=utf8&autoReconnect=true";
            } else {
                this.databaseType = "SQLITE";
                if (!plugin.getDataFolder().exists()) {
                    plugin.getDataFolder().mkdirs();
                }
                File dbFile = new File(plugin.getDataFolder(), "mobarena.db");
                this.jdbcUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            }

            this.initialized = true;
            createTables();
            plugin.getLogger().info("[MobArenav2] Database connected successfully using " + databaseType + "!");

            // Run asynchronous auto-migration of old flatfile data
            CompletableFuture.runAsync(this::autoMigrateLegacyData);

        } catch (Exception ex) {
            this.initialized = false;
            plugin.getLogger().log(Level.SEVERE, "[MobArenav2] Failed to initialize database: " + ex.getMessage(), ex);
        }
    }

    public Connection getConnection() throws SQLException {
        if (!initialized || jdbcUrl == null) return null;
        if (databaseType.equals("SQLITE")) {
            return DriverManager.getConnection(jdbcUrl);
        } else {
            return DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPass);
        }
    }

    private void createTables() throws SQLException {
        try (Connection conn = getConnection()) {
            if (conn == null) return;

            // [v8.2.0]: Enable SQLite WAL mode (Write-Ahead Logging) for concurrent lock-free queries
            if (databaseType.equals("SQLITE")) {
                try (Statement pragmaStmt = conn.createStatement()) {
                    pragmaStmt.execute("PRAGMA journal_mode = WAL;");
                    pragmaStmt.execute("PRAGMA synchronous = NORMAL;");
                } catch (SQLException ignored) {}
            }

            try (Statement stmt = conn.createStatement()) {
                // Player overall stats
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ma_player_stats (
                        uuid VARCHAR(36) PRIMARY KEY,
                        player_name VARCHAR(64),
                        kills INT DEFAULT 0,
                        wins INT DEFAULT 0,
                        games_played INT DEFAULT 0,
                        highest_wave INT DEFAULT 0,
                        last_class VARCHAR(64) DEFAULT 'None',
                        boss_kills INT DEFAULT 0,
                        damage_dealt DOUBLE DEFAULT 0.0,
                        damage_taken DOUBLE DEFAULT 0.0,
                        total_playtime_seconds BIGINT DEFAULT 0,
                        fastest_clear_seconds BIGINT DEFAULT 0,
                        last_played BIGINT DEFAULT 0
                    );
                """);

                // [v8.2.0]: Automatic non-destructive schema migration for existing tables
                try {
                    stmt.execute("ALTER TABLE ma_player_stats ADD COLUMN total_playtime_seconds BIGINT DEFAULT 0;");
                } catch (SQLException ignored) {}
                try {
                    stmt.execute("ALTER TABLE ma_player_stats ADD COLUMN fastest_clear_seconds BIGINT DEFAULT 0;");
                } catch (SQLException ignored) {}
                try {
                    stmt.execute("ALTER TABLE ma_arena_records ADD COLUMN duration_seconds BIGINT DEFAULT 0;");
                } catch (SQLException ignored) {}

                // Player shop & ultimate data
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ma_player_shop (
                        uuid VARCHAR(36) PRIMARY KEY,
                        player_name VARCHAR(64),
                        levels_json TEXT,
                        equipped_ultimate VARCHAR(64) DEFAULT NULL,
                        reroll_tickets INT DEFAULT 0,
                        updated_at BIGINT DEFAULT 0
                    );
                """);

                // Arena match records
                String recordsTable = databaseType.equals("SQLITE") ?
                    """
                    CREATE TABLE IF NOT EXISTS ma_arena_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        arena_name VARCHAR(64),
                        wave INT DEFAULT 0,
                        is_endless BOOLEAN DEFAULT 0,
                        top_player VARCHAR(64),
                        top_kills INT DEFAULT 0,
                        top_class VARCHAR(64),
                        duration_seconds BIGINT DEFAULT 0,
                        recorded_at BIGINT DEFAULT 0
                    );
                    """ :
                    """
                    CREATE TABLE IF NOT EXISTS ma_arena_records (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        arena_name VARCHAR(64),
                        wave INT DEFAULT 0,
                        is_endless BOOLEAN DEFAULT 0,
                        top_player VARCHAR(64),
                        top_kills INT DEFAULT 0,
                        top_class VARCHAR(64),
                        duration_seconds BIGINT DEFAULT 0,
                        recorded_at BIGINT DEFAULT 0
                    );
                    """;
                stmt.executeUpdate(recordsTable);
            }
        }
    }

    public CompletableFuture<CachedStats> loadPlayerStats(UUID uuid) {
        if (statsCache.containsKey(uuid)) {
            return CompletableFuture.completedFuture(statsCache.get(uuid));
        }

        return CompletableFuture.supplyAsync(() -> {
            if (!initialized) return null;
            String sql = "SELECT * FROM ma_player_stats WHERE uuid = ?";
            try (Connection conn = getConnection()) {
                if (conn == null) return null;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            long totalPlaytime = 0;
                            long fastestClear = 0;
                            try { totalPlaytime = rs.getLong("total_playtime_seconds"); } catch (SQLException ignored) {}
                            try { fastestClear = rs.getLong("fastest_clear_seconds"); } catch (SQLException ignored) {}

                            CachedStats stats = new CachedStats(
                                    rs.getInt("kills"),
                                    rs.getInt("wins"),
                                    rs.getInt("games_played"),
                                    rs.getInt("highest_wave"),
                                    rs.getString("last_class"),
                                    rs.getInt("boss_kills"),
                                    rs.getDouble("damage_dealt"),
                                    rs.getDouble("damage_taken"),
                                    totalPlaytime,
                                    fastestClear
                            );
                            statsCache.put(uuid, stats);
                            return stats;
                        }
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("[MobArenav2] Error loading stats for " + uuid + ": " + ex.getMessage());
            }
            return new CachedStats(0, 0, 0, 0, "None", 0, 0.0, 0.0, 0L, 0L);
        });
    }

    public void savePlayerStatsAsync(UUID uuid, String name, int kills, boolean isWin, int wave, String className, int bossKills, double damageDealt, double damageTaken) {
        savePlayerStatsAsync(uuid, name, kills, isWin, wave, className, bossKills, damageDealt, damageTaken, 0L);
    }

    public void savePlayerStatsAsync(UUID uuid, String name, int kills, boolean isWin, int wave, String className, int bossKills, double damageDealt, double damageTaken, long durationSeconds) {
        CompletableFuture.runAsync(() -> {
            if (!initialized) return;
            CachedStats current = statsCache.getOrDefault(uuid, new CachedStats(0, 0, 0, 0, "None", 0, 0.0, 0.0, 0L, 0L));

            int newKills = current.kills() + kills;
            int newWins = current.wins() + (isWin ? 1 : 0);
            int newGames = current.gamesPlayed() + 1;
            int newHighestWave = Math.max(current.highestWave(), wave);
            int newBossKills = current.bossKills() + bossKills;
            double newDmgDealt = current.damageDealt() + damageDealt;
            double newDmgTaken = current.damageTaken() + damageTaken;
            String newClass = className != null ? className : current.lastClass();

            long newTotalPlaytime = current.totalPlaytimeSeconds() + Math.max(0, durationSeconds);
            long newFastestClear = current.fastestClearSeconds();
            if (isWin && durationSeconds > 0) {
                newFastestClear = (newFastestClear <= 0) ? durationSeconds : Math.min(newFastestClear, durationSeconds);
            }

            CachedStats updated = new CachedStats(newKills, newWins, newGames, newHighestWave, newClass, newBossKills, newDmgDealt, newDmgTaken, newTotalPlaytime, newFastestClear);
            statsCache.put(uuid, updated);

            String sql = databaseType.equals("SQLITE") ?
                    """
                    INSERT INTO ma_player_stats (uuid, player_name, kills, wins, games_played, highest_wave, last_class, boss_kills, damage_dealt, damage_taken, total_playtime_seconds, fastest_clear_seconds, last_played)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        player_name = excluded.player_name,
                        kills = excluded.kills,
                        wins = excluded.wins,
                        games_played = excluded.games_played,
                        highest_wave = excluded.highest_wave,
                        last_class = excluded.last_class,
                        boss_kills = excluded.boss_kills,
                        damage_dealt = excluded.damage_dealt,
                        damage_taken = excluded.damage_taken,
                        total_playtime_seconds = excluded.total_playtime_seconds,
                        fastest_clear_seconds = excluded.fastest_clear_seconds,
                        last_played = excluded.last_played
                    """ :
                    """
                    INSERT INTO ma_player_stats (uuid, player_name, kills, wins, games_played, highest_wave, last_class, boss_kills, damage_dealt, damage_taken, total_playtime_seconds, fastest_clear_seconds, last_played)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        player_name = VALUES(player_name),
                        kills = VALUES(kills),
                        wins = VALUES(wins),
                        games_played = VALUES(games_played),
                        highest_wave = VALUES(highest_wave),
                        last_class = VALUES(last_class),
                        boss_kills = VALUES(boss_kills),
                        damage_dealt = VALUES(damage_dealt),
                        damage_taken = VALUES(damage_taken),
                        total_playtime_seconds = VALUES(total_playtime_seconds),
                        fastest_clear_seconds = VALUES(fastest_clear_seconds),
                        last_played = VALUES(last_played)
                    """;

            try (Connection conn = getConnection()) {
                if (conn == null) return;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    ps.setString(2, name);
                    ps.setInt(3, newKills);
                    ps.setInt(4, newWins);
                    ps.setInt(5, newGames);
                    ps.setInt(6, newHighestWave);
                    ps.setString(7, newClass);
                    ps.setInt(8, newBossKills);
                    ps.setDouble(9, newDmgDealt);
                    ps.setDouble(10, newDmgTaken);
                    ps.setLong(11, newTotalPlaytime);
                    ps.setLong(12, newFastestClear);
                    ps.setLong(13, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("[MobArenav2] Error saving stats for " + uuid + ": " + ex.getMessage());
            }
        });
    }

    public CompletableFuture<PlayerShopData> loadPlayerShopData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            PlayerShopData data = new PlayerShopData(uuid);
            if (!initialized) return data;

            String sql = "SELECT * FROM ma_player_shop WHERE uuid = ?";
            try (Connection conn = getConnection()) {
                if (conn == null) return data;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            data.setEquippedUltimate(rs.getString("equipped_ultimate"));
                            data.setRerollTickets(rs.getInt("reroll_tickets"));
                            String levelsJson = rs.getString("levels_json");
                            if (levelsJson != null && !levelsJson.isEmpty()) {
                                // Format: item:lvl,item2:lvl
                                String[] pairs = levelsJson.split(",");
                                for (String pair : pairs) {
                                    String[] kv = pair.split(":");
                                    if (kv.length == 2) {
                                        try {
                                            data.setItemLevel(kv[0], Integer.parseInt(kv[1]));
                                        } catch (NumberFormatException ignored) {}
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("[MobArenav2] Error loading shop data for " + uuid + ": " + ex.getMessage());
            }
            return data;
        });
    }

    public void savePlayerShopDataAsync(PlayerShopData data) {
        CompletableFuture.runAsync(() -> {
            if (!initialized || data == null) return;

            StringBuilder sb = new StringBuilder();
            data.getPurchasedItemLevels().forEach((id, lvl) -> {
                if (!sb.isEmpty()) sb.append(",");
                sb.append(id).append(":").append(lvl);
            });
            String levelsJson = sb.toString();

            String name = Bukkit.getOfflinePlayer(data.getUuid()).getName();
            if (name == null) name = "Unknown";

            String sql = databaseType.equals("SQLITE") ?
                    """
                    INSERT INTO ma_player_shop (uuid, player_name, levels_json, equipped_ultimate, reroll_tickets, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(uuid) DO UPDATE SET
                        player_name = excluded.player_name,
                        levels_json = excluded.levels_json,
                        equipped_ultimate = excluded.equipped_ultimate,
                        reroll_tickets = excluded.reroll_tickets,
                        updated_at = excluded.updated_at
                    """ :
                    """
                    INSERT INTO ma_player_shop (uuid, player_name, levels_json, equipped_ultimate, reroll_tickets, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        player_name = VALUES(player_name),
                        levels_json = VALUES(levels_json),
                        equipped_ultimate = VALUES(equipped_ultimate),
                        reroll_tickets = VALUES(reroll_tickets),
                        updated_at = VALUES(updated_at)
                    """;

            try (Connection conn = getConnection()) {
                if (conn == null) return;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, data.getUuid().toString());
                    ps.setString(2, name);
                    ps.setString(3, levelsJson);
                    ps.setString(4, data.getEquippedUltimate());
                    ps.setInt(5, data.getRerollTickets());
                    ps.setLong(6, System.currentTimeMillis());
                    ps.executeUpdate();
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("[MobArenav2] Error saving shop data for " + data.getUuid() + ": " + ex.getMessage());
            }
        });
    }

    public record LeaderboardEntry(String playerName, int kills, int highestWave, String lastClass) {}

    public CompletableFuture<List<LeaderboardEntry>> getTopPlayers(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            List<LeaderboardEntry> list = new ArrayList<>();
            if (!initialized) return list;

            String sql = "SELECT player_name, kills, highest_wave, last_class FROM ma_player_stats ORDER BY highest_wave DESC, kills DESC LIMIT ?";
            try (Connection conn = getConnection()) {
                if (conn == null) return list;
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setInt(1, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(new LeaderboardEntry(
                                    rs.getString("player_name"),
                                    rs.getInt("kills"),
                                    rs.getInt("highest_wave"),
                                    rs.getString("last_class")
                            ));
                        }
                    }
                }
            } catch (SQLException ex) {
                plugin.getLogger().warning("[MobArenav2] Error fetching top players: " + ex.getMessage());
            }
            return list;
        });
    }

    private void autoMigrateLegacyData() {
        File userDir = new File(plugin.getDataFolder(), "userdata");
        if (userDir.exists() && userDir.isDirectory()) {
            File[] files = userDir.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files != null && files.length > 0) {
                int migratedStats = 0;
                int migratedShops = 0;
                for (File f : files) {
                    try {
                        String fileName = f.getName().replace(".yml", "");
                        if (fileName.startsWith("shop_")) {
                            // Migrate shop data
                            String uuidStr = fileName.substring(5);
                            UUID uuid = UUID.fromString(uuidStr);
                            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
                            PlayerShopData sData = new PlayerShopData(uuid);
                            org.bukkit.configuration.ConfigurationSection levelsSec = yaml.getConfigurationSection("levels");
                            if (levelsSec != null) {
                                for (String key : levelsSec.getKeys(false)) {
                                    sData.setItemLevel(key, levelsSec.getInt(key, 0));
                                }
                            }
                            sData.setRerollTickets(yaml.getInt("tickets.reroll", 0));
                            sData.setEquippedUltimate(yaml.getString("equipped_ultimate", null));
                            savePlayerShopDataAsync(sData);
                            migratedShops++;
                        } else {
                            // Migrate user stats
                            UUID uuid = UUID.fromString(fileName);
                            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(f);
                            String name = yaml.getString("identity.username", yaml.getString("name", "Unknown"));
                            int kills = yaml.getInt("career_statistics.lifetime_kills", yaml.getInt("stats.lifetime_kills", yaml.getInt("kills", 0)));
                            int wins = yaml.getInt("career_statistics.games_won", yaml.getInt("stats.games_won", yaml.getInt("wins", 0)));
                            int games = yaml.getInt("career_statistics.games_played", yaml.getInt("stats.games_played", yaml.getInt("games-played", 0)));
                            int wave = yaml.getInt("career_statistics.highest_wave", yaml.getInt("stats.highest_wave", yaml.getInt("highest-wave", 0)));
                            String lastClass = yaml.getString("class_preferences.favorite_class", yaml.getString("last-class", "None"));
                            int bossKills = yaml.getInt("career_statistics.boss_kills", yaml.getInt("boss-kills", 0));
                            double dmgDealt = yaml.getDouble("career_statistics.total_damage_dealt", yaml.getDouble("damage-dealt", 0.0));
                            double dmgTaken = yaml.getDouble("career_statistics.total_damage_taken", yaml.getDouble("damage-taken", 0.0));

                            savePlayerStatsAsync(uuid, name, kills, wins > 0, wave, lastClass, bossKills, dmgDealt, dmgTaken);
                            migratedStats++;
                        }
                    } catch (Exception ignored) {}
                }
                if (migratedStats > 0 || migratedShops > 0) {
                    plugin.getLogger().info("[MobArenav2] Successfully migrated legacy data to Database: " + migratedStats + " player stats, " + migratedShops + " shop profiles.");
                }
            }
        }
    }

    public void close() {
        this.initialized = false;
        plugin.getLogger().info("[MobArenav2] Database connection manager closed.");
    }
}
