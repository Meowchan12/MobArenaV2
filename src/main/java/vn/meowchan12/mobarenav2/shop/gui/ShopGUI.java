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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [v8.4.5]: 100% Data-Driven Dynamic Shop GUI System.
 * Fully configurable titles, glass fillers, confirmation thresholds, dynamic currency names,
 * and dynamically populated Shop Guide menus derived straight from YAML definitions.
 */
public class ShopGUI implements Listener {

    private final MobArenav2 plugin;
    private final ShopManager shopManager;

    private static final Map<UUID, String> pendingConfirmations = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> confirmationTimes = new ConcurrentHashMap<>();

    public ShopGUI(MobArenav2 plugin, ShopManager shopManager) {
        this.plugin = plugin;
        this.shopManager = shopManager;
    }

    public static String getCurrencyDisplayName(MobArenav2 plugin, String priceType) {
        if (priceType == null) return "Coins";
        String pt = priceType.toLowerCase();
        if (plugin.getConfig().contains("shop.currency-names." + pt)) {
            return plugin.getConfig().getString("shop.currency-names." + pt);
        }
        if (priceType.equalsIgnoreCase("VAULT")) return "Vault Money";
        if (priceType.equalsIgnoreCase("PLAYERPOINTS")) return "PlayerPoints";
        if (priceType.equalsIgnoreCase("CUSTOM_COMMAND")) return "Casino Chips";
        return priceType;
    }

    public static ItemStack createFiller(String materialName, Material fallback) {
        Material mat = fallback;
        if (materialName != null) {
            try {
                mat = Material.valueOf(materialName.toUpperCase());
            } catch (Exception ignored) {}
        }
        ItemStack filler = new ItemStack(mat);
        ItemMeta fMeta = filler.getItemMeta();
        if (fMeta != null) {
            fMeta.setDisplayName(" ");
            filler.setItemMeta(fMeta);
        }
        return filler;
    }

    private static String colorize(String text) {
        if (text == null) return "";
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }

    // =====================================================================
    // MAIN SHOP MENU
    // =====================================================================
    public static void openMainShop(Player player, MobArenav2 plugin) {
        ShopManager manager = plugin.getShopManager();
        if (manager == null) return;

        String title = plugin.getConfig().getString("shop.gui.main-title", "&0&l📜 MOBARENA GLOBAL SHOP");
        Inventory gui = Bukkit.createInventory(new ShopHolder(null), 54, colorize(title));

        String fillerMat = plugin.getConfig().getString("shop.gui.main-filler", "BLACK_STAINED_GLASS_PANE");
        ItemStack filler = createFiller(fillerMat, Material.BLACK_STAINED_GLASS_PANE);
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
            guideLore.add(" ");
            guideLore.add("§e➡ Click to open guide");
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

        populateFinancialBar(gui, player, plugin);
        player.openInventory(gui);
    }

    // =====================================================================
    // DYNAMIC SHOP GUIDE MENU (Generated directly from Shop Items)
    // =====================================================================
    public static void openGuideMenu(Player player, MobArenav2 plugin) {
        ShopManager manager = plugin.getShopManager();
        if (manager == null) return;

        String title = plugin.getConfig().getString("shop.gui.guide-title", "&0&l📖 SHOP UPGRADE GUIDE");
        Inventory gui = Bukkit.createInventory(new ShopHolder("guide"), 54, colorize(title));

        String fillerMat = plugin.getConfig().getString("shop.gui.guide-filler", "LIGHT_BLUE_STAINED_GLASS_PANE");
        ItemStack filler = createFiller(fillerMat, Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, filler);
        }

        // Available slots for guide cards across 4 rows
        int[] guideSlots = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

