package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.command.CommandSender;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.command.SubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * [v8.4.4]: Role-Aware Help Command.
 * Segregates Regular Player commands from Administrative commands with strict permission gating.
 */
public class HelpCommand implements SubCommand {

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public List<String> getAliases() {
        return List.of("?");
    }

    @Override
    public String getPermission() {
        return null; // Publicly accessible; content filtered by sender permissions
    }

    @Override
    public String getUsage() {
        return "/ma help [player|admin]";
    }

    @Override
    public String getDescription() {
        return "Display available commands categorized by role";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args, MobArenav2 plugin) {
        boolean isAdmin = sender.hasPermission("mobarenav2.admin");

        if (args.length >= 2) {
            String targetCategory = args[1].toLowerCase();
            if (targetCategory.equals("admin")) {
                if (!isAdmin) {
                    sender.sendMessage("§cYou do not have permission to view administrative commands.");
                    return true;
                }
                sendAdminHelp(sender);
                return true;
            } else if (targetCategory.equals("player")) {
                sendPlayerHelp(sender);
                return true;
            }
        }

        if (isAdmin) {
            sendAdminOverview(sender);
        } else {
            sendPlayerHelp(sender);
        }
        return true;
    }

    public void sendPlayerHelp(CommandSender sender) {
        sender.sendMessage("§6§l=== MobArenav2 Player Commands ===");
        if (hasPerm(sender, "mobarenav2.player.join")) {
            sender.sendMessage("§e/ma join <arena> §7- Join an arena lobby");
        }
        if (hasPerm(sender, "mobarenav2.player.leave")) {
            sender.sendMessage("§e/ma leave §7- Leave the current arena");
        }
        if (hasPerm(sender, "mobarenav2.player.join")) {
            sender.sendMessage("§e/ma ready §7- Toggle ready state in arena lobby");
            sender.sendMessage("§e/ma class [name] §7- Select class or open class selection GUI");
        }
        if (hasPerm(sender, "mobarenav2.player.spec")) {
            sender.sendMessage("§e/ma spec <arena> §7- Spectate an ongoing match");
        }
        if (hasPerm(sender, "mobarenav2.player.list")) {
            sender.sendMessage("§e/ma list §7- View list of available arenas");
        }
        if (hasPerm(sender, "mobarenav2.player.shop")) {
            sender.sendMessage("§e/ma shop [category] §7- Open permanent upgrade shop GUI");
        }
        if (hasPerm(sender, "mobarenav2.player.join")) {
            sender.sendMessage("§e/ma upgrades §7- Preview arena upgrade tree");
            sender.sendMessage("§e/ma myupgrades [player] §7- View active match upgrades & stats");
            sender.sendMessage("§e/ma fuse §7- Open Ultimate Fusion draft GUI");
        }
        if (hasPerm(sender, "mobarenav2.player.top")) {
            sender.sendMessage("§e/ma top <arena> [page] §7- View arena leaderboard");
        }
        if (hasPerm(sender, "mobarenav2.player.stats")) {
            sender.sendMessage("§e/ma stats [player] §7- View player lifetime statistics");
        }
        if (sender.hasPermission("mobarenav2.admin")) {
            sender.sendMessage("§7Tip: Type §e/ma help admin §7for administrative commands.");
        }
    }

