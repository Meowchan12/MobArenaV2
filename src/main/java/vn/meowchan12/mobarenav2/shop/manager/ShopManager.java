package vn.meowchan12.mobarenav2.shop.manager;

import me.clip.placeholderapi.PlaceholderAPI;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.shop.hook.PlayerPointsHook;
import vn.meowchan12.mobarenav2.shop.model.PlayerShopData;
import vn.meowchan12.mobarenav2.shop.model.ShopCategory;
import vn.meowchan12.mobarenav2.shop.model.ShopItem;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShopManager {

    private final MobArenav2 plugin;
    private final PlayerPointsHook playerPointsHook;
    private final Map<String, ShopCategory> categories = new HashMap<>();
    private final Map<String, ShopItem> shopItems = new HashMap<>();
    private final Map<UUID, PlayerShopData> playerDataMap = new HashMap<>();

    private File shopsFolder;

    public ShopManager(MobArenav2 plugin) {
        this.plugin = plugin;
        this.playerPointsHook = new PlayerPointsHook(plugin);
        initShopsDirectory();
        loadAllShops();
    }

    private void initShopsDirectory() {
        this.shopsFolder = new File(plugin.getDataFolder(), "shops");
        if (!shopsFolder.exists()) {
            shopsFolder.mkdirs();
        }

        saveDefaultShopFile("base_stats.yml");
        saveDefaultShopFile("ultimates.yml");
        saveDefaultShopFile("casino_utilities.yml");
        saveDefaultShopFile("advanced_stats.yml");
        saveDefaultShopFile("template_example.yml");
    }

    private void saveDefaultShopFile(String fileName) {
        File file = new File(shopsFolder, fileName);
        if (!file.exists()) {
            try (InputStream in = plugin.getResource("shops/" + fileName)) {
                if (in != null) {
                    Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (Exception ignored) {}
        }
    }

    public void loadAllShops() {
        categories.clear();
        shopItems.clear();

        File[] files = shopsFolder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

            if (config.contains("category")) {
                ConfigurationSection catSec = config.getConfigurationSection("category");
                if (catSec != null) {
                    String id = catSec.getString("id", file.getName().replace(".yml", ""));
                    String name = colorize(catSec.getString("display-name", "&eCategory"));
                    Material mat = Material.matchMaterial(catSec.getString("material", "CHEST"));
                    int slot = catSec.getInt("slot", 10);
                    List<String> lore = colorizeList(catSec.getStringList("lore"));

                    ShopCategory cat = new ShopCategory(id, name, mat, slot, lore);
                    categories.put(id.toLowerCase(), cat);
                }
            }

            if (config.contains("items")) {
                ConfigurationSection itemsSec = config.getConfigurationSection("items");
                if (itemsSec != null) {
                    for (String key : itemsSec.getKeys(false)) {
                        ConfigurationSection sec = itemsSec.getConfigurationSection(key);
                        if (sec == null) continue;

                        String categoryId = sec.getString("category", "base_stats");
                        int slot = sec.getInt("slot", 0);
                        String displayName = colorize(sec.getString("display-name", "&a" + key));
                        Material mat = Material.matchMaterial(sec.getString("material", "STONE"));
                        List<String> lore = colorizeList(sec.getStringList("lore"));

                        String priceType = sec.getString("price-type", "VAULT");
                        double baseCost = sec.getDouble("base-cost", sec.getDouble("cost", 1000.0));
                        double costMultiplier = sec.getDouble("cost-multiplier", 1.5);
                        int maxLevel = sec.getInt("max-level", 1);

                        String attributeName = sec.getString("attribute", null);
                        double baseValue = sec.getDouble("base-value", 0.0);
                        double incrementValue = sec.getDouble("increment", 0.0);

                        String inputPlaceholder = sec.getString("input-check.placeholder", null);
                        double inputReqValue = sec.getDouble("input-check.required-value", 0.0);
                        String inputFailMsg = colorize(sec.getString("input-check.fail-message", "&cRequirement not met!"));
                        String inputTakeCmd = sec.getString("input-take-command", null);

                        List<String> outputActions = colorizeList(sec.getStringList("output-actions"));

                        ShopItem item = new ShopItem(key, categoryId, slot, displayName, mat, lore,
                                priceType, baseCost, costMultiplier, maxLevel,
                                attributeName, baseValue, incrementValue,
                                inputPlaceholder, inputReqValue, inputFailMsg,
                                inputTakeCmd, outputActions);

                        shopItems.put(key.toLowerCase(), item);
                    }
                }
            }
        }

        plugin.getLogger().info("[MobArenav2] Loaded " + categories.size() + " shop categories and " + shopItems.size() + " shop items.");
    }

    public java.util.Set<String> getShopCategories() {
        return categories.keySet();
    }

    public java.util.Set<String> getAllItemIds() {
        return shopItems.keySet();
    }

    public PlayerShopData getPlayerData(UUID uuid) {
        if (!playerDataMap.containsKey(uuid)) {
            playerDataMap.put(uuid, loadPlayerDataFromDisk(uuid));
        }
        return playerDataMap.get(uuid);
    }

    private PlayerShopData loadPlayerDataFromDisk(UUID uuid) {
        PlayerShopData data = new PlayerShopData(uuid);
        File userDir = new File(plugin.getDataFolder(), "userdata");
        if (!userDir.exists()) userDir.mkdirs();

        File file = new File(userDir, "shop_" + uuid.toString() + ".yml");
        if (file.exists()) {
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection levelsSec = config.getConfigurationSection("levels");
            if (levelsSec != null) {
                for (String key : levelsSec.getKeys(false)) {
                    data.setItemLevel(key, levelsSec.getInt(key, 0));
                }
            }
        }
        return data;
    }

    public void savePlayerDataToDisk(UUID uuid) {
        PlayerShopData data = playerDataMap.get(uuid);
        if (data == null) return;

        File userDir = new File(plugin.getDataFolder(), "userdata");
        if (!userDir.exists()) userDir.mkdirs();

        File file = new File(userDir, "shop_" + uuid.toString() + ".yml");
        YamlConfiguration config = new YamlConfiguration();

        for (Map.Entry<String, Integer> entry : data.getItemLevels().entrySet()) {
            config.set("levels." + entry.getKey(), entry.getValue());
        }

        try {
            config.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("[MobArenav2] Failed to save shop user data for: " + uuid);
        }
    }

    // [v6.2 FIX] Memory Leak Prevention: Save and unload player data from RAM on disconnect
    public void unloadPlayerData(UUID uuid) {
        savePlayerDataToDisk(uuid);
        playerDataMap.remove(uuid);
    }

    // =====================================================================
    // PURCHASE LOGIC & TRANSACTION VALIDATION
    // =====================================================================
    public boolean buyItem(Player player, String itemId) {
        ShopItem item = shopItems.get(itemId.toLowerCase());
        if (item == null) return false;

        PlayerShopData data = getPlayerData(player.getUniqueId());
        int currentLevel = data.getItemLevel(item.getId());

        if (currentLevel >= item.getMaxLevel()) {
            player.sendMessage("§c[MobArena Shop] You have reached the maximum level for this upgrade!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return false;
        }

        int targetLevel = currentLevel + 1;
        double cost = item.calculateCostForLevel(targetLevel);

        boolean success = false;
        String pType = item.getPriceType();

        if (pType.equals("VAULT")) {
            if (plugin.getEconomy() == null) {
                player.sendMessage("§c[MobArena Shop] Economy system (Vault) is not available!");
                return false;
            }
            if (!plugin.getEconomy().has(player, cost)) {
                player.sendMessage("§c[MobArena Shop] You need $" + String.format("%.2f", cost) + " Vault Money!");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return false;
            }
            if (plugin.getEconomy().withdrawPlayer(player, cost).transactionSuccess()) {
                success = true;
            }
        } else if (pType.equals("PLAYERPOINTS")) {
            int intCost = (int) Math.round(cost);
            if (playerPointsHook.isEnabled()) {
                if (!playerPointsHook.hasEnoughPoints(player, intCost)) {
                    player.sendMessage("§c[MobArena Shop] You need " + intCost + " PlayerPoints!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return false;
                }
                success = playerPointsHook.takePoints(player, intCost);
            } else {
                // Fallback using PlaceholderAPI or Console command
                String checkResult = parsePlaceholder(player, "%playerpoints_points%");
                try {
                    int pts = Integer.parseInt(checkResult);
                    if (pts >= intCost) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "points take " + player.getName() + " " + intCost);
                        success = true;
                    } else {
                        player.sendMessage("§c[MobArena Shop] You need " + intCost + " PlayerPoints!");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage("§c[MobArena Shop] PlayerPoints system is unavailable!");
                    return false;
                }
            }
        } else if (pType.equals("CUSTOM_COMMAND")) {
            if (item.getInputCheckPlaceholder() != null) {
                String valStr = parsePlaceholder(player, item.getInputCheckPlaceholder());
                try {
                    double val = Double.parseDouble(valStr);
                    if (val < item.getInputCheckRequiredValue()) {
                        player.sendMessage(item.getInputCheckFailMessage());
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        return false;
                    }
                } catch (NumberFormatException e) {
                    player.sendMessage(item.getInputCheckFailMessage());
                    return false;
                }
            }

            if (item.getInputTakeCommand() != null && !item.getInputTakeCommand().isEmpty()) {
                String cmd = item.getInputTakeCommand().replace("%player%", player.getName()).replace("%cost%", String.valueOf(cost));
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }
            success = true;
        }

        if (success) {
            data.incrementItemLevel(item.getId());
            savePlayerDataToDisk(player.getUniqueId());

            executeOutputActions(player, item, targetLevel);

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            player.sendMessage("§a[MobArena Shop] §ePurchased " + item.getDisplayName() + " §e[Lv." + targetLevel + "/" + item.getMaxLevel() + "]!");

            // If player is inside an active arena, apply attribute changes immediately
            if (plugin.getArenaManager().isPlaying(player)) {
                applyPermanentAttributesToPlayer(player);
            }

            return true;
        }

        return false;
    }

    private void executeOutputActions(Player player, ShopItem item, int targetLevel) {
        if (item.getOutputActions() == null) return;

        for (String action : item.getOutputActions()) {
            action = action.replace("%player%", player.getName()).replace("%level%", String.valueOf(targetLevel));

            if (action.startsWith("command:")) {
                String cmd = action.replace("command:", "").trim();
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            } else if (action.startsWith("permission:")) {
                String perm = action.replace("permission:", "").trim();
                if (perm.startsWith("add ")) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + player.getName() + " permission set " + perm.replace("add ", "").trim() + " true");
                }
            } else if (action.startsWith("sound:")) {
                String[] parts = action.replace("sound:", "").trim().split(" ");
                try {
                    Sound s = Sound.valueOf(parts[0].toUpperCase());
                    float vol = parts.length > 1 ? Float.parseFloat(parts[1]) : 1.0f;
                    float pitch = parts.length > 2 ? Float.parseFloat(parts[2]) : 1.0f;
                    player.playSound(player.getLocation(), s, vol, pitch);
                } catch (Exception ignored) {}
            } else if (action.startsWith("message:")) {
                player.sendMessage(colorize(action.replace("message:", "").trim()));
            }
        }
    }

    // =====================================================================
    // =====================================================================
    // APPLY PERMANENT SHOP BASE ATTRIBUTES INSIDE MOBARENA (IDEMPOTENT)
    // =====================================================================
    public void applyPermanentAttributesToPlayer(Player player) {
        PlayerShopData data = getPlayerData(player.getUniqueId());
        if (data == null) return;

        Map<Attribute, Double> totalBonuses = new HashMap<>();

        for (Map.Entry<String, Integer> entry : data.getItemLevels().entrySet()) {
            if (entry.getValue() <= 0) continue;

            ShopItem item = shopItems.get(entry.getKey().toLowerCase());
            if (item == null || item.getAttributeName() == null) continue;

            double totalBonus = item.calculateStatForLevel(entry.getValue());
            Attribute attr = parseAttribute(item.getAttributeName());

            if (attr != null) {
                totalBonuses.put(attr, totalBonuses.getOrDefault(attr, 0.0) + totalBonus);
            }
        }

        // Apply HP
        double hpBonus = totalBonuses.getOrDefault(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH, 0.0);
        AttributeInstance hpInst = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH);
        if (hpInst != null) {
            hpInst.setBaseValue(20.0 + hpBonus);
            player.setHealth(Math.min(player.getHealth() + hpBonus, hpInst.getValue()));
        }

        // Apply Armor
        double armorBonus = totalBonuses.getOrDefault(vn.meowchan12.mobarenav2.utils.AttributeUtils.ARMOR, 0.0);
        AttributeInstance armorInst = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.ARMOR);
        if (armorInst != null) {
            armorInst.setBaseValue(0.0 + armorBonus);
        }

        // Apply Attack Damage
        double dmgBonus = totalBonuses.getOrDefault(vn.meowchan12.mobarenav2.utils.AttributeUtils.ATTACK_DAMAGE, 0.0);
        AttributeInstance dmgInst = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.ATTACK_DAMAGE);
        if (dmgInst != null) {
            dmgInst.setBaseValue(1.0 + dmgBonus);
        }

        // Apply Armor Toughness
        double toughnessBonus = totalBonuses.getOrDefault(vn.meowchan12.mobarenav2.utils.AttributeUtils.ARMOR_TOUGHNESS, 0.0);
        AttributeInstance toughnessInst = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.ARMOR_TOUGHNESS);
        if (toughnessInst != null && toughnessBonus > 0) {
            toughnessInst.setBaseValue(toughnessBonus);
        }
    }

    private Attribute parseAttribute(String name) {
        if (name == null) return null;
        String upper = name.toUpperCase();
        if (upper.equals("GENERIC_MAX_HEALTH") || upper.equals("MAX_HEALTH")) return vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH;
        if (upper.equals("GENERIC_ATTACK_DAMAGE") || upper.equals("ATTACK_DAMAGE")) return vn.meowchan12.mobarenav2.utils.AttributeUtils.ATTACK_DAMAGE;
        if (upper.equals("GENERIC_MOVEMENT_SPEED") || upper.equals("MOVEMENT_SPEED")) return vn.meowchan12.mobarenav2.utils.AttributeUtils.MOVEMENT_SPEED;
        if (upper.equals("GENERIC_ARMOR") || upper.equals("ARMOR")) return vn.meowchan12.mobarenav2.utils.AttributeUtils.ARMOR;
        if (upper.equals("GENERIC_ARMOR_TOUGHNESS") || upper.equals("ARMOR_TOUGHNESS")) return vn.meowchan12.mobarenav2.utils.AttributeUtils.ARMOR_TOUGHNESS;
        
        try {
            return Attribute.valueOf(upper);
        } catch (Exception e) {
            return null;
        }
    }

    private String parsePlaceholder(Player player, String raw) {
        if (raw == null) return "";
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            return PlaceholderAPI.setPlaceholders(player, raw);
        }
        return raw;
    }

    public static String colorize(String text) {
        return ColorUtils.colorize(text);
    }

    private List<String> colorizeList(List<String> list) {
        if (list == null) return new ArrayList<>();
        List<String> res = new ArrayList<>();
        for (String s : list) res.add(colorize(s));
        return res;
    }

    public double getCustomStatBonus(UUID uuid, String customAttributeName) {
        PlayerShopData data = getPlayerData(uuid);
        if (data == null) return 0.0;
        
        double total = 0.0;
        for (Map.Entry<String, Integer> entry : data.getItemLevels().entrySet()) {
            if (entry.getValue() <= 0) continue;
            ShopItem item = shopItems.get(entry.getKey().toLowerCase());
            if (item == null || item.getAttributeName() == null) continue;
            if (item.getAttributeName().equalsIgnoreCase(customAttributeName)) {
                total += item.calculateStatForLevel(entry.getValue());
            }
        }
        return total;
    }

    public Map<String, ShopCategory> getCategories() { return categories; }
    public Map<String, ShopItem> getShopItems() { return shopItems; }
    public PlayerPointsHook getPlayerPointsHook() { return playerPointsHook; }
}
