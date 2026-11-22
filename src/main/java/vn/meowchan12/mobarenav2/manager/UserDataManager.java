package vn.meowchan12.mobarenav2.manager;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import vn.meowchan12.mobarenav2.MobArenav2;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class UserDataManager {

    private final MobArenav2 plugin;
    private final File userFolder;
    private final Map<UUID, YamlConfiguration> cache = new ConcurrentHashMap<>();
    private final Set<UUID> dirtyProfiles = ConcurrentHashMap.newKeySet();
    private final BukkitTask autoSaveTask;

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public UserDataManager(MobArenav2 plugin) {
        this.plugin = plugin;
        this.userFolder = new File(plugin.getDataFolder(), "userdata");
        if (!userFolder.exists()) {
            userFolder.mkdirs();
        }

        // [v7.5.2 ANTI-LAG GUARANTEE]: Periodic asynchronous auto-save task every 5 minutes (6000 ticks)
        this.autoSaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::saveAllDirtyAsync, 6000L, 6000L);
    }

    private File getPlayerFile(UUID uuid) {
        return new File(userFolder, uuid.toString() + ".yml");
    }

    public void loadData(UUID uuid) {
        File file = getPlayerFile(uuid);
        YamlConfiguration config;
        if (file.exists()) {
            config = YamlConfiguration.loadConfiguration(file);
        } else {
            config = new YamlConfiguration();
        }

        // Check if legacy schema needs auto-migration to rich structured schema
        boolean needsMigration = migrateLegacySchemaIfNeeded(uuid, config);
        cache.put(uuid, config);
        if (needsMigration) {
            markDirty(uuid);
        }
    }

    private boolean migrateLegacySchemaIfNeeded(UUID uuid, YamlConfiguration config) {
        // If it's already migrated, just update username/last_played if online
        if (config.contains("identity.uuid") || config.contains("career_statistics.lifetime_kills")) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            if (op.getName() != null) {
                config.set("identity.username", op.getName());
            }
            return false;
        }

        // Check if it has any legacy data
        boolean hasLegacyStats = config.contains("stats");
        boolean hasLegacyArenas = config.contains("unlocked_arenas");

        int legacyKills = config.getInt("stats.lifetime_kills", 0);
        int legacyGames = config.getInt("stats.games_played", 0);
        int legacyWins = config.getInt("stats.games_won", 0);
        int legacyWave = config.getInt("stats.highest_wave", 0);
        List<String> legacyArenas = config.getStringList("unlocked_arenas");

        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        String name = (op.getName() != null) ? op.getName() : "Unknown";
        String nowStr = DATE_FORMAT.format(new Date());

        // Build new structured schema
        config.set("identity.username", name);
        config.set("identity.uuid", uuid.toString());
        config.set("identity.first_joined", nowStr);
        config.set("identity.last_played", nowStr);

        int gamesLost = Math.max(0, legacyGames - legacyWins);
        double winRate = (legacyGames > 0) ? Math.round(((double) legacyWins / legacyGames * 100.0) * 100.0) / 100.0 : 0.0;

        config.set("career_statistics.lifetime_kills", legacyKills);
        config.set("career_statistics.boss_kills", 0);
        config.set("career_statistics.games_played", legacyGames);
        config.set("career_statistics.games_won", legacyWins);
        config.set("career_statistics.games_lost", gamesLost);
        config.set("career_statistics.win_rate_percent", winRate);
        config.set("career_statistics.highest_wave", legacyWave);
        config.set("career_statistics.total_damage_dealt", 0.0);
        config.set("career_statistics.total_damage_taken", 0.0);
        config.set("career_statistics.total_deaths", gamesLost);

        config.set("class_preferences.favorite_class", "None");
        config.set("class_preferences.games_per_class", new HashMap<String, Integer>());

        if (legacyArenas == null || legacyArenas.isEmpty()) {
            legacyArenas = new ArrayList<>();
        }
        config.set("campaign_progression.unlocked_arenas", legacyArenas);

        // Remove deprecated legacy root keys
        if (hasLegacyStats) config.set("stats", null);
        if (hasLegacyArenas) config.set("unlocked_arenas", null);

        return true;
    }

    public void unloadData(UUID uuid) {
        if (dirtyProfiles.contains(uuid)) {
            YamlConfiguration config = cache.get(uuid);
            if (config != null) {
                File file = getPlayerFile(uuid);
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        config.save(file);
                    } catch (IOException e) {
                        plugin.getLogger().severe("[MobArenav2] Failed to save userdata on disconnect: " + uuid);
                    }
                });
            }
            dirtyProfiles.remove(uuid);
        }
        cache.remove(uuid);
    }

    private YamlConfiguration getConfig(UUID uuid) {
        if (!cache.containsKey(uuid)) {
            loadData(uuid);
        }
        return cache.get(uuid);
    }

    public void markDirty(UUID uuid) {
        dirtyProfiles.add(uuid);
    }

    public void saveAllDirtyAsync() {
        if (dirtyProfiles.isEmpty()) return;
        Set<UUID> pending = new HashSet<>(dirtyProfiles);
        for (UUID u : pending) {
            YamlConfiguration cfg = cache.get(u);
            if (cfg != null) {
                File f = getPlayerFile(u);
                try {
                    cfg.save(f);
                    dirtyProfiles.remove(u);
                } catch (IOException e) {
                    plugin.getLogger().severe("[MobArenav2] Failed to save userdata for " + u);
                }
            }
        }
    }

    public void saveAllDirtyNow() {
        if (autoSaveTask != null) {
            autoSaveTask.cancel();
        }
        if (dirtyProfiles.isEmpty()) return;
        for (UUID u : new HashSet<>(dirtyProfiles)) {
            YamlConfiguration cfg = cache.get(u);
            if (cfg != null) {
                try {
                    cfg.save(getPlayerFile(u));
                } catch (IOException e) {
                    plugin.getLogger().severe("[MobArenav2] Failed emergency save for " + u);
                }
            }
        }
        dirtyProfiles.clear();
    }

    // ==========================================
    // ARENA LOCK SYSTEM (CAMPAIGN PROGRESS)
    // ==========================================
    public void unlockArena(UUID uuid, String arenaName) {
        YamlConfiguration config = getConfig(uuid);
        List<String> unlocked = config.getStringList("campaign_progression.unlocked_arenas");
        if (unlocked.isEmpty() && config.contains("unlocked_arenas")) {
            unlocked = config.getStringList("unlocked_arenas");
        }

        if (!unlocked.contains(arenaName.toLowerCase())) {
            unlocked.add(arenaName.toLowerCase());
            config.set("campaign_progression.unlocked_arenas", unlocked);
            markDirty(uuid);
        }
    }

    public boolean hasUnlockedArena(UUID uuid, String arenaName) {
        YamlConfiguration config = getConfig(uuid);
        List<String> unlocked = config.getStringList("campaign_progression.unlocked_arenas");
        if (unlocked.isEmpty() && config.contains("unlocked_arenas")) {
            unlocked = config.getStringList("unlocked_arenas");
        }
        return unlocked.contains(arenaName.toLowerCase());
    }

    // ==========================================
    // LIFETIME STATS & CAREER TELEMETRY
    // ==========================================
    public void addStats(UUID uuid, int kills, boolean isWin, int maxWave) {
        addStats(uuid, kills, isWin, maxWave, null, 0, 0.0, 0.0);
    }

    public void addStats(UUID uuid, int kills, boolean isWin, int maxWave, String combatClass, int bossKills, double damageDealt, double damageTaken) {
        addStats(uuid, kills, isWin, maxWave, combatClass, bossKills, damageDealt, damageTaken, 0L);
    }

    public void addStats(UUID uuid, int kills, boolean isWin, int maxWave, String combatClass, int bossKills, double damageDealt, double damageTaken, long durationSeconds) {
        YamlConfiguration config = getConfig(uuid);

        OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
        if (op.getName() != null) {
            config.set("identity.username", op.getName());
        }
        config.set("identity.last_played", DATE_FORMAT.format(new Date()));

        int currentKills = config.getInt("career_statistics.lifetime_kills", config.getInt("stats.lifetime_kills", 0));
        int currentBossKills = config.getInt("career_statistics.boss_kills", 0);
        int gamesPlayed = config.getInt("career_statistics.games_played", config.getInt("stats.games_played", 0));
        int gamesWon = config.getInt("career_statistics.games_won", config.getInt("stats.games_won", 0));
        int gamesLost = config.getInt("career_statistics.games_lost", 0);
        int highestWave = config.getInt("career_statistics.highest_wave", config.getInt("stats.highest_wave", 0));
        double totalDmgDealt = config.getDouble("career_statistics.total_damage_dealt", 0.0);
        double totalDmgTaken = config.getDouble("career_statistics.total_damage_taken", 0.0);
        int totalDeaths = config.getInt("career_statistics.total_deaths", 0);

        int newGamesPlayed = gamesPlayed + 1;
        int newGamesWon = isWin ? (gamesWon + 1) : gamesWon;
        int newGamesLost = !isWin ? (gamesLost + 1) : gamesLost;
        double winRate = (newGamesPlayed > 0) ? Math.round(((double) newGamesWon / newGamesPlayed * 100.0) * 100.0) / 100.0 : 0.0;

        config.set("career_statistics.lifetime_kills", currentKills + kills);
        config.set("career_statistics.boss_kills", currentBossKills + bossKills);
        config.set("career_statistics.games_played", newGamesPlayed);
        config.set("career_statistics.games_won", newGamesWon);
        config.set("career_statistics.games_lost", newGamesLost);
        config.set("career_statistics.win_rate_percent", winRate);
        if (maxWave > highestWave) {
            config.set("career_statistics.highest_wave", maxWave);
        }
        config.set("career_statistics.total_damage_dealt", Math.round((totalDmgDealt + damageDealt) * 10.0) / 10.0);
        config.set("career_statistics.total_damage_taken", Math.round((totalDmgTaken + damageTaken) * 10.0) / 10.0);
        if (!isWin) {
            config.set("career_statistics.total_deaths", totalDeaths + 1);
        }

        // Class usage telemetry
        if (combatClass != null && !combatClass.equalsIgnoreCase("None")) {
            String path = "class_preferences.games_per_class." + combatClass;
            int classGames = config.getInt(path, 0) + 1;
            config.set(path, classGames);

            // Recompute favorite class
            ConfigurationSection classSec = config.getConfigurationSection("class_preferences.games_per_class");
            if (classSec != null) {
                String fav = combatClass;
                int maxClassCount = 0;
                for (String cKey : classSec.getKeys(false)) {
                    int cnt = classSec.getInt(cKey, 0);
                    if (cnt > maxClassCount) {
                        maxClassCount = cnt;
                        fav = cKey;
                    }
                }
                config.set("class_preferences.favorite_class", fav);
            }
        }

        if (durationSeconds > 0) {
            long curPlaytime = config.getLong("career_statistics.total_playtime_seconds", 0L);
            config.set("career_statistics.total_playtime_seconds", curPlaytime + durationSeconds);
            if (isWin) {
                long fastClear = config.getLong("career_statistics.fastest_clear_seconds", 0L);
                config.set("career_statistics.fastest_clear_seconds", (fastClear <= 0) ? durationSeconds : Math.min(fastClear, durationSeconds));
            }
        }

        markDirty(uuid);
        if (plugin.getDatabaseManager() != null) {
            String pName = (op.getName() != null) ? op.getName() : "Unknown";
            plugin.getDatabaseManager().savePlayerStatsAsync(uuid, pName, kills, isWin, maxWave, combatClass, bossKills, damageDealt, damageTaken, durationSeconds);
        }
    }

    public int getStat(UUID uuid, String statType) {
        YamlConfiguration config = getConfig(uuid);
        if (config.contains("career_statistics." + statType)) {
            return config.getInt("career_statistics." + statType, 0);
        }
        return config.getInt("stats." + statType, 0);
    }

    public double getCareerDouble(UUID uuid, String statType) {
        YamlConfiguration config = getConfig(uuid);
        return config.getDouble("career_statistics." + statType, 0.0);
    }

    public String getFavoriteClass(UUID uuid) {
        YamlConfiguration config = getConfig(uuid);
        return config.getString("class_preferences.favorite_class", "None");
    }

    public void resetData(UUID uuid) {
        YamlConfiguration config = getConfig(uuid);
        config.set("career_statistics", null);
        config.set("class_preferences", null);
        config.set("stats", null);
        markDirty(uuid);
    }

    // Dummy backward-compatible method
    public void removeData(Player player) {
        // Safe to ignore. Backup deletion is handled by InventoryBackupManager.
    }
}
