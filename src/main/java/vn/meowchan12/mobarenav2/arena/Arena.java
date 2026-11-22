package vn.meowchan12.mobarenav2.arena;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import net.md_5.bungee.api.chat.BaseComponent;
import vn.meowchan12.mobarenav2.MobArenav2;

import java.util.*;

public class Arena {

    private final String name;
    private FileConfiguration config;

    private Location p1, p2, lobby, arenaSpawn, spectatorSpawn;
    private final List<Location> spawnpoints = new ArrayList<>();
    private final List<Location> supplypoints = new ArrayList<>();
    private final double spawnMinDistance = 5.0;

    private boolean isRunning = false;
    private int currentWave = 0;

    private boolean isVoting = false;
    private boolean isEndlessMode = false;
    private int voteTicks = 0;
    private boolean isStarting = false;
    private org.bukkit.scheduler.BukkitTask startCountdownTask = null;
    private final Queue<String> endlessWaveQueue = new LinkedList<>();
    private final Map<UUID, Boolean> activeVotes = new HashMap<>();

    // [v7.5.4]: Boss Wave Tracking & Upgrade Phase Controls
    private boolean bossDefeatedInWave = false;
    private boolean upgradePhase = false;
    private int upgradePhaseTicks = 0;

    // [v8.2.0]: Match Duration and Start/End Timestamps
    private long matchStartTime = 0L;
    private long matchEndTime = 0L;

    private final Map<UUID, ArenaPlayerProfile> playerProfiles = new HashMap<>();
    private final Map<UUID, ArenaPlayerProfile> historicalProfiles = new HashMap<>();
    private final Set<UUID> spectators = new HashSet<>();
    private final List<LivingEntity> activeMobs = new ArrayList<>();

    private final List<Location> activeSupplies = new ArrayList<>();
    
    // In-memory match summary backup to guarantee /ma top & GUI accessibility even before disk flush
    private org.bukkit.configuration.ConfigurationSection memoryMatchSummary;

    // Staggered Spawning Queue
    private final java.util.Queue<Runnable> spawnQueue = new java.util.LinkedList<>();

    public Arena(String name) {
        this.name = name;
    }

    public String getName() { return name; }
    public FileConfiguration getConfig() { return config; }
    public void setConfig(FileConfiguration config) { this.config = config; }

    public Location getP1() { return p1; }
    public void setPoint1(Location p1) { this.p1 = p1; }
    public Location getP2() { return p2; }
    public void setPoint2(Location p2) { this.p2 = p2; }
    public Location getLobby() { return lobby; }
    public void setLobby(Location lobby) { this.lobby = lobby; }
    public Location getArenaSpawn() { return arenaSpawn; }
    public void setArenaSpawn(Location arenaSpawn) { this.arenaSpawn = arenaSpawn; }
    public Location getSpectatorSpawn() { return spectatorSpawn; }
    public void setSpectatorSpawn(Location spectatorSpawn) { this.spectatorSpawn = spectatorSpawn; }
    public List<Location> getSpawnpoints() { return spawnpoints; }
    public List<Location> getSupplypoints() { return supplypoints; }
    public double getSpawnMinDistance() { return spawnMinDistance; }

    public boolean isRunning() { return isRunning; }
    public void setRunning(boolean running) { this.isRunning = running; }
    public int getCurrentWave() { return currentWave; }
    public void setCurrentWave(int currentWave) { this.currentWave = currentWave; }

    public boolean isVoting() { return isVoting; }
    public void setVoting(boolean voting) { this.isVoting = voting; }
    public boolean isStarting() { return isStarting; }
    public boolean isEndlessMode() { return isEndlessMode; }
    public void setEndlessMode(boolean endlessMode) { this.isEndlessMode = endlessMode; }
    public Queue<String> getEndlessWaveQueue() { return endlessWaveQueue; }
    public Map<UUID, Boolean> getActiveVotes() { return activeVotes; }
    public int getVoteTicks() { return voteTicks; }
    public void setVoteTicks(int voteTicks) { this.voteTicks = voteTicks; }

