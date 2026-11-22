package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.command.SubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JoinCommand implements SubCommand {

    @Override
    public String getName() {
        return "join";
    }

    @Override
    public String getPermission() {
        return "mobarenav2.player.join";
    }

    @Override
    public String getUsage() {
        return "/ma join <arena>";
    }

    @Override
    public String getDescription() {
        return "Join a MobArena waiting lobby";
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args, MobArenav2 plugin) {
        Player player = (Player) sender;
        if (args.length < 2) {
            sender.sendMessage("§cUsage: " + getUsage());
            return true;
        }

        plugin.getArenaManager().joinArena(player, args[1]);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (args.length == 2) {
            List<String> arenas = new ArrayList<>();
            for (var a : plugin.getArenaManager().getAllArenas()) {
                if (a.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    arenas.add(a.getName());
                }
            }
            return arenas;
        }
        return Collections.emptyList();
    }
}
