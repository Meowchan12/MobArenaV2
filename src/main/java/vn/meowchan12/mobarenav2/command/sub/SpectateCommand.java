package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.command.SubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SpectateCommand implements SubCommand {

    @Override
    public String getName() {
        return "spec";
    }

    @Override
    public List<String> getAliases() {
        return List.of("spectate");
    }

    @Override
    public String getPermission() {
        return "mobarenav2.player.spec";
    }

    @Override
    public String getUsage() {
        return "/ma spec <arena>";
    }

    @Override
    public String getDescription() {
        return "Spectate an ongoing arena battle";
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args, MobArenav2 plugin) {
        Player player = (Player) sender;
        if (args.length < 2) {
            player.sendMessage("§cUsage: " + getUsage());
            return true;
        }

        plugin.getArenaManager().joinSpectator(player, args[1]);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (args.length == 2) {
            List<String> list = new ArrayList<>();
            for (var a : plugin.getArenaManager().getAllArenas()) {
                if (a.isRunning() && a.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    list.add(a.getName());
                }
            }
            return list;
        }
        return Collections.emptyList();
    }
}