    public void sendAdminOverview(CommandSender sender) {
        sender.sendMessage("§6§l=== MobArenav2 Master Command Overview ===");
        sender.sendMessage("§7Type §e/ma help player §7to view detailed player commands.");
        sender.sendMessage("§7Type §e/ma help admin §7to view complete admin commands breakdown.");
        sender.sendMessage("");
        sender.sendMessage("§e§l--- Quick Player Commands ---");
        sender.sendMessage("§e/ma join, leave, ready, class, spec, list, shop, upgrades, myupgrades, fuse, top, stats");
        sender.sendMessage("");
        sender.sendMessage("§c§l--- Admin: Match Management ---");
        sender.sendMessage("§c/ma forcestart <arena> §7- Force start match immediately");
        sender.sendMessage("§c/ma forceend <arena> §7- Force stop active match");
        sender.sendMessage("§c/ma skipwave <arena> §7- Skip current wave in arena");
        sender.sendMessage("§c/ma killmobs <arena> §7- Kill all mobs in arena");
        sender.sendMessage("§c/ma kick <player> §7- Kick player from arena");
        sender.sendMessage("§c/ma revive <player> §7- Revive player (100% HP, class, upgrades)");
        sender.sendMessage("");
        sender.sendMessage("§4§l--- Admin: Arena Setup & Config ---");
        sender.sendMessage("§c/ma setup <arena> §7- Interactive arena setup wizard");
        sender.sendMessage("§c/ma setp1/setp2 <arena> §7- Set arena boundary corners");
        sender.sendMessage("§c/ma setspectator <arena> §7- Set spectator location");
        sender.sendMessage("§c/ma savearena <arena> §7- Save and activate arena");
        sender.sendMessage("§c/ma arenaedit <arena> §7- Visual GUI arena editor");
        sender.sendMessage("§c/ma tp <arena> §7- Teleport to arena lobby");
        sender.sendMessage("");
        sender.sendMessage("§d§l--- Admin: System & Testing ---");
        sender.sendMessage("§c/ma reload §7- Reload all configuration files");
        sender.sendMessage("§c/ma summonboss <arena> <boss> §7- Summon test boss into arena");
        sender.sendMessage("§c/ma killtestboss <arena> §7- Kill active test boss");
        sender.sendMessage("§c/ma upgraderoll <player> §7- Open upgrade draft GUI for player");
        sender.sendMessage("§c/ma supply <arena> §7- Spawn supply drop chest in arena");
        sender.sendMessage("§c/ma inv <player> §7- Inspect player backup inventory");
        sender.sendMessage("§c/ma stats reset <player> §7- Reset player lifetime stats");
    }

    public void sendAdminHelp(CommandSender sender) {
        sender.sendMessage("§c§l=== MobArenav2 Administrative Commands ===");
        sender.sendMessage("§e§l[Match Management]");
        sender.sendMessage("  §c/ma forcestart <arena> §7- Force start match immediately");
        sender.sendMessage("  §c/ma forceend <arena> §7- Force terminate active match");
        sender.sendMessage("  §c/ma skipwave <arena> §7- Skip current wave in arena");
        sender.sendMessage("  §c/ma killmobs <arena> §7- Kill all mobs in arena");
        sender.sendMessage("  §c/ma kick <player> §7- Kick player from arena");
        sender.sendMessage("  §c/ma revive <player> §7- Revive fallen player (100% HP, class, upgrades)");
        sender.sendMessage("");
        sender.sendMessage("§4§l[Arena Setup & Configuration]");
        sender.sendMessage("  §c/ma setup <arena> §7- Interactive arena setup wizard");
        sender.sendMessage("  §c/ma setp1 <arena> §7- Set first boundary corner point");
        sender.sendMessage("  §c/ma setp2 <arena> §7- Set second boundary corner point");
        sender.sendMessage("  §c/ma setspectator <arena> §7- Set spectator warp point");
        sender.sendMessage("  §c/ma savearena <arena> §7- Validate, save and activate arena");
        sender.sendMessage("  §c/ma arenaedit <arena> §7- Open visual GUI arena editor");
        sender.sendMessage("  §c/ma tp <arena> §7- Teleport to arena lobby");
        sender.sendMessage("");
        sender.sendMessage("§d§l[System & Testing]");
        sender.sendMessage("  §c/ma reload §7- Reload all configs, classes, upgrades, shops");
        sender.sendMessage("  §c/ma summonboss <arena> <boss> §7- Summon test boss into arena");
        sender.sendMessage("  §c/ma killtestboss <arena> §7- Kill active test boss");
        sender.sendMessage("  §c/ma upgraderoll <player> §7- Open upgrade draft GUI for player");
        sender.sendMessage("  §c/ma supply <arena> §7- Spawn supply drop chest in arena");
        sender.sendMessage("  §c/ma inv <player> §7- Inspect player backup inventory");
        sender.sendMessage("  §c/ma stats reset <player> §7- Reset player lifetime statistics");
    }

    private boolean hasPerm(CommandSender sender, String perm) {
        return perm == null || sender.hasPermission(perm) || sender.hasPermission("mobarenav2.admin");
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (args.length == 2) {
            List<String> list = new ArrayList<>();
            list.add("player");
            if (sender.hasPermission("mobarenav2.admin")) {
                list.add("admin");
            }
            return list.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
