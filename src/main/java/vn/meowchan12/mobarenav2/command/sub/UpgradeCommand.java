package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.arena.UpgradeGUI;
import vn.meowchan12.mobarenav2.command.SubCommand;

import java.util.List;

public class UpgradeCommand implements SubCommand {

    @Override
    public String getName() {
        return "upgrade";
    }

    @Override
    public List<String> getAliases() {
        return List.of("up", "upgrades", "preview");
    }

    @Override
    public String getPermission() {
        return "mobarenav2.player.join";
    }

    @Override
    public String getUsage() {
        return "/ma upgrade | /ma upgrades | /ma preview [ultimate]";
    }

    @Override
    public String getDescription() {
        return "Choose pending upgrades or preview upgrades and ultimate recipes";
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args, MobArenav2 plugin) {
        Player player = (Player) sender;
        String label = (args.length > 0) ? args[0].toLowerCase() : "upgrade";

        // Check for ultimate preview
        if (args.length > 1 && (args[1].equalsIgnoreCase("ultimate") || args[1].equalsIgnoreCase("ultimates") || args[1].equalsIgnoreCase("fusion"))) {
            vn.meowchan12.mobarenav2.arena.UltimatePreviewGUI.open(player, plugin);
            return true;
        }

        // If command is /ma upgrades or /ma preview, open preview mode
        if (label.equals("upgrades") || label.equals("preview")) {
            UpgradeGUI.openPreview(player, plugin);
            return true;
        }

        // Otherwise open active draft choice GUI
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena == null) {
            player.sendMessage("§c[MobArenav2] You are not currently playing in an active arena match!");
            return true;
        }
        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
        if (profile == null) {
            player.sendMessage("§c[MobArenav2] No active profile found for this arena!");
            return true;
        }
        if (profile.getPendingUpgradeChoices() <= 0) {
            player.sendMessage("§c[MobArenav2] You do not have any pending upgrade choices right now!");
            player.sendMessage("§eTip: Use §a/ma myupgrades §eto view your active upgrades or §a/ma upgrades §eto preview all upgrades.");
            return true;
        }

        UpgradeGUI.open(player, plugin);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (args.length == 2) {
            return List.of("ultimate");
        }
        return List.of();
    }
}
