package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.command.SubCommand;

public class LeaveCommand implements SubCommand {

    @Override
    public String getName() {
        return "leave";
    }

    @Override
    public String getPermission() {
        return "mobarenav2.player.leave";
    }

    @Override
    public String getUsage() {
        return "/ma leave";
    }

    @Override
    public String getDescription() {
        return "Leave your current arena match or lobby";
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args, MobArenav2 plugin) {
        Player player = (Player) sender;
        plugin.getArenaManager().leaveArena(player);
        return true;
    }
}
