package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.command.CommandSender;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.command.SubCommand;

import java.util.Collections;
import java.util.List;

public class KillMobsCommand implements SubCommand {

    @Override
    public String getName() {
        return "killmobs";
    }

    @Override
    public String getPermission() {
        return "mobarenav2.admin";
    }

    @Override
    public String getUsage() {
        return "/ma killmobs <arena>";
    }

    @Override
    public String getDescription() {
        return "Purge all active mobs and bosses in the specified arena";
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
        if (args.length < 2) {
            sender.sendMessage("§cUsage: " + getUsage());
            return true;
        }
        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            sender.sendMessage("§cArena not found!");
            return true;
        }
        int count = arena.killAllMobs(plugin);
        sender.sendMessage("§a[MobArenav2] Successfully eliminated §e" + count + " §amobs and bosses in arena §e" + arena.getName() + "§a!");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (args.length == 2) {
            return plugin.getArenaManager().getAllArenas().stream()
                    .map(Arena::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
