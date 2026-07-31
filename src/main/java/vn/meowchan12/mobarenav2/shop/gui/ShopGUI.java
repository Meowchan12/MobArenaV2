package vn.meowchan12.mobarenav2.shop.gui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.shop.manager.ShopManager;
import vn.meowchan12.mobarenav2.shop.model.PlayerShopData;
import vn.meowchan12.mobarenav2.shop.model.ShopCategory;
import vn.meowchan12.mobarenav2.shop.model.ShopItem;

import java.util.ArrayList;
import java.util.List;

public class ShopGUI implements Listener {

    private final MobArenav2 plugin;
    private final ShopManager shopManager;

    public ShopGUI(MobArenav2 plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    public static void openMainShop(Player player, MobArenav2 plugin) {
        ShopManager manager = plugin.getShopManager();
        if (manager == null) return;

        Inventory gui = Bukkit.createInventory(new ShopHolder(null), 54, "§0§l📜 MOBARENA GLOBAL SHOP");

        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        if (fMeta != null) {
            fMeta.setDisplayName(" ");
            filler.setItemMeta(fMeta);
        }
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, filler);
        }

        for (ShopCategory cat : manager.getCategories().values()) {
            ItemStack item = new ItemStack(cat.getMaterial());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(cat.getDisplayName());
                List<String> lore = new ArrayList<>(cat.getLore());
                lore.add(" ");
                lore.add("§e➡ Click to enter category");
                meta.setLore(lore);

                NamespacedKey key = new NamespacedKey(plugin, "shop_category_id");
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, cat.getId());
                item.setItemMeta(meta);
            }
            if (cat.getSlot() >= 0 && cat.getSlot() < 54) {
                gui.setItem(cat.getSlot(), item);
            }
        }

        ItemStack guideBtn = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta gMeta = guideBtn.getItemMeta();
        if (gMeta != null) {
            gMeta.setDisplayName("§e§l📖 SHOP GUIDE");
            List<String> guideLore = new ArrayList<>();
            guideLore.add("§7Click to view detailed information");
            guideLore.add("§7about each shop upgrade and special.");
            gMeta.setLore(guideLore);
            NamespacedKey guideKey = new NamespacedKey(plugin, "shop_guide_btn");
            gMeta.getPersistentDataContainer().set(guideKey, PersistentDataType.STRING, "guide");
            guideBtn.setItemMeta(gMeta);
        }
        gui.setItem(50, guideBtn);

        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = closeBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§c§lCLOSE SHOP");
            closeBtn.setItemMeta(cMeta);
        }
        gui.setItem(49, closeBtn);

        player.openInventory(gui);
    }

    public static void openGuideMenu(Player player, MobArenav2 plugin) {
        Inventory gui = Bukkit.createInventory(new ShopHolder("guide"), 54, "§0§l📖 SHOP UPGRADE GUIDE");
        
        ItemStack filler = new ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        if (fMeta != null) { fMeta.setDisplayName(" "); filler.setItemMeta(fMeta); }
        for (int i = 0; i < 54; i++) gui.setItem(i, filler);
        
        // Base Stats Guide
        gui.setItem(10, createGuideItem(Material.RED_CONCRETE, "§c§lHeart of Steel (HP)",
            "§7Increases your Base Max HP.",
            "§7This bonus is applied as a Bukkit attribute,",
            "§7meaning it survives Totem of Undying pops!",
            "§ePer Level: §a+2.0 HP §7| §eMax: Lv.10 (+20 HP)"));
        
        gui.setItem(12, createGuideItem(Material.IRON_CHESTPLATE, "§7§lIron Bulwark (Armor)",
            "§7Increases your Base Armor points.",
            "§7Reduces incoming physical damage.",
            "§ePer Level: §b+1.5 Armor §7| §eMax: Lv.5 (+7.5)"));
        
        gui.setItem(14, createGuideItem(Material.NETHERITE_SWORD, "§c§lTitan Strength (DMG)",
            "§7Increases your Base Melee Damage.",
            "§7Stacks with weapon damage and enchantments.",
            "§ePer Level: §c+1.5 DMG §7| §eMax: Lv.5 (+7.5)"));
        
        // Advanced Stats Guide
        gui.setItem(20, createGuideItem(Material.FEATHER, "§6§lPhantom Reflexes (Dodge)",
            "§7Increases your chance to completely dodge attacks.",
            "§7Stacks with the Upgrade System's dodge chance.",
            "§ePer Level: §a+0.2% §7| §eMax: Lv.6 (+1.2%)"));
        
        gui.setItem(22, createGuideItem(Material.CLOCK, "§b§lTemporal Mastery (Cooldown)",
            "§7Reduces cooldown of all special abilities.",
            "§7Affects upgrade cooldowns and special skills.",
            "§ePer Level: §b-5% CD §7| §eMax: Lv.5 (-25%)"));
        
        gui.setItem(24, createGuideItem(Material.REDSTONE, "§c§lBlood Pact (Lifesteal)",
            "§7Increases your lifesteal trigger chance.",
            "§7Stacks with Vampiric Strike upgrade.",
            "§ePer Level: §c+0.5% §7| §eMax: Lv.3 (+1.5%)"));
        
        gui.setItem(30, createGuideItem(Material.GOLDEN_SWORD, "§6§lPrecision Edge (Crit)",
            "§7Increases your critical strike chance.",
            "§7Stacks with Critical Strike upgrade.",
            "§ePer Level: §6+0.3% §7| §eMax: Lv.3 (+0.9%)"));
        
        gui.setItem(32, createGuideItem(Material.NETHERITE_INGOT, "§9§lAdamantine Skin (Toughness)",
            "§7Increases your Armor Toughness.",
            "§7Reduces effectiveness of high-damage attacks.",
            "§ePer Level: §9+0.5 §7| §eMax: Lv.4 (+2.0)"));
        
        // Ultimates Guide
        gui.setItem(38, createGuideItem(Material.FEATHER, "§b§lShadow Dash Ultimate",
            "§7Active: Dash forward 8 blocks,",
            "§7stunning all mobs in your trajectory.",
            "§eCooldown: §c25s §7| §ePrice: 5000 PlayerPoints"));
        
        gui.setItem(40, createGuideItem(Material.GOLDEN_APPLE, "§e§lRallying Beacon Ultimate",
            "§7Active: Plant a Holy Banner that restores",
            "§730% Max HP to all nearby teammates.",
            "§eCooldown: §c45s §7| §ePrice: 8000 PlayerPoints"));
        
        gui.setItem(42, createGuideItem(Material.SHIELD, "§3§lStasis Aegis Ultimate",
            "§7Active: Freeze yourself in stasis for 3s.",
            "§7You become 100% invulnerable, then burst",
            "§7an icy shockwave when stasis ends.",
            "§eCooldown: §c60s §7| §ePrice: 10000 PlayerPoints"));
        
        // Back button
        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta bMeta = backBtn.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName("§e§l◄ BACK TO MAIN MENU");
            backBtn.setItemMeta(bMeta);
        }
        gui.setItem(48, backBtn);
        
        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = closeBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§c§lCLOSE");
            closeBtn.setItemMeta(cMeta);
        }
        gui.setItem(49, closeBtn);
        
        player.openInventory(gui);
    }

    private static ItemStack createGuideItem(Material mat, String name, String... loreLines) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            List<String> lore = new ArrayList<>();
            for (String line : loreLines) lore.add(line);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public static void openCategoryShop(Player player, String categoryId, MobArenav2 plugin) {
        ShopManager manager = plugin.getShopManager();
        if (manager == null) return;

        ShopCategory category = manager.getCategories().get(categoryId.toLowerCase());
        String title = (category != null) ? category.getDisplayName() : "§0§lMOBARENA SHOP";

        Inventory gui = Bukkit.createInventory(new ShopHolder(categoryId), 54, title);

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        if (fMeta != null) {
            fMeta.setDisplayName(" ");
            filler.setItemMeta(fMeta);
        }
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, filler);
        }

        PlayerShopData shopData = manager.getPlayerData(player.getUniqueId());

        for (ShopItem item : manager.getShopItems().values()) {
            if (!item.getCategoryId().equalsIgnoreCase(categoryId)) continue;

            int currentLvl = shopData.getItemLevel(item.getId());
            boolean isMaxed = currentLvl >= item.getMaxLevel();
            int nextLvl = Math.min(item.getMaxLevel(), currentLvl + 1);

            double currentCost = item.calculateCostForLevel(nextLvl);
            double currentStat = item.calculateStatForLevel(currentLvl);
            double nextStat = item.calculateStatForLevel(nextLvl);

            ItemStack icon = new ItemStack(item.getMaterial());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(item.getDisplayName() + " §8[Lv." + currentLvl + "/" + item.getMaxLevel() + "]");

                List<String> formattedLore = new ArrayList<>();
                for (String line : item.getLore()) {
                    String formatted = line
                            .replace("%current_level%", String.valueOf(currentLvl))
                            .replace("%max_level%", String.valueOf(item.getMaxLevel()))
                            .replace("%cost%", String.format("%.0f", currentCost))
                            .replace("%current_value%", String.format("%.1f", currentStat))
                            .replace("%next_value%", String.format("%.1f", nextStat));
                    formattedLore.add(formatted);
                }

                formattedLore.add(" ");
                if (isMaxed) {
                    formattedLore.add("§a✔ MAXED OUT!");
                } else {
                    formattedLore.add("§ePrice: §6" + String.format("%,.0f", currentCost) + " " + item.getPriceType());
                    formattedLore.add("§a➡ Click to Upgrade to Lv." + nextLvl);
                }
                meta.setLore(formattedLore);

                NamespacedKey key = new NamespacedKey(plugin, "shop_item_id");
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, item.getId());
                icon.setItemMeta(meta);
            }

            if (item.getSlot() >= 0 && item.getSlot() < 54) {
                gui.setItem(item.getSlot(), icon);
            }
        }

        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta bMeta = backBtn.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName("§e§l◄ BACK TO MAIN MENU");
            backBtn.setItemMeta(bMeta);
        }
        gui.setItem(48, backBtn);

        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = closeBtn.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§c§lCLOSE SHOP");
            closeBtn.setItemMeta(cMeta);
        }
        gui.setItem(49, closeBtn);

        player.openInventory(gui);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShopInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ShopHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onShopInventoryClick(InventoryClickEvent event) {
        boolean isShopGUI = event.getInventory().getHolder() instanceof ShopHolder;

        if (!isShopGUI) {
            String title = event.getView().getTitle();
            if (title.contains("MOBARENA GLOBAL SHOP") || title.contains("STATS") || title.contains("ABILITIES") || title.contains("UTILITIES") || title.contains("SHOP") || title.contains("GUIDE")) {
                isShopGUI = true;
            }
        }

        if (isShopGUI) {
            // UNCONDITIONAL CANCEL TO PREVENT ITEM STEALING / TAKING
            event.setCancelled(true);

            if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) {
                return;
            }

            if (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR || !event.getCurrentItem().hasItemMeta()) {
                return;
            }

            if (!(event.getWhoClicked() instanceof Player player)) return;

            ItemStack current = event.getCurrentItem();
            ItemMeta meta = current.getItemMeta();
            if (meta == null) return;

            if (current.getType() == Material.BARRIER) {
                player.closeInventory();
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                return;
            }

            if (current.getType() == Material.ARROW) {
                openMainShop(player, plugin);
                player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
                return;
            }

            NamespacedKey guideKey = new NamespacedKey(plugin, "shop_guide_btn");
            if (meta.getPersistentDataContainer().has(guideKey, PersistentDataType.STRING)) {
                openGuideMenu(player, plugin);
                player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                return;
            }

            NamespacedKey catKey = new NamespacedKey(plugin, "shop_category_id");
            if (meta.getPersistentDataContainer().has(catKey, PersistentDataType.STRING)) {
                String catId = meta.getPersistentDataContainer().get(catKey, PersistentDataType.STRING);
                if (catId != null) {
                    openCategoryShop(player, catId, plugin);
                    player.playSound(player.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
                }
                return;
            }

            NamespacedKey itemKey = new NamespacedKey(plugin, "shop_item_id");
            if (meta.getPersistentDataContainer().has(itemKey, PersistentDataType.STRING)) {
                String itemId = meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
                if (itemId != null) {
                    boolean bought = shopManager.buyItem(player, itemId);
                    if (bought) {
                        ShopItem item = shopManager.getShopItems().get(itemId.toLowerCase());
                        if (item != null) {
                            openCategoryShop(player, item.getCategoryId(), plugin);
                        }
                    }
                }
            }
        }
    }
}