    public boolean hasBossDefeatedInWave() { return bossDefeatedInWave; }
    public void setBossDefeatedInWave(boolean v) { this.bossDefeatedInWave = v; }
    public boolean isUpgradePhase() { return upgradePhase; }
    public void setUpgradePhase(boolean v) { 
        this.upgradePhase = v; 
        this.upgradePhaseTicks = 0;
    }
    public int getUpgradePhaseTicks() { return upgradePhaseTicks; }
    public void incrementUpgradePhaseTicks() { this.upgradePhaseTicks++; }

    public Map<UUID, ArenaPlayerProfile> getPlayerProfiles() { return playerProfiles; }
    public Map<UUID, ArenaPlayerProfile> getHistoricalProfiles() { return historicalProfiles; }
    public java.util.Queue<Runnable> getSpawnQueue() { return spawnQueue; }
    public Set<UUID> getSpectators() { return spectators; }
    public List<LivingEntity> getActiveMobs() { return activeMobs; }
    public List<Location> getActiveSupplies() { return activeSupplies; }
    public org.bukkit.configuration.ConfigurationSection getMemoryMatchSummary() { return memoryMatchSummary; }
    public void setMemoryMatchSummary(org.bukkit.configuration.ConfigurationSection memoryMatchSummary) { this.memoryMatchSummary = memoryMatchSummary; }

    // [v8.2.0]: Match timing accessors
    public long getMatchStartTime() { return matchStartTime; }
    public void setMatchStartTime(long matchStartTime) { this.matchStartTime = matchStartTime; }
    public long getMatchEndTime() { return matchEndTime; }
    public void setMatchEndTime(long matchEndTime) { this.matchEndTime = matchEndTime; }

    public long getElapsedDurationSeconds() {
        if (matchStartTime <= 0) return 0;
        long end = isRunning ? System.currentTimeMillis() : (matchEndTime > 0 ? matchEndTime : System.currentTimeMillis());
        return Math.max(0, (end - matchStartTime) / 1000L);
    }

    public String getElapsedTimeString() {
        long totalSec = getElapsedDurationSeconds();
        long hrs = totalSec / 3600;
        long mins = (totalSec % 3600) / 60;
        long secs = totalSec % 60;
        return String.format("%02d:%02d:%02d", hrs, mins, secs);
    }

    public Set<UUID> getActivePlayers() {
        Set<UUID> active = new HashSet<>(playerProfiles.keySet());
        active.removeAll(spectators);
        return active;
    }

