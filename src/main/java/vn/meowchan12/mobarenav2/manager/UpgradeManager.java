package vn.meowchan12.mobarenav2.manager;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UpgradeManager {

    private final MobArenav2 plugin;
    private FileConfiguration config;

    private int triggerEvery = 5;
    private int defaultMaxSlots = 3;
    private final Map<String, Integer> classSlots = new HashMap<>();
    private final List<UpgradeData> availableUpgrades = new ArrayList<>();

    public UpgradeManager(MobArenav2 plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        File file = new File(plugin.getDataFolder(), "upgrades.yml");
        if (!file.exists()) {
            plugin.saveResource("upgrades.yml", false);
        }
        config = YamlConfiguration.loadConfiguration(file);

        availableUpgrades.clear();
        classSlots.clear();

        if (config.getBoolean("settings.enabled", true)) {
            triggerEvery = config.getInt("settings.trigger-every", config.getInt("settings.trigger-every-waves", 5));
            defaultMaxSlots = config.getInt("settings.default-max-slots", 3);

            ConfigurationSection classSec = config.getConfigurationSection("settings.class-slots");
            if (classSec != null) {
                for (String className : classSec.getKeys(false)) {
                    classSlots.put(className.toLowerCase(), classSec.getInt(className));
                }
            }

            ConfigurationSection upgradesSec = config.getConfigurationSection("upgrades");
            if (upgradesSec != null) {
                for (String key : upgradesSec.getKeys(false)) {
                    ConfigurationSection sec = upgradesSec.getConfigurationSection(key);
                    if (sec == null) continue;

                    UpgradeData data = new UpgradeData();
                    data.id = key;
                    data.displayName = sec.getString("display-name", "&a" + key).replace("&", "§");
                    data.material = Material.matchMaterial(sec.getString("material", "BOOK"));
                    if (data.material == null) data.material = Material.BOOK;

                    List<String> rawLore = sec.getStringList("lore");
                    data.lore = new ArrayList<>();
                    for (String l : rawLore) data.lore.add(l.replace("&", "§"));

                    data.type = sec.getString("type", "HEAL").toUpperCase();

                    data.maxLevel = sec.getInt("max-level", 1);
                    data.baseValue = sec.getDouble("base-value", sec.getDouble("value", 0.0));
                    data.increment = sec.getDouble("increment", 0.0);
                    data.eventsSection = sec.getConfigurationSection("events");

                    data.effectType = sec.getString("effect", "SPEED");
                    data.amplifier = sec.getInt("amplifier", 0);
                    data.duration = sec.getInt("duration", 200);
                    data.command = sec.getString("command", "");
                    data.weight = sec.getInt("weight", 10);
                    data.slot = sec.getInt("slot", -1);

                    availableUpgrades.add(data);
                }
            }
        } else {
            triggerEvery = -1;
        }
    }

    public int getTriggerEvery() {
        return triggerEvery;
    }

    /**
     * [v5.7 FEATURE]: SMART UPGRADE WAVE EVALUATOR
     * Checks if current wave triggers upgrade menu via INTERVAL, SPECIFIC_WAVES, or STAGE_BASED modes.
     */
    public boolean isUpgradeWave(vn.meowchan12.mobarenav2.arena.Arena arena, int wave) {
        if (wave <= 0) return false;

        // 1. Check Arena Config override (Independent for each arena!)
        FileConfiguration arenaCfg = (arena != null) ? arena.getConfig() : null;
        ConfigurationSection sec = null;
        if (arenaCfg != null && arenaCfg.contains("settings.upgrade-system")) {
            ConfigurationSection arenaSec = arenaCfg.getConfigurationSection("settings.upgrade-system");
            if (arenaSec != null && arenaSec.getBoolean("override-global", true)) {
                sec = arenaSec;
            }
        }

        // 2. Check settings.yml upgrade-system
        if (sec == null && plugin.getConfigManager() != null && plugin.getConfigManager().getSettings() != null) {
            sec = plugin.getConfigManager().getSettings().getConfigurationSection("upgrade-system");
        }

        // 3. Fallback to upgrades.yml settings
        if (sec == null && config != null) {
            sec = config.getConfigurationSection("settings");
        }

        if (sec == null) {
            return triggerEvery > 0 && wave % triggerEvery == 0;
        }

        if (!sec.getBoolean("enabled", true)) return false;

        String mode = sec.getString("mode", "INTERVAL").toUpperCase();

        switch (mode) {
            case "SPECIFIC_WAVES":
                List<Integer> specificWaves = sec.getIntegerList("specific-waves");
                return specificWaves.contains(wave);

            case "STAGE_BASED":
                ConfigurationSection stages = sec.getConfigurationSection("stage-triggers");
                if (stages != null) {
                    for (String key : stages.getKeys(false)) {
                        ConfigurationSection stage = stages.getConfigurationSection(key);
                        if (stage == null) continue;
                        int min = stage.getInt("min-wave", 1);
                        int max = stage.getInt("max-wave", 999);
                        int interval = stage.getInt("interval", 5);

                        if (wave >= min && wave <= max) {
                            return interval > 0 && (wave - min + 1) % interval == 0;
                        }
                    }
                }
                break;

            case "INTERVAL":
            default:
                int interval = sec.getInt("trigger-every-waves", sec.getInt("trigger-every", triggerEvery));
                return interval > 0 && wave % interval == 0;
        }

        return triggerEvery > 0 && wave % triggerEvery == 0;
    }

    public List<UpgradeData> getAvailableUpgrades() {
        return availableUpgrades;
    }

    public UpgradeData getUpgradeData(String id) {
        for (UpgradeData data : availableUpgrades) {
            if (data.id.equals(id)) return data;
        }
        return null;
    }

    // ==========================================
    // RPG SLOTS LOGIC
    // ==========================================
    public int getMaxSlots(Player player, String className) {
        return getMaxSlots(player, className, null);
    }

    public int getMaxSlots(Player player, String className, vn.meowchan12.mobarenav2.arena.Arena arena) {
        for (int i = 10; i >= 1; i--) {
            if (player.hasPermission("mobarenav2.upgradeslot." + i)) {
                return i;
            }
        }
        if (className != null) {
            String lowerClass = className.toLowerCase();

            // 1. Arena config override
            if (arena != null && arena.getConfig() != null) {
                int classLimit = arena.getConfig().getInt("settings.upgrade-system.class-slots." + className, -1);
                if (classLimit > 0) return classLimit;
            }

            // 2. settings.yml class-slots
            if (plugin.getConfigManager() != null && plugin.getConfigManager().getSettings() != null) {
                int classLimit = plugin.getConfigManager().getSettings().getInt("upgrade-system.class-slots." + className, -1);
                if (classLimit > 0) return classLimit;
            }

            // 3. upgrades.yml classSlots
            if (classSlots.containsKey(lowerClass)) {
                return classSlots.get(lowerClass);
            }
        }

        // 4. Default max slots from settings.yml or upgrades.yml
        if (plugin.getConfigManager() != null && plugin.getConfigManager().getSettings() != null) {
            int def = plugin.getConfigManager().getSettings().getInt("upgrade-system.default-max-slots", -1);
            if (def > 0) return def;
        }

        return defaultMaxSlots;
    }

    public int getUsedSlots(ArenaPlayerProfile profile) {
        int count = 0;
        for (UpgradeData data : availableUpgrades) {
            if (profile.getUpgradeLevel(data.id) > 0) {
                count++;
            }
        }
        return count;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    // --- HÀM MỚI LƯU CẤU HÌNH CLASS SLOT TỪ GUI ---
    public void saveClassSlotLimit(String className, int slots) {
        if (config == null) return;
        config.set("settings.class-slots." + className, slots);

        try {
            config.save(new File(plugin.getDataFolder(), "upgrades.yml"));
        } catch (Exception ignored) {}

        classSlots.put(className.toLowerCase(), slots);
    }

    public static class UpgradeData {
        public String id;
        public String displayName;
        public Material material;
        public List<String> lore;
        public String type;

        public int maxLevel;
        public double baseValue;
        public double increment;
        public ConfigurationSection eventsSection;

        public String effectType;
        public int amplifier;
        public int duration;
        public String command;
        public int weight;

        public int slot;
    }
}