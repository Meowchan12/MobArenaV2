package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.command.SubCommand;

import java.util.Collections;
import java.util.List;

public class KickCommand implements SubCommand {

    @Override
    public String getName() {
        return "kick";
    }

    @Override
    public String getPermission() {
        return "mobarenav2.admin";
    }

    @Override
    public String getUsage() {
        return "/ma kick <player>";
    }

    @Override
    public String getDescription() {
        return "Forcefully kick a player from their current arena";
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
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("§cPlayer not found or offline!");
            return true;
        }
        Arena targetArena = plugin.getArenaManager().getArenaByPlayer(target);
        if (targetArena == null) {
            for (Arena a : plugin.getArenaManager().getAllArenas()) {
                if (a.getSpectators().contains(target.getUniqueId())) {
                    targetArena = a;
                    break;
                }
            }
        }
        if (targetArena == null) {
            sender.sendMessage("§cPlayer §e" + target.getName() + " §cis not in any arena!");
            return true;
        }
        plugin.getArenaManager().leaveArena(target);
        target.sendMessage("§c[MobArenav2] You were kicked from the arena by an administrator.");
        sender.sendMessage("§a[MobArenav2] Successfully kicked §e" + target.getName() + " §afrom arena §e" + targetArena.getName() + "§a!");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (args.length == 2) {
            return Bukkit.getOnlinePlayers().stream()
                    .filter(p -> plugin.getArenaManager().getArenaByPlayer(p) != null || plugin.getArenaManager().isSpectating(p))
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .toList();
        }
        return Collections.emptyList();
    }
}