    public List<Player> getPlayersAsObjects() {
        List<Player> players = new ArrayList<>();
        for (UUID uuid : getActivePlayers()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) players.add(p);
        }
        return players;
    }

    public List<Player> getPlayers() {
        return getPlayersAsObjects();
    }

    public MobArenav2 getPlugin() {
        return MobArenav2.getInstance();
    }

    public boolean containsLocation(Location loc) {
        if (p1 == null || p2 == null || loc == null || loc.getWorld() != p1.getWorld()) return false;
        double minX = Math.min(p1.getX(), p2.getX());
        double minY = Math.min(p1.getY(), p2.getY());
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxX = Math.max(p1.getX(), p2.getX());
        double maxY = Math.max(p1.getY(), p2.getY());
        double maxZ = Math.max(p1.getZ(), p2.getZ());
        return loc.getX() >= minX && loc.getX() <= maxX &&
                loc.getY() >= minY && loc.getY() <= maxY &&
                loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    // ==========================================
    // [FIX BUG #2] Chunk Ticket System (Chunk Lock)
    // ==========================================
    private void setChunksLocked(MobArenav2 plugin, boolean locked) {
        if (p1 == null || p2 == null || p1.getWorld() == null) return;

        World world = p1.getWorld();
        int minX = Math.min(p1.getBlockX(), p2.getBlockX()) >> 4;
        int minZ = Math.min(p1.getBlockZ(), p2.getBlockZ()) >> 4;
        int maxX = Math.max(p1.getBlockX(), p2.getBlockX()) >> 4;
        int maxZ = Math.max(p1.getBlockZ(), p2.getBlockZ()) >> 4;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                Chunk chunk = world.getChunkAt(x, z);
                if (locked) {
                    chunk.addPluginChunkTicket(plugin);
                } else {
                    chunk.removePluginChunkTicket(plugin);
                }
            }
        }
    }

    public void checkAndStart(MobArenav2 plugin) {
        if (isRunning || isStarting) return;

        for (ArenaPlayerProfile profile : playerProfiles.values()) {
            if (!profile.isReady() && !spectators.contains(profile.getPlayerUUID())) return;
        }

        int minPlayers = config != null ? config.getInt("settings.player-limits.min-players", 1) : 1;
        if (getActivePlayers().size() < minPlayers) {
            broadcastMessage("§c[MobArenav2] Everyone is ready, but the arena requires at least " + minPlayers + " players to start!");
            for (ArenaPlayerProfile profile : playerProfiles.values()) {
                profile.setReady(false);
            }
            return;
        }

        startReadyCountdown(plugin, false);
    }

    public boolean forceStart(MobArenav2 plugin) {
        if (isRunning || isStarting) return false;
        if (getActivePlayers().isEmpty()) return false;

        for (ArenaPlayerProfile profile : playerProfiles.values()) {
            profile.setReady(true);
            if (profile.getSelectedClass() == null) {
                profile.setSelectedClass("Knight");
            }
        }

        startReadyCountdown(plugin, true);
        return true;
    }

    public void startReadyCountdown(MobArenav2 plugin, boolean isForced) {
        if (isRunning || isStarting) return;
        isStarting = true;

        // Pre-match cleanup and chunk reservation
        setChunksLocked(plugin, true);
        new ArenaRestorer(plugin).restoreArena(this);
        clearLeftoverEntities(plugin);
        clearActiveSupplies();

        if (isForced) {
            broadcastMessage("§e[MobArenav2] Match start forced by admin. Starting in 5 seconds...");
        } else {
            broadcastMessage("§a[MobArenav2] All players are ready! Match starting in 5 seconds...");
        }

        final int[] secondsLeft = {5};
        startCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!isStarting) {
                if (startCountdownTask != null) startCountdownTask.cancel();
                startCountdownTask = null;
                return;
            }

            int minPlayers = config != null ? config.getInt("settings.player-limits.min-players", 1) : 1;
            if (!isForced && getActivePlayers().size() < minPlayers) {
                broadcastMessage("§c[MobArenav2] Countdown cancelled: Not enough players in the lobby!");
                cancelReadyCountdown(plugin);
                return;
            }

            if (secondsLeft[0] > 0) {
                float pitch = 1.0f + (5 - secondsLeft[0]) * 0.2f;
                String color = switch (secondsLeft[0]) {
                    case 5 -> "§a";
                    case 4 -> "§2";
                    case 3 -> "§e";
                    case 2 -> "§6";
                    case 1 -> "§c";
                    default -> "§f";
                };

                for (UUID uuid : playerProfiles.keySet()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) {
                        p.sendTitle(color + "§l" + secondsLeft[0], "§7Prepare for battle!", 0, 25, 5);
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
                    }
                }
                secondsLeft[0]--;
            } else {
                if (startCountdownTask != null) {
                    startCountdownTask.cancel();
                    startCountdownTask = null;
                }
                isStarting = false;

                for (UUID uuid : getActivePlayers()) {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null && arenaSpawn != null) {
                        p.teleport(arenaSpawn);
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                        p.sendTitle("§a§lBATTLE COMMENCED!", "§7Survive the waves!", 10, 40, 10);
                    }
                }

                broadcastMessage("§a[MobArenav2] The arena battle has begun!");

                // Starter Upgrades distribution if enabled for this arena
                if (plugin.getUpgradeManager() != null && plugin.getUpgradeManager().isStarterUpgradeEnabled(this)) {
                    int starterCount = plugin.getUpgradeManager().getStarterUpgradeCount(this);
                    if (starterCount > 0) {
                        for (UUID uuid : getActivePlayers()) {
                            ArenaPlayerProfile profile = playerProfiles.get(uuid);
                            Player p = Bukkit.getPlayer(uuid);
                            if (profile != null && p != null && p.isOnline()) {
                                profile.addPendingUpgradeChoices(starterCount, vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType.STARTER);
                                vn.meowchan12.mobarenav2.arena.UpgradeGUI.open(p, plugin);
                            }
                        }
                    }
                }

                plugin.getWaveManager().startArena(this);
            }
        }, 0L, 20L);
    }

    public void cancelReadyCountdown(MobArenav2 plugin) {
        if (startCountdownTask != null) {
            startCountdownTask.cancel();
            startCountdownTask = null;
        }
        isStarting = false;
        setChunksLocked(plugin, false);
        for (ArenaPlayerProfile profile : playerProfiles.values()) {
            profile.setReady(false);
        }
    }

    public int killAllMobs(MobArenav2 plugin) {
        int count = 0;
        NamespacedKey bossKey = new NamespacedKey(plugin, "arena_boss_id");
        for (LivingEntity mob : new ArrayList<>(activeMobs)) {
            if (mob != null && mob.isValid()) {
                if (mob.hasMetadata("MA_BOSS") || mob.getPersistentDataContainer().has(bossKey, org.bukkit.persistence.PersistentDataType.STRING)) {
                    setBossDefeatedInWave(true);
                }
                try {
                    mob.setHealth(0.0);
                } catch (Exception e) {
                    mob.remove();
                }
                count++;
            }
        }
        activeMobs.clear();

        if (p1 != null && p2 != null && p1.getWorld() != null) {
            double minX = Math.min(p1.getX(), p2.getX());
            double minY = Math.min(p1.getY(), p2.getY());
            double minZ = Math.min(p1.getZ(), p2.getZ());
            double maxX = Math.max(p1.getX(), p2.getX());
            double maxY = Math.max(p1.getY(), p2.getY());
            double maxZ = Math.max(p1.getZ(), p2.getZ());

            double centerX = (minX + maxX) / 2.0;
            double centerY = (minY + maxY) / 2.0;
            double centerZ = (minZ + maxZ) / 2.0;
            double rx = (maxX - minX) / 2.0 + 1.0;
            double ry = (maxY - minY) / 2.0 + 1.0;
            double rz = (maxZ - minZ) / 2.0 + 1.0;
            Location center = new Location(p1.getWorld(), centerX, centerY, centerZ);

            for (Entity entity : p1.getWorld().getNearbyEntities(center, rx, ry, rz)) {
                if (!(entity instanceof LivingEntity le) || entity instanceof Player) continue;

                Location loc = entity.getLocation();
                if (loc.getX() >= minX && loc.getX() <= maxX &&
                        loc.getY() >= minY && loc.getY() <= maxY &&
                        loc.getZ() >= minZ && loc.getZ() <= maxZ) {

                    if (entity.getPersistentDataContainer().has(bossKey, org.bukkit.persistence.PersistentDataType.STRING) || entity.hasMetadata("MA_BOSS")) {
                        setBossDefeatedInWave(true);
                    }

                    if (entity instanceof org.bukkit.entity.Monster ||
                            entity instanceof org.bukkit.entity.Slime ||
                            entity.getPersistentDataContainer().has(bossKey, org.bukkit.persistence.PersistentDataType.STRING)) {
                        try {
                            le.setHealth(0.0);
                        } catch (Exception e) {
                            le.remove();
                        }
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public void clearActiveSupplies() {
        for (Location loc : activeSupplies) {
            if (loc != null && loc.getBlock().getType() == Material.CHEST) {
                if (loc.getBlock().getState() instanceof Chest chest) {
                    chest.getInventory().clear();
                }
                loc.getBlock().setType(Material.AIR);
            }
        }
        activeSupplies.clear();
    }

    public void broadcastMessage(String message) {
        for (UUID uuid : playerProfiles.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.sendMessage(message);
        }
    }

    public void broadcastMessage(BaseComponent component) {
        for (UUID uuid : playerProfiles.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.spigot().sendMessage(component);
        }
    }

    public void clearLeftoverEntities(MobArenav2 plugin) {
        if (p1 == null || p2 == null || p1.getWorld() == null) return;
        double minX = Math.min(p1.getX(), p2.getX());
        double minY = Math.min(p1.getY(), p2.getY());
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxX = Math.max(p1.getX(), p2.getX());
        double maxY = Math.max(p1.getY(), p2.getY());
        double maxZ = Math.max(p1.getZ(), p2.getZ());

        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double rx = (maxX - minX) / 2.0 + 1.0;
        double ry = (maxY - minY) / 2.0 + 1.0;
        double rz = (maxZ - minZ) / 2.0 + 1.0;
        Location center = new Location(p1.getWorld(), centerX, centerY, centerZ);

        NamespacedKey bossKey = new NamespacedKey(plugin, "arena_boss_id");

        for (Entity entity : p1.getWorld().getNearbyEntities(center, rx, ry, rz)) {
            if (entity instanceof Player) continue;

            Location loc = entity.getLocation();
            if (loc.getX() >= minX && loc.getX() <= maxX &&
                    loc.getY() >= minY && loc.getY() <= maxY &&
                    loc.getZ() >= minZ && loc.getZ() <= maxZ) {

                if (entity instanceof org.bukkit.entity.Monster ||
                        entity instanceof org.bukkit.entity.Slime ||
                        entity instanceof org.bukkit.entity.Ambient ||
                        entity instanceof org.bukkit.entity.Flying ||
                        entity.getPersistentDataContainer().has(bossKey, org.bukkit.persistence.PersistentDataType.STRING) ||
                        entity instanceof org.bukkit.entity.Projectile ||
                        entity instanceof org.bukkit.entity.Item ||
                        entity instanceof org.bukkit.entity.ExperienceOrb) {
                    entity.remove();
                }
            }
        }

        // Clean any chests at registered supply coordinates
        clearActiveSupplies();
        for (Location sloc : supplypoints) {
            if (sloc != null && sloc.getWorld() != null && sloc.getBlock().getType() == Material.CHEST) {
                if (sloc.getBlock().getState() instanceof Chest chest) {
                    chest.getInventory().clear();
                }
                sloc.getBlock().setType(Material.AIR);
            }
        }
    }

    public ConfigurationSection getWaveConfig(int wave) {
        ConfigurationSection wavesSection = config.getConfigurationSection("waves");
        if (wavesSection == null) return null;

        List<ConfigurationSection> matchedWaves = new ArrayList<>();
        int highestPriority = Integer.MAX_VALUE;

        for (String key : wavesSection.getKeys(false)) {
            ConfigurationSection waveSec = wavesSection.getConfigurationSection(key);
            if (waveSec == null) continue;

            String waveCondition = waveSec.getString("waves");
            if (waveCondition == null) continue;

            if (matchesWaveCondition(waveCondition, wave)) {
                int priority = waveSec.getInt("priority", 5);

                if (priority < highestPriority) {
                    highestPriority = priority;
                    matchedWaves.clear();
                    matchedWaves.add(waveSec);
                } else if (priority == highestPriority) {
                    matchedWaves.add(waveSec);
                }
            }
        }

        if (matchedWaves.isEmpty()) return null;
        if (matchedWaves.size() == 1) return matchedWaves.get(0);
        return matchedWaves.get(new Random().nextInt(matchedWaves.size()));
    }

    private boolean matchesWaveCondition(String condition, int wave) {
        String[] parts = condition.split(",");
        for (String part : parts) {
            part = part.trim();
            try {
                if (part.contains("-")) {
                    String[] bounds = part.split("-");
                    int min = Integer.parseInt(bounds[0].trim());
                    int max = Integer.parseInt(bounds[1].trim());
                    if (wave >= min && wave <= max) return true;
                } else if (part.endsWith("+")) {
                    int min = Integer.parseInt(part.replace("+", "").trim());
                    if (wave >= min) return true;
                } else {
                    int exact = Integer.parseInt(part);
                    if (wave == exact) return true;
                }
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    public void endArena(MobArenav2 plugin) {
        Map<UUID, ArenaPlayerProfile> allProfiles = new HashMap<>(historicalProfiles);
        allProfiles.putAll(playerProfiles);

        if (this.config != null && !allProfiles.isEmpty()) {
            boolean isVictory = !getActivePlayers().isEmpty();
            int maxWaves = config.getInt("settings.max-waves", 20);

            long matchEnd = System.currentTimeMillis();
            this.matchEndTime = matchEnd;
            long elapsedSec = getElapsedDurationSeconds();
            String durationStr = getElapsedTimeString();
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String startedAtStr = (matchStartTime > 0) ? sdf.format(new java.util.Date(matchStartTime)) : "Unknown";
            String endedAtStr = sdf.format(new java.util.Date(matchEnd));

            config.set("last-match.wave", this.currentWave);
            config.set("last-match.max-waves", maxWaves);
            config.set("last-match.is-endless", this.isEndlessMode);
            config.set("last-match.duration", durationStr);
            config.set("last-match.duration-seconds", elapsedSec);
            config.set("last-match.started-at", startedAtStr);
            config.set("last-match.ended-at", endedAtStr);
            config.set("last-match.players", null);

            config.set("last-match-summary.wave", this.currentWave);
            config.set("last-match-summary.max-waves", maxWaves);
            config.set("last-match-summary.endless", this.isEndlessMode);
            config.set("last-match-summary.duration", durationStr);
            config.set("last-match-summary.duration-seconds", elapsedSec);
            config.set("last-match-summary.started-at", startedAtStr);
            config.set("last-match-summary.ended-at", endedAtStr);
            config.set("last-match-summary.players", null);

            List<ArenaPlayerProfile> mvps = new ArrayList<>(allProfiles.values());
            mvps.sort((a, b) -> Integer.compare(b.getKills(), a.getKills()));

            String[] topNames = {"---", "---", "---"};
            int[] topKills = {0, 0, 0};
            String[] topClasses = {"None", "None", "None"};

            for (int i = 0; i < mvps.size(); i++) {
                ArenaPlayerProfile prof = mvps.get(i);
                String pName = Bukkit.getOfflinePlayer(prof.getPlayerUUID()).getName();
                if (pName == null) pName = "Unknown";

                if (i < 3) {
                    topNames[i] = pName;
                    topKills[i] = prof.getKills();
                    topClasses[i] = prof.getSelectedClass() != null ? prof.getSelectedClass() : "None";
                }

                String path = "last-match.players." + prof.getPlayerUUID().toString();
                config.set(path + ".name", pName);
                config.set(path + ".class", prof.getSelectedClass() != null ? prof.getSelectedClass() : "None");
                config.set(path + ".kills", prof.getKills());
                config.set(path + ".damage", prof.getDamageDealt());
                config.set(path + ".damageTaken", prof.getDamageTaken());
                config.set(path + ".bossKills", prof.getBossKills());

                String pathSummary = "last-match-summary.players." + prof.getPlayerUUID().toString();
                config.set(pathSummary + ".name", pName);
                config.set(pathSummary + ".class", prof.getSelectedClass() != null ? prof.getSelectedClass() : "None");
                config.set(pathSummary + ".kills", prof.getKills());
                config.set(pathSummary + ".damage", prof.getDamageDealt());
                config.set(pathSummary + ".damageTaken", prof.getDamageTaken());
                config.set(pathSummary + ".bossKills", prof.getBossKills());

                // Save all upgrades acquired by this player in this match (Prioritizing immutable history over cleared active map)
                Map<String, Integer> matchUps = new LinkedHashMap<>(prof.getMatchUpgradesHistory());
                if (matchUps.isEmpty()) {
                    matchUps.putAll(prof.getOwnedUpgrades());
                }
                for (Map.Entry<String, Integer> upEntry : matchUps.entrySet()) {
                    config.set(path + ".upgrades." + upEntry.getKey(), upEntry.getValue());
                    config.set(pathSummary + ".upgrades." + upEntry.getKey(), upEntry.getValue());
                }

                plugin.getUserDataManager().addStats(prof.getPlayerUUID(), prof.getKills(), isVictory, this.currentWave, prof.getSelectedClass(), prof.getBossKills(), prof.getDamageDealt(), prof.getDamageTaken(), elapsedSec);
            }
            this.memoryMatchSummary = config.getConfigurationSection("last-match-summary");
            plugin.getConfigManager().saveArenaCoords(this);

            if (isVictory) {
                for (UUID uuid : playerProfiles.keySet()) {
                    plugin.getUserDataManager().unlockArena(uuid, this.name);
                }
            }

            boolean useAnnouncer = config.getBoolean("settings.announcer.announce-when-arena-ends", true);

            if (useAnnouncer) {
                boolean compactMode = config.getBoolean("settings.announcer.compact-mode", true);
                if (compactMode) {
                    String waveSummary = String.valueOf(this.currentWave) + (this.isEndlessMode ? " (Endless)" : "");
                    if (isVictory) {
                        broadcastMessage("§a§lVICTORY! §fArena §e" + this.name + " §fhas been cleared! §7(Reached Wave: §b" + waveSummary + "§7)");
                    } else {
                        broadcastMessage("§c§lMATCH OVER! §fArena §e" + this.name + " §7(Reached Wave: §b" + waveSummary + "§7)");
                    }

                    net.md_5.bungee.api.chat.TextComponent prompt = new net.md_5.bungee.api.chat.TextComponent("§7To view full match stats and MVP leaderboard, ");
                    net.md_5.bungee.api.chat.TextComponent viewBtn = new net.md_5.bungee.api.chat.TextComponent("§6§l[VIEW RESULTS]");
                    viewBtn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                            new net.md_5.bungee.api.chat.ComponentBuilder("§eClick to view match summary and MVP leaderboard").create()
                    ));
                    viewBtn.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
                            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND,
                            "/ma top " + this.name
                    ));
                    prompt.addExtra(viewBtn);
                    broadcastMessage(prompt);
                } else {
                    String path = isVictory ? "settings.announcer.messages.victory" : "settings.announcer.messages.defeat";
                    List<String> messages = config.getStringList(path);

                    if (messages == null || messages.isEmpty()) {
                        messages = Arrays.asList(
                                "&e==========================================",
                                isVictory ? "&a&lVICTORY! &fArena &e%arena% &fhas been cleared!" : "&c&lMATCH OVER! &fArena &e%arena%",
                                "&7Reached Wave: &b%wave%&7/&b%max_wave%",
                                "",
                                "&e&lTOP MVPs:",
                                " &61. &f%top1_player% &7- &c%top1_kills% Kills &8(%top1_class%)",
                                " &62. &f%top2_player% &7- &c%top2_kills% Kills &8(%top2_class%)",
                                " &63. &f%top3_player% &7- &c%top3_kills% Kills &8(%top3_class%)",
                                "&e=========================================="
                        );
                    }

                    for (String line : messages) {
                        if (line.contains("%top2_player%") && mvps.size() < 2) continue;
                        if (line.contains("%top3_player%") && mvps.size() < 3) continue;

                        String parsedLine = line.replace("%arena%", this.name)
                                .replace("%wave%", String.valueOf(this.currentWave) + (this.isEndlessMode ? " (Endless)" : ""))
                                .replace("%max_wave%", String.valueOf(maxWaves))
                                .replace("%top1_player%", topNames[0]).replace("%top1_kills%", String.valueOf(topKills[0])).replace("%top1_class%", topClasses[0])
                                .replace("%top2_player%", topNames[1]).replace("%top2_kills%", String.valueOf(topKills[1])).replace("%top2_class%", topClasses[1])
                                .replace("%top3_player%", topNames[2]).replace("%top3_kills%", String.valueOf(topKills[2])).replace("%top3_class%", topClasses[2])
                                .replace("&", "§");

                        broadcastMessage(parsedLine);
                    }
                }
            }
        }

        if (startCountdownTask != null) {
            startCountdownTask.cancel();
            startCountdownTask = null;
        }
        this.isStarting = false;
        this.isRunning = false;
        this.currentWave = 0;
        this.isEndlessMode = false;
        this.isVoting = false;
        this.endlessWaveQueue.clear();
        this.activeVotes.clear();

        for (LivingEntity mob : activeMobs) {
            if (mob != null && mob.isValid()) mob.remove();
        }
        activeMobs.clear();
        clearLeftoverEntities(plugin);
        clearActiveSupplies();

        // [FIX BUG #2] Release chunk tickets to free server memory
        setChunksLocked(plugin, false);

        for (ArenaPlayerProfile profile : playerProfiles.values()) {
            if (profile.getScoreboard() != null) {
                profile.getScoreboard().remove();
            }
        }

        Set<UUID> toLeave = new java.util.LinkedHashSet<>(playerProfiles.keySet());
        toLeave.addAll(spectators);

        for (UUID uuid : toLeave) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) plugin.getArenaManager().leaveArena(p);
        }

        playerProfiles.clear();
        historicalProfiles.clear();
        spawnQueue.clear(); // [v6.2 FIX] Memory Leak #4: Release all pending spawn closures
        spectators.clear();
        endlessWaveQueue.clear(); // [v6.4 HOTFIX] Prevent endless queue memory leak
        activeVotes.clear(); // [v6.4 HOTFIX] Clear pending votes to prevent UUID leak
        bossDefeatedInWave = false;
        upgradePhase = false;
        upgradePhaseTicks = 0;

        new ArenaRestorer(plugin).restoreArena(this);
    }
}