package vn.meowchan12.mobarenav2.manager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.arena.ArenaScoreboard;
import vn.meowchan12.mobarenav2.exceptions.InvalidConfigurationException;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.NamespacedKey;

public class ArenaManager {

    private final MobArenav2 plugin;
    private final Map<String, Arena> arenas = new HashMap<>();
    private final Map<UUID, Location> pendingRespawnLocations = new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<UUID> internalGameModeSwitches = new HashSet<>();

    public ArenaManager(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    public boolean isInternalGameModeSwitch(UUID uuid) {
        return uuid != null && internalGameModeSwitches.contains(uuid);
    }

    public void setGameModeInternally(Player player, GameMode gameMode) {
        if (player == null || gameMode == null) return;
        internalGameModeSwitches.add(player.getUniqueId());
        try {
            player.setGameMode(gameMode);
        } finally {
            internalGameModeSwitches.remove(player.getUniqueId());
        }
    }

    public void setPendingRespawnLocation(UUID uuid, Location loc) {
        if (uuid != null && loc != null) {
            pendingRespawnLocations.put(uuid, loc);
        }
    }

    public Location getAndRemovePendingRespawnLocation(UUID uuid) {
        if (uuid == null) return null;
        return pendingRespawnLocations.remove(uuid);
    }

    public void addArena(Arena arena) { arenas.put(arena.getName().toLowerCase(), arena); }
    public Arena getArena(String name) { return arenas.get(name.toLowerCase()); }
    public Collection<Arena> getAllArenas() { return arenas.values(); }

    public Arena getArenaByPlayer(Player player) {
        for (Arena arena : arenas.values()) {
            if (arena.getPlayerProfiles().containsKey(player.getUniqueId())) return arena;
        }
        return null;
    }

    public boolean isPlaying(Player player) { return getArenaByPlayer(player) != null; }

    public boolean isSpectating(Player player) {
        for (Arena arena : arenas.values()) {
            if (arena.getSpectators().contains(player.getUniqueId())) return true;
        }
        return false;
    }

    // --- ENHANCED SAFE LOAD ALGORITHM ---
    public void loadArenas() {
        File folder = plugin.getConfigManager().getArenasFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String name = file.getName().replace(".yml", "");
            YamlConfiguration config = new YamlConfiguration();

            try {
                config.load(file);
                validateArenaConfig(config, file.getName());
            } catch (InvalidConfigurationException e) {
                printConfigError(e, "Skipping arena '" + name + "' to protect the server instance.");
                continue;
            } catch (Exception e) {
                plugin.getLogger().severe("[MobArenav2] ❌ CRITICAL YAML SYNTAX ERROR IN FILE: " + file.getName());
                plugin.getLogger().severe("[MobArenav2] Details: The file is structurally broken. Please check for missing quotes or bad indentation.");
                continue;
            }

            Arena arena = new Arena(name);
            arena.setConfig(config);

            if (config.contains("coords.p1")) arena.setPoint1(parseLocation(config.getString("coords.p1")));
            if (config.contains("coords.p2")) arena.setPoint2(parseLocation(config.getString("coords.p2")));
            if (config.contains("coords.lobby")) arena.setLobby(parseLocation(config.getString("coords.lobby")));
            if (config.contains("coords.arena")) arena.setArenaSpawn(parseLocation(config.getString("coords.arena")));
            if (config.contains("coords.spectator")) arena.setSpectatorSpawn(parseLocation(config.getString("coords.spectator")));

            if (config.contains("coords.spawnpoints")) {
                for (String key : config.getConfigurationSection("coords.spawnpoints").getKeys(false)) {
                    Location sp = parseLocation(config.getString("coords.spawnpoints." + key));
                    if (sp != null) arena.getSpawnpoints().add(sp);
                }
            }

            if (config.contains("coords.supplypoints")) {
                for (String key : config.getConfigurationSection("coords.supplypoints").getKeys(false)) {
                    Location sp = parseLocation(config.getString("coords.supplypoints." + key));
                    if (sp != null) arena.getSupplypoints().add(sp);
                }
            }

            addArena(arena);
            plugin.getLogger().info("[MobArenav2] Loaded arena blueprint: " + name);
        }

        // [v8.4.0 CENTRALIZED]: Auto-provision upgrade-system entries in settings.yml for all detected arenas
        plugin.getConfigManager().syncArenaUpgradeConfigs(getAllArenas());
    }

