package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.command.CommandSender;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.command.SubCommand;

import java.util.Collections;
import java.util.List;

public class ReloadCommand implements SubCommand {

    @Override
    public String getName() {
        return "reload";
    }

    @Override
    public String getPermission() {
        return "mobarenav2.admin";
    }

    @Override
    public String getUsage() {
        return "/ma reload";
    }

    @Override
    public String getDescription() {
        return "Reload all MobArenav2 configs, arenas, bosses, classes, and messages";
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

        plugin.getConfigManager().loadAllConfigs();
        if (plugin.getMessageManager() != null) {
            plugin.getMessageManager().reload();
        }
        if (plugin.getSupplyManager() != null) {
            plugin.getSupplyManager().loadConfig();
        }
        if (plugin.getBossManager() != null) {
            plugin.getBossManager().reloadConfigs();
        }
        if (plugin.getArenaManager() != null) {
            plugin.getArenaManager().reloadArenas();
        }
        if (plugin.getUpgradeManager() != null) {
            plugin.getUpgradeManager().loadConfig();
        }
        if (plugin.getShopManager() != null) {
            plugin.getShopManager().loadAllShops();
        }

        sender.sendMessage("§a[MobArenav2] All configurations (Settings, Messages, Classes, Arenas, Bosses, Supplies, Shops, Upgrades) reloaded successfully!");
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        return Collections.emptyList();
    }
}
