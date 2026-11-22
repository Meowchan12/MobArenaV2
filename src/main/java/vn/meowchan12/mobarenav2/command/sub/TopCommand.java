package vn.meowchan12.mobarenav2.command.sub;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.command.SubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TopCommand implements SubCommand {

    private record PlayerStat(String name, String clazz, int kills, double damage, int bossKills) {}

    @Override
    public String getName() {
        return "top";
    }

    @Override
    public String getPermission() {
        return "mobarenav2.player.top";
    }

    @Override
    public String getUsage() {
        return "/ma top <arena> [page]";
    }

    @Override
    public String getDescription() {
        return "View top records and previous match leaderboards";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: " + getUsage());
            return true;
        }

        String arenaName = args[1];
        Arena arena = plugin.getArenaManager().getArena(arenaName);
        if (arena == null) {
            sender.sendMessage("§cArena not found!");
            return true;
        }

        if (args.length >= 4 && (args[2].equalsIgnoreCase("detail") || args[2].equalsIgnoreCase("gui"))) {
            if (sender instanceof Player p) {
                vn.meowchan12.mobarenav2.arena.MatchDetailGUI.open(p, arena, args[3], plugin);
            } else {
                sender.sendMessage("§cOnly in-game players can open the Match Details GUI!");
            }
            return true;
        }

        ConfigurationSection summary = arena.getConfig() != null ? arena.getConfig().getConfigurationSection("last-match-summary") : null;
        if (summary == null && arena.getConfig() != null) {
            summary = arena.getConfig().getConfigurationSection("last-match");
        }
        if (summary == null) {
            summary = arena.getMemoryMatchSummary();
        }
        if (summary == null) {
            sender.sendMessage("§cNo match history found for arena §e" + arena.getName() + "§c!");
            return true;
        }

        int wave = summary.getInt("wave", 0);
        int maxWaves = summary.getInt("max-waves", arena.getConfig().getInt("settings.max-waves", 20));
        boolean isEndless = summary.getBoolean("endless", summary.getBoolean("is-endless", false));
        ConfigurationSection playersSec = summary.getConfigurationSection("players");

        if (playersSec == null || playersSec.getKeys(false).isEmpty()) {
            sender.sendMessage("§cNo player records found for the last match!");
            return true;
        }

        List<PlayerStat> stats = new ArrayList<>();
        for (String uuidStr : playersSec.getKeys(false)) {
            String path = uuidStr + ".";
            String name = playersSec.getString(path + "name", "Unknown");
            String clazz = playersSec.getString(path + "class", "None");
            int kills = playersSec.getInt(path + "kills", 0);
            double damage = playersSec.getDouble(path + "damage", 0.0);
            int bossKills = playersSec.getInt(path + "bossKills", 0);
            stats.add(new PlayerStat(name, clazz, kills, damage, bossKills));
        }

        stats.sort((a, b) -> Integer.compare(b.kills, a.kills));

        int page = 1;
        if (args.length >= 3) {
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                if (sender instanceof Player p) {
                    if (vn.meowchan12.mobarenav2.arena.MatchDetailGUI.open(p, arena, args[2], plugin)) {
                        return true;
                    }
                }
            }
        }

        int perPage = 5;
        int totalPages = (int) Math.ceil((double) stats.size() / perPage);
        if (totalPages == 0) totalPages = 1;
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        String waveDisplay = isEndless ? (wave + " §8(Endless)") : (wave + "§7/§b" + maxWaves);

        sender.sendMessage("§8§m----------------------------------");
        sender.sendMessage("§6§l MATCH SUMMARY §7- §e" + arena.getName());
        sender.sendMessage("§f Final Wave: §b" + waveDisplay);
        String durationStr = summary.getString("duration", null);
        if (durationStr != null && !durationStr.isEmpty()) {
            boolean isVictory = (wave >= maxWaves && !isEndless);
            String timeTag = isVictory ? " Clear Time: §a" : " Survival Time: §b";
            sender.sendMessage("§f" + timeTag + durationStr);
        }
        sender.sendMessage("§8§m----------------------------------");

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, stats.size());

        Player player = (sender instanceof Player p) ? p : null;

        for (int i = start; i < end; i++) {
            PlayerStat stat = stats.get(i);
            int rank = i + 1;
            String rankBadge;
            if (rank == 1) rankBadge = "§e🥇 #1.";
            else if (rank == 2) rankBadge = "§f🥈 #2.";
            else if (rank == 3) rankBadge = "§6🥉 #3.";
            else rankBadge = "§7   #" + rank + ".";

            if (player != null) {
                TextComponent line = new TextComponent(rankBadge + " §f" + stat.name + " §8── §c" + stat.kills + " Kills §8(§7" + stat.clazz + "§8) ");

                TextComponent detailBtn = new TextComponent("§a§l[CLICK HERE]");
                String hoverText = "§6§l" + stat.name + " §7(Match Overview)\n" +
                        "§e• Combat Class: §b" + stat.clazz + "\n" +
                        "§e• Mobs Eliminated: §c" + stat.kills + " Mobs\n" +
                        "§e• Bosses Slain: §d" + stat.bossKills + " Bosses\n" +
                        "§e• Damage Dealt: §a" + String.format("%.0f", stat.damage) + " DMG\n" +
                        "§8§m--------------------\n" +
                        "§e▶ Click to inspect full match details GUI!";
                detailBtn.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
                        net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
                        new net.md_5.bungee.api.chat.ComponentBuilder(hoverText).create()
                ));
                detailBtn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ma top " + arena.getName() + " detail " + stat.name));
                line.addExtra(detailBtn);

                player.spigot().sendMessage(line);
            } else {
                sender.sendMessage(rankBadge + " §f" + stat.name + " §8── §c" + stat.kills + " Kills §7(" + stat.clazz + ") §8[DMG: " + String.format("%.0f", stat.damage) + "]");
            }
        }

        sender.sendMessage(" ");

        if (player != null) {
            TextComponent footer = new TextComponent("§7Page " + page + "/" + totalPages + "   ");
            if (page > 1) {
                TextComponent prev = new TextComponent("§a[◀ Previous] ");
                prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ma top " + arena.getName() + " " + (page - 1)));
                footer.addExtra(prev);
            }
            if (page < totalPages) {
                TextComponent next = new TextComponent("§a[Next ▶]");
                next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/ma top " + arena.getName() + " " + (page + 1)));
                footer.addExtra(next);
            }
            player.spigot().sendMessage(footer);
        } else {
            sender.sendMessage("§7Page " + page + "/" + totalPages);
        }
        sender.sendMessage("§8§m----------------------------------");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (args.length == 2) {
            List<String> list = new ArrayList<>();
            for (var a : plugin.getArenaManager().getAllArenas()) {
                if (a.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    list.add(a.getName());
                }
            }
            return list;
        }
        if (args.length == 3) {
            Arena a = plugin.getArenaManager().getArena(args[1]);
            List<String> list = new ArrayList<>(List.of("1", "2", "3", "detail"));
            if (a != null) {
                ConfigurationSection sec = a.getConfig() != null ? a.getConfig().getConfigurationSection("last-match-summary.players") : null;
                if (sec == null && a.getConfig() != null) sec = a.getConfig().getConfigurationSection("last-match.players");
                if (sec == null && a.getMemoryMatchSummary() != null) sec = a.getMemoryMatchSummary().getConfigurationSection("players");
                if (sec != null) {
                    for (String key : sec.getKeys(false)) {
                        String pName = sec.getString(key + ".name");
                        if (pName != null && !pName.isEmpty() && !pName.equals("Unknown")) {
                            list.add(pName);
                        }
                    }
                }
            }
            return list.stream().filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase())).toList();
        }
        if (args.length == 4 && args[2].equalsIgnoreCase("detail")) {
            Arena a = plugin.getArenaManager().getArena(args[1]);
            if (a != null) {
                ConfigurationSection sec = a.getConfig() != null ? a.getConfig().getConfigurationSection("last-match-summary.players") : null;
                if (sec == null && a.getConfig() != null) sec = a.getConfig().getConfigurationSection("last-match.players");
                if (sec == null && a.getMemoryMatchSummary() != null) sec = a.getMemoryMatchSummary().getConfigurationSection("players");
                if (sec != null) {
                    List<String> players = new ArrayList<>();
                    for (String key : sec.getKeys(false)) {
                        String pName = sec.getString(key + ".name");
                        if (pName != null && !pName.isEmpty() && !pName.equals("Unknown")) {
                            players.add(pName);
                        }
                    }
                    return players.stream().filter(s -> s.toLowerCase().startsWith(args[3].toLowerCase())).toList();
                }
            }
        }
        return Collections.emptyList();
    }
}
