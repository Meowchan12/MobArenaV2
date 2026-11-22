package vn.meowchan12.mobarenav2.arena;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.upgrade.fusion.FusionManager.UltimateRecipe;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * [v8.3.0]: Ultimate Upgrades Almanac / Preview GUI.
 * Allows players to inspect all 8 legendary fusion recipes, their required Max-Level components,
 * and merged powers.
 */
public class UltimatePreviewGUI implements Listener {

    private static final String GUI_TITLE = ColorUtils.colorize("&#FFAA00&l⚡ ULTIMATE UPGRADES ALMANAC ⚡");
    private final MobArenav2 plugin;

    public UltimatePreviewGUI(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    public static void open(Player player, MobArenav2 plugin) {
        if (player == null || plugin == null) return;

        Inventory inv = Bukkit.createInventory(null, 54, GUI_TITLE);

        // Fill borders
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bMeta = border.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName(" ");
            border.setItemMeta(bMeta);
        }
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                inv.setItem(i, border);
            }
        }

        // Slot 4: Almanac Header
        ItemStack header = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta hMeta = header.getItemMeta();
        if (hMeta != null) {
            hMeta.setDisplayName(ColorUtils.colorize("&#FFAA00&l⚡ ULTIMATE FUSION RECIPES ⚡"));
            hMeta.setLore(Arrays.asList(
                    ColorUtils.colorize("&7&m----------------------------------------"),
                    ColorUtils.colorize("&fCombine two &eMax-Level &fupgrades in battle to"),
                    ColorUtils.colorize("&fforge a legendary &6[Ultimate] &fcombination!"),
                    ColorUtils.colorize("&b✔ Each fusion frees +1 Upgrade Slot!"),
                    ColorUtils.colorize("&7&m----------------------------------------")
            ));
            header.setItemMeta(hMeta);
        }
        inv.setItem(4, header);

        // Place the 8 recipes in slots: 20, 21, 22, 23, 24, 29, 31, 33
        int[] recipeSlots = {20, 21, 22, 23, 24, 29, 31, 33};
        List<UltimateRecipe> allRecipes = new ArrayList<>(plugin.getFusionManager().getAllRecipes());

        for (int i = 0; i < Math.min(allRecipes.size(), recipeSlots.length); i++) {
            UltimateRecipe recipe = allRecipes.get(i);
            int slot = recipeSlots[i];

            ItemStack card = new ItemStack(recipe.material());
            ItemMeta meta = card.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtils.colorize(recipe.displayName()));
                List<String> lore = new ArrayList<>();
                for (String line : recipe.description()) {
                    lore.add(ColorUtils.colorize(line));
                }
                meta.setLore(lore);
                card.setItemMeta(meta);
            }
            inv.setItem(slot, card);
        }

        // Slot 49: Back to Standard Vault
        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta backMeta = backBtn.getItemMeta();
        if (backMeta != null) {
            backMeta.setDisplayName(ColorUtils.colorize("&c&l⬅ BACK TO UPGRADES DIRECTORY"));
            backBtn.setItemMeta(backMeta);
        }
        inv.setItem(49, backBtn);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot == 49) {
            // Back to Standard Directory
            UpgradeGUI.openStandardDirectory(player, plugin, 1, "ALL");
        }
    }
}
