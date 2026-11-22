package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.command.SubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class StatsCommand implements SubCommand {

    @Override
    public String getName() {
        return "stats";
    }

    @Override
    public String getPermission() {
        return "mobarenav2.player.stats";
    }

    @Override
    public String getUsage() {
        return "/ma stats [player]";
    }

    @Override
    public String getDescription() {
        return "View your lifetime arena statistics and achievements";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("mobarenav2.admin")) {
                sender.sendMessage("§cYou do not have permission to reset player stats.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage("§cUsage: /ma stats reset <player>");
                return true;
            }
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
            if (target.getName() == null) {
                sender.sendMessage("§cPlayer not found.");
                return true;
            }
            plugin.getUserDataManager().resetData(target.getUniqueId());
            sender.sendMessage("§a[MobArenav2] Reset stats for player §e" + target.getName() + "§a!");
            return true;
        }

        OfflinePlayer target;
        if (args.length >= 2) {
            String targetName = args[1].equalsIgnoreCase("info") && args.length >= 3 ? args[2] : args[1];
            @SuppressWarnings("deprecation")
            OfflinePlayer found = Bukkit.getOfflinePlayer(targetName);
            target = found;
        } else if (sender instanceof Player p) {
            target = p;
        } else {
            sender.sendMessage("§cConsole usage: /ma stats <player>");
            return true;
        }

        UUID uuid = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : "Unknown";

        int kills = plugin.getUserDataManager().getStat(uuid, "lifetime_kills");
        int wins = plugin.getUserDataManager().getStat(uuid, "games_won");
        int gamesPlayed = plugin.getUserDataManager().getStat(uuid, "games_played");
        int highestWave = plugin.getUserDataManager().getStat(uuid, "highest_wave");
        String favClass = plugin.getUserDataManager().getFavoriteClass(uuid);

        sender.sendMessage("§8§m----------------------------------");
        sender.sendMessage("§6§l LIFETIME STATS §7- §e" + name);
        sender.sendMessage("§e Kills: §a" + kills);
        sender.sendMessage("§e Wins: §a" + wins);
        sender.sendMessage("§e Games Played: §a" + gamesPlayed);
        sender.sendMessage("§e Highest Wave: §a" + highestWave);
        sender.sendMessage("§e Favorite Class: §a" + favClass);
        sender.sendMessage("§8§m----------------------------------");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (args.length == 2) {
            List<String> list = new ArrayList<>(List.of("info"));
            if (sender.hasPermission("mobarenav2.admin")) {
                list.add("reset");
            }
            for (Player p : Bukkit.getOnlinePlayers()) {
                list.add(p.getName());
            }
            return list.stream().filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase())).toList();
        }
        if (args.length == 3 && (args[1].equalsIgnoreCase("info") || args[1].equalsIgnoreCase("reset"))) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).toList();
        }
        return Collections.emptyList();
    }
}
