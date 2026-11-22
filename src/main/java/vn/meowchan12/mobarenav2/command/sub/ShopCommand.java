package vn.meowchan12.mobarenav2.command.sub;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.command.SubCommand;
import vn.meowchan12.mobarenav2.shop.gui.ShopGUI;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ShopCommand implements SubCommand {

    @Override
    public String getName() {
        return "shop";
    }

    @Override
    public String getPermission() {
        return "mobarenav2.player.shop";
    }

    @Override
    public String getUsage() {
        return "/ma shop [category]";
    }

    @Override
    public String getDescription() {
        return "Open the MobArena Global Shop";
    }

    @Override
    public boolean isPlayerOnly() {
        return true;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args, MobArenav2 plugin) {
        Player player = (Player) sender;
        if (plugin.getArenaManager().getArenaByPlayer(player) != null) {
            player.sendMessage("§c[MobArenav2] You cannot use the shop while inside an arena!");
            return true;
        }

        if (args.length >= 2) {
            ShopGUI.openCategoryShop(player, args[1], plugin);
        } else {
            ShopGUI.openMainShop(player, plugin);
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args, MobArenav2 plugin) {
        if (args.length == 2 && plugin.getShopManager() != null) {
            List<String> list = new ArrayList<>();
            for (String cat : plugin.getShopManager().getShopCategories()) {
                if (cat.toLowerCase().startsWith(args[1].toLowerCase())) {
                    list.add(cat);
                }
            }
            return list;
        }
        return Collections.emptyList();
    }
}
