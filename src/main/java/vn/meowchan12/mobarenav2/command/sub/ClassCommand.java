package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ClassGUI;
import vn.meowchan12.mobarenav2.command.SubCommand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ClassCommand implements SubCommand {

    @Override
    public String getName() {
        return "class";
    }

    @Override
    public String getPermission() {
        return "mobarenav2.player.join";
    }

    @Override
    public String getUsage() {
        return "/ma class [class_name]";
    }

    @Override
    public String getDescription() {
        return "Open the class selection menu or select a class directly";
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args, MobArenav2 plugin) {
        Player player = (Player) sender;
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena == null) {
            player.sendMessage("§cYou must join an arena first to select a class!");
            return true;
        }
        if (arena.isRunning()) {
            player.sendMessage("§cYou cannot change your class after the arena has started!");
            return true;
        }

        if (args.length >= 2) {
            String targetClass = args[1];
            plugin.getClassManager().equipClass(player, targetClass);
        } else {
            ClassGUI.open(player, plugin);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (args.length == 2) {
            List<String> list = new ArrayList<>();
            for (String c : plugin.getClassManager().getAvailableClasses()) {
                if (c.toLowerCase().startsWith(args[1].toLowerCase())) {
                    list.add(c);
                }
            }
            return list;
        }
        return Collections.emptyList();
    }
}