        int slotIdx = 0;
        for (ShopItem item : manager.getShopItems().values()) {
            if (slotIdx >= guideSlots.length) break;

            ItemStack gItem = new ItemStack(item.getMaterial());
            ItemMeta meta = gItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(item.getDisplayName());
                List<String> lore = new ArrayList<>();

                // Include clean descriptive lore from item definition
                for (String line : item.getLore()) {
                    if (line.contains("%cost%") || line.contains("%current_level%")) continue;
                    lore.add(line);
                }

                lore.add(" ");
                if (item.getMaxLevel() > 1) {
                    lore.add("§eMax Level: §f" + item.getMaxLevel());
                    if (item.getAttributeName() != null) {
                        lore.add("§ePer Level: §a+" + String.format(java.util.Locale.US, "%.1f", item.getIncrementValue())
                                + " §7| §eMax: §a+" + String.format(java.util.Locale.US, "%.1f", item.calculateStatForLevel(item.getMaxLevel())));
                    }
                }

                String cur = getCurrencyDisplayName(plugin, item.getPriceType());
                lore.add("§eBase Cost: §6" + String.format(java.util.Locale.US, "%,.0f", item.getBaseCost()) + " " + cur);

                if (item.getCategoryId().equalsIgnoreCase("ultimates")) {
                    int cd = item.getIntSetting("cooldown", 0);
                    if (cd > 0) lore.add("§bCooldown: §e" + cd + " Seconds");
                }

                lore.add(" ");
                lore.add("§a➡ Click to jump to this Shop Category!");

                NamespacedKey catKey = new NamespacedKey(plugin, "shop_category_id");
                meta.getPersistentDataContainer().set(catKey, PersistentDataType.STRING, item.getCategoryId());
                meta.setLore(lore);
                gItem.setItemMeta(meta);
            }

