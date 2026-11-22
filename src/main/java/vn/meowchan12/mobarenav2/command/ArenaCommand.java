package vn.meowchan12.mobarenav2.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.command.sub.*;

import java.util.*;

/**
 * [v8.1.0]: Master Command Router for /mobarena (/ma).
 * Lightweight delegator implementing the Command Pattern (< 80 lines).
 */
public class ArenaCommand implements CommandExecutor, TabCompleter {
    private final MobArenav2 plugin;
    private final Map<String, SubCommand> commands = new HashMap<>();
    private final HelpCommand helpCommand = new HelpCommand();

    public ArenaCommand(MobArenav2 plugin) {
        this.plugin = plugin;
        register(helpCommand,
                 new JoinCommand(), new LeaveCommand(), new ReadyCommand(), new ClassCommand(),
                 new SpectateCommand(), new ShopCommand(), new UpgradeCommand(), new MyUpgradesCommand(),
                 new FuseCommand(),
                 new TopCommand(), new StatsCommand(), new ListCommand(), new ForceStartCommand(),
                 new ForceEndCommand(), new KillMobsCommand(), new KickCommand(), new ReloadCommand(),
                 new AdminArenaCommand());
    }

    private void register(SubCommand... subs) {
        for (SubCommand sub : subs) {
            commands.put(sub.getName().toLowerCase(), sub);
            for (String alias : sub.getAliases()) commands.put(alias.toLowerCase(), sub);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            return helpCommand.execute(sender, new String[]{"help"}, plugin);
        }
        SubCommand sub = commands.get(args[0].toLowerCase());
        if (sub == null) {
            sender.sendMessage("§cUnknown command. Type §e/ma help §cfor a list of commands.");
            return true;
        }
        if (sub.isPlayerOnly() && !(sender instanceof Player)) {
            sender.sendMessage("§cThis command can only be used by players in-game!");
            return true;
        }
        if (sub.getPermission() != null && !sender.hasPermission(sub.getPermission())) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }
        return sub.execute(sender, args, plugin);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return commands.keySet().stream()
                    .filter(name -> name.startsWith(args[0].toLowerCase()))
                    .filter(name -> {
                        SubCommand sc = commands.get(name);
                        return sc.getPermission() == null || sender.hasPermission(sc.getPermission());
                    })
                    .sorted().toList();
        }
        SubCommand sub = commands.get(args[0].toLowerCase());
        return (sub != null) ? sub.tabComplete(sender, args, plugin) : Collections.emptyList();
    }
}
