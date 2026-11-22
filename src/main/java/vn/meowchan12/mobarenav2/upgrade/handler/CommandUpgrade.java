package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

/**
 * [v7.3 MODULAR]: CommandUpgrade
 * Xử lý các loại nâng cấp thực thi Console Command.
 */
public class CommandUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "COMMAND";
    }

    @Override
    public void onApply(Player player, int newLevel, UpgradeData data, Arena arena) {
        if (player == null || data == null || data.command == null || data.command.isEmpty()) return;

        String cmd = data.command.replace("%player%", player.getName());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
    }
}