            gui.setItem(guideSlots[slotIdx++], gItem);
        }

        // Navigation
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

    // =====================================================================
    // CATEGORY SHOP MENU
    // =====================================================================
    public static void openCategoryShop(Player player, String categoryId, MobArenav2 plugin) {
        ShopManager manager = plugin.getShopManager();
        if (manager == null) return;

        ShopCategory category = manager.getCategories().get(categoryId.toLowerCase());
        String title = (category != null) ? category.getDisplayName() : "§0§lMOBARENA SHOP";

        Inventory gui = Bukkit.createInventory(new ShopHolder(categoryId), 54, colorize(title));

        String fillerMat = plugin.getConfig().getString("shop.gui.category-filler", "GRAY_STAINED_GLASS_PANE");
        ItemStack filler = createFiller(fillerMat, Material.GRAY_STAINED_GLASS_PANE);
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

            boolean isConsumable = item.isConsumable();
            String currencyName = getCurrencyDisplayName(plugin, item.getPriceType());

            ItemStack icon = new ItemStack(item.getMaterial());
            ItemMeta meta = icon.getItemMeta();
            if (meta != null) {
                if (isConsumable && item.getId().toLowerCase().contains("ticket")) {
                    meta.setDisplayName(item.getDisplayName() + " §8[Held: §e" + shopData.getRerollTickets() + " Tickets§8]");
                } else {
                    meta.setDisplayName(item.getDisplayName() + " §8[Lv." + currentLvl + "/" + item.getMaxLevel() + "]");
                }

                int cdVal = item.getIntSetting("cooldown", 0);
                double dmgVal = item.getDoubleSetting("damage", item.getDoubleSetting("burst-damage", 0.0));
                double radVal = item.getDoubleSetting("radius", item.getDoubleSetting("burst-radius", 0.0));
                int durSec = item.getIntSetting("duration-ticks", 0) / 20;
                int healPct = (int) (item.getDoubleSetting("heal-percent", 0.0) * 100);

                List<String> formattedLore = new ArrayList<>();
                for (String line : item.getLore()) {
                    String formatted = line
                            .replace("%current_level%", String.valueOf(currentLvl))
                            .replace("%max_level%", String.valueOf(item.getMaxLevel()))
                            .replace("%cost%", String.format(java.util.Locale.US, "%,.0f", currentCost))
                            .replace("%current_value%", String.format(java.util.Locale.US, "%.1f", currentStat))
                            .replace("%next_value%", String.format(java.util.Locale.US, "%.1f", nextStat))
                            .replace("%cooldown%", String.valueOf(cdVal))
                            .replace("%damage%", String.format(java.util.Locale.US, "%.1f", dmgVal))
                            .replace("%radius%", String.format(java.util.Locale.US, "%.1f", radVal))
                            .replace("%duration%", String.valueOf(durSec))
                            .replace("%heal_percent%", String.valueOf(healPct))
                            .replace("%currency%", currencyName);
                    formattedLore.add(colorize(formatted));
                }

                formattedLore.add(" ");
                if (!isConsumable && isMaxed) {
                    if (categoryId.equalsIgnoreCase("ultimates")) {
                        boolean isEquipped = item.getId().equalsIgnoreCase(shopData.getEquippedUltimate());
                        if (isEquipped) {
                            formattedLore.add("§a✔ CURRENTLY EQUIPPED");
                            formattedLore.add("§7(Active in battle: Shift + Right-Click)");
                            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                        } else {
                            formattedLore.add("§e➡ Click to EQUIP this Ultimate!");
                        }
                    } else {
                        formattedLore.add("§a✔ MAXED OUT!");
                    }
                } else {
                    formattedLore.add("§ePrice: §6" + String.format(java.util.Locale.US, "%,.0f", currentCost) + " " + currencyName);
                    if (isConsumable) {
                        formattedLore.add("§a➡ Click to Purchase 1x " + item.getDisplayName());
                    } else {
                        formattedLore.add("§a➡ Click to Upgrade to Lv." + nextLvl);
                    }
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

        if (categoryId.equalsIgnoreCase("ultimates")) {
            ItemStack guideBook = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta gMeta = guideBook.getItemMeta();
            if (gMeta != null) {
                gMeta.setDisplayName("§6§l✦ ULTIMATE LOADOUT GUIDE ✦");
                List<String> gLore = new ArrayList<>();
                gLore.add("§7§m----------------------------------");
                gLore.add("§fMaster the art of Combat Ultimates:");
                gLore.add(" ");
                gLore.add("§e1. Single Loadout Limit:");
                gLore.add(" §7You may only equip §b1 Ultimate §7at a time.");
                gLore.add(" §7Click any owned ultimate in this menu");
                gLore.add(" §7to equip it for your upcoming matches.");
                gLore.add(" ");
                gLore.add("§e2. In-Game Casting:");
                gLore.add(" §7Hold any weapon and press §bShift + Right-Click");
                gLore.add(" §7to trigger your equipped ultimate ability.");
                gLore.add(" ");
                gLore.add("§e3. Cooldown Reduction:");
                gLore.add(" §7Upgrade §bCooldown Reduction §7in the");
                gLore.add(" §7Advanced Stats shop to cast ultimates faster!");
                gLore.add("§7§m----------------------------------");
                gMeta.setLore(gLore);
                guideBook.setItemMeta(gMeta);
            }
            gui.setItem(4, guideBook);
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

        populateFinancialBar(gui, player, plugin);
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
                    ShopItem item = shopManager.getShopItems().get(itemId.toLowerCase());
                    if (item != null) {
                        PlayerShopData shopData = shopManager.getPlayerData(player.getUniqueId());
                        if (item.getCategoryId().equalsIgnoreCase("ultimates") && shopData.getItemLevel(item.getId()) >= item.getMaxLevel()) {
                            if (item.getId().equalsIgnoreCase(shopData.getEquippedUltimate())) {
                                player.sendMessage("§e[MobArena Shop] This ultimate is already equipped!");
                                player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                            } else {
                                shopData.setEquippedUltimate(item.getId());
                                shopManager.savePlayerDataToDisk(player.getUniqueId());
                                player.sendMessage("§a[MobArena Shop] §eEquipped §6" + item.getDisplayName() + " §eas your active Ultimate!");
                                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 2.0f);
                                openCategoryShop(player, item.getCategoryId(), plugin);
                            }
                            return;
                        }

                        int nextLvl = shopData.getItemLevel(item.getId()) + 1;
                        double cost = item.calculateCostForLevel(nextLvl);

                        // Configurable thresholds for confirmation
                        double vThresh = plugin.getConfig().getDouble("shop.confirmation-thresholds.vault", 1000000.0);
                        double ppThresh = plugin.getConfig().getDouble("shop.confirmation-thresholds.playerpoints", 1000.0);
                        double ccThresh = plugin.getConfig().getDouble("shop.confirmation-thresholds.custom-command", 1000.0);
                        long timeoutMs = plugin.getConfig().getLong("shop.confirmation-timeout-ms", 3000L);

                        boolean isExpensive = item.isConfirmationRequired()
                                || (item.getPriceType().equalsIgnoreCase("VAULT") && cost >= vThresh)
                                || (item.getPriceType().equalsIgnoreCase("PLAYERPOINTS") && cost >= ppThresh)
                                || (item.getPriceType().equalsIgnoreCase("CUSTOM_COMMAND") && cost >= ccThresh);

                        UUID puuid = player.getUniqueId();
                        if (isExpensive && (item.isConsumable() || nextLvl <= item.getMaxLevel())) {
                            String pending = pendingConfirmations.get(puuid);
                            Long pTime = confirmationTimes.get(puuid);
                            long now = System.currentTimeMillis();

                            if (pending == null || !pending.equalsIgnoreCase(itemId) || pTime == null || (now - pTime > timeoutMs)) {
                                pendingConfirmations.put(puuid, itemId);
                                confirmationTimes.put(puuid, now);
                                player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
                                String cur = getCurrencyDisplayName(plugin, item.getPriceType());
                                player.sendMessage("§e§l⚠ [SHOP CONFIRMATION] §fThis item costs §6" + String.format(java.util.Locale.US, "%,.0f", cost) + " " + cur + "§f! Click again within " + (timeoutMs / 1000) + "s to confirm purchase.");
                                return;
                            } else {
                                pendingConfirmations.remove(puuid);
                                confirmationTimes.remove(puuid);
                            }
                        }

                        boolean bought = shopManager.buyItem(player, itemId);
                        if (bought) {
                            openCategoryShop(player, item.getCategoryId(), plugin);
                        }
                    }
                }
            }
        }
    }

    private static void populateFinancialBar(Inventory gui, Player player, MobArenav2 plugin) {
        double vaultBal = (plugin.getEconomy() != null) ? plugin.getEconomy().getBalance(player) : 0.0;
        int ppPoints = 0;
        if (plugin.getShopManager() != null && plugin.getShopManager().getPlayerPointsHook().isEnabled()) {
            try {
                if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    String ppStr = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, "%playerpoints_points%");
                    ppPoints = Integer.parseInt(ppStr);
                }
            } catch (Exception ignored) {}
        }
        int rerollTickets = 0;
        boolean hasHephaestus = false;
        boolean hasPinPermit = false;
        if (plugin.getShopManager() != null) {
            PlayerShopData d = plugin.getShopManager().getPlayerData(player.getUniqueId());
            if (d != null) {
                rerollTickets = d.getRerollTickets();
                hasHephaestus = d.hasHephaestusLicense();
                hasPinPermit = d.hasPinPermit();
            }
        }

        String curVault = getCurrencyDisplayName(plugin, "VAULT");
        String curPP = getCurrencyDisplayName(plugin, "PLAYERPOINTS");

        // Slot 45: Vault
        ItemStack vItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta vMeta = vItem.getItemMeta();
        if (vMeta != null) {
            vMeta.setDisplayName("§6§l" + curVault.toUpperCase() + " BALANCE");
            List<String> lore = new ArrayList<>();
            lore.add("§7Current Balance: §e$" + String.format(java.util.Locale.US, "%,.2f", vaultBal));
            vMeta.setLore(lore);
            vItem.setItemMeta(vMeta);
        }
        gui.setItem(45, vItem);

        // Slot 46: PlayerPoints
        ItemStack pItem = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta pMeta = pItem.getItemMeta();
        if (pMeta != null) {
            pMeta.setDisplayName("§d§l" + curPP.toUpperCase());
            List<String> lore = new ArrayList<>();
            lore.add("§7Current Points: §b" + ppPoints + " Pts");
            pMeta.setLore(lore);
            pItem.setItemMeta(pMeta);
        }
        gui.setItem(46, pItem);

        // Slot 47: Tickets & Perks
        ItemStack tItem = new ItemStack(Material.NETHER_STAR);
        ItemMeta tMeta = tItem.getItemMeta();
        if (tMeta != null) {
            tMeta.setDisplayName("§b§lARENA PASSES & UTILITIES");
            List<String> lore = new ArrayList<>();
            lore.add("§7Free Reroll Tickets: §e" + rerollTickets);
            lore.add("§7Hephaestus License: " + (hasHephaestus ? "§a§lUNLOCKED" : "§cLocked"));
            lore.add("§7Upgrade Pin Permit: " + (hasPinPermit ? "§a§lUNLOCKED" : "§cLocked"));
            tMeta.setLore(lore);
            tItem.setItemMeta(tMeta);
        }
        gui.setItem(47, tItem);
    }
}
