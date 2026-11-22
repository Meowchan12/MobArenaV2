package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.FusionDraftGUI;
import vn.meowchan12.mobarenav2.command.SubCommand;

import java.util.List;

/**
 * [v8.3.0]: Fuse Command (/ma fuse / /ma combine)
 * Opens the non-mandatory draft menu to fuse pairs of Max-Level upgrades into [Ultimate] upgrades.
 */
public class FuseCommand implements SubCommand {

    @Override
    public String getName() {
        return "fuse";
    }

    @Override
    public List<String> getAliases() {
        return List.of("fusion", "combine");
    }

    @Override
    public String getPermission() {
        return "mobarenav2.player.join";
    }

    @Override
    public String getUsage() {
        return "/ma fuse";
    }

    @Override
    public String getDescription() {
        return "Open the Ultimate Fusion draft to combine maxed upgrades";
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args, MobArenav2 plugin) {
        Player player = (Player) sender;
        FusionDraftGUI.open(player, plugin);
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        return List.of();
    }
}
