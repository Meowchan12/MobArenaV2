package vn.meowchan12.mobarenav2.arena;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.util.*;

/**
 * [v8.0.3]: Interactive Match Record & Player Profile Detail GUI.
 * Displays match statistics, personal best record wave, career metrics,
 * and all roguelike upgrade cards used during the match.
 */
public class MatchDetailGUI {

    private static final int[] UPGRADE_SLOTS = {29, 30, 31, 32, 33, 38, 39, 40, 41, 42};

    public static boolean open(Player viewer, Arena arena, String targetName, MobArenav2 plugin) {
        if (viewer == null || arena == null || targetName == null) return false;

        ConfigurationSection summary = arena.getConfig() != null ? arena.getConfig().getConfigurationSection("last-match-summary") : null;
        if (summary == null && arena.getConfig() != null) {
            summary = arena.getConfig().getConfigurationSection("last-match");
        }
        if (summary == null) {
            summary = arena.getMemoryMatchSummary();
        }
        if (summary == null) {
            viewer.sendMessage("§c[MobArenav2] No match history found for arena §e" + arena.getName() + "§c!");
            return false;
        }

        ConfigurationSection playersSec = summary.getConfigurationSection("players");
        if (playersSec == null || playersSec.getKeys(false).isEmpty()) {
            viewer.sendMessage("§c[MobArenav2] No player records found for the last match!");
            return false;
        }

        String matchedUUID = null;
        String resolvedName = targetName;
        ConfigurationSection playerMatchSec = null;

        for (String uuidKey : playersSec.getKeys(false)) {
            ConfigurationSection pSec = playersSec.getConfigurationSection(uuidKey);
            if (pSec == null) continue;
            String name = pSec.getString("name", "");
            if (name.equalsIgnoreCase(targetName) || uuidKey.equalsIgnoreCase(targetName)) {
                matchedUUID = uuidKey;
                resolvedName = name.isEmpty() ? targetName : name;
                playerMatchSec = pSec;
                break;
            }
        }

        if (playerMatchSec == null) {
            viewer.sendMessage("§c[MobArenav2] Player '§e" + targetName + "§c' was not found in arena §e" + arena.getName() + "§c's last match!");
            return false;
        }

        UUID playerUUID;
        try {
            playerUUID = UUID.fromString(matchedUUID);
        } catch (Exception e) {
            @SuppressWarnings("deprecation")
            OfflinePlayer op = Bukkit.getOfflinePlayer(resolvedName);
            playerUUID = op.getUniqueId();
        }

        // Match metrics
        int wave = summary.getInt("wave", 0);
        int maxWaves = summary.getInt("max-waves", arena.getConfig().getInt("settings.max-waves", 20));
        boolean isEndless = summary.getBoolean("endless", summary.getBoolean("is-endless", false));

        String combatClass = playerMatchSec.getString("class", "None");
        int kills = playerMatchSec.getInt("kills", 0);
        double damageDealt = playerMatchSec.getDouble("damage", 0.0);
        double damageTaken = playerMatchSec.getDouble("damageTaken", 0.0);
        int bossKills = playerMatchSec.getInt("bossKills", 0);

        // Career metrics
        int lifetimeKills = plugin.getUserDataManager().getStat(playerUUID, "lifetime_kills");
        int lifetimeBossKills = plugin.getUserDataManager().getStat(playerUUID, "boss_kills");
        int gamesPlayed = plugin.getUserDataManager().getStat(playerUUID, "games_played");
        int gamesWon = plugin.getUserDataManager().getStat(playerUUID, "games_won");
        int highestWave = plugin.getUserDataManager().getStat(playerUUID, "highest_wave");
        double winRate = plugin.getUserDataManager().getCareerDouble(playerUUID, "win_rate_percent");
        String favClass = plugin.getUserDataManager().getFavoriteClass(playerUUID);

        // Upgrades used in this match
        Map<String, Integer> upgrades = new LinkedHashMap<>();
        ConfigurationSection upSec = playerMatchSec.getConfigurationSection("upgrades");
        if (upSec != null) {
            for (String upId : upSec.getKeys(false)) {
                upgrades.put(upId, upSec.getInt(upId, 1));
            }
        }

        // Build GUI
        String title = "Match Details: " + resolvedName;
        Inventory gui = Bukkit.createInventory(null, 54, ColorUtils.toAdventure("&8Match Details: &6" + resolvedName));

        // Fill background borders
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 54; i++) {
            if (i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, border);
            }
        }

        // Slot 4: Player Head & Match Overview
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(playerUUID));
            skullMeta.displayName(ColorUtils.toAdventure("&6&l" + resolvedName + " &8[&b" + combatClass + "&8]"));
            List<Component> headLore = new ArrayList<>();
            headLore.add(ColorUtils.toAdventure("&8&m--------------------------------"));
            headLore.add(ColorUtils.toAdventure("&eArena: &b" + arena.getName()));
            headLore.add(ColorUtils.toAdventure("&eCombat Class: &a" + combatClass));
            String waveStr = isEndless ? (wave + " &8(Endless)") : (wave + "&7/&b" + maxWaves);
            headLore.add(ColorUtils.toAdventure("&eMatch Final Wave: &b" + waveStr));
            headLore.add(ColorUtils.toAdventure("&ePersonal Record Wave: &d★ Wave " + highestWave + " ★"));
            headLore.add(ColorUtils.toAdventure("&8&m--------------------------------"));
            headLore.add(ColorUtils.toAdventure("&7Career Matches: &f" + gamesPlayed));
            headLore.add(ColorUtils.toAdventure("&7Career Victories: &a" + gamesWon));
            headLore.add(ColorUtils.toAdventure("&7Career Win Rate: &e" + winRate + "%"));
            headLore.add(ColorUtils.toAdventure("&7Favorite Class: &6" + favClass));
            headLore.add(ColorUtils.toAdventure("&8&m--------------------------------"));
            skullMeta.lore(headLore);
            head.setItemMeta(skullMeta);
        }
        gui.setItem(4, head);

        // Slot 20: Match Combat Performance (Sword)
        List<String> combatLore = Arrays.asList(
                "&8&m--------------------------------",
                "&e• Mobs Eliminated: &c" + kills + " Mobs",
                "&e• Bosses Slain: &d" + bossKills + " Bosses",
                "&e• Damage Dealt: &a" + String.format("%.0f", damageDealt) + " DMG",
                "&e• Damage Absorbed: &6" + String.format("%.0f", damageTaken) + " DMG",
                "&8&m--------------------------------"
        );
        ItemStack swordItem = createItem(Material.DIAMOND_SWORD, "&c&lMatch Combat Performance", combatLore);
        ItemMeta swordMeta = swordItem.getItemMeta();
        if (swordMeta != null) {
            swordMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            swordItem.setItemMeta(swordMeta);
        }
        gui.setItem(20, swordItem);

        // Slot 21: Match Duration (Clock)
        String duration = summary.getString("duration", "N/A");
        String startedAt = summary.getString("started-at", "N/A");
        String endedAt = summary.getString("ended-at", "N/A");
        List<String> clockLore = Arrays.asList(
                "&8&m--------------------------------",
                "&e• Match Duration: &b" + duration,
                "&e• Started At: &7" + startedAt,
                "&e• Concluded At: &7" + endedAt,
                "&e• Outcome: " + (wave >= maxWaves && !isEndless ? "&aArena Cleared (Wave " + wave + ")" : "&cDefeated at Wave " + wave),
                "&8&m--------------------------------"
        );
        gui.setItem(21, createItem(Material.CLOCK, "&e&lMatch Duration & Time", clockLore));

        // Slot 24: Lifetime Career Stats (Star)
        List<String> careerLore = Arrays.asList(
                "&8&m--------------------------------",
                "&e• Lifetime Kills: &a" + lifetimeKills + " Mobs",
                "&e• Lifetime Boss Kills: &d" + lifetimeBossKills + " Bosses",
                "&e• Personal Best Wave: &6★ Wave " + highestWave + " ★",
                "&8&m--------------------------------"
        );
        gui.setItem(24, createItem(Material.NETHER_STAR, "&e&lLifetime Career Records", careerLore));

        // Slot 22: Upgrades Used Divider
        List<String> upgHeaderLore = Arrays.asList(
                "&7Total Upgrades Selected: &e" + upgrades.size(),
                "&7Below are all the upgrade cards and passive abilities",
                "&7utilized by this player in this match:"
        );
        gui.setItem(22, createItem(Material.BOOK, "&b&lMatch Upgrades Selected &8(&e" + upgrades.size() + "&8)", upgHeaderLore));

        // Slots 29..33, 38..42: Upgrade Cards
        if (upgrades.isEmpty()) {
            gui.setItem(31, createItem(Material.STRUCTURE_VOID, "&7No Upgrades Selected",
                    List.of("&7This player did not select any upgrade cards in this match.")));
        } else {
            int slotIdx = 0;
            for (Map.Entry<String, Integer> entry : upgrades.entrySet()) {
                if (slotIdx >= UPGRADE_SLOTS.length) break;
                String upId = entry.getKey();
                int level = entry.getValue();

                UpgradeData data = plugin.getUpgradeManager().getUpgradeData(upId);
                ItemStack cardItem;
                if (data != null) {
                    boolean isUltimate = upId.toLowerCase().startsWith("ultimate_")
                            || (data.type != null && data.type.toUpperCase().startsWith("ULTIMATE_"));
                    String cleanName = data.displayName != null ? data.displayName
                            .replace("%current_level%", String.valueOf(level))
                            .replace("%max_level%", String.valueOf(data.maxLevel))
                            .replaceAll("(?i)\\s*(&#[a-fA-F0-9]{6}|[&§][0-9a-fk-orA-FK-OR])?\\s*\\[Lv[^\\]]*\\]", "")
                            .trim() : upId;

                    List<String> cardLore = new ArrayList<>();
                    cardLore.add("&7Type: &e" + data.type);
                    cardLore.add(isUltimate ? "&6&l✦ ULTIMATE FUSION (MAX)" : "&7Level: &aLv." + level + " &7/ &eMAX (Lv." + data.maxLevel + ")");
                    cardLore.add("&8&m--------------------------------");
                    cardLore.addAll(data.getFormattedLore(level));
                    String itemTitle = isUltimate ? "&6✦ " + cleanName : "&6[Lv." + level + "] " + cleanName;
                    cardItem = createItem(data.material != null ? data.material : Material.BOOK, itemTitle, cardLore);
                } else {
                    cardItem = createItem(Material.ENCHANTED_BOOK, "&6[Lv." + level + "] &f" + upId,
                            List.of("&7Level: &aLv." + level, "&8(Custom Upgrade)"));
                }

                gui.setItem(UPGRADE_SLOTS[slotIdx], cardItem);
                slotIdx++;
            }
        }

        // Slot 49: Close / Back Button
        gui.setItem(49, createItem(Material.BARRIER, "&c&lClose", List.of("&7Click to close this window.")));

        viewer.openInventory(gui);
        viewer.playSound(viewer.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        return true;
    }

    private static ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (name != null) meta.displayName(ColorUtils.toAdventure(name));
            if (lore != null) {
                List<Component> componentLore = new ArrayList<>();
                for (String l : lore) {
                    componentLore.add(ColorUtils.toAdventure(l));
                }
                meta.lore(componentLore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }
}
