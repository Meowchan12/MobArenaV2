package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.command.SubCommand;

public class ReadyCommand implements SubCommand {

    @Override
    public String getName() {
        return "ready";
    }

    @Override
    public String getPermission() {
        return "mobarenav2.player.join";
    }

    @Override
    public String getUsage() {
        return "/ma ready";
    }

    @Override
    public String getDescription() {
        return "Ready up in the lobby to start the match";
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
            player.sendMessage("§cYou must join an arena first to get ready!");
            return true;
        }
        if (arena.isRunning()) {
            player.sendMessage("§cThe arena has already started!");
            return true;
        }

        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
        if (profile != null) {
            if (profile.getSelectedClass() == null) {
                player.sendMessage("§c[MobArenav2] You must select a Class before getting ready! Use /ma class");
                return true;
            }

            if (profile.isReady()) {
                player.sendMessage("§eYou are already ready!");
                return true;
            }

            profile.setReady(true);
            arena.broadcastMessage("§a[MobArenav2] §e" + player.getName() + " §ais ready!");
            arena.checkAndStart(plugin);
        }
        return true;
    }
}
