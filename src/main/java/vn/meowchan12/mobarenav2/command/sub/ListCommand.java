package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.command.CommandSender;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.command.SubCommand;

public class ListCommand implements SubCommand {

    @Override
    public String getName() {
        return "list";
    }

    @Override
    public String getPermission() {
        return "mobarenav2.player.list";
    }

    @Override
    public String getUsage() {
        return "/ma list";
    }

    @Override
    public String getDescription() {
        return "List all available arenas and their live statuses";
    }

    @Override
    public boolean isPlayerOnly() {
        return false;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args, MobArenav2 plugin) {
        sender.sendMessage("§a--- Available Arenas ---");
        if (plugin.getArenaManager().getAllArenas().isEmpty()) {
            sender.sendMessage("§7No arenas available.");
        } else {
            for (Arena a : plugin.getArenaManager().getAllArenas()) {
                String status = a.isRunning() ? "§c[In Progress]" : "§a[Waiting]";
                sender.sendMessage("§f- §e" + a.getName() + " " + status + " §7(" + a.getActivePlayers().size() + " players)");
            }
        }
        return true;
    }
}
