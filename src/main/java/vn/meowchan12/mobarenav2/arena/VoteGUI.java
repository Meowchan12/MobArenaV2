package vn.meowchan12.mobarenav2.arena;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;

public class VoteGUI {

    public static final String VOTE_PDC_KEY = "ma_vote_choice";

    public static void open(Player player, MobArenav2 plugin) {
        FileConfiguration settings = (plugin.getConfigManager() != null)
                ? plugin.getConfigManager().getSettings() : null;

        String titleRaw = (settings != null)
                ? settings.getString("gui.vote.title", "Enter Endless Mode?")
                : "Enter Endless Mode?";
        String title = ColorUtils.colorize(titleRaw);

        Inventory gui = Bukkit.createInventory(null, 27, title);
        NamespacedKey voteKey = new NamespacedKey(plugin, VOTE_PDC_KEY);

        // Yes item
        String yesMatName = (settings != null) ? settings.getString("gui.vote.yes-item.material", "EMERALD_BLOCK") : "EMERALD_BLOCK";
        Material yesMat = Material.matchMaterial(yesMatName != null ? yesMatName : "EMERALD_BLOCK");
        if (yesMat == null) yesMat = Material.EMERALD_BLOCK;

        ItemStack yes = new ItemStack(yesMat);
        ItemMeta yesMeta = yes.getItemMeta();
        if (yesMeta != null) {
            String yesName = (settings != null) ? settings.getString("gui.vote.yes-item.name", "&a&lCONTINUE (YES)") : "&a&lCONTINUE (YES)";
            yesMeta.setDisplayName(ColorUtils.colorize(yesName));

            List<String> rawLore = (settings != null) ? settings.getStringList("gui.vote.yes-item.lore") : null;
            if (rawLore != null && !rawLore.isEmpty()) {
                List<String> lore = new ArrayList<>();
                for (String l : rawLore) lore.add(ColorUtils.colorize(l));
                yesMeta.setLore(lore);
            }
            yesMeta.getPersistentDataContainer().set(voteKey, PersistentDataType.STRING, "yes");
            yes.setItemMeta(yesMeta);
        }

        // No item
        String noMatName = (settings != null) ? settings.getString("gui.vote.no-item.material", "REDSTONE_BLOCK") : "REDSTONE_BLOCK";
        Material noMat = Material.matchMaterial(noMatName != null ? noMatName : "REDSTONE_BLOCK");
        if (noMat == null) noMat = Material.REDSTONE_BLOCK;

        ItemStack no = new ItemStack(noMat);
        ItemMeta noMeta = no.getItemMeta();
        if (noMeta != null) {
            String noName = (settings != null) ? settings.getString("gui.vote.no-item.name", "&c&lSTOP (NO)") : "&c&lSTOP (NO)";
            noMeta.setDisplayName(ColorUtils.colorize(noName));

            List<String> rawLore = (settings != null) ? settings.getStringList("gui.vote.no-item.lore") : null;
            if (rawLore != null && !rawLore.isEmpty()) {
                List<String> lore = new ArrayList<>();
                for (String l : rawLore) lore.add(ColorUtils.colorize(l));
                noMeta.setLore(lore);
            }
            noMeta.getPersistentDataContainer().set(voteKey, PersistentDataType.STRING, "no");
            no.setItemMeta(noMeta);
        }

        gui.setItem(11, yes);
        gui.setItem(15, no);

        player.openInventory(gui);
    }
}
