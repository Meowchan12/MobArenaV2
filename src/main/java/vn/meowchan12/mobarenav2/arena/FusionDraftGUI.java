package vn.meowchan12.mobarenav2.arena;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.upgrade.fusion.FusionManager;
import vn.meowchan12.mobarenav2.upgrade.fusion.FusionManager.UltimateRecipe;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * [v8.3.0]: Fusion Draft GUI.
 * Non-mandatory draft menu presenting eligible [Ultimate] upgrades.
 * Slot 22 allows saving for later without forfeiting eligibility.
 */
public class FusionDraftGUI implements Listener {

    private static final String GUI_TITLE = ColorUtils.colorize("&#FFAA00&l⚡ ULTIMATE FUSION DRAFT ⚡");
    private final MobArenav2 plugin;

    public FusionDraftGUI(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    public static void open(Player player, MobArenav2 plugin) {
        if (player == null || plugin == null) return;

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena == null) {
            player.sendMessage(ColorUtils.colorize("&c[MobArenav2] You must be in an active arena to perform an Ultimate Fusion!"));
            return;
        }

        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
        if (profile == null) return;

        List<UltimateRecipe> eligible = plugin.getFusionManager().getEligibleFusions(profile);
        if (eligible.isEmpty()) {
            player.sendMessage(ColorUtils.colorize("&c[MobArenav2] You do not have any pairs of Max-Level upgrades ready for fusion yet!"));
            player.sendMessage(ColorUtils.colorize("&7Type &e/ma preview ultimate &7to view all available fusion recipes!"));
            return;
        }

        Inventory inv = Bukkit.createInventory(null, 27, GUI_TITLE);

        // Fill border
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bMeta = border.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName(" ");
            border.setItemMeta(bMeta);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        // Slot 4: Info Header
        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName(ColorUtils.colorize("&#FFAA00&l⚡ ULTIMATE FUSION MECHANICS ⚡"));
            infoMeta.setLore(Arrays.asList(
                    ColorUtils.colorize("&7&m----------------------------------------"),
                    ColorUtils.colorize("&eChoose one pair of Max-Level upgrades to fuse!"),
                    ColorUtils.colorize("&fBoth components will be merged into a single &6[Ultimate] &fcard."),
                    ColorUtils.colorize("&b&l✔ Slot Compression: &aFrees +1 Upgrade Slot instantly!"),
                    ColorUtils.colorize("&7&m----------------------------------------")
            ));
            info.setItemMeta(infoMeta);
        }
        inv.setItem(4, info);

        // Populate up to 3 choices in slots 11, 13, 15
        int[] choiceSlots = {11, 13, 15};
        NamespacedKey fusionKey = new NamespacedKey(plugin, "fusion_recipe_id");

        for (int i = 0; i < Math.min(eligible.size(), choiceSlots.length); i++) {
            UltimateRecipe recipe = eligible.get(i);
            int slot = choiceSlots[i];

            ItemStack card = new ItemStack(recipe.material());
            ItemMeta meta = card.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ColorUtils.colorize(recipe.displayName()));
                List<String> lore = new ArrayList<>();
                for (String line : recipe.description()) {
                    lore.add(ColorUtils.colorize(line));
                }
                lore.add(ColorUtils.colorize("&a➡ Click to execute this Ultimate Fusion!"));
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(fusionKey, PersistentDataType.STRING, recipe.id());
                card.setItemMeta(meta);
            }
            inv.setItem(slot, card);
        }

        // Slot 22: Save For Later / Close
        ItemStack saveLater = new ItemStack(Material.CLOCK);
        ItemMeta saveMeta = saveLater.getItemMeta();
        if (saveMeta != null) {
            saveMeta.setDisplayName(ColorUtils.colorize("&e&l⏳ SAVE FOR LATER (CLOSE)"));
            saveMeta.setLore(Arrays.asList(
                    ColorUtils.colorize("&7&m----------------------------------------"),
                    ColorUtils.colorize("&fNot ready to fuse right now?"),
                    ColorUtils.colorize("&7Click or press ESC to postpone this fusion."),
                    ColorUtils.colorize("&7You can reopen this menu anytime using &e/ma fuse&7."),
                    ColorUtils.colorize("&7&m----------------------------------------"),
                    ColorUtils.colorize("&e➡ Click to close and save for later")
            ));
            saveLater.setItemMeta(saveMeta);
        }
        inv.setItem(22, saveLater);

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.4f);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().equals(GUI_TITLE)) return;

        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        int slot = event.getRawSlot();

        // Slot 22: Save For Later
        if (slot == 22) {
            player.closeInventory();
            player.sendMessage(ColorUtils.colorize("&e[MobArenav2] Fusion postponed! You can reopen this draft at any time by typing &6/ma fuse&e."));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.0f);
            return;
        }

        // Check if an eligible recipe was clicked
        NamespacedKey fusionKey = new NamespacedKey(plugin, "fusion_recipe_id");
        if (clicked.getItemMeta().getPersistentDataContainer().has(fusionKey, PersistentDataType.STRING)) {
            String recipeId = clicked.getItemMeta().getPersistentDataContainer().get(fusionKey, PersistentDataType.STRING);
            UltimateRecipe recipe = plugin.getFusionManager().getRecipe(recipeId);

            if (recipe != null) {
                Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
                if (arena != null) {
                    ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
                    if (profile != null) {
                        boolean success = plugin.getFusionManager().fuse(player, profile, recipe, arena);
                        if (success) {
                            player.closeInventory();
                        }
                    }
                }
            }
        }
    }
}
