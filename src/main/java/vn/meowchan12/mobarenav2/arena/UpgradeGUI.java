package vn.meowchan12.mobarenav2.arena;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class UpgradeGUI implements Listener {

    private final MobArenav2 plugin;

    public UpgradeGUI(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    private static String colorize(String text) {
        if (text == null) return null;
        return vn.meowchan12.mobarenav2.utils.ColorUtils.colorize(text);
    }

    private static String formatVal(double val) {
        if (val == (long) val) {
            return String.valueOf((long) val);
        }
        return String.format("%.1f", val);
    }

    // ==========================================================
    // MENU 1: STANDARD UPGRADES DIRECTORY (Paginated 54-Slot GUI)
    // ==========================================================
    public static void openStandardDirectory(Player player, MobArenav2 plugin, int page, String categoryFilter) {
        if (categoryFilter == null || categoryFilter.isEmpty()) categoryFilter = "ALL";
        final String currentFilter = categoryFilter.toUpperCase();

        List<UpgradeData> allUpgrades = plugin.getUpgradeManager().getAvailableUpgrades();
        List<UpgradeData> filtered = new ArrayList<>();

        for (UpgradeData u : allUpgrades) {
            if (u.id.startsWith("super_") || u.type.startsWith("SUPER_") || u.id.startsWith("ultimate_") || u.type.startsWith("ULTIMATE_")) continue; // Only standard upgrades

            if (currentFilter.equals("ALL")) {
                filtered.add(u);
            } else if (currentFilter.equals("OFFENSIVE")) {
                String t = (u.type + "_" + u.id).toUpperCase();
                if (t.contains("DAMAGE") || t.contains("CRIT") || t.contains("EXECUTE") || t.contains("REACH") || t.contains("ATTACK") || t.contains("PIERCE") || t.contains("PREDATOR") || t.contains("WARRIOR")) {
                    filtered.add(u);
                }
            } else if (currentFilter.equals("DEFENSIVE")) {
                String t = (u.type + "_" + u.id).toUpperCase();
                if (t.contains("HEALTH") || t.contains("IRON") || t.contains("BARRIER") || t.contains("THORN") || t.contains("SHIELD") || t.contains("DECOY")) {
                    filtered.add(u);
                }
            } else if (currentFilter.equals("UTILITY")) {
                String t = (u.type + "_" + u.id).toUpperCase();
                if (t.contains("SPEED") || t.contains("WIND") || t.contains("MIDAS") || t.contains("SOUL") || t.contains("QUIVER") || t.contains("GRAVITY") || t.contains("TETHER")) {
                    filtered.add(u);
                }
            }
        }

        int itemsPerPage = 28; // 4 rows x 7 columns (slots 10..16, 19..25, 28..34, 37..43)
        int totalPages = Math.max(1, (int) Math.ceil((double) filtered.size() / itemsPerPage));
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        String title = colorize("&0&l📜 STANDARD UPGRADES &8(Page " + page + "/" + totalPages + ")");
        Inventory gui = Bukkit.createInventory(null, 54, title);

        // Fill background borders with Gray Stained Glass
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.setDisplayName(" ");
            border.setItemMeta(borderMeta);
        }
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, border);
            }
        }

        // Top bar buttons
        // Slot 0: Directory indicator
        ItemStack tabIndicator = new ItemStack(Material.BOOK);
        ItemMeta tabMeta = tabIndicator.getItemMeta();
        if (tabMeta != null) {
            tabMeta.setDisplayName(colorize("&a&l✔ STANDARD UPGRADES DIRECTORY"));
            tabMeta.setLore(Arrays.asList(
                    colorize("&7Review all regular upgrades available during wave drafts."),
                    colorize("&eClick the top-right button to view Super Upgrades.")
            ));
            tabIndicator.setItemMeta(tabMeta);
        }
        gui.setItem(0, tabIndicator);

        // Slot 4: Category Filter Button
        ItemStack filterBtn = new ItemStack(Material.HOPPER);
        ItemMeta fMeta = filterBtn.getItemMeta();
        if (fMeta != null) {
            fMeta.setDisplayName(colorize("&e&lCATEGORY FILTER: &b&l" + currentFilter));
            List<String> fLore = new ArrayList<>();
            fLore.add(colorize("&7&m----------------------------"));
            fLore.add(colorize("&fCurrent Filter: &a" + currentFilter));
            fLore.add(colorize("&7Showing &e" + filtered.size() + " &7standard upgrades."));
            fLore.add(colorize("&7&m----------------------------"));
            fLore.add(colorize("&e▶ Click to cycle: &fALL ➔ OFFENSIVE ➔ DEFENSIVE ➔ UTILITY"));
            fMeta.setLore(fLore);
            filterBtn.setItemMeta(fMeta);
        }
        gui.setItem(4, filterBtn);

        // Slot 8: Tab Switch to Super Upgrades Vault
        ItemStack toSuperBtn = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta superMeta = toSuperBtn.getItemMeta();
        if (superMeta != null) {
            superMeta.setDisplayName(colorize("&d&lSwitch to Super Upgrades Vault ➔"));
            superMeta.setLore(Arrays.asList(
                    colorize("&7Click to open the Legendary Super Upgrades Vault"),
                    colorize("&7and inspect ultra-rare mythic items & live trackers.")
            ));
            toSuperBtn.setItemMeta(superMeta);
        }
        gui.setItem(8, toSuperBtn);

        // [v8.3.0]: Button linking to Ultimate Fusion Almanac
        ItemStack toUltBtn = new ItemStack(Material.NETHER_STAR);
        ItemMeta ultMeta = toUltBtn.getItemMeta();
        if (ultMeta != null) {
            ultMeta.setDisplayName(colorize("&#FFAA00&l⚡ Ultimate Fusion Almanac ➔"));
            ultMeta.setLore(Arrays.asList(
                    colorize("&7Click to browse all 8 legendary [Ultimate]"),
                    colorize("&7fusion recipes, requirements & combined stats.")
            ));
            toUltBtn.setItemMeta(ultMeta);
        }
        gui.setItem(7, toUltBtn);

        // Populate Center Grid (slots 10..16, 19..25, 28..34, 37..43)
        int[] gridSlots = {
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43
        };

        Arena currentArena = plugin.getArenaManager().getArenaByPlayer(player);
        ArenaPlayerProfile currentProfile = (currentArena != null) ? currentArena.getPlayerProfiles().get(player.getUniqueId()) : null;

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, filtered.size());

        for (int i = startIndex; i < endIndex; i++) {
            UpgradeData data = filtered.get(i);
            int slot = gridSlots[i - startIndex];

            ItemStack item = new ItemStack(data.material);
            ItemMeta meta = item.getItemMeta();
            int ownedLvl = (currentProfile != null) ? currentProfile.getUpgradeLevel(data.id) : 0;
            int displayLvl = (ownedLvl > 0) ? ownedLvl : 1;

            double curVal = (ownedLvl > 0) ? (data.baseValue + (ownedLvl - 1) * data.increment) : data.baseValue;
            double nextVal = (ownedLvl >= data.maxLevel) ? curVal : (data.baseValue + ownedLvl * data.increment);

            if (meta != null) {
                meta.setDisplayName(colorize(data.displayName
                        .replace("%current_level%", String.valueOf(displayLvl))
                        .replace("%max_level%", String.valueOf(data.maxLevel))));

                List<String> coloredLore = new ArrayList<>();
                for (String loreLine : data.lore) {
                    coloredLore.add(colorize(loreLine
                            .replace("%current_level%", String.valueOf(displayLvl))
                            .replace("%max_level%", String.valueOf(data.maxLevel))
                            .replace("%current_value%", formatVal(curVal))
                            .replace("%next_value%", formatVal(nextVal))));
                }

                coloredLore.add("");
                if (currentProfile != null) {
                    if (ownedLvl >= data.maxLevel) {
                        coloredLore.add(colorize("&a★ MAX LEVEL REACHED &8(Mastered)"));
                    } else if (ownedLvl > 0) {
                        coloredLore.add(colorize("&a✔ Currently Acquired: &eLevel " + ownedLvl + "/" + data.maxLevel));
                    } else {
                        coloredLore.add(colorize("&7Status: &cNot Acquired &8(Can roll in wave drafts)"));
                    }
                } else {
                    double maxPotential = data.baseValue + (data.maxLevel - 1) * data.increment;
                    coloredLore.add(colorize("&7Max Potential (Lv." + data.maxLevel + "): &e" + formatVal(maxPotential)));
                }

                meta.setLore(coloredLore);
                item.setItemMeta(meta);
            }
            gui.setItem(slot, item);
        }

        // Bottom Navigation Bar
        // Slot 45: Previous Page
        if (page > 1) {
            ItemStack prevBtn = new ItemStack(Material.ARROW);
            ItemMeta prevMeta = prevBtn.getItemMeta();
            if (prevMeta != null) {
                prevMeta.setDisplayName(colorize("&e◀ Previous Page &7(" + (page - 1) + ")"));
                prevBtn.setItemMeta(prevMeta);
            }
            gui.setItem(45, prevBtn);
        }

        // Slot 49: Close Button
        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeBtn.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName(colorize("&c&lClose Directory"));
            closeBtn.setItemMeta(closeMeta);
        }
        gui.setItem(49, closeBtn);

        // Slot 53: Next Page
        if (page < totalPages) {
            ItemStack nextBtn = new ItemStack(Material.ARROW);
            ItemMeta nextMeta = nextBtn.getItemMeta();
            if (nextMeta != null) {
                nextMeta.setDisplayName(colorize("&eNext Page ▶ &7(" + (page + 1) + ")"));
                nextBtn.setItemMeta(nextMeta);
            }
            gui.setItem(53, nextBtn);
        }

        player.openInventory(gui);
    }

    // ==========================================================
    // MENU 2: SUPER UPGRADES VAULT (Legendary 54-Slot GUI)
    // ==========================================================
    public static void openSuperVault(Player player, MobArenav2 plugin, int page) {
        Inventory gui = Bukkit.createInventory(null, 54, colorize("&5&l⭐ SUPER UPGRADES VAULT"));

        // Fill borders with Purple Stained Glass
        ItemStack border = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.setDisplayName(" ");
            border.setItemMeta(borderMeta);
        }
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, border);
            }
        }

        // Slot 0: Switch back to Standard Directory
        ItemStack backBtn = new ItemStack(Material.BOOK);
        ItemMeta bMeta = backBtn.getItemMeta();
        if (bMeta != null) {
            bMeta.setDisplayName(colorize("&b&l⬅ Switch to Standard Upgrades Directory"));
            bMeta.setLore(Arrays.asList(
                    colorize("&7Click to return to the Standard Upgrades Directory.")
            ));
            backBtn.setItemMeta(bMeta);
        }
        gui.setItem(0, backBtn);

        // Slot 4: Vault Crown
        ItemStack vaultHeader = new ItemStack(Material.NETHER_STAR);
        ItemMeta vMeta = vaultHeader.getItemMeta();
        if (vMeta != null) {
            vMeta.setDisplayName(colorize("&6&l✦ LEGENDARY SUPER UPGRADES VAULT ✦"));
            List<String> vLore = new ArrayList<>();
            vLore.add(colorize("&7&m----------------------------------"));
            vLore.add(colorize("&fSuper Upgrades are game-changing legendary perks!"));
            vLore.add(colorize("&e• Guaranteed roll upon defeating Bosses."));
            vLore.add(colorize("&e• Tiered lucky rolls during normal wave drafts."));
            vLore.add(colorize("&e• Super Limit: &6Unlimited &eper match (No Hard Cap)."));
            vLore.add(colorize("&7&m----------------------------------"));
            vMeta.setLore(vLore);
            vaultHeader.setItemMeta(vMeta);
        }
        gui.setItem(4, vaultHeader);

        Arena currentArena = plugin.getArenaManager().getArenaByPlayer(player);
        ArenaPlayerProfile currentProfile = (currentArena != null) ? currentArena.getPlayerProfiles().get(player.getUniqueId()) : null;

        List<UpgradeData> allUpgrades = plugin.getUpgradeManager().getAvailableUpgrades();
        List<UpgradeData> supers = new ArrayList<>();
        for (UpgradeData u : allUpgrades) {
            if (u.id.startsWith("super_") || u.type.startsWith("SUPER_")) {
                supers.add(u);
            }
        }

        int[] superSlots = {20, 21, 22, 23, 24, 29, 30, 31, 32, 33};
        for (int i = 0; i < supers.size() && i < superSlots.length; i++) {
            UpgradeData data = supers.get(i);
            int slot = superSlots[i];

            ItemStack item = new ItemStack(data.material);
            ItemMeta meta = item.getItemMeta();
            int ownedLvl = (currentProfile != null) ? currentProfile.getUpgradeLevel(data.id) : 0;

            if (meta != null) {
                meta.setDisplayName(colorize(data.displayName
                        .replace("%current_level%", String.valueOf(Math.max(1, ownedLvl)))
                        .replace("%max_level%", String.valueOf(data.maxLevel))));

                List<String> lore = new ArrayList<>();
                for (String l : data.lore) {
                    lore.add(colorize(l
                            .replace("%current_level%", String.valueOf(Math.max(1, ownedLvl)))
                            .replace("%max_level%", String.valueOf(data.maxLevel))
                            .replace("%current_value%", formatVal(data.baseValue))
                            .replace("%next_value%", formatVal(data.baseValue + data.increment))));
                }

                lore.add("");
                // Check requirements & live telemetry
                if (data.id.equalsIgnoreCase("super_hephaestus")) {
                    boolean hasLic = false;
                    if (plugin.getShopManager() != null) {
                        var sData = plugin.getShopManager().getPlayerData(player.getUniqueId());
                        if (sData != null && sData.hasHephaestusLicense()) hasLic = true;
                    }
                    if (!hasLic) {
                        lore.add(colorize("&c🔒 LOCKED: Requires Hephaestus License in /ma shop!"));
                    } else {
                        lore.add(colorize("&a🔓 UNLOCKED: Hephaestus License acquired!"));
                    }
                }

                if (currentProfile != null) {
                    if (ownedLvl > 0) {
                        lore.add(colorize("&a★ EQUIPPED IN CURRENT MATCH"));
                        // Live trackers
                        if (data.id.equalsIgnoreCase("super_slaughterhouse")) {
                            lore.add(colorize("&e• Kills Stacked: &c" + currentProfile.getSlaughterhouseKills() + " (+ " + currentProfile.getSlaughterhouseKills() + "% DMG)"));
                        } else if (data.id.equalsIgnoreCase("super_retribution")) {
                            lore.add(colorize("&e• Reflected Damage Total: &6" + (int) currentProfile.getReflectedDamageTotal() + " DMG"));
                        } else if (data.id.equalsIgnoreCase("super_hephaestus")) {
                            lore.add(colorize("&e• Equipment Repairs Triggered: &a" + currentProfile.getHephaestusRepairs() + " times"));
                        }
                    } else {
                        lore.add(colorize("&7Status: &eAvailable to draft"));
                    }
                }

                meta.setLore(lore);
                meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
            gui.setItem(slot, item);
        }

        // Slot 49: Close
        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = closeBtn.getItemMeta();
        if (closeMeta != null) {
            closeMeta.setDisplayName(colorize("&c&lClose Vault"));
            closeBtn.setItemMeta(closeMeta);
        }
        gui.setItem(49, closeBtn);

        player.openInventory(gui);
    }

    public static void openPreview(Player player, MobArenav2 plugin) {
        openStandardDirectory(player, plugin, 1, "ALL");
    }

    // ==========================================================
    // IN-MATCH DRAFT CHOICE MENU (Wave Upgrade Selection)
    // ==========================================================
    public static void open(Player player, MobArenav2 plugin) {
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena == null) return;

        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
        if (profile == null) return;

        profile.clearPinnedUpgrade();

        vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType draftType = profile.peekPendingDraftType();
        int remainingChoices = Math.max(1, profile.getPendingUpgradeChoices());
        String waveStr = String.valueOf(arena.getCurrentWave());
        String leftStr = String.valueOf(remainingChoices);

        org.bukkit.configuration.file.FileConfiguration settings = (plugin.getConfigManager() != null)
                ? plugin.getConfigManager().getSettings() : null;

        String rawTitle;
        if (draftType == vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType.BOSS) {
            rawTitle = (settings != null)
                    ? settings.getString("gui.upgrade.boss-title", "&#FFAA00&l✦ BOSS VICTORY UPGRADE ✦ &8(%left% Left)")
                    : "&#FFAA00&l✦ BOSS VICTORY UPGRADE ✦ &8(%left% Left)";
        } else if (draftType == vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType.SWARM) {
            rawTitle = (settings != null)
                    ? settings.getString("gui.upgrade.swarm-title", "&#FF55FF&l✦ SWARM VICTORY UPGRADE ✦ &8(%left% Left)")
                    : "&#FF55FF&l✦ SWARM VICTORY UPGRADE ✦ &8(%left% Left)";
        } else if (draftType == vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType.STARTER) {
            rawTitle = (settings != null)
                    ? settings.getString("gui.upgrade.starter-title", "&#00FFFF&l✦ STARTER UPGRADE ✦ &8(%left% Left)")
                    : "&#00FFFF&l✦ STARTER UPGRADE ✦ &8(%left% Left)";
        } else {
            rawTitle = (settings != null)
                    ? settings.getString("gui.upgrade.title", "Choose Upgrade (Wave %wave%)")
                    : "Choose Upgrade (Wave %wave%)";
        }
        String title = colorize(rawTitle.replace("%wave%", waveStr).replace("%left%", leftStr));

        Inventory inv = Bukkit.createInventory(null, 27, title);
        populateUpgradeChoices(inv, player, arena, profile, plugin);

        if (plugin.getUpgradeRerollService() != null && plugin.getUpgradeRerollService().isEnabled()) {
            int rSlot = plugin.getUpgradeRerollService().getRerollSlot();
            if (rSlot >= 0 && rSlot < 27) {
                inv.setItem(rSlot, plugin.getUpgradeRerollService().buildRerollButton(player, arena, profile));
            }
        }

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
    }

    public static void populateUpgradeChoices(Inventory inv, Player player, Arena arena, ArenaPlayerProfile profile, MobArenav2 plugin) {
        int maxSlots = plugin.getUpgradeManager().getMaxSlots(player, profile.getSelectedClass(), arena);
        int usedSlots = plugin.getUpgradeManager().getUsedSlots(profile);
        boolean isFullSlot = usedSlots >= maxSlots;
        // [v8.3.0]: Unlimited Super Upgrades allowed (balanced via tiered diminishing roll rates)
        boolean superCapped = false;

        List<UpgradeData> pool = new ArrayList<>();

        for (UpgradeData u : plugin.getUpgradeManager().getAvailableUpgrades()) {
            if (profile != null && profile.isBanished(u.id)) continue;
            // [v8.3.0]: Ultimate cards are strictly Fusion-exclusive and do not appear in normal rolls
            if (u.weight <= 0 || u.id.startsWith("ultimate_") || u.type.startsWith("ULTIMATE_")) continue;

            boolean isSuper = u.id.startsWith("super_") || u.type.startsWith("SUPER_");
            if (isSuper && superCapped) continue;

            if (u.id.equalsIgnoreCase("super_hephaestus")) {
                if (plugin.getShopManager() != null) {
                    var sData = plugin.getShopManager().getPlayerData(player.getUniqueId());
                    if (sData == null || !sData.hasHephaestusLicense()) continue;
                } else continue;
            }

            int currentLevel = profile.getUpgradeLevel(u.id);
            if (currentLevel < u.maxLevel) {
                if (isFullSlot) {
                    if (currentLevel > 0) pool.add(u);
                } else {
                    pool.add(u);
                }
            }
        }

        // ==========================================================
        // [v8.2.0]: MAXED OUT ASCENDANCE POOL (7 Balanced Choices)
        // ==========================================================
        if (pool.isEmpty()) {
            NamespacedKey key = new NamespacedKey(plugin, "upgrade_id");
            List<ItemStack> maxedPool = new ArrayList<>();

            // 1. Emergency Recovery
            ItemStack healCard = new ItemStack(Material.ENCHANTED_GOLDEN_APPLE);
            ItemMeta healMeta = healCard.getItemMeta();
            if (healMeta != null) {
                healMeta.setDisplayName(colorize("&#55FF55&l✦ EMERGENCY RECOVERY ✦"));
                List<String> healLore = Arrays.asList(
                        colorize("&7&m---------------------"),
                        colorize("&eAll upgrade slots are currently maxed!"),
                        colorize("&fInstantly restores &aFull Health&f."),
                        colorize("&fGrants &6Absorption IV &f& &bResistance II &ffor 10s."),
                        colorize("&7&m---------------------"),
                        colorize("&a➡ Click to claim Emergency Recovery!")
                );
                healMeta.setLore(healLore);
                healMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "MAXED_OUT_HEAL");
                healCard.setItemMeta(healMeta);
            }
            maxedPool.add(healCard);

            // 2. Master Armorer's Forge (Full Durability Repair to all armor & inventory)
            ItemStack repairCard = new ItemStack(Material.ANVIL);
            ItemMeta repMeta = repairCard.getItemMeta();
            if (repMeta != null) {
                repMeta.setDisplayName(colorize("&#FFAA00&l✦ MASTER ARMORER'S FORGE ✦"));
                List<String> repLore = Arrays.asList(
                        colorize("&7&m---------------------"),
                        colorize("&6Master craftsmanship restores your battlefield gear!"),
                        colorize("&a✔ Instantly repairs 100% durability of ALL equipped armor."),
                        colorize("&a✔ Fully restores durability for main weapon, shield & bag items."),
                        colorize("&7&m---------------------"),
                        colorize("&e➡ Click to repair entire armory to 100%!")
                );
                repMeta.setLore(repLore);
                repMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "MAXED_OUT_FULL_REPAIR");
                repairCard.setItemMeta(repMeta);
            }
            maxedPool.add(repairCard);

            // 3. Overpower Mastery (+5% Total DMG vĩnh viễn)
            ItemStack overpowerCard = new ItemStack(Material.NETHERITE_SWORD);
            ItemMeta opMeta = overpowerCard.getItemMeta();
            if (opMeta != null) {
                opMeta.setDisplayName(colorize("&#FF0055&l✦ OVERPOWER MASTERY (+5% DMG) ✦"));
                List<String> opLore = Arrays.asList(
                        colorize("&7&m---------------------"),
                        colorize("&cEndless strength scaling for endgame masters!"),
                        colorize("&fPermanently grants &c+5% Total Damage &fon all strikes."),
                        colorize("&7Current Bonus: &c+" + (int) profile.getOvercapDamageBonusPercent() + "% DMG"),
                        colorize("&7Stackable infinitely without cap."),
                        colorize("&7&m---------------------"),
                        colorize("&c➡ Click to claim +5% Damage Mastery!")
                );
                opMeta.setLore(opLore);
                opMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                opMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "MAXED_OUT_OVERPOWER");
                overpowerCard.setItemMeta(opMeta);
            }
            maxedPool.add(overpowerCard);

            // 4. True Strike Essence (+3.0 Flat True DMG)
            ItemStack trueStrikeCard = new ItemStack(Material.AMETHYST_SHARD);
            ItemMeta tsMeta = trueStrikeCard.getItemMeta();
            if (tsMeta != null) {
                tsMeta.setDisplayName(colorize("&#CC00FF&l✦ TRUE STRIKE ESSENCE (+3 TRUE DMG) ✦"));
                List<String> tsLore = Arrays.asList(
                        colorize("&7&m---------------------"),
                        colorize("&dImbues weapons with pure armor-piercing essence."),
                        colorize("&fPermanently adds &d+3.0 Flat True Damage &fon all hits."),
                        colorize("&7Current Bonus: &d+" + (int) profile.getOvercapFlatTrueDmg() + " True DMG"),
                        colorize("&7Ignores all monster armor & resistance."),
                        colorize("&7&m---------------------"),
                        colorize("&d➡ Click to claim +3.0 True Damage!")
                );
                tsMeta.setLore(tsLore);
                tsMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "MAXED_OUT_TRUE_STRIKE");
                trueStrikeCard.setItemMeta(tsMeta);
            }
            maxedPool.add(trueStrikeCard);

            // 5. Windwalker Agility (+4% Speed)
            ItemStack agilityCard = new ItemStack(Material.FEATHER);
            ItemMeta agMeta = agilityCard.getItemMeta();
            if (agMeta != null) {
                agMeta.setDisplayName(colorize("&#00FFFF&l✦ WINDWALKER AGILITY (+4% SPEED) ✦"));
                List<String> agLore = Arrays.asList(
                        colorize("&7&m---------------------"),
                        colorize("&bTranscendent agility to dodge enemy swarms."),
                        colorize("&fPermanently increases movement speed by &b+4%&f."),
                        colorize("&7Stackable infinitely without cap."),
                        colorize("&7&m---------------------"),
                        colorize("&b➡ Click to claim +4% Speed Boost!")
                );
                agMeta.setLore(agLore);
                agMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "MAXED_OUT_AGILITY");
                agilityCard.setItemMeta(agMeta);
            }
            maxedPool.add(agilityCard);

            // 6. Instant $300,000 Bounty
            ItemStack cashCard = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta cashMeta = cashCard.getItemMeta();
            if (cashMeta != null) {
                cashMeta.setDisplayName(colorize("&#FFAA00&l✦ INSTANT BOUNTY ($300,000) ✦"));
                List<String> cashLore = Arrays.asList(
                        colorize("&7&m---------------------"),
                        colorize("&eInstant cash reward for Arena Masters!"),
                        colorize("&fInstantly deposits &6$300,000 &finto your account."),
                        colorize("&fNo risks or penalties attached."),
                        colorize("&7&m---------------------"),
                        colorize("&e➡ Click to claim $300,000 Cash!")
                );
                cashMeta.setLore(cashLore);
                cashMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "MAXED_OUT_BOUNTY");
                cashCard.setItemMeta(cashMeta);
            }
            maxedPool.add(cashCard);

            // 7. Blood Money Sacrifice ($10,000,000 vs -40% Max HP)
            ItemStack bloodCard = new ItemStack(Material.WITHER_SKELETON_SKULL);
            ItemMeta bloodMeta = bloodCard.getItemMeta();
            if (bloodMeta != null) {
                bloodMeta.setDisplayName(colorize("&#FF0055&l✦ BLOOD MONEY SACRIFICE ($10,000,000) ✦"));
                List<String> bloodLore = Arrays.asList(
                        colorize("&7&m---------------------"),
                        colorize("&4&lHIGH RISK &e- &6&lHIGH REWARD!"),
                        colorize("&fInstantly claims an astronomical &6$10,000,000 &fcash!"),
                        colorize("&c&lSACRIFICIAL PACT:"),
                        colorize("&7Permanently sacrifice &c-40% Max Health &7in this match!"),
                        colorize("&7&m---------------------"),
                        colorize("&c➡ Click to sign the Blood Pact!")
                );
                bloodMeta.setLore(bloodLore);
                bloodMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "MAXED_OUT_BLOOD_SACRIFICE");
                bloodCard.setItemMeta(bloodMeta);
            }
            maxedPool.add(bloodCard);

            Collections.shuffle(maxedPool);
            inv.setItem(11, maxedPool.get(0));
            inv.setItem(13, maxedPool.get(1));
            inv.setItem(15, maxedPool.get(2));
            return;
        }

        // Pinned Card handling
        int pinnedSlot = (profile != null) ? profile.getPinnedUpgradeSlot() : -1;
        ItemStack pinnedItem = (pinnedSlot != -1) ? inv.getItem(pinnedSlot) : null;
        String pinnedId = null;

        if (pinnedItem != null && pinnedItem.hasItemMeta()) {
            NamespacedKey upKey = new NamespacedKey(plugin, "upgrade_id");
            pinnedId = pinnedItem.getItemMeta().getPersistentDataContainer().get(upKey, PersistentDataType.STRING);
            if (pinnedId != null && !pinnedId.startsWith("MAXED_OUT_")) {
                final String finalPinnedId = pinnedId;
                pool.removeIf(u -> u.id.equalsIgnoreCase(finalPinnedId));
            } else {
                pinnedId = null;
                if (profile != null) profile.clearPinnedUpgrade();
                pinnedSlot = -1;
            }
        } else {
            if (profile != null) profile.clearPinnedUpgrade();
            pinnedSlot = -1;
        }

        List<Integer> slotsToFill = new ArrayList<>();
        int[] allSlots = {11, 13, 15};
        for (int s : allSlots) {
            if (s != pinnedSlot) {
                inv.setItem(s, null);
                slotsToFill.add(s);
            }
        }

        // Separate Standard vs Super pool
        List<UpgradeData> standardPool = new ArrayList<>();
        List<UpgradeData> superPool = new ArrayList<>();
        for (UpgradeData u : pool) {
            if (u.id.startsWith("super_") || u.type.startsWith("SUPER_")) {
                if (!superCapped) superPool.add(u);
            } else {
                standardPool.add(u);
            }
        }

        // ==========================================================
        // [v8.4.0 CONFIGURABLE DRAFT ROLL ENGINE]
        // Reads arena-specific Normal / Swarm / Boss roll rates from settings.yml
        // ==========================================================
        vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType draftType = (profile != null)
                ? profile.peekPendingDraftType()
                : vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType.NORMAL;
        vn.meowchan12.mobarenav2.manager.UpgradeManager.WaveDraftRule rule =
                plugin.getUpgradeManager().getWaveDraftRule(arena, draftType);

        // Detect if the currently pinned card is already a Super upgrade
        boolean pinnedIsSuper = false;
        if (pinnedId != null) {
            UpgradeData pinnedData = plugin.getUpgradeManager().getUpgradeData(pinnedId);
            pinnedIsSuper = pinnedId.startsWith("super_")
                    || (pinnedData != null && pinnedData.type != null && pinnedData.type.startsWith("SUPER_"));
        }

        int currentSuperCount = pinnedIsSuper ? 1 : 0;
        int maxAddableSuper = superCapped
                ? 0
                : Math.min(slotsToFill.size(), Math.min(superPool.size(), Math.max(0, rule.maxSuperSlots - currentSuperCount)));
        int guaranteedAddSuper = Math.min(maxAddableSuper, Math.max(0, rule.guaranteedSuperSlots - currentSuperCount));

        int superToAssign = guaranteedAddSuper;
        double superProb = rule.getNormalizedSuperProbability();

        for (int i = guaranteedAddSuper; i < slotsToFill.size(); i++) {
            if (superToAssign < maxAddableSuper && ThreadLocalRandom.current().nextDouble() < superProb) {
                superToAssign++;
            }
        }

        // Configurable Pity System (if enabled for this wave draft rule)
        if (rule.pityThreshold > 0 && profile != null) {
            if ((currentSuperCount + superToAssign) == 0 && maxAddableSuper > 0 && profile.getPityRollCounter() >= rule.pityThreshold) {
                superToAssign = 1;
            }
            if ((currentSuperCount + superToAssign) > 0) {
                profile.resetPityRollCounter();
            } else {
                profile.incrementPityRollCounter();
            }
        }

        // Shuffle slot roles so Super upgrades can land in any unpinned slot (11, 13, or 15)
        List<Boolean> slotRollsSuper = new ArrayList<>(slotsToFill.size());
        for (int i = 0; i < slotsToFill.size(); i++) {
            slotRollsSuper.add(i < superToAssign);
        }
        Collections.shuffle(slotRollsSuper);

        List<UpgradeData> selectedUpgrades = new ArrayList<>();
        for (int i = 0; i < slotsToFill.size(); i++) {
            UpgradeData picked = null;
            boolean wantSuper = slotRollsSuper.get(i);

            if (wantSuper && !superPool.isEmpty() && !superCapped) {
                picked = rollWeighted(superPool);
                if (picked != null) {
                    superPool.remove(picked);
                    pool.remove(picked);
                }
            }

            // Primary Standard roll or fallback if Super pool exhausted
            if (picked == null && !standardPool.isEmpty()) {
                picked = rollWeighted(standardPool);
                if (picked != null) {
                    standardPool.remove(picked);
                    pool.remove(picked);
                }
            }

            // Fallback to Super pool if Standard pool exhausted
            if (picked == null && !superPool.isEmpty() && !superCapped) {
                picked = rollWeighted(superPool);
                if (picked != null) {
                    superPool.remove(picked);
                    pool.remove(picked);
                }
            }

            // Ultimate Fallback
            if (picked == null && !pool.isEmpty()) {
                picked = rollWeighted(pool);
                if (picked != null) {
                    pool.remove(picked);
                }
            }

            if (picked != null) {
                selectedUpgrades.add(picked);
            }
        }

        for (int i = 0; i < selectedUpgrades.size(); i++) {
            UpgradeData data = selectedUpgrades.get(i);
            int slot = slotsToFill.get(i);

            ItemStack item = new ItemStack(data.material);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                int ownedLvl = (profile != null) ? profile.getUpgradeLevel(data.id) : 0;
                int displayLvl = Math.max(1, ownedLvl + 1);

                double curVal = (ownedLvl > 0) ? (data.baseValue + (ownedLvl - 1) * data.increment) : data.baseValue;
                double nextVal = (ownedLvl >= data.maxLevel) ? curVal : (data.baseValue + ownedLvl * data.increment);

                meta.setDisplayName(colorize(data.displayName
                        .replace("%current_level%", String.valueOf(displayLvl))
                        .replace("%max_level%", String.valueOf(data.maxLevel))));

                List<String> coloredLore = new ArrayList<>();
                for (String loreLine : data.lore) {
                    coloredLore.add(colorize(loreLine
                            .replace("%current_level%", String.valueOf(displayLvl))
                            .replace("%max_level%", String.valueOf(data.maxLevel))
                            .replace("%current_value%", formatVal(curVal))
                            .replace("%next_value%", formatVal(nextVal))));
                }

                coloredLore.add("");
                if (ownedLvl > 0) {
                    coloredLore.add(colorize("&a✔ Current Rank: Level " + ownedLvl + " ➔ Upgrading to Level " + displayLvl));
                } else {
                    coloredLore.add(colorize("&e✦ New Upgrade: Claim Level 1"));
                }
                coloredLore.add(colorize("&a➡ Left-Click to Select"));
                coloredLore.add(colorize("&6📌 Right-Click to Pin/Lock (Preserve on Reroll)"));
                coloredLore.add(colorize("&c✖ Shift-Right-Click to Banish"));

                meta.setLore(coloredLore);
                if (data.id.startsWith("super_") || data.type.startsWith("SUPER_")) {
                    meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                    meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                }

                NamespacedKey key = new NamespacedKey(plugin, "upgrade_id");
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, data.id);
                item.setItemMeta(meta);
            }
            inv.setItem(slot, item);
        }
    }

    private static UpgradeData rollWeighted(List<UpgradeData> list) {
        if (list.isEmpty()) return null;
        int totalWeight = list.stream().mapToInt(u -> Math.max(1, u.weight)).sum();
        int value = ThreadLocalRandom.current().nextInt(totalWeight);
        for (UpgradeData u : list) {
            value -= Math.max(1, u.weight);
            if (value < 0) return u;
        }
        return list.get(0);
    }

    public static boolean isUpgradeSelectionTitle(String title, MobArenav2 plugin) {
        if (title == null || title.isEmpty()) return false;
        String plain = ChatColor.stripColor(title);
        if (plain == null) return false;

        if (plain.contains("Choose Upgrade") || plain.contains("BOSS VICTORY UPGRADE") || plain.contains("SWARM VICTORY UPGRADE")) {
            return true;
        }

        if (plugin != null && plugin.getConfigManager() != null && plugin.getConfigManager().getSettings() != null) {
            org.bukkit.configuration.file.FileConfiguration settings = plugin.getConfigManager().getSettings();
            String[] keys = {"gui.upgrade.title", "gui.upgrade.swarm-title", "gui.upgrade.boss-title", "gui.upgrade.starter-title"};
            for (String key : keys) {
                String raw = settings.getString(key);
                if (raw == null || raw.isEmpty()) continue;
                String stripped = ChatColor.stripColor(colorize(raw));
                if (stripped == null) continue;
                String prefix = stripped.split("%(wave|left)%")[0].trim();
                if (!prefix.isEmpty() && plain.contains(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();

        // 1. Menu 1: Standard Upgrades Directory
        if (title.contains("STANDARD UPGRADES")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;

            int slot = event.getRawSlot();
            if (slot == 49) {
                player.closeInventory();
                return;
            }
            if (slot == 8) {
                openSuperVault(player, plugin, 1);
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
                return;
            }
            if (slot == 7) {
                UltimatePreviewGUI.open(player, plugin);
                player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.2f);
                return;
            }
            if (slot == 4) {
                // Cycle category
                String current = "ALL";
                if (title.contains("OFFENSIVE")) current = "OFFENSIVE";
                else if (title.contains("DEFENSIVE")) current = "DEFENSIVE";
                else if (title.contains("UTILITY")) current = "UTILITY";

                String next = switch (current) {
                    case "ALL" -> "OFFENSIVE";
                    case "OFFENSIVE" -> "DEFENSIVE";
                    case "DEFENSIVE" -> "UTILITY";
                    default -> "ALL";
                };
                openStandardDirectory(player, plugin, 1, next);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
                return;
            }
            if (slot == 45 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                int curPage = parsePageFromTitle(title);
                openStandardDirectory(player, plugin, Math.max(1, curPage - 1), getCategoryFromTitle(title));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                return;
            }
            if (slot == 53 && event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.ARROW) {
                int curPage = parsePageFromTitle(title);
                openStandardDirectory(player, plugin, curPage + 1, getCategoryFromTitle(title));
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                return;
            }
            return;
        }

        // 2. Menu 2: Super Upgrades Vault
        if (title.contains("SUPER UPGRADES VAULT")) {
            event.setCancelled(true);
            if (!(event.getWhoClicked() instanceof Player player)) return;

            int slot = event.getRawSlot();
            if (slot == 49) {
                player.closeInventory();
                return;
            }
            if (slot == 0) {
                openStandardDirectory(player, plugin, 1, "ALL");
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
                return;
            }
            return;
        }

        if (title.contains("UPGRADE DIRECTORY")) {
            event.setCancelled(true);
            if (event.getCurrentItem() != null && event.getCurrentItem().getType() == Material.BARRIER) {
                event.getWhoClicked().closeInventory();
            }
            return;
        }

        // 3. In-Match Upgrade Selection
        if (isUpgradeSelectionTitle(title, plugin)) {
            event.setCancelled(true);

            if (event.getCurrentItem() == null || !event.getCurrentItem().hasItemMeta()) return;
            if (!(event.getWhoClicked() instanceof Player player)) return;

            NamespacedKey rerollKey = new NamespacedKey(plugin, "ma_reroll_btn");
            if (event.getCurrentItem().getItemMeta().getPersistentDataContainer().has(rerollKey, PersistentDataType.BYTE)) {
                if (plugin.getUpgradeRerollService() != null) {
                    Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
                    if (arena != null) {
                        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
                        plugin.getUpgradeRerollService().handleRerollClick(player, arena, profile, event.getInventory());
                    }
                }
                return;
            }

            NamespacedKey key = new NamespacedKey(plugin, "upgrade_id");
            String upgradeId = event.getCurrentItem().getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);

            if (upgradeId != null) {
                Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
                if (arena == null) return;
                ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
                if (profile == null) return;

                int rawSlot = event.getRawSlot();

                // Banish handling
                if ((event.getClick() == org.bukkit.event.inventory.ClickType.MIDDLE || (event.isRightClick() && event.isShiftClick())) && (rawSlot == 11 || rawSlot == 13 || rawSlot == 15)) {
                    handleCardBanish(player, arena, profile, event.getInventory(), rawSlot, upgradeId, plugin);
                    return;
                }

                // Pin toggle handling
                if (event.isRightClick() && (rawSlot == 11 || rawSlot == 13 || rawSlot == 15)) {
                    handleCardPinToggle(player, arena, profile, event.getInventory(), rawSlot, plugin);
                    return;
                }

                profile.clearPinnedUpgrade();

                // ==========================================
                // APPLY SELECTED UPGRADE CARD
                // ==========================================
                if (upgradeId.equals("MAXED_OUT_HEAL")) {
                    double maxHp = 20.0;
                    AttributeInstance hpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
                    if (hpAttr != null) maxHp = hpAttr.getValue();
                    player.setHealth(maxHp);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 3));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 1));
                    player.sendMessage("§a[MobArenav2] §eEmergency Recovery claimed! Full health restored + Absorption IV & Resistance II for 10s.");
                    player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
                } else if (upgradeId.equals("MAXED_OUT_FULL_REPAIR")) {
                    // Full repair to all worn armor, weapons, offhand, and storage items
                    for (ItemStack armor : player.getInventory().getArmorContents()) {
                        if (armor != null && armor.getItemMeta() instanceof Damageable dmg && dmg.hasDamage()) {
                            dmg.setDamage(0);
                            armor.setItemMeta(dmg);
                        }
                    }
                    ItemStack mainHand = player.getInventory().getItemInMainHand();
                    if (mainHand != null && mainHand.getItemMeta() instanceof Damageable dmg && dmg.hasDamage()) {
                        dmg.setDamage(0);
                        mainHand.setItemMeta(dmg);
                    }
                    ItemStack offHand = player.getInventory().getItemInOffHand();
                    if (offHand != null && offHand.getItemMeta() instanceof Damageable dmg && dmg.hasDamage()) {
                        dmg.setDamage(0);
                        offHand.setItemMeta(dmg);
                    }
                    for (ItemStack item : player.getInventory().getStorageContents()) {
                        if (item != null && item.getItemMeta() instanceof Damageable dmg && dmg.hasDamage()) {
                            dmg.setDamage(0);
                            item.setItemMeta(dmg);
                        }
                    }
                    player.sendMessage("§6§l⚒ [ARMORY RESTORED] §aAll equipped armor & inventory weapons fully repaired to 100% durability! ⚒");
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                    player.getWorld().spawnParticle(Particle.SCRAPE, player.getLocation().add(0, 1.2, 0), 20, 0.4, 0.4, 0.4, 0.05);
                } else if (upgradeId.equals("MAXED_OUT_OVERPOWER")) {
                    profile.addOvercapDamageBonusPercent(5.0);
                    player.sendMessage("§c§l[LIMIT BREAK] §eOverpower Mastery claimed! +5% Total Damage bonus (Current: +" + (int) profile.getOvercapDamageBonusPercent() + "%).");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.8f);
                    player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.05);
                } else if (upgradeId.equals("MAXED_OUT_TRUE_STRIKE")) {
                    profile.addOvercapFlatTrueDmg(3.0);
                    player.sendMessage("§d§l[LIMIT BREAK] §eTrue Strike Essence claimed! +3.0 Flat True Damage on all hits (Current: +" + (int) profile.getOvercapFlatTrueDmg() + ").");
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
                    player.getWorld().spawnParticle(Particle.ENCHANTED_HIT, player.getLocation().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.1);
                } else if (upgradeId.equals("MAXED_OUT_AGILITY")) {
                    profile.addOvercapBonusSpeed(0.005);
                    AttributeInstance speedAttr = player.getAttribute(AttributeUtils.MOVEMENT_SPEED);
                    if (speedAttr != null) speedAttr.setBaseValue(speedAttr.getBaseValue() + 0.005);
                    player.sendMessage("§b§l[LIMIT BREAK] §eWindwalker Agility claimed! +4% Movement Speed bonus.");
                    player.playSound(player.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1.0f, 1.6f);
                    player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.5, 0), 20, 0.4, 0.2, 0.4, 0.05);
                } else if (upgradeId.equals("MAXED_OUT_BOUNTY")) {
                    if (plugin.getEconomy() != null) {
                        plugin.getEconomy().depositPlayer(player, 300000.0);
                    }
                    player.sendMessage("§6§l[BOUNTY CLAIMED] §a+$300,000 cash deposited to your account!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    player.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.1);
                } else if (upgradeId.equals("MAXED_OUT_BLOOD_SACRIFICE")) {
                    if (plugin.getEconomy() != null) {
                        plugin.getEconomy().depositPlayer(player, 10000000.0);
                    }
                    AttributeInstance hpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
                    if (hpAttr != null) {
                        double currentBase = hpAttr.getBaseValue();
                        double newBase = Math.max(4.0, currentBase * 0.6);
                        hpAttr.setBaseValue(newBase);
                        if (player.getHealth() > newBase) player.setHealth(newBase);
                    }
                    player.sendMessage("§4§l[BLOOD PACT SEALED] §cYou sacrificed 40% Max Health for §6+$10,000,000 Cash§c!");
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.7f, 1.0f);
                } else {
                    applyUpgrade(player, upgradeId);

                    String playerClass = profile.getSelectedClass() != null ? profile.getSelectedClass() : "None";
                    int mSlots = plugin.getUpgradeManager().getMaxSlots(player, playerClass, arena);
                    int uSlots = plugin.getUpgradeManager().getUsedSlots(profile);
                    String itemName = (event.getCurrentItem() != null && event.getCurrentItem().hasItemMeta())
                            ? event.getCurrentItem().getItemMeta().getDisplayName() : "Upgrade";
                    player.sendMessage("§a§l✔ UPGRADED: §f" + itemName + " §7• §eSlots: §b" + uSlots + "§7/§b" + mSlots);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 2.0f);
                }

                profile.resetWaveRerolls();
                if (profile.getPendingUpgradeChoices() > 0) {
                    profile.decrementPendingUpgradeChoices();
                    if (profile.getPendingUpgradeChoices() > 0) {
                        int remaining = profile.getPendingUpgradeChoices();
                        vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType nextType = profile.peekPendingDraftType();
                        String tag = switch (nextType) {
                            case BOSS -> "&6&l✦ BOSS REWARD ✦";
                            case SWARM -> "&d&l✦ SWARM REWARD ✦";
                            case STARTER -> "&#00FFFF&l✦ STARTER REWARD ✦";
                            default -> "&b&l✦ UPGRADE REWARD ✦";
                        };
                        player.sendActionBar(vn.meowchan12.mobarenav2.utils.ColorUtils.toAdventure(tag + " &e&l" + remaining + " choice" + (remaining > 1 ? "s" : "") + " remaining! &f&l(Opening menu...)"));
                        vn.meowchan12.mobarenav2.listener.GUIListener.completedUpgradeSelection.add(player.getUniqueId());
                        player.closeInventory();
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (player.isOnline() && arena.isRunning() && arena.getActivePlayers().contains(player.getUniqueId())) {
                                UpgradeGUI.open(player, plugin);
                            }
                        }, 5L);
                        return;
                    }
                }

                vn.meowchan12.mobarenav2.listener.GUIListener.completedUpgradeSelection.add(player.getUniqueId());
                player.closeInventory();
            }
        }
    }

    private void applyUpgrade(Player player, String upgradeId) {
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena == null) return;
        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
        if (profile == null) return;

        UpgradeData targetData = plugin.getUpgradeManager().getUpgradeData(upgradeId);
        if (targetData == null) return;

        profile.addUpgradeLevel(upgradeId);
        int newLevel = profile.getUpgradeLevel(upgradeId);

        if (upgradeId.startsWith("super_") || targetData.type.startsWith("SUPER_")) {
            profile.incrementSuperUpgradesChosen();
        }

        plugin.getUpgradeRegistry().dispatchApply(player, newLevel, targetData, arena);

        if (plugin.getSynergyManager() != null) {
            plugin.getSynergyManager().checkAndNotifyNewSynergies(player, profile, arena);
        }

        if (plugin.getFusionManager() != null) {
            plugin.getFusionManager().checkAndSendFusionPrompt(player, profile);
        }
    }

    private static int parsePageFromTitle(String title) {
        try {
            if (title.contains("Page ")) {
                String sub = title.substring(title.indexOf("Page ") + 5);
                String num = sub.split("/")[0].trim();
                return Integer.parseInt(num);
            }
        } catch (Exception ignored) {}
        return 1;
    }

    private static String getCategoryFromTitle(String title) {
        if (title.contains("OFFENSIVE")) return "OFFENSIVE";
        if (title.contains("DEFENSIVE")) return "DEFENSIVE";
        if (title.contains("UTILITY")) return "UTILITY";
        return "ALL";
    }

    private static void handleCardPinToggle(Player player, Arena arena, ArenaPlayerProfile profile, Inventory inv, int slot, MobArenav2 plugin) {
        ItemStack item = inv.getItem(slot);
        if (item == null || !item.hasItemMeta()) return;

        NamespacedKey upKey = new NamespacedKey(plugin, "upgrade_id");
        String upId = item.getItemMeta().getPersistentDataContainer().get(upKey, PersistentDataType.STRING);
        if (upId == null || upId.startsWith("MAXED_OUT_")) return;

        boolean hasPermit = false;
        if (plugin.getShopManager() != null) {
            var sData = plugin.getShopManager().getPlayerData(player.getUniqueId());
            if (sData != null && sData.hasPinPermit()) {
                hasPermit = true;
            }
        }

        if (!hasPermit) {
            player.sendMessage("§c[MobArenav2] You must purchase the §6Upgrade Pin License §cin §e/ma shop §cto pin cards!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        int currentlyPinned = profile.getPinnedUpgradeSlot();

        if (currentlyPinned == slot) {
            profile.clearPinnedUpgrade();
            removePinDisplay(item, plugin);
            player.playSound(player.getLocation(), Sound.BLOCK_LEVER_CLICK, 1.0f, 0.8f);
            player.sendMessage("§e[MobArenav2] Unpinned upgrade. All cards will reroll normally.");
        } else {
            if (currentlyPinned != -1 && currentlyPinned != slot) {
                ItemStack oldItem = inv.getItem(currentlyPinned);
                if (oldItem != null) {
                    removePinDisplay(oldItem, plugin);
                }
            }
            if (profile.getRemainingPins() <= 0) {
                player.sendMessage("§c[MobArenav2] You have used all 10 Pin/Lock uses for this match!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }
            profile.usePin();
            profile.setPinnedUpgradeSlot(slot);
            applyPinDisplay(item, plugin);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.8f);
            player.sendMessage("§a§l[PINNED] §eCard locked! (" + profile.getRemainingPins() + "/10 remaining)");
        }

        if (plugin.getUpgradeRerollService() != null && plugin.getUpgradeRerollService().isEnabled()) {
            int rSlot = plugin.getUpgradeRerollService().getRerollSlot();
            if (rSlot >= 0 && rSlot < inv.getSize()) {
                inv.setItem(rSlot, plugin.getUpgradeRerollService().buildRerollButton(player, arena, profile));
            }
        }
    }

    private static void handleCardBanish(Player player, Arena arena, ArenaPlayerProfile profile, Inventory inv, int slot, String upgradeId, MobArenav2 plugin) {
        if (upgradeId == null || upgradeId.startsWith("MAXED_OUT_")) return;

        if (profile == null || !profile.banishUpgrade(upgradeId)) {
            player.sendMessage("§c[MobArenav2] You have used all 10 Banishes for this match!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (profile.getPinnedUpgradeSlot() == slot) {
            profile.clearPinnedUpgrade();
        }

        player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1.0f, 0.6f);
        player.sendMessage("§c§l[BANISHED] §7Upgrade §e" + upgradeId + " §7has been banished from this match! (" + profile.getRemainingBanishes() + "/10 remaining)");

        populateUpgradeChoices(inv, player, arena, profile, plugin);
    }

    public static void applyPinDisplay(ItemStack item, MobArenav2 plugin) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        String currentName = meta.getDisplayName();
        if (!currentName.contains("PINNED")) {
            meta.setDisplayName(currentName + colorize(" &#FFD700&l[📌 PINNED]"));
        }
        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();
        lore.add(0, colorize("&#FFD700&l★ PINNED: &ePreserved upon reroll!"));
        meta.setLore(lore);
        meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    public static void removePinDisplay(ItemStack item, MobArenav2 plugin) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        String currentName = meta.getDisplayName();
        if (currentName.contains("[📌 PINNED]")) {
            meta.setDisplayName(currentName.replace(colorize(" &#FFD700&l[📌 PINNED]"), "").replace(" [📌 PINNED]", ""));
        }
        List<String> lore = meta.getLore();
        if (lore != null) {
            lore.removeIf(l -> l.contains("★ PINNED:"));
            meta.setLore(lore);
        }
        NamespacedKey upKey = new NamespacedKey(plugin, "upgrade_id");
        String upId = meta.getPersistentDataContainer().get(upKey, PersistentDataType.STRING);
        if (upId != null && !upId.startsWith("super_")) {
            meta.removeEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING);
        }
        item.setItemMeta(meta);
    }
}
