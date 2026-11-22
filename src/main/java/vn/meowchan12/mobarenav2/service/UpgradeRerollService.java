package vn.meowchan12.mobarenav2.service;

import net.md_5.bungee.api.ChatColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.arena.UpgradeGUI;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service managing Roguelike Upgrade Rerolls with non-fixed Vault economy costs.
 * Escalating formula:
 * Attempt 1: (BaseCost + Wave * WaveFactor) * 10
 * Attempt 2: Attempt 1 * 8
 * Attempt 3: Attempt 2 * 7 (Final attempt)
 */
public class UpgradeRerollService {

    private final MobArenav2 plugin;
    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,###");

    public UpgradeRerollService(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    private static String colorize(String text) {
        if (text == null) return "";
        Pattern pattern = Pattern.compile("&#[a-fA-F0-9]{6}|#[a-fA-F0-9]{6}");
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            String hexCode = text.substring(matcher.start(), matcher.end());
            String replaceSharp = hexCode.replace("&#", "#");
            text = text.replace(hexCode, ChatColor.of(replaceSharp).toString());
            matcher = pattern.matcher(text);
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public boolean isEnabled() {
        FileConfiguration settings = plugin.getConfigManager().getSettings();
        return settings != null && settings.getBoolean("upgrade-reroll.enabled", true);
    }

    public int getMaxRerolls() {
        FileConfiguration settings = plugin.getConfigManager().getSettings();
        return settings != null ? settings.getInt("upgrade-reroll.max-rerolls-per-wave", 3) : 3;
    }

    public int getRerollSlot() {
        FileConfiguration settings = plugin.getConfigManager().getSettings();
        return settings != null ? settings.getInt("upgrade-reroll.slot", 22) : 22;
    }

    /**
     * Calculates dynamic reroll cost based on current wave and how many rerolls the player already did in this wave.
     * Attempt 1 (curRerolls == 0): BaseBracket * m1 (default 10x)
     * Attempt 2 (curRerolls == 1): Attempt 1 * m2 (default 8x)
     * Attempt 3 (curRerolls == 2): Attempt 2 * m3 (default 7x)
     */
    public double calculateCost(int wave, int currentRerollCount) {
        FileConfiguration settings = plugin.getConfigManager().getSettings();
        double baseCost = settings != null ? settings.getDouble("upgrade-reroll.base-cost", 150.0) : 150.0;
        double waveFactor = settings != null ? settings.getDouble("upgrade-reroll.wave-factor", 25.0) : 25.0;

        double baseBracket = baseCost + (wave * waveFactor);

        double m1 = settings != null ? settings.getDouble("upgrade-reroll.multipliers.1", 10.0) : 10.0;
        double m2 = settings != null ? settings.getDouble("upgrade-reroll.multipliers.2", 8.0) : 8.0;
        double m3 = settings != null ? settings.getDouble("upgrade-reroll.multipliers.3", 7.0) : 7.0;

        int nextAttempt = currentRerollCount + 1;
        if (nextAttempt <= 1) {
            return Math.round(baseBracket * m1);
        } else if (nextAttempt == 2) {
            return Math.round(baseBracket * m1 * m2);
        } else {
            return Math.round(baseBracket * m1 * m2 * m3);
        }
    }

    public ItemStack buildRerollButton(Player player, Arena arena, ArenaPlayerProfile profile) {
        if (!isEnabled()) return null;

        FileConfiguration settings = plugin.getConfigManager().getSettings();
        int maxRerolls = getMaxRerolls();
        int curRerolls = profile != null ? profile.getCurrentWaveRerolls() : 0;
        int wave = arena != null ? arena.getCurrentWave() : 1;

        boolean limitReached = maxRerolls > 0 && curRerolls >= maxRerolls;

        if (limitReached) {
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta bm = barrier.getItemMeta();
            if (bm != null) {
                bm.setDisplayName(colorize("&c&lMAX REROLLS REACHED"));
                List<String> lore = new ArrayList<>();
                lore.add(colorize("&7You have used all &e" + maxRerolls + "/" + maxRerolls + " &7rerolls for this wave."));
                lore.add(colorize("&7Select one of the 3 available upgrades above."));
                bm.setLore(lore);

                NamespacedKey key = new NamespacedKey(plugin, "ma_reroll_btn");
                bm.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
                barrier.setItemMeta(bm);
            }
            return barrier;
        }

        double cost = calculateCost(wave, curRerolls);
        Economy econ = plugin.getEconomy();
        double balance = econ != null ? econ.getBalance(player) : 0.0;
        int tickets = (plugin.getShopManager() != null) ? plugin.getShopManager().getPlayerData(player.getUniqueId()).getRerollTickets() : 0;
        boolean canAfford = (tickets > 0) || (balance >= cost);

        String matName = settings != null ? settings.getString("upgrade-reroll.material", "RECOVERY_COMPASS") : "RECOVERY_COMPASS";
        Material mat = Material.matchMaterial(matName);
        if (mat == null) mat = Material.RECOVERY_COMPASS;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colorize("&#00FFAA&l🎲 REROLL UPGRADES"));

            List<String> lore = new ArrayList<>();
            lore.add(colorize("&7&m-----------------------------"));
            lore.add(colorize("&fDiscard current choices & draw &e3 new upgrades&f."));
            lore.add(colorize("&fCurrent Wave: &e" + wave));
            lore.add(colorize("&fReroll Turn: &e" + (curRerolls + 1) + "/" + maxRerolls));
            if (profile != null && profile.getPinnedUpgradeSlot() != -1) {
                lore.add(colorize("&6📌 1 Pinned Card: &ePreserved on reroll"));
            }
            lore.add(colorize(""));
            if (tickets > 0) {
                lore.add(colorize("&a✦ Free Reroll Tickets: &e" + tickets + " Available"));
                lore.add(colorize("&a✔ Click to use 1 Ticket (Free)!"));
            } else {
                lore.add(colorize("&fCost: &6$" + MONEY_FORMAT.format(cost)));
                lore.add(colorize("&fYour Balance: &e$" + MONEY_FORMAT.format(balance)));
                lore.add(colorize(""));
                if (canAfford) {
                    lore.add(colorize("&a✔ Click to Reroll!"));
                } else {
                    lore.add(colorize("&c✘ Insufficient Funds!"));
                }
            }
            lore.add(colorize("&7&m-----------------------------"));
            meta.setLore(lore);

            NamespacedKey key = new NamespacedKey(plugin, "ma_reroll_btn");
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void handleRerollClick(Player player, Arena arena, ArenaPlayerProfile profile, Inventory inv) {
        if (!isEnabled()) return;
        if (player == null || arena == null || profile == null || inv == null) return;

        FileConfiguration settings = plugin.getConfigManager().getSettings();
        int maxRerolls = getMaxRerolls();
        int curRerolls = profile.getCurrentWaveRerolls();

        if (maxRerolls > 0 && curRerolls >= maxRerolls) {
            String limitMsg = settings != null ? settings.getString("upgrade-reroll.messages.limit-reached", "&c[MobArenav2] You have reached the maximum reroll limit for this wave! (%max%/%max%)") : "&c[MobArenav2] Limit reached!";
            player.sendMessage(colorize(limitMsg.replace("%max%", String.valueOf(maxRerolls))));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        boolean usedTicket = false;
        if (plugin.getShopManager() != null) {
            var shopData = plugin.getShopManager().getPlayerData(player.getUniqueId());
            if (shopData != null && shopData.useRerollTicket()) {
                plugin.getShopManager().savePlayerDataToDisk(player.getUniqueId());
                usedTicket = true;
                player.sendMessage(colorize("&a[MobArena Shop] Used 1x Upgrade Reroll Ticket! (" + shopData.getRerollTickets() + " remaining)"));
            }
        }

        double cost = calculateCost(arena.getCurrentWave(), curRerolls);
        Economy econ = plugin.getEconomy();

        if (!usedTicket) {
            if (econ == null || !econ.has(player, cost)) {
                double bal = econ != null ? econ.getBalance(player) : 0.0;
                String noMoneyMsg = settings != null ? settings.getString("upgrade-reroll.messages.not-enough-money", "&c[MobArenav2] You need &e$%cost% &cto reroll upgrades! (Your balance: &e$%balance%&c)") : "&cNot enough money!";
                player.sendMessage(colorize(noMoneyMsg
                        .replace("%cost%", MONEY_FORMAT.format(cost))
                        .replace("%balance%", MONEY_FORMAT.format(bal))));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Deduct Vault money
            econ.withdrawPlayer(player, cost);
        }

        profile.incrementRerolls();

        // Regenerate choices in-place (no screen flicker)
        UpgradeGUI.populateUpgradeChoices(inv, player, arena, profile, plugin);
        ItemStack newBtn = buildRerollButton(player, arena, profile);
        int slot = getRerollSlot();
        if (slot >= 0 && slot < inv.getSize()) {
            inv.setItem(slot, newBtn);
        }

        // Play feedback sounds & send message
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.5f);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.8f);

        String successMsg = settings != null ? settings.getString("upgrade-reroll.messages.success", "&a[MobArenav2] Rerolled upgrades for &e$%cost%&a! &7(Wave reroll: &e%count%/%max%&7)") : "&aRerolled!";
        player.sendMessage(colorize(successMsg
                .replace("%cost%", MONEY_FORMAT.format(cost))
                .replace("%count%", String.valueOf(profile.getCurrentWaveRerolls()))
                .replace("%max%", String.valueOf(maxRerolls))));
    }
}
