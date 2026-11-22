package vn.meowchan12.mobarenav2.command;

import org.bukkit.command.CommandSender;
import vn.meowchan12.mobarenav2.MobArenav2;

import java.util.Collections;
import java.util.List;

/**
 * [v8.1.0]: SubCommand interface implementing the Command Pattern for MobArenav2.
 * Decouples logic into standalone modules with autonomous permission, argument validation, and tab-completion.
 */
public interface SubCommand {

    String getName();

    default List<String> getAliases() {
        return Collections.emptyList();
    }

    String getPermission();

    String getUsage();

    String getDescription();

    boolean isPlayerOnly();

    boolean execute(CommandSender sender, String[] args, MobArenav2 plugin);

    default List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        return Collections.emptyList();
    }
}
