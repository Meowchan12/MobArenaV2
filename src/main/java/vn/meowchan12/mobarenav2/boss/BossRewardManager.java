package vn.meowchan12.mobarenav2.boss;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [v7.0] Tracks player damage contributions to bosses and distributes
 * tiered rewards (Money, PlayerPoints, commands) when a boss is killed.
 * 
 * Anti-leech: Players who deal less than the minimum threshold get nothing.
 * Thread-safe damage tracking using ConcurrentHashMap.
 */
public class BossRewardManager {

    private final MobArenav2 plugin;
    private YamlConfiguration rewardsConfig;
    private File rewardsFile;

    // Track damage per boss instance: BossUUID -> (PlayerUUID -> TotalDamage)
    private final Map<UUID, Map<UUID, Double>> bossDamageTracker = new ConcurrentHashMap<>();

    public BossRewardManager(MobArenav2 plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    private void loadConfig() {
        rewardsFile = new File(plugin.getDataFolder(), "boss_rewards.yml");
        if (!rewardsFile.exists()) {
            plugin.saveResource("boss_rewards.yml", false);
        }
        rewardsConfig = YamlConfiguration.loadConfiguration(rewardsFile);
    }

    public void reloadConfig() {
        rewardsConfig = YamlConfiguration.loadConfiguration(rewardsFile);
        plugin.getLogger().info("[MobArenav2] Boss rewards configuration reloaded.");
    }

    // ==========================================
    // DAMAGE TRACKING
    // ==========================================

    /**
     * Starts tracking damage for a new boss instance.
     */
    public void startTracking(UUID bossUUID) {
        bossDamageTracker.put(bossUUID, new ConcurrentHashMap<>());
    }

    /**
     * Records damage dealt by a player to a tracked boss.
     * Thread-safe via ConcurrentHashMap.
     */
    public void recordDamage(UUID bossUUID, UUID playerUUID, double damage) {
        Map<UUID, Double> tracker = bossDamageTracker.get(bossUUID);
        if (tracker != null) {
            tracker.merge(playerUUID, damage, Double::sum);
        }
    }

    /**
     * Stops tracking and cleans up data for a boss instance.
     */
    public void stopTracking(UUID bossUUID) {
        bossDamageTracker.remove(bossUUID);
    }

    /**
     * Gets the sorted damage ranking for a boss (highest damage first).
     */
    public List<Map.Entry<UUID, Double>> getDamageRanking(UUID bossUUID) {
        Map<UUID, Double> tracker = bossDamageTracker.get(bossUUID);
        if (tracker == null) return Collections.emptyList();

        List<Map.Entry<UUID, Double>> sorted = new ArrayList<>(tracker.entrySet());
        sorted.sort(Map.Entry.<UUID, Double>comparingByValue().reversed());
        return sorted;
    }

    // ==========================================
    // REWARD DISTRIBUTION
    // ==========================================

    /**
     * Distributes rewards for a killed boss based on damage contributions.
     * Called when a boss entity dies.
     *
     * @param bossUUID   The UUID of the killed boss
     * @param bossId     The config key of the boss (e.g., "abyssal_warlord")
     * @param totalBossHp The total max HP the boss had
     */
    public void distributeRewards(UUID bossUUID, String bossId, double totalBossHp) {
        List<Map.Entry<UUID, Double>> ranking = getDamageRanking(bossUUID);
        if (ranking.isEmpty()) {
            stopTracking(bossUUID);
            return;
        }

        // Determine which reward tier config to use
        // Priority: boss-specific -> default
        String configPath = rewardsConfig.contains(bossId) ? bossId : "default";
        ConfigurationSection rewardSec = rewardsConfig.getConfigurationSection(configPath);
        if (rewardSec == null) {
            plugin.getLogger().warning("[MobArenav2] No reward config found for boss '" + bossId + "' and no 'default' section.");
            stopTracking(bossUUID);
            return;
        }

        double minDamagePercent = rewardSec.getDouble("min-damage-percent", 1.0);
        double minDamageThreshold = totalBossHp * (minDamagePercent / 100.0);

        // Distribute rewards by tier
        for (int i = 0; i < ranking.size(); i++) {
            Map.Entry<UUID, Double> entry = ranking.get(i);
            UUID playerUUID = entry.getKey();
            double damage = entry.getValue();

            // Anti-Leech: skip players below minimum damage threshold
            if (damage < minDamageThreshold) {
                Player p = Bukkit.getPlayer(playerUUID);
                if (p != null && p.isOnline()) {
                    p.sendMessage("§c[MobArenav2] You did not deal enough damage to receive boss rewards. (Min: " + String.format("%.1f", minDamagePercent) + "%)");
                }
                continue;
            }

            int rank = i + 1; // 1-indexed rank
            String tierKey = getTierKey(rewardSec, rank);
            if (tierKey == null) continue;

            ConfigurationSection tierCfg = rewardSec.getConfigurationSection("tiers." + tierKey);
            if (tierCfg == null) continue;

            Player player = Bukkit.getPlayer(playerUUID);
            if (player == null || !player.isOnline()) continue;

            giveRewards(player, tierCfg, rank, damage, totalBossHp);
        }

        stopTracking(bossUUID);
    }

    /**
     * Determines the reward tier key based on the player's damage rank.
     * Config format:
     * tiers:
     *   top1:
     *     rank: 1
     *   top5:
     *     rank: 2-5
     *   participation:
     *     rank: 6+
     */
    private String getTierKey(ConfigurationSection rewardSec, int rank) {
        ConfigurationSection tiersSec = rewardSec.getConfigurationSection("tiers");
        if (tiersSec == null) return null;

        for (String tierKey : tiersSec.getKeys(false)) {
            String rankStr = tiersSec.getString(tierKey + ".rank", "");
            if (matchesRank(rankStr, rank)) {
                return tierKey;
            }
        }
        return null;
    }

    /**
     * Checks if a rank matches a rank specification string.
     * Supported formats: "1" (exact), "2-5" (range), "6+" (minimum)
     */
    private boolean matchesRank(String rankStr, int rank) {
        if (rankStr == null || rankStr.isEmpty()) return false;

        rankStr = rankStr.trim();

        // Range: "2-5"
        if (rankStr.contains("-")) {
            String[] parts = rankStr.split("-");
            try {
                int min = Integer.parseInt(parts[0].trim());
                int max = Integer.parseInt(parts[1].trim());
                return rank >= min && rank <= max;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // Minimum: "6+"
        if (rankStr.endsWith("+")) {
            try {
                int min = Integer.parseInt(rankStr.replace("+", "").trim());
                return rank >= min;
            } catch (NumberFormatException e) {
                return false;
            }
        }

        // Exact: "1"
        try {
            return rank == Integer.parseInt(rankStr);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * Gives rewards to a player based on their tier configuration.
     */
    private void giveRewards(Player player, ConfigurationSection tierCfg, int rank, double damage, double totalBossHp) {
        String tierName = tierCfg.getString("name", "Reward");
        double damagePercent = (damage / totalBossHp) * 100.0;

        // Money (Vault)
        double money = tierCfg.getDouble("money", 0);
        if (money > 0) {
            Economy econ = plugin.getEconomy();
            if (econ != null) {
                econ.depositPlayer(player, money);
            }
        }

        // PlayerPoints (via reflection - no compile-time dependency)
        int points = tierCfg.getInt("points", 0);
        if (points > 0) {
            try {
                org.bukkit.plugin.Plugin ppPlugin = Bukkit.getPluginManager().getPlugin("PlayerPoints");
                if (ppPlugin != null) {
                    java.lang.reflect.Method getApiMethod = ppPlugin.getClass().getMethod("getAPI");
                    Object api = getApiMethod.invoke(ppPlugin);
                    if (api != null) {
                        java.lang.reflect.Method giveMethod = api.getClass().getMethod("give", java.util.UUID.class, int.class);
                        giveMethod.invoke(api, player.getUniqueId(), points);
                    }
                }
            } catch (Exception ignored) {
                // PlayerPoints not installed or API changed
            }
        }

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        ArenaPlayerProfile profile = (arena != null) ? arena.getPlayerProfiles().get(player.getUniqueId()) : null;

        // Commands
        List<String> commands = tierCfg.getStringList("commands");
        for (String cmd : commands) {
            String parsed = cmd
                .replace("%player%", player.getName())
                .replace("%rank%", String.valueOf(rank))
                .replace("%damage%", String.format("%.0f", damage))
                .replace("%percent%", String.format("%.1f", damagePercent));
            if (profile != null) {
                profile.cacheReward("cmd(" + parsed + ")");
            } else {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
            }
        }

        // Message
        String message = tierCfg.getString("message", "");
        if (!message.isEmpty()) {
            String parsed = message
                .replace("%tier%", tierName)
                .replace("%rank%", String.valueOf(rank))
                .replace("%money%", String.format("%.0f", money))
                .replace("%points%", String.valueOf(points))
                .replace("%damage%", String.format("%.0f", damage))
                .replace("%percent%", String.format("%.1f", damagePercent))
                .replace("&", "§");
            player.sendMessage(parsed);
        }

        // Sound
        String sound = tierCfg.getString("sound", "ENTITY_PLAYER_LEVELUP");
        try {
            player.playSound(player.getLocation(), Sound.valueOf(sound), 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }

    /**
     * Checks if tracking is active for a boss.
     */
    public boolean isTracking(UUID bossUUID) {
        return bossDamageTracker.containsKey(bossUUID);
    }
}
