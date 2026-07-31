package vn.meowchan12.mobarenav2.manager;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.exceptions.InvalidConfigurationException;
import vn.meowchan12.mobarenav2.utils.ItemParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ClassManager {

    private final MobArenav2 plugin;
    private final Map<String, ItemStack> classDisplayIcons = new HashMap<>();

    public ClassManager(MobArenav2 plugin) {
        this.plugin = plugin;
        loadClasses();
    }

    public void loadClasses() {
        classDisplayIcons.clear();
        ConfigurationSection section = plugin.getConfigManager().getClasses().getConfigurationSection("classes");
        if (section == null) return;

        for (String className : section.getKeys(false)) {
            try {
                validateClassConfig(section, className);

                String itemsStr = section.getString(className + ".items", "stone_sword");
                String firstItemStr = itemsStr.split(",")[0].trim();

                ItemStack icon;
                // Chuỗi Base64 của Java Serialization luôn bắt đầu bằng "rO0AB"
                if (firstItemStr.startsWith("rO0AB")) {
                    icon = ItemParser.itemFromBase64(firstItemStr);
                } else {
                    icon = ItemParser.parseItem(firstItemStr);
                }

                if (icon == null || icon.getType() == Material.AIR) {
                    icon = new ItemStack(Material.STONE_SWORD);
                }

                classDisplayIcons.put(className, icon);

            } catch (InvalidConfigurationException e) {
                plugin.getLogger().severe("========================================");
                plugin.getLogger().severe("[MobArenav2] ⛔ CLASS CONFIGURATION ERROR ⛔");
                plugin.getLogger().severe("[MobArenav2] File: " + e.getFileName() + " | Class: " + e.getSection());
                plugin.getLogger().severe("[MobArenav2] Error: " + e.getMessage());
                plugin.getLogger().severe("[MobArenav2] Action: Class '" + className + "' has been disabled to prevent crashes.");
                plugin.getLogger().severe("========================================");
            }
        }
    }

    private void validateClassConfig(ConfigurationSection section, String className) throws InvalidConfigurationException {
        if (section.contains(className + ".limit") && !section.isInt(className + ".limit")) {
            throw new InvalidConfigurationException("The 'limit' value must be an integer (number).", "classes.yml", className);
        }

        String itemsStr = section.getString(className + ".items");
        if (itemsStr == null || itemsStr.isEmpty()) {
            throw new InvalidConfigurationException("Missing mandatory 'items' list for this class.", "classes.yml", className);
        }

        String[] items = itemsStr.split(",");
        for (String itemStr : items) {
            String trimmedItem = itemStr.trim();
            // Nếu là Base64 thì bỏ qua bước kiểm tra Material
            if (trimmedItem.startsWith("rO0AB")) continue;

            String rawMat = trimmedItem.split(" ")[0].split(":")[0].toUpperCase().replace("OFF_HAND", "");
            if (Material.matchMaterial(rawMat) == null) {
                throw new InvalidConfigurationException("Invalid item material '" + rawMat + "' found in the items list. Please check your spelling.", "classes.yml", className);
            }
        }

        if (section.contains(className + ".armor")) {
            String armorStr = section.getString(className + ".armor");
            if (armorStr != null && !armorStr.isEmpty()) {
                String[] armors = armorStr.split(",");
                for (String arm : armors) {
                    String trimmedArm = arm.trim();
                    if (trimmedArm.startsWith("rO0AB")) continue;

                    String rawArm = trimmedArm.split(" ")[0].split(":")[0].toUpperCase();
                    if (Material.matchMaterial(rawArm) == null) {
                        throw new InvalidConfigurationException("Invalid armor material '" + rawArm + "' found in the armor list.", "classes.yml", className);
                    }
                }
            }
        }
    }

    public Set<String> getAvailableClasses() {
        return classDisplayIcons.keySet();
    }

    public ItemStack getClassIcon(String className) {
        return classDisplayIcons.get(className);
    }

    public boolean isClassAllowedInArena(String className, Arena arena) {
        if (arena == null) return true;
        ConfigurationSection section = plugin.getConfigManager().getClasses().getConfigurationSection("classes." + className);

        if (section != null && section.contains("allowed-arenas")) {
            List<String> allowedArenas = section.getStringList("allowed-arenas");
            if (allowedArenas != null && !allowedArenas.isEmpty()) {
                for (String allowed : allowedArenas) {
                    if (allowed.equalsIgnoreCase(arena.getName())) {
                        return true;
                    }
                }
                return false;
            }
        }
        return true;
    }

    public int getClassLimit(String className) {
        ConfigurationSection section = plugin.getConfigManager().getClasses().getConfigurationSection("classes." + className);
        if (section != null && section.contains("limit")) {
            return section.getInt("limit", -1);
        }
        return -1;
    }

    public int getPlayersInClass(Arena arena, String className) {
        if (arena == null) return 0;
        int count = 0;
        for (ArenaPlayerProfile profile : arena.getPlayerProfiles().values()) {
            if (className.equalsIgnoreCase(profile.getSelectedClass())) {
                count++;
            }
        }
        return count;
    }

    public boolean equipClass(Player player, String className) {
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena == null) return false;

        if (!isClassAllowedInArena(className, arena)) {
            player.sendMessage("§c[MobArenav2] The " + className + " class is restricted and cannot be used in this arena!");
            return false;
        }

        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());

        int limit = getClassLimit(className);
        if (limit != -1) {
            int current = getPlayersInClass(arena, className);

            if (profile != null && className.equalsIgnoreCase(profile.getSelectedClass())) {
                player.sendMessage("§e[MobArenav2] You have already equipped the " + className + " class!");
                return true;
            }

            if (current >= limit) {
                player.sendMessage("§c[MobArenav2] The " + className + " class is currently FULL (" + current + "/" + limit + ")!");
                return false;
            }
        }

        ConfigurationSection section = plugin.getConfigManager().getClasses().getConfigurationSection("classes." + className);
        if (section == null) return false;

        player.getInventory().clear();
        player.getInventory().setArmorContents(null);
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }

        String itemsStr = section.getString("items");
        if (itemsStr != null) {
            String[] items = itemsStr.split(",");
            for (String itemStr : items) {
                String trimmed = itemStr.trim();
                ItemStack item;
                if (trimmed.startsWith("rO0AB")) {
                    item = ItemParser.itemFromBase64(trimmed);
                } else {
                    item = ItemParser.parseItem(trimmed);
                }

                if (item != null) player.getInventory().addItem(item);
            }
        }

        String armorStr = section.getString("armor");
        if (armorStr != null) {
            String[] armors = armorStr.split(",");
            for (String armStr : armors) {
                String trimmed = armStr.trim();
                ItemStack armor;
                if (trimmed.startsWith("rO0AB")) {
                    armor = ItemParser.itemFromBase64(trimmed);
                } else {
                    armor = ItemParser.parseItem(trimmed);
                }

                if (armor != null) {
                    String typeName = armor.getType().name();
                    if (typeName.endsWith("_HELMET") || typeName.equals("CARVED_PUMPKIN") || typeName.equals("PLAYER_HEAD")) player.getInventory().setHelmet(armor);
                    else if (typeName.endsWith("_CHESTPLATE") || typeName.equals("ELYTRA")) player.getInventory().setChestplate(armor);
                    else if (typeName.endsWith("_LEGGINGS")) player.getInventory().setLeggings(armor);
                    else if (typeName.endsWith("_BOOTS")) player.getInventory().setBoots(armor);
                }
            }
        }

        String effectsStr = section.getString("effects");
        if (effectsStr != null) {
            String[] effects = effectsStr.split(",");
            for (String effStr : effects) {
                String[] effData = effStr.trim().split(":");
                PotionEffectType type = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(effData[0].toLowerCase()));
                if (type != null) {
                    int amplifier = effData.length > 1 ? Integer.parseInt(effData[1]) : 0;
                    player.addPotionEffect(new PotionEffect(type, Integer.MAX_VALUE, amplifier, false, false, true));
                }
            }
        }

        if (section.getBoolean("heal-on-start", true)) {
            AttributeInstance hpAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hpAttr != null) {
                player.setHealth(hpAttr.getValue());
            } else {
                player.setHealth(20.0);
            }
        }

        // =====================================================================
        // [v5.6 FEATURE]: PER-CLASS BASE HEALTH OVERRIDE
        // Checks arena config for class-specific base health values
        // =====================================================================
        Arena arena2 = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena2 != null && arena2.getConfig() != null) {
            FileConfiguration arenaConfig = arena2.getConfig();
            
            // Check per-class override first
            double classHealth = arenaConfig.getDouble("settings.base-health-per-class." + className, 0.0);
            double arenaBaseHealth = arenaConfig.getDouble("settings.base-health", 0.0);
            
            double targetBaseHealth = classHealth > 0 ? classHealth : arenaBaseHealth;
            
            if (targetBaseHealth > 0) {
                AttributeInstance hpAttr2 = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (hpAttr2 != null) {
                    // Preserve shop bonuses
                    double shopBonus = 0.0;
                    if (plugin.getShopManager() != null) {
                        shopBonus = hpAttr2.getBaseValue() - 20.0;
                        if (shopBonus < 0) shopBonus = 0;
                    }
                    hpAttr2.setBaseValue(targetBaseHealth + shopBonus);
                    player.setHealth(hpAttr2.getValue());
                }
            }
        }

        if (profile != null) {
            profile.setSelectedClass(className);
        }

        player.sendMessage("§a[MobArenav2] You have selected the §e" + className + " §aclass!");
        return true;
    }

    public ItemStack[] getClassArmor(String className) {
        ItemStack[] armor = new ItemStack[4];
        ConfigurationSection section = plugin.getConfigManager().getClasses().getConfigurationSection("classes." + className);
        if (section == null) return armor;
        String armorStr = section.getString("armor");
        if (armorStr != null) {
            String[] armors = armorStr.split(",");
            for (String armStr : armors) {
                String trimmed = armStr.trim();
                ItemStack item;
                if (trimmed.startsWith("rO0AB")) {
                    item = ItemParser.itemFromBase64(trimmed);
                } else {
                    item = ItemParser.parseItem(trimmed);
                }

                if (item != null) {
                    String type = item.getType().name();
                    if (type.endsWith("_HELMET") || type.equals("CARVED_PUMPKIN") || type.equals("PLAYER_HEAD")) armor[3] = item;
                    else if (type.endsWith("_CHESTPLATE") || type.equals("ELYTRA")) armor[2] = item;
                    else if (type.endsWith("_LEGGINGS")) armor[1] = item;
                    else if (type.endsWith("_BOOTS")) armor[0] = item;
                }
            }
        }
        return armor;
    }

    public List<ItemStack> getClassItems(String className) {
        List<ItemStack> items = new ArrayList<>();
        ConfigurationSection section = plugin.getConfigManager().getClasses().getConfigurationSection("classes." + className);
        if (section == null) return items;
        String itemsStr = section.getString("items");
        if (itemsStr != null) {
            String[] parts = itemsStr.split(",");
            for (String part : parts) {
                String trimmed = part.trim();
                ItemStack item;
                if (trimmed.startsWith("rO0AB")) {
                    item = ItemParser.itemFromBase64(trimmed);
                } else {
                    item = ItemParser.parseItem(trimmed);
                }
                if (item != null) items.add(item);
            }
        }
        return items;
    }

    public void saveClassSetup(String className, ItemStack[] armor, List<ItemStack> items, int limit, boolean healOnStart) {
        org.bukkit.configuration.file.FileConfiguration config = plugin.getConfigManager().getClasses();

        config.set("classes." + className + ".limit", limit);
        config.set("classes." + className + ".heal-on-start", healOnStart);

        List<String> armorStrings = new ArrayList<>();
        for (ItemStack a : armor) {
            if (a != null && a.getType() != Material.AIR) {
                armorStrings.add(ItemParser.itemToBase64(a));
            }
        }
        config.set("classes." + className + ".armor", armorStrings.isEmpty() ? null : String.join(",", armorStrings));

        List<String> itemStrings = new ArrayList<>();
        for (ItemStack it : items) {
            if (it != null && it.getType() != Material.AIR) {
                itemStrings.add(ItemParser.itemToBase64(it));
            }
        }
        config.set("classes." + className + ".items", itemStrings.isEmpty() ? null : String.join(",", itemStrings));

        try {
            config.save(new java.io.File(plugin.getDataFolder(), "classes.yml"));
        } catch(java.io.IOException ignored) {}

        loadClasses();
    }
}