package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.arena.ArenaRestorer;
import vn.meowchan12.mobarenav2.arena.MobFactory;
import vn.meowchan12.mobarenav2.arena.UpgradeGUI;
import vn.meowchan12.mobarenav2.command.SubCommand;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AdminArenaCommand implements SubCommand {

    private static final List<String> ALIASES = Arrays.asList(
            "setp1", "setp2", "setspectator", "savearena", "save", "arenaedit", "tp",
            "supply", "inv", "setupclass", "skipwave", "summonboss", "killtestboss", "upgraderoll", "revive"
    );

    @Override
    public String getName() {
        return "setup";
    }

    @Override
    public List<String> getAliases() {
        return ALIASES;
    }

    @Override
    public String getPermission() {
        return "mobarenav2.admin";
    }

    @Override
    public String getUsage() {
        return "/ma setup <arena>";
    }

    @Override
    public String getDescription() {
        return "Interactive arena setup and administration wizard";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (!sender.hasPermission(getPermission())) {
            sender.sendMessage("§cYou do not have permission to use admin commands.");
            return true;
        }

        String sub = args[0].toLowerCase();
        Player player = (sender instanceof Player p) ? p : null;

        switch (sub) {
            case "arenaedit" -> {
                if (player == null) {
                    sender.sendMessage("§cOnly in-game players can toggle arena edit mode!");
                    return true;
                }
                plugin.getSetupManager().toggleEditMode(player);
                return true;
            }

            case "setup" -> {
                if (player == null) {
                    sender.sendMessage("§cOnly in-game players can use visual setup mode!");
                    return true;
                }
                if (plugin.getSetupManager().isInSetupMode(player)) {
                    plugin.getSetupManager().exitSetupMode(player);
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /ma setup <arena_name>");
                    return true;
                }
                String arenaName = args[1].toLowerCase();
                Arena arena = plugin.getArenaManager().getArena(arenaName);
                if (arena == null) {
                    plugin.getConfigManager().createArenaTemplate(arenaName);
                    arena = new Arena(arenaName);
                    File file = new File(plugin.getConfigManager().getArenasFolder(), arenaName + ".yml");
                    arena.setConfig(org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file));
                    plugin.getArenaManager().addArena(arena);
                    plugin.getConfigManager().ensureArenaUpgradeConfig(arenaName);
                    player.sendMessage("§aCreated new arena blueprint: §e" + arenaName);
                }
                plugin.getSetupManager().enterSetupMode(player, arena);
                return true;
            }

            case "setp1" -> {
                if (player == null) { sender.sendMessage("§cOnly players can set points!"); return true; }
                player.sendMessage("§aPoint 1 has been set at your current location.");
                return true;
            }

            case "setp2" -> {
                if (player == null) { sender.sendMessage("§cOnly players can set points!"); return true; }
                player.sendMessage("§aPoint 2 has been set at your current location.");
                return true;
            }

            case "setspectator" -> {
                if (player == null) { sender.sendMessage("§cOnly players can set spectator spawn!"); return true; }
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /ma setspectator <arena>");
                    return true;
                }
                Arena arena = plugin.getArenaManager().getArena(args[1]);
                if (arena != null) {
                    arena.setSpectatorSpawn(player.getLocation());
                    plugin.getConfigManager().saveArenaCoords(arena);
                    player.sendMessage("§aSpectator spawn point has been set at your location for arena: §e" + arena.getName());
                } else {
                    player.sendMessage("§cArena not found!");
                }
                return true;
            }

            case "savearena" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /ma savearena <arena>");
                    return true;
                }
                String arenaName = args[1];
                Arena arena = plugin.getArenaManager().getArena(arenaName);
                if (arena != null) {
                    sender.sendMessage("§aStarting block backup for arena: §e" + arenaName + "§a...");
                    new ArenaRestorer(plugin).backupArena(arena);
                } else {
                    sender.sendMessage("§cArena not found!");
                }
                return true;
            }

            case "save" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /ma save <arena>");
                    return true;
                }
                String arenaName = args[1].toLowerCase();
                Arena arena = plugin.getArenaManager().getArena(arenaName);
                if (arena != null) {
                    plugin.getConfigManager().saveArenaCoords(arena);
                    sender.sendMessage("§a[MobArenav2] Coordinates for arena §e" + arena.getName() + " §ahave been successfully saved to file!");
                } else {
                    sender.sendMessage("§cArena not found!");
                }
                return true;
            }

            case "tp" -> {
                if (player == null) {
                    sender.sendMessage("§cOnly in-game players can teleport to an arena!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /ma tp <arena>");
                    return true;
                }
                Arena arena = plugin.getArenaManager().getArena(args[1]);
                if (arena == null) {
                    player.sendMessage("§cArena not found!");
                    return true;
                }
                if (arena.getLobby() != null) {
                    player.teleport(arena.getLobby());
                    player.sendMessage("§aTeleported to the lobby of §e" + arena.getName());
                } else if (arena.getArenaSpawn() != null) {
                    player.teleport(arena.getArenaSpawn());
                    player.sendMessage("§aTeleported to the spawn of §e" + arena.getName());
                } else {
                    player.sendMessage("§cThis arena has no spawn or lobby set yet!");
                }
                return true;
            }

            case "skipwave" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /ma skipwave <arena> <wave>");
                    return true;
                }
                Arena arena = plugin.getArenaManager().getArena(args[1]);
                if (arena == null) {
                    sender.sendMessage("§cArena not found!");
                    return true;
                }
                if (!arena.isRunning()) {
                    sender.sendMessage("§cThis arena is not currently running!");
                    return true;
                }
                int targetWave;
                try {
                    targetWave = Integer.parseInt(args[2]);
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid wave number format!");
                    return true;
                }
                plugin.getWaveManager().forceSkipWave(arena, targetWave);
                sender.sendMessage("§a[MobArenav2] Successfully forced arena §e" + arena.getName() + " §ato skip to wave §e" + targetWave + "!");
                return true;
            }

            case "inv" -> {
                if (player == null) {
                    sender.sendMessage("§cOnly in-game players can inspect inventory backups!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /ma inv <player>");
                    return true;
                }
                String targetName = args[1];
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                if (plugin.getInventoryBackupManager() != null) {
                    String displayName = target.getName() != null ? target.getName() : targetName;
                    plugin.getInventoryBackupManager().openBackupGUI(player, target.getUniqueId(), displayName);
                } else {
                    player.sendMessage("§c[MobArenav2] The Inventory Backup System is currently disabled or failed to load!");
                }
                return true;
            }

            case "setupclass" -> {
                if (player == null) {
                    sender.sendMessage("§cOnly in-game players can setup classes via GUI!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /ma setupclass <class_name>");
                    return true;
                }
                String className = args[1];
                if (!plugin.getClassManager().getAvailableClasses().contains(className)) {
                    plugin.getConfigManager().getClasses().set("classes." + className + ".limit", -1);
                    player.sendMessage("§a[MobArenav2] Creating new class profile: §e" + className);
                }
                vn.meowchan12.mobarenav2.arena.ClassSetupGUI.open(player, plugin, className);
                return true;
            }

            case "supply" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /ma supply <create|delete|reset|edit> <id>");
                    return true;
                }
                String action = args[1].toLowerCase();
                String id = args[2].toLowerCase();
                switch (action) {
                    case "create" -> {
                        if (plugin.getSupplyManager().createSupply(id)) {
                            sender.sendMessage("§aCreated supply drop: §e" + id);
                        } else {
                            sender.sendMessage("§cSupply '" + id + "' already exists!");
                        }
                    }
                    case "delete" -> {
                        if (plugin.getSupplyManager().deleteSupply(id)) {
                            sender.sendMessage("§aDeleted supply drop: §e" + id);
                        } else {
                            sender.sendMessage("§cSupply '" + id + "' does not exist!");
                        }
                    }
                    case "reset" -> {
                        if (plugin.getSupplyManager().resetSupply(id)) {
                            sender.sendMessage("§aCleared all items from supply: §e" + id);
                        } else {
                            sender.sendMessage("§cSupply '" + id + "' does not exist!");
                        }
                    }
                    case "edit" -> {
                        if (player == null) {
                            sender.sendMessage("§cOnly in-game players can edit supplies via GUI!");
                            return true;
                        }
                        plugin.getSupplyManager().openEditGUI(player, id);
                    }
                    default -> sender.sendMessage("§cUnknown supply action. Use create/delete/reset/edit.");
                }
                return true;
            }

            case "summonboss" -> {
                if (player == null) {
                    sender.sendMessage("§cOnly in-game players can summon test bosses!");
                    return true;
                }
                if (args.length < 2) {
                    player.sendMessage("§cUsage: /ma summonboss <boss_name>");
                    return true;
                }
                String bossName = args[1];
                LivingEntity spawned = new MobFactory(plugin).spawnArenaMob(null, bossName, player.getLocation(), null, null);
                if (spawned != null) {
                    player.sendMessage("§a[MobArenav2] Standalone Boss §e" + bossName + " §aspawned successfully at your location for testing!");
                    player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
                } else {
                    player.sendMessage("§c[MobArenav2] Failed to spawn boss '" + bossName + "'. Check bosses.yml to verify the boss key.");
                }
                return true;
            }

            case "killtestboss" -> {
                if (player == null) {
                    sender.sendMessage("§cOnly in-game players can execute this command!");
                    return true;
                }
                double radius = 50.0;
                if (args.length >= 2) {
                    try {
                        radius = Double.parseDouble(args[1]);
                    } catch (NumberFormatException ignored) {}
                }
                int killed = 0;
                org.bukkit.NamespacedKey bossKey = new org.bukkit.NamespacedKey(plugin, "arena_boss_id");
                for (Entity ent : player.getWorld().getNearbyEntities(player.getLocation(), radius, radius, radius)) {
                    if (ent instanceof LivingEntity le && !(ent instanceof Player)) {
                        if (ent.hasMetadata("MA_TEST_BOSS") ||
                            ent.getPersistentDataContainer().has(bossKey, org.bukkit.persistence.PersistentDataType.STRING) ||
                            (ent.hasMetadata("MA_MOB") && !ent.getMetadata("MA_MOB").isEmpty() && "STANDALONE".equals(ent.getMetadata("MA_MOB").get(0).asString()))) {
                            le.setHealth(0.0);
                            le.remove();
                            killed++;
                        }
                    }
                }
                player.sendMessage("§a[MobArenav2] Successfully purged §e" + killed + " §atest boss(es) within radius §e" + radius + "§a!");
                return true;
            }

            case "upgraderoll" -> {
                if (args.length < 3) {
                    sender.sendMessage("§cUsage: /ma upgraderoll <amount> <me|all|player>");
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[1]);
                    if (amount <= 0) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    sender.sendMessage("§cInvalid amount! Please specify a positive integer.");
                    return true;
                }

                String targetType = args[2].toLowerCase();
                if (targetType.equals("me")) {
                    if (player == null) {
                        sender.sendMessage("§cOnly in-game players can target 'me'!");
                        return true;
                    }
                    Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
                    if (arena == null || !arena.isRunning()) {
                        player.sendMessage("§c[MobArenav2] You must be currently playing in an active arena match to receive upgrade rolls!");
                        return true;
                    }
                    ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
                    if (profile != null) {
                        profile.addPendingUpgradeChoices(amount, false);
                    }
                    UpgradeGUI.open(player, plugin);
                    player.sendMessage("§a[MobArenav2] Successfully granted §e" + amount + " §atest upgrade roll(s) to yourself!");
                } else if (targetType.equals("all")) {
                    Arena targetArena = null;
                    if (player != null) {
                        targetArena = plugin.getArenaManager().getArenaByPlayer(player);
                    }
                    if (targetArena == null && args.length >= 4) {
                        targetArena = plugin.getArenaManager().getArena(args[3]);
                    }
                    if (targetArena == null || !targetArena.isRunning()) {
                        sender.sendMessage("§c[MobArenav2] You must either be in a running arena or specify a running arena: /ma upgraderoll <amount> all <arena>");
                        return true;
                    }
                    for (UUID uuid : targetArena.getActivePlayers()) {
                        ArenaPlayerProfile prof = targetArena.getPlayerProfiles().get(uuid);
                        if (prof != null) {
                            prof.addPendingUpgradeChoices(amount, false);
                        }
                        Player p = Bukkit.getPlayer(uuid);
                        if (p != null && p.isOnline()) {
                            UpgradeGUI.open(p, plugin);
                        }
                    }
                    targetArena.broadcastMessage("§a[MobArenav2] Administrator granted §e" + amount + " §atest upgrade roll(s) to all fighters!");
                    sender.sendMessage("§a[MobArenav2] Successfully granted §e" + amount + " §atest upgrade roll(s) to all players in arena §e" + targetArena.getName() + "§a!");
                } else {
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target == null || !target.isOnline()) {
                        sender.sendMessage("§cPlayer not found or offline!");
                        return true;
                    }
                    Arena arena = plugin.getArenaManager().getArenaByPlayer(target);
                    if (arena == null || !arena.isRunning()) {
                        sender.sendMessage("§cPlayer §e" + target.getName() + " §cis not currently playing in an active arena match!");
                        return true;
                    }
                    ArenaPlayerProfile profile = arena.getPlayerProfiles().get(target.getUniqueId());
                    if (profile != null) {
                        profile.addPendingUpgradeChoices(amount, false);
                    }
                    UpgradeGUI.open(target, plugin);
                    target.sendMessage("§a[MobArenav2] An administrator granted you §e" + amount + " §atest upgrade roll(s)!");
                    sender.sendMessage("§a[MobArenav2] Successfully granted §e" + amount + " §atest upgrade roll(s) to §e" + target.getName() + "§a!");
                }
                return true;
            }

            case "revive" -> {
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /ma revive <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage("§cPlayer not found or offline!");
                    return true;
                }
                Arena arena = plugin.getArenaManager().getArenaByPlayer(target);
                if (arena == null || !arena.isRunning()) {
                    sender.sendMessage("§cPlayer §e" + target.getName() + " §cis not participating in any active running arena match!");
                    return true;
                }
                if (!arena.getSpectators().contains(target.getUniqueId())) {
                    sender.sendMessage("§cPlayer §e" + target.getName() + " §cis already active (not a spectator)!");
                    return true;
                }
                boolean success = plugin.getArenaManager().revivePlayerInArena(target, arena);
                if (success) {
                    sender.sendMessage("§a[MobArenav2] Successfully revived §e" + target.getName() + " §ain arena §e" + arena.getName() + "§a!");
                } else {
                    sender.sendMessage("§cFailed to revive player §e" + target.getName() + "§c.");
                }
                return true;
            }

            default -> {
                sender.sendMessage("§cUnknown admin command: " + sub);
                return true;
            }
        }
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (!sender.hasPermission(getPermission())) return Collections.emptyList();

        String sub = args[0].toLowerCase();
        if (args.length == 2) {
            switch (sub) {
                case "setup", "save", "savearena", "tp", "setspectator" -> {
                    return plugin.getArenaManager().getAllArenas().stream()
                            .map(Arena::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
                case "skipwave" -> {
                    return plugin.getArenaManager().getAllArenas().stream()
                            .filter(Arena::isRunning)
                            .map(Arena::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
                case "revive" -> {
                    return Bukkit.getOnlinePlayers().stream()
                            .filter(p -> {
                                Arena a = plugin.getArenaManager().getArenaByPlayer(p);
                                return a != null && a.getSpectators().contains(p.getUniqueId());
                            })
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
                case "supply" -> {
                    return List.of("create", "delete", "reset", "edit").stream()
                            .filter(s -> s.startsWith(args[1].toLowerCase()))
                            .toList();
                }
                case "inv" -> {
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
                case "setupclass" -> {
                    return plugin.getClassManager().getAvailableClasses().stream()
                            .filter(c -> c.toLowerCase().startsWith(args[1].toLowerCase()))
                            .toList();
                }
                case "summonboss" -> {
                    if (plugin.getBossManager() != null && plugin.getBossManager().getBossesConfig() != null) {
                        return plugin.getBossManager().getBossesConfig().getKeys(false).stream()
                                .filter(k -> k.toLowerCase().startsWith(args[1].toLowerCase()))
                                .toList();
                    }
                    return Collections.emptyList();
                }
                case "killtestboss" -> {
                    return List.of("10", "25", "50", "100").stream()
                            .filter(s -> s.startsWith(args[1]))
                            .toList();
                }
                case "upgraderoll" -> {
                    return List.of("1", "3", "5", "10").stream()
                            .filter(s -> s.startsWith(args[1]))
                            .toList();
                }
            }
        }
        if (args.length == 3) {
            if (sub.equals("supply")) {
                String action = args[1].toLowerCase();
                if (plugin.getSupplyManager() != null && (action.equals("delete") || action.equals("reset") || action.equals("edit"))) {
                    return plugin.getSupplyManager().getAllSupplyIds().stream()
                            .filter(id -> id.toLowerCase().startsWith(args[2].toLowerCase()))
                            .toList();
                }
            } else if (sub.equals("skipwave")) {
                return List.of("1", "5", "10", "15", "20").stream()
                        .filter(w -> w.startsWith(args[2]))
                        .toList();
            } else if (sub.equals("upgraderoll")) {
                List<String> targets = new ArrayList<>(Arrays.asList("me", "all"));
                targets.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                return targets.stream().filter(t -> t.toLowerCase().startsWith(args[2].toLowerCase())).toList();
            }
        }
        if (args.length == 4 && sub.equals("upgraderoll") && args[2].equalsIgnoreCase("all")) {
            return plugin.getArenaManager().getAllArenas().stream()
                    .filter(Arena::isRunning)
                    .map(Arena::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[3].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