    public void reloadArenas() {
        File folder = plugin.getConfigManager().getArenasFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            String name = file.getName().replace(".yml", "");
            YamlConfiguration config = new YamlConfiguration();

            try {
                config.load(file);
                validateArenaConfig(config, file.getName());
            } catch (InvalidConfigurationException e) {
                printConfigError(e, "Reload operation ABORTED for this file to protect active data!");
                continue;
            } catch (Exception e) {
                plugin.getLogger().severe("[MobArenav2] ❌ CRITICAL YAML SYNTAX ERROR IN FILE: " + file.getName());
                plugin.getLogger().severe("[MobArenav2] Reload operation ABORTED for this file to protect active data!");
                continue;
            }

            Arena arena = getArena(name);
            if (arena == null) {
                arena = new Arena(name);
                addArena(arena);
            }

            arena.setConfig(config);

            if (config.contains("coords.p1")) arena.setPoint1(parseLocation(config.getString("coords.p1")));
            if (config.contains("coords.p2")) arena.setPoint2(parseLocation(config.getString("coords.p2")));
            if (config.contains("coords.lobby")) arena.setLobby(parseLocation(config.getString("coords.lobby")));
            if (config.contains("coords.arena")) arena.setArenaSpawn(parseLocation(config.getString("coords.arena")));
            if (config.contains("coords.spectator")) arena.setSpectatorSpawn(parseLocation(config.getString("coords.spectator")));

            if (config.contains("coords.spawnpoints")) {
                arena.getSpawnpoints().clear();
                for (String key : config.getConfigurationSection("coords.spawnpoints").getKeys(false)) {
                    Location sp = parseLocation(config.getString("coords.spawnpoints." + key));
                    if (sp != null) arena.getSpawnpoints().add(sp);
                }
            }

            if (config.contains("coords.supplypoints")) {
                arena.getSupplypoints().clear();
                for (String key : config.getConfigurationSection("coords.supplypoints").getKeys(false)) {
                    Location sp = parseLocation(config.getString("coords.supplypoints." + key));
                    if (sp != null) arena.getSupplypoints().add(sp);
                }
            }
        }
        // [v8.4.0 CENTRALIZED]: Auto-provision upgrade-system entries for any newly added arenas
        plugin.getConfigManager().syncArenaUpgradeConfigs(getAllArenas());
        plugin.getLogger().info("[MobArenav2] Hot-reloaded all arena configurations safely.");
    }

    private void validateArenaConfig(YamlConfiguration config, String fileName) throws InvalidConfigurationException {
        if (!config.contains("settings")) {
            throw new InvalidConfigurationException("Missing the mandatory 'settings' section.", fileName, "ROOT");
        }

        if (!config.isInt("settings.max-waves")) {
            throw new InvalidConfigurationException("The 'max-waves' value must be a valid integer.", fileName, "settings");
        }

        if (config.contains("waves")) {
            for (String waveKey : config.getConfigurationSection("waves").getKeys(false)) {
                String path = "waves." + waveKey;
                if (!config.contains(path + ".waves")) {
                    throw new InvalidConfigurationException("Missing the mandatory 'waves' condition (e.g., '1-5').", fileName, path);
                }
                if (!config.contains(path + ".wave_types")) {
                    throw new InvalidConfigurationException("Missing the mandatory 'wave_types' identifier (e.g., 'mob', 'boss').", fileName, path);
                }
            }
        }
    }

    private void printConfigError(InvalidConfigurationException e, String action) {
        plugin.getLogger().severe("========================================");
        plugin.getLogger().severe("[MobArenav2] ⛔ CONFIGURATION ERROR DETECTED ⛔");
        plugin.getLogger().severe("[MobArenav2] File: " + e.getFileName());
        plugin.getLogger().severe("[MobArenav2] Section: " + e.getSection());
        plugin.getLogger().severe("[MobArenav2] Error: " + e.getMessage());
        plugin.getLogger().severe("[MobArenav2] Action: " + action);
        plugin.getLogger().severe("========================================");
    }

    private Location parseLocation(String str) {
        if (str == null || str.isEmpty()) return null;
        try {
            String[] parts = str.split(",");
            if (parts.length < 6) return null;
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            float yaw = Float.parseFloat(parts[3]);
            float pitch = Float.parseFloat(parts[4]);
            World world = Bukkit.getWorld(parts[5]);
            return new Location(world, x, y, z, yaw, pitch);
        } catch (Exception e) {
            plugin.getLogger().warning("[MobArenav2] ⚠ Invalid location format detected and bypassed: " + str);
            return null;
        }
    }

    // ==========================================
    // FIX ARENA JOIN BUG (#3)
    // ==========================================
    public boolean joinArena(Player player, String arenaName) {
        UUID uuid = player.getUniqueId();

        // 1. Prevent joining if already playing in lobby
        if (isPlaying(player)) {
            player.sendMessage("§c[MobArenav2] You are already in an arena!");
            return false;
        }

        // 2. Prevent joining if currently spectating
        if (isSpectating(player)) {
            player.sendMessage("§c[MobArenav2] You are spectating! Use /ma leave first.");
            return false;
        }

        if (plugin.getInventoryBackupManager().isPendingJoin(uuid)) {
            player.sendMessage("§c[MobArenav2] Please wait, your previous join request is still processing!");
            return false;
        }

        if (plugin.getInventoryBackupManager().hasBackup(uuid)) {
            player.sendMessage("§c[MobArenav2] Detected a hanging backup file! Restoring your items first...");
            plugin.getInventoryBackupManager().restoreBackup(player);
            return false;
        }

        Arena arena = getArena(arenaName);
        if (arena == null || arena.getLobby() == null) {
            player.sendMessage("§c[MobArenav2] Arena not found or Lobby is missing.");
            return false;
        }

        // 3. Block join if the arena is already running or countdown has begun
        if (arena.isRunning() || arena.isStarting()) {
            player.sendMessage("§c[MobArenav2] The battle in this arena has already started or is currently starting!");
            return false;
        }

        FileConfiguration config = arena.getConfig();
        boolean isEnabled = config != null && config.getBoolean("settings.enabled", true);
        if (!isEnabled) {
            player.sendMessage("§c[MobArenav2] This arena is currently disabled for maintenance!");
            return false;
        }

        if (config != null) {
            String requiredArena = config.getString("settings.required-arena", "");
            if (requiredArena != null && !requiredArena.isEmpty()) {
                if (!player.isOp() && !player.hasPermission("mobarenav2.admin") && !player.hasPermission("mobarenav2.bypass.arenalock")) {
                    if (!plugin.getUserDataManager().hasUnlockedArena(uuid, requiredArena)) {
                        player.sendMessage("§c[MobArenav2] This arena is locked! You must clear §e" + requiredArena + " §cfirst to enter.");
                        return false;
                    }
                }
            }
        }

        int maxPlayers = config != null ? config.getInt("settings.player-limits.max-players", 99) : 99;
        if (arena.getActivePlayers().size() >= maxPlayers) {
            player.sendMessage("§c[MobArenav2] This arena is currently full! (Max: " + maxPlayers + " players)");
            return false;
        }

        if (config != null && config.getBoolean("settings.entry-fee.enabled", false)) {
            String feeType = config.getString("settings.entry-fee.type", "VAULT").toUpperCase();
            if (feeType.equals("VAULT") && plugin.getEconomy() != null) {
                double amount = config.getDouble("settings.entry-fee.amount", 0.0);
                if (!plugin.getEconomy().withdrawPlayer(player, amount).transactionSuccess()) {
                    player.sendMessage("§c[MobArenav2] You do not have enough money! Cost: $" + amount);
                    return false;
                }
                player.sendMessage("§a[MobArenav2] Paid $" + amount + " entry fee.");
            } else if (feeType.equals("COMMAND")) {
                String cmd = config.getString("settings.entry-fee.command", "");
                if (!cmd.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", player.getName()));
            }
        }

        plugin.getInventoryBackupManager().addPending(uuid);
        player.sendMessage("§e[MobArenav2] Scanning and backing up your inventory... Please stand still for 3 seconds!");

        new BukkitRunnable() {
            int countdown = 3;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    plugin.getInventoryBackupManager().removePending(uuid);
                    this.cancel();
                    return;
                }

                if (countdown > 0) {
                    player.sendTitle("§a" + countdown, "§fPreparing for battle...", 0, 25, 0);
                    player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    countdown--;
                } else {
                    this.cancel();

                    plugin.getInventoryBackupManager().backupAndClear(player, () -> {
                        plugin.getInventoryBackupManager().removePending(uuid);

                        ArenaPlayerProfile profile = new ArenaPlayerProfile(uuid);
                        profile.saveOriginalLocation(player);

                        profile.setScoreboard(new ArenaScoreboard(player, plugin));
                        profile.getScoreboard().update(arena);
                        arena.getPlayerProfiles().put(uuid, profile);
                        arena.getHistoricalProfiles().put(uuid, profile);

                        Location lobby = arena.getLobby();
                        if (lobby != null && lobby.getWorld() != null) {
                            lobby.getWorld().getChunkAt(lobby).load(true);
                            player.teleport(lobby);
                        }

                        resetPlayerState(player);

                        // =====================================================================
                        // [v5.2 FEATURE]: APPLY PERMANENT /MA SHOP BASE ATTRIBUTES
                        // Applies permanent HP, Armor, Damage bonuses bought from /ma shop
                        // =====================================================================
                        if (plugin.getShopManager() != null) {
                            plugin.getShopManager().applyPermanentAttributesToPlayer(player);
                        }

                        // =====================================================================
                        // [v5.6 FEATURE]: PER-ARENA BASE HEALTH SYSTEM
                        // Sets base max health for all players or per-class in this arena.
                        // Survives Totem of Undying pops (attribute-based, not effect-based).
                        // =====================================================================
                        if (config != null) {
                            double baseHealth = config.getDouble("settings.base-health", 0.0);
                            if (baseHealth > 0) {
                                AttributeInstance hpAttr = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH);
                                if (hpAttr != null) {
                                    // Stack with shop bonuses: shop sets base to 20+bonus, we set it to baseHealth+bonus
                                    double shopBonus = hpAttr.getBaseValue() - 20.0; // Shop already added bonus on top of 20
                                    hpAttr.setBaseValue(baseHealth + Math.max(0, shopBonus));
                                    player.setHealth(hpAttr.getValue());
                                }
                            }
                        }

                        setGameModeInternally(player, GameMode.SURVIVAL);
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

                        player.sendMessage("§a[MobArenav2] You have joined arena: §e" + arena.getName());
                        player.sendMessage("§b➡ Type §e/ma class §bto select your loadout.");

                        // =====================================================================
                        // [v5.2 FEATURE]: JOIN ANNOUNCEMENT (ENGLISH)
                        // Configurable server-wide or arena-only announcement to prevent spamming survival
                        // =====================================================================
                        int currentCount = arena.getActivePlayers().size();
                        int limitPlayers = (config != null) ? config.getInt("settings.player-limits.max-players", 99) : 99;
                        String joinMsg = "§6§l[MobArenav2] §e" + player.getName() + " §7has joined arena §b" + arena.getName() + "§7! §8[§e" + currentCount + "§7/§e" + limitPlayers + "§8] §bType §e/ma join " + arena.getName() + " §bto fight together!";
                        boolean broadcastAll = (plugin.getConfigManager() != null && plugin.getConfigManager().getSettings() != null)
                                ? plugin.getConfigManager().getSettings().getBoolean("broadcast-join-to-all-server", plugin.getConfig().getBoolean("messages.broadcast-join-to-all-server", false))
                                : plugin.getConfig().getBoolean("messages.broadcast-join-to-all-server", false);
                        if (broadcastAll) {
                            Bukkit.broadcastMessage(joinMsg);
                        } else {
                            arena.broadcastMessage(joinMsg);
                        }
                    });
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        return true;
    }

    public boolean joinSpectator(Player player, String arenaName) {
        if (isPlaying(player) || isSpectating(player)) {
            player.sendMessage("§c[MobArenav2] You are already in an arena!");
            return false;
        }

        Arena arena = getArena(arenaName);
        if (arena == null || arena.getSpectatorSpawn() == null) {
            player.sendMessage("§c[MobArenav2] Arena not found or spectator spawn is missing.");
            return false;
        }

        plugin.getInventoryBackupManager().backupAndClear(player, () -> {
            ArenaPlayerProfile profile = new ArenaPlayerProfile(player.getUniqueId());
            profile.saveOriginalLocation(player);

            profile.setScoreboard(new ArenaScoreboard(player, plugin));
            profile.getScoreboard().update(arena);

            arena.getPlayerProfiles().put(player.getUniqueId(), profile);
            arena.getHistoricalProfiles().put(player.getUniqueId(), profile);
            arena.getSpectators().add(player.getUniqueId());

            Location specSpawn = arena.getSpectatorSpawn();
            if (specSpawn != null && specSpawn.getWorld() != null) {
                specSpawn.getWorld().getChunkAt(specSpawn).load(true);
                player.teleport(specSpawn);
            }

            resetPlayerState(player);

            setGameModeInternally(player, GameMode.ADVENTURE);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.setInvulnerable(true);

            player.sendMessage("§7[MobArenav2] You are now spectating §e" + arena.getName());
        });

        return true;
    }

    /**
     * [v7.2 FIX]: Immediately clear all active potion effects, absorption, fire, freeze ticks,
     * arrows in body, and temporary combat data from the player.
     */
    public void clearAllPlayerEffects(Player player) {
        if (player == null || !player.isOnline()) return;

        // 1. Clear all active potion effects safely
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }

        // 2. Clear absorption hearts
        player.setAbsorptionAmount(0.0);

        // 3. Clear fire ticks and visual burning
        player.setFireTicks(0);
        player.setVisualFire(false);

        // 4. Clear freeze ticks (powder snow / stray / breeze)
        player.setFreezeTicks(0);

        // 5. Clear embedded arrows
        player.setArrowsInBody(0);

        // 6. Remove temporary combat PDC keys
        NamespacedKey dmKey = new NamespacedKey(plugin, "ma_death_mark");
        player.getPersistentDataContainer().remove(dmKey);
        NamespacedKey swCooldownKey = new NamespacedKey(plugin, "ma_second_wind_cd");
        player.getPersistentDataContainer().remove(swCooldownKey);
        NamespacedKey aegisKey = new NamespacedKey(plugin, "ma_super_aegis_used");
        player.getPersistentDataContainer().remove(aegisKey);
    }

    public void handlePlayerDeath(Player player, Arena arena) {
        if (player == null || !player.isOnline()) return;

        arena.getSpectators().add(player.getUniqueId());

        // 1. Immediately purge all potion effects, fire, freeze, and reset player state
        clearAllPlayerEffects(player);
        resetPlayerState(player, arena);
        player.getInventory().clear();
        setGameModeInternally(player, GameMode.ADVENTURE);
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setInvulnerable(true);
        player.setInvisible(true); // Visual invisibility without potion effect icon or particles

        // 2. Safe Spectator Location lookup (spectatorSpawn -> arenaSpawn -> lobby)
        Location specSpawn = arena.getSpectatorSpawn();
        if (specSpawn == null || specSpawn.getWorld() == null) {
            specSpawn = arena.getArenaSpawn();
        }
        if (specSpawn == null || specSpawn.getWorld() == null) {
            specSpawn = arena.getLobby();
        }

        final Location targetSpecLoc = specSpawn;

        // 3. Teleport 1 tick later and PURGE AGAIN to wipe any lingering or post-hit queued effects cleanly!
        if (targetSpecLoc != null && targetSpecLoc.getWorld() != null) {
            targetSpecLoc.getWorld().getChunkAt(targetSpecLoc).addPluginChunkTicket(plugin);
            targetSpecLoc.getWorld().getChunkAt(targetSpecLoc).load(true);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    clearAllPlayerEffects(player);
                    player.setHealth(20.0);
                    player.setFoodLevel(20);
                    player.setSaturation(20.0f);
                    player.setFireTicks(0);
                    player.setFreezeTicks(0);
                    player.setAbsorptionAmount(0.0);
                    if (arena.getSpectators().contains(player.getUniqueId())) {
                        player.teleport(targetSpecLoc);
                    }
                }
            }, 1L);
        }

        arena.broadcastMessage("§c[MobArenav2] §e" + player.getName() + " §chas fallen and is now spectating!");

        Set<UUID> activePlayers = new HashSet<>(arena.getPlayerProfiles().keySet());
        activePlayers.removeAll(arena.getSpectators());

        if (activePlayers.isEmpty()) {
            arena.broadcastMessage("§c§l[MobArenav2] All players have died! The arena is over.");
            // [v7.4.0 BUG 2 FIX]: If solo player died on screen, save original location and auto-respawn
            ArenaPlayerProfile prof = arena.getPlayerProfiles().get(player.getUniqueId());
            if (prof != null && prof.getOriginalLocation() != null) {
                setPendingRespawnLocation(player.getUniqueId(), prof.getOriginalLocation());
            }
            if (player.isDead()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && player.isDead()) {
                        try {
                            player.spigot().respawn();
                        } catch (Throwable ignored) {}
                    }
                });
            }
            arena.endArena(plugin);
        }
    }

    public void resetPlayerState(Player player) {
        resetPlayerState(player, getArenaByPlayer(player));
    }

    public void resetPlayerState(Player player, Arena arena) {
        if (player == null || !player.isOnline()) return;

        clearAllPlayerEffects(player);

        AttributeInstance hpAttr = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH);
        if (hpAttr != null) hpAttr.setBaseValue(20.0);

        AttributeInstance dmgAttr = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.ATTACK_DAMAGE);
        if (dmgAttr != null) dmgAttr.setBaseValue(1.0);

        AttributeInstance armorAttr = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.ARMOR);
        if (armorAttr != null) armorAttr.setBaseValue(0.0);

        AttributeInstance toughnessAttr = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.ARMOR_TOUGHNESS);
        if (toughnessAttr != null) toughnessAttr.setBaseValue(0.0);

        AttributeInstance speedAttr = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.1);

        AttributeInstance atkSpeedAttr = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.ATTACK_SPEED);
        if (atkSpeedAttr != null) atkSpeedAttr.setBaseValue(4.0);

        if (vn.meowchan12.mobarenav2.utils.AttributeUtils.ENTITY_INTERACTION_RANGE != null) {
            AttributeInstance reachAttr = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.ENTITY_INTERACTION_RANGE);
            if (reachAttr != null) reachAttr.setBaseValue(3.0);
        }

        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0.0f);

        player.setHealth(20.0);
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setFireTicks(0);
        player.setFreezeTicks(0);
        player.setVisualFire(false);
        player.setAbsorptionAmount(0.0);
        player.setInvulnerable(false);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setWalkSpeed(0.2f);
        player.setInvisible(false);

        // [v7.3.1 HOTFIX]: Reset all upgrade status of player
        if (plugin.getUpgradeRegistry() != null) {
            plugin.getUpgradeRegistry().dispatchReset(player, arena);
        }

        // [v7.5.0 PURGE]: Wipe all upgrades in profile upon death or leaving
        if (arena != null && arena.getPlayerProfiles() != null) {
            ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
            if (profile != null) {
                profile.clearUpgrades();
            }
        }
    }

    public void leaveArena(Player player) {
        Arena arena = getArenaByPlayer(player);
        if (arena == null) {
            for (Arena a : arenas.values()) {
                if (a.getSpectators().contains(player.getUniqueId())) {
                    arena = a;
                    break;
                }
            }
        }
        if (arena == null) return;

        boolean wasSpectator = arena.getSpectators().contains(player.getUniqueId());
        ArenaPlayerProfile removedProfile = arena.getPlayerProfiles().remove(player.getUniqueId());
        final ArenaPlayerProfile profile = (removedProfile != null)
                ? removedProfile
                : arena.getHistoricalProfiles().get(player.getUniqueId());
        if (removedProfile != null && arena.isRunning()) {
            arena.getHistoricalProfiles().put(player.getUniqueId(), removedProfile);
        }

        if (wasSpectator) {
            arena.getSpectators().remove(player.getUniqueId());
        }
        
        // [v6.4 HOTFIX] Remove from active votes if leaving mid-vote
        arena.getActiveVotes().remove(player.getUniqueId());

        player.getInventory().clear();
        setGameModeInternally(player, GameMode.SURVIVAL);
        resetPlayerState(player, arena);

        Location leaveLoc = null;
        if (profile != null && profile.getOriginalLocation() != null) {
            leaveLoc = profile.getOriginalLocation();
        } else if (arena.getLobby() != null && arena.getLobby().getWorld() != null) {
            leaveLoc = arena.getLobby().getWorld().getSpawnLocation();
        }

        if (leaveLoc != null && leaveLoc.getWorld() != null) {
            leaveLoc.getWorld().getChunkAt(leaveLoc).load(true);
            if (player.isDead()) {
                // [v7.4.0 BUG 2 FIX]: Dead players cannot teleport; cache respawn target and trigger auto-respawn
                setPendingRespawnLocation(player.getUniqueId(), leaveLoc);
                final Location targetLoc = leaveLoc;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && player.isDead()) {
                        try {
                            player.spigot().respawn();
                        } catch (Throwable ignored) {}
                    } else if (player.isOnline()) {
                        player.teleport(targetLoc);
                    }
                });
            } else {
                player.teleport(leaveLoc);
            }
        }

        if (profile != null && profile.getScoreboard() != null) {
            profile.getScoreboard().remove();
        }

        plugin.getInventoryBackupManager().restoreBackup(player, () -> {
            if (profile != null) {
                profile.distributeRewards(plugin);
            }
        });

        player.sendMessage("§e[MobArenav2] You have left the arena and your items have been restored.");

        if (!wasSpectator && arena.isRunning()) {
            Set<UUID> activePlayers = new HashSet<>(arena.getPlayerProfiles().keySet());
            activePlayers.removeAll(arena.getSpectators());
            if (activePlayers.isEmpty()) {
                arena.broadcastMessage("§c§l[MobArenav2] All active players have left! The arena is over.");
                arena.endArena(plugin);
            }
        } else if (!wasSpectator && arena.isStarting()) {
            int minPlayers = arena.getConfig() != null ? arena.getConfig().getInt("settings.player-limits.min-players", 1) : 1;
            if (arena.getActivePlayers().size() < minPlayers) {
                arena.broadcastMessage("§c[MobArenav2] Countdown cancelled: A player left the lobby!");
                arena.cancelReadyCountdown(plugin);
            }
        }
    }

    public void handlePlayerDisconnect(Player player) {
        Arena arena = getArenaByPlayer(player);
        if (arena != null) {
            leaveArena(player);
            plugin.getLogger().info("[MobArenav2] Player " + player.getName() + " disconnected mid-game. State saved and reset safely.");
        }
    }

    /**
     * [v7.5.6]: Revive a spectator back to active player state inside a running arena.
     */
    public boolean revivePlayerInArena(Player player, Arena arena) {
        if (player == null || !player.isOnline() || arena == null || !arena.isRunning()) return false;
        UUID uuid = player.getUniqueId();
        if (!arena.getSpectators().contains(uuid)) {
            return false;
        }

        // 1. Remove from spectators
        arena.getSpectators().remove(uuid);

        // 2. Clear lingering spectator effects and restore survival game mode
        clearAllPlayerEffects(player);
        resetPlayerState(player, arena);
        player.setInvulnerable(false);
        player.setInvisible(false);
        player.setAllowFlight(false);
        player.setFlying(false);
        setGameModeInternally(player, GameMode.SURVIVAL);

        // 3. Teleport to Arena Spawn
        Location spawn = arena.getArenaSpawn();
        if (spawn != null && spawn.getWorld() != null) {
            spawn.getWorld().getChunkAt(spawn).load(true);
            player.teleport(spawn);
        }

        // 4. Re-equip class loadout
        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(uuid);
        String targetClass = "Knight";
        if (profile != null && profile.getSelectedClass() != null && !profile.getSelectedClass().isEmpty()) {
            targetClass = profile.getSelectedClass();
        }
        if (profile != null) {
            profile.setSelectedClass(null); // Clear temporarily so equipClass doesn't early-return
        }
        if (plugin.getClassManager() != null) {
            plugin.getClassManager().equipClass(player, targetClass);
        }

        // [v8.4.2 REVIVE OVERHAUL]: Re-apply permanent Shop stats, Class HP, and 100% full health
        if (plugin.getShopManager() != null) {
            plugin.getShopManager().applyPermanentAttributesToPlayer(player);
        }

        double targetBaseHealth = 20.0;
        if (arena.getConfig() != null) {
            double classHealth = arena.getConfig().getDouble("settings.base-health-per-class." + targetClass, 0.0);
            double arenaBaseHealth = arena.getConfig().getDouble("settings.base-health", 0.0);
            if (classHealth > 0) targetBaseHealth = classHealth;
            else if (arenaBaseHealth > 0) targetBaseHealth = arenaBaseHealth;
        }

        AttributeInstance hpAttr = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH);
        if (hpAttr != null) {
            double shopBonus = 0.0;
            if (plugin.getShopManager() != null) {
                vn.meowchan12.mobarenav2.shop.model.PlayerShopData sd = plugin.getShopManager().getPlayerData(uuid);
                if (sd != null) {
                    shopBonus = sd.getItemLevel("max_health_base") * 2.0;
                }
            }
            hpAttr.setBaseValue(Math.max(20.0, targetBaseHealth + shopBonus));
        }

        // Re-apply Class potion effects permanently
        if (plugin.getConfigManager() != null && plugin.getConfigManager().getClasses() != null) {
            ConfigurationSection sec = plugin.getConfigManager().getClasses().getConfigurationSection("classes." + targetClass);
            if (sec != null) {
                String effStr = sec.getString("effects");
                if (effStr != null && !effStr.isEmpty()) {
                    for (String single : effStr.split(",")) {
                        String[] effData = single.trim().split(":");
                        org.bukkit.potion.PotionEffectType type = org.bukkit.Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(effData[0].toLowerCase()));
                        if (type != null) {
                            int amp = effData.length > 1 ? Integer.parseInt(effData[1]) : 0;
                            player.addPotionEffect(new org.bukkit.potion.PotionEffect(type, Integer.MAX_VALUE, amp, false, false, true));
                        }
                    }
                }
            }
        }

        // Re-apply active match upgrades to restore passive tasks and bonuses
        if (plugin.getUpgradeRegistry() != null && profile != null) {
            for (Map.Entry<String, Integer> entry : profile.getOwnedUpgrades().entrySet()) {
                String upId = entry.getKey();
                int upLvl = entry.getValue();
                if (upLvl <= 0) continue;
                UpgradeManager.UpgradeData data = plugin.getUpgradeManager().getUpgradeData(upId);
                if (data != null) {
                    plugin.getUpgradeRegistry().dispatchApply(player, upLvl, data, arena);
                }
            }
        }

        // 100% full health restore and hunger reset
        if (hpAttr != null) {
            player.setHealth(hpAttr.getValue());
        }
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.setFireTicks(0);

        // 5. 3-second invulnerability protection (Shield & VFX)
        player.setInvulnerable(true);
        player.sendTitle("§a§l✦ REVIVED! ✦", "§e3s Invulnerability Shield active!", 5, 40, 10);
        try {
            player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1.0f, 1.2f);
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
        } catch (Throwable ignored) {}
        try {
            player.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 60, 0.5, 0.5, 0.5, 0.2);
            player.getWorld().spawnParticle(org.bukkit.Particle.FLASH, player.getLocation().add(0, 1, 0), 2);
        } catch (Throwable ignored) {}

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && arena.isRunning() && !arena.getSpectators().contains(uuid)) {
                player.setInvulnerable(false);
                player.sendMessage("§e[MobArenav2] Invulnerability shield expired! Fight on!");
            }
        }, 60L);

        // 6. Refresh scoreboards and broadcast
        if (profile != null && profile.getScoreboard() != null) {
            profile.getScoreboard().update(arena);
        }
        for (UUID activeUuid : arena.getActivePlayers()) {
            Player p = Bukkit.getPlayer(activeUuid);
            if (p != null && p.isOnline()) {
                ArenaPlayerProfile pProfile = arena.getPlayerProfiles().get(activeUuid);
                if (pProfile != null && pProfile.getScoreboard() != null) {
                    pProfile.getScoreboard().update(arena);
                }
            }
        }
        arena.broadcastMessage("§a§l[MobArenav2] §e" + player.getName() + " §ahas been REVIVED by an Admin and returned to the battle!");
        return true;
    }
}