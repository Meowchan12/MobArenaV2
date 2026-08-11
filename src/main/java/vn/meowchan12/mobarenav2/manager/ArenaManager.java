package vn.meowchan12.mobarenav2.manager;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ArenaManager {

    private final MobArenav2 plugin;
    private final Map<String, Arena> arenas = new HashMap<>();

    public ArenaManager(MobArenav2 plugin) {
        this.plugin = plugin;
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
    // VÁ LỖI THAM GIA TRẬN ĐẤU (BUG #3)
    // ==========================================
    public boolean joinArena(Player player, String arenaName) {
        UUID uuid = player.getUniqueId();

        // 1. Chặn join nếu đang chơi (Lobby)
        if (isPlaying(player)) {
            player.sendMessage("§c[MobArenav2] You are already in an arena!");
            return false;
        }

        // 2. [MỚI] Chặn join nếu đang làm Spectator (Người chết)
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

        // 3. [MỚI] Chặn join nếu trận đấu đang diễn ra
        if (arena.isRunning()) {
            player.sendMessage("§c[MobArenav2] The battle in this arena has already started!");
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

                        player.setGameMode(GameMode.SURVIVAL);
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);

                        player.sendMessage("§a[MobArenav2] You have joined arena: §e" + arena.getName());
                        player.sendMessage("§b➡ Type §e/ma class §bto select your loadout.");

                        // =====================================================================
                        // [v5.2 FEATURE]: SERVER-WIDE JOIN ANNOUNCEMENT (ENGLISH)
                        // Broadcasts a stylish message to all players to attract team members!
                        // =====================================================================
                        int currentCount = arena.getActivePlayers().size();
                        int limitPlayers = (config != null) ? config.getInt("settings.player-limits.max-players", 99) : 99;
                        Bukkit.broadcastMessage("§6§l[MobArenav2] §e" + player.getName() + " §7has joined arena §b" + arena.getName() + "§7! §8[§e" + currentCount + "§7/§e" + limitPlayers + "§8] §bType §e/ma join " + arena.getName() + " §bto fight together!");
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

            player.setGameMode(GameMode.ADVENTURE);
            player.setInvulnerable(true);

            player.sendMessage("§7[MobArenav2] You are now spectating §e" + arena.getName());
        });

        return true;
    }

    public void handlePlayerDeath(Player player, Arena arena) {
        if (player == null || !player.isOnline()) return;

        arena.getSpectators().add(player.getUniqueId());

        // 1. Reset health and player state FIRST to stop death animation / knockback / fire
        resetPlayerState(player);
        player.getInventory().clear();
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.setInvulnerable(true);
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));

        // 2. Safe Spectator Location lookup (spectatorSpawn -> arenaSpawn -> lobby)
        Location specSpawn = arena.getSpectatorSpawn();
        if (specSpawn == null || specSpawn.getWorld() == null) {
            specSpawn = arena.getArenaSpawn();
        }
        if (specSpawn == null || specSpawn.getWorld() == null) {
            specSpawn = arena.getLobby();
        }

        final Location targetSpecLoc = specSpawn;

        // 3. Teleport 1 tick later so Bukkit/Paper completes the event pipeline cleanly!
        if (targetSpecLoc != null && targetSpecLoc.getWorld() != null) {
            targetSpecLoc.getWorld().getChunkAt(targetSpecLoc).addPluginChunkTicket(plugin);
            targetSpecLoc.getWorld().getChunkAt(targetSpecLoc).load(true);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && arena.getSpectators().contains(player.getUniqueId())) {
                    player.teleport(targetSpecLoc);
                }
            }, 1L);
        }

        arena.broadcastMessage("§c[MobArenav2] §e" + player.getName() + " §chas fallen and is now spectating!");

        Set<UUID> activePlayers = new HashSet<>(arena.getPlayerProfiles().keySet());
        activePlayers.removeAll(arena.getSpectators());

        if (activePlayers.isEmpty()) {
            arena.broadcastMessage("§c§l[MobArenav2] All players have died! The arena is over.");
            arena.endArena(plugin);
        }
    }

    public void resetPlayerState(Player player) {
        if (player == null || !player.isOnline()) return;

        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        AttributeInstance hpAttr = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH);
        if (hpAttr != null) hpAttr.setBaseValue(20.0);

        AttributeInstance dmgAttr = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.ATTACK_DAMAGE);
        if (dmgAttr != null) dmgAttr.setBaseValue(1.0);

        player.setVelocity(new Vector(0, 0, 0));
        player.setFallDistance(0.0f);

        player.setHealth(Math.min(player.getHealth(), 20.0));
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.setFireTicks(0);
        player.setInvulnerable(false);
        player.setAllowFlight(false);
        player.setFlying(false);
        player.setWalkSpeed(0.2f);
    }

    public void leaveArena(Player player) {
        Arena arena = getArenaByPlayer(player);
        if (arena == null) return;

        boolean wasSpectator = arena.getSpectators().contains(player.getUniqueId());
        ArenaPlayerProfile profile = arena.getPlayerProfiles().remove(player.getUniqueId());

        if (wasSpectator) {
            arena.getSpectators().remove(player.getUniqueId());
        }
        
        // [v6.4 HOTFIX] Remove from active votes if leaving mid-vote
        arena.getActiveVotes().remove(player.getUniqueId());

        player.getInventory().clear();
        player.setGameMode(GameMode.SURVIVAL);
        resetPlayerState(player);

        Location leaveLoc = null;
        if (profile != null && profile.getOriginalLocation() != null) {
            leaveLoc = profile.getOriginalLocation();
        } else if (arena.getLobby() != null && arena.getLobby().getWorld() != null) {
            leaveLoc = arena.getLobby().getWorld().getSpawnLocation();
        }

        if (leaveLoc != null && leaveLoc.getWorld() != null) {
            leaveLoc.getWorld().getChunkAt(leaveLoc).load(true);
            player.teleport(leaveLoc);
        }

        plugin.getInventoryBackupManager().restoreBackup(player);

        if (profile != null) {
            if (profile.getScoreboard() != null) profile.getScoreboard().remove();
            profile.distributeRewards(plugin);
            plugin.getUserDataManager().removeData(player);
        }

        player.sendMessage("§e[MobArenav2] You have left the arena and your items have been restored.");

        if (!wasSpectator && arena.isRunning()) {
            Set<UUID> activePlayers = new HashSet<>(arena.getPlayerProfiles().keySet());
            activePlayers.removeAll(arena.getSpectators());
            if (activePlayers.isEmpty()) {
                arena.broadcastMessage("§c§l[MobArenav2] All active players have left! The arena is over.");
                arena.endArena(plugin);
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
}