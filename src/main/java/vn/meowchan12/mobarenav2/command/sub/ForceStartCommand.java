package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.command.CommandSender;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.command.SubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ForceStartCommand implements SubCommand {

    @Override
    public String getName() {
        return "forcestart";
    }

    @Override
    public String getPermission() {
        return "mobarenav2.admin";
    }

    @Override
    public String getUsage() {
        return "/ma forcestart <arena>";
    }

    @Override
    public String getDescription() {
        return "Force an arena match to start immediately";
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

        Arena arena = plugin.getArenaManager().getArena(args[1]);
        if (arena == null) {
            sender.sendMessage("§cArena not found!");
            return true;
        }
        if (arena.isRunning()) {
            sender.sendMessage("§cThis arena is already running!");
            return true;
        }
        if (arena.getActivePlayers().isEmpty()) {
            sender.sendMessage("§cCannot start an empty arena! At least 1 player must be in lobby.");
            return true;
        }

        boolean started = arena.forceStart(plugin);
        if (started) {
            sender.sendMessage("§a[MobArenav2] Successfully force-started arena §e" + arena.getName() + "§a!");
        } else {
            sender.sendMessage("§cFailed to force start arena.");
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (args.length == 2) {
            List<String> list = new ArrayList<>();
            for (var a : plugin.getArenaManager().getAllArenas()) {
                if (!a.isRunning() && a.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    list.add(a.getName());
                }
            }
            return list;
        }
        return Collections.emptyList();
    }
}
