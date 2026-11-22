package vn.meowchan12.mobarenav2.manager;

import org.bukkit.Material;

import org.bukkit.configuration.ConfigurationSection;

import org.bukkit.configuration.file.FileConfiguration;

import org.bukkit.configuration.file.YamlConfiguration;

import org.bukkit.entity.Player;

import vn.meowchan12.mobarenav2.MobArenav2;

import vn.meowchan12.mobarenav2.arena.Arena;

import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;

import vn.meowchan12.mobarenav2.upgrade.UpgradeDraftType;

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

        ConfigurationSection defSec = getDefaultUpgradeSection();

        if (defSec != null) {

            triggerEvery = defSec.getInt("trigger-every-waves", defSec.getInt("trigger-every", 5));

            defaultMaxSlots = defSec.getInt("default-max-slots", 3);

            ConfigurationSection classSec = defSec.getConfigurationSection("class-slots");

            if (classSec != null) {

                for (String className : classSec.getKeys(false)) {

                    classSlots.put(className.toLowerCase(), classSec.getInt(className));

                }

            }

        } else if (config.contains("settings")) {

            triggerEvery = config.getInt("settings.trigger-every", config.getInt("settings.trigger-every-waves", 5));

            defaultMaxSlots = config.getInt("settings.default-max-slots", 3);

            ConfigurationSection classSec = config.getConfigurationSection("settings.class-slots");

            if (classSec != null) {

                for (String className : classSec.getKeys(false)) {

                    classSlots.put(className.toLowerCase(), classSec.getInt(className));

                }

            }

        }

        ConfigurationSection upgradesSec = config.getConfigurationSection("upgrades");

        if (upgradesSec != null) {

            for (String key : upgradesSec.getKeys(false)) {

                ConfigurationSection sec = upgradesSec.getConfigurationSection(key);

                if (sec == null) continue;

                UpgradeData data = new UpgradeData();

                data.id = key;

                data.displayName = sec.getString("display-name", "&a" + key);

                data.material = Material.matchMaterial(sec.getString("material", "BOOK"));

                if (data.material == null) data.material = Material.BOOK;

                List<String> rawLore = sec.getStringList("lore");

                data.lore = new ArrayList<>(rawLore);

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

                data.section = sec;

                availableUpgrades.add(data);

            }

        }

    }

    public int getTriggerEvery() {

        return triggerEvery;

    }

    // =========================================================================

    // [v8.4.0 CENTRALIZED]: SINGLE SOURCE OF TRUTH IN settings.yml

    // Reads upgrade-system.arenas.<arenaName> with automatic fallback to upgrade-system.default

    // =========================================================================

    private ConfigurationSection getDefaultUpgradeSection() {

        if (plugin.getConfigManager() == null || plugin.getConfigManager().getSettings() == null) return null;

        FileConfiguration settings = plugin.getConfigManager().getSettings();

        ConfigurationSection defSec = settings.getConfigurationSection("upgrade-system.default");

        if (defSec != null) return defSec;

        return settings.getConfigurationSection("upgrade-system");

    }

    private ConfigurationSection getArenaUpgradeSection(Arena arena) {

        if (plugin.getConfigManager() == null || plugin.getConfigManager().getSettings() == null) return null;

        FileConfiguration settings = plugin.getConfigManager().getSettings();

        if (arena != null && arena.getName() != null) {

            ConfigurationSection arenasSec = settings.getConfigurationSection("upgrade-system.arenas");

            if (arenasSec != null) {

                for (String key : arenasSec.getKeys(false)) {

                    if (key.equalsIgnoreCase(arena.getName())) {

                        ConfigurationSection matched = arenasSec.getConfigurationSection(key);

                        if (matched != null) return matched;

                    }

                }

            }

        }

        return getDefaultUpgradeSection();

    }

    public boolean isUpgradeSystemEnabled(Arena arena) {

        ConfigurationSection arenaSec = getArenaUpgradeSection(arena);

        ConfigurationSection defSec = getDefaultUpgradeSection();

        if (arenaSec != null && arenaSec.contains("enabled")) {

            return arenaSec.getBoolean("enabled", true);

        }

        if (defSec != null && defSec.contains("enabled")) {

            return defSec.getBoolean("enabled", true);

        }

        return true;

    }

    public boolean isStarterUpgradeEnabled(Arena arena) {

        if (!isUpgradeSystemEnabled(arena)) return false;

        ConfigurationSection arenaSec = getArenaUpgradeSection(arena);

        ConfigurationSection defSec = getDefaultUpgradeSection();

        ConfigurationSection arenaStarter = (arenaSec != null) ? arenaSec.getConfigurationSection("starter-upgrade") : null;

        ConfigurationSection defStarter = (defSec != null) ? defSec.getConfigurationSection("starter-upgrade") : null;

        if (arenaStarter != null && arenaStarter.contains("enabled")) {

            return arenaStarter.getBoolean("enabled", true);

        }

        if (defStarter != null && defStarter.contains("enabled")) {

            return defStarter.getBoolean("enabled", true);

        }

        return false;

    }

    public int getStarterUpgradeCount(Arena arena) {

        ConfigurationSection arenaSec = getArenaUpgradeSection(arena);

        ConfigurationSection defSec = getDefaultUpgradeSection();

        ConfigurationSection arenaStarter = (arenaSec != null) ? arenaSec.getConfigurationSection("starter-upgrade") : null;

        ConfigurationSection defStarter = (defSec != null) ? defSec.getConfigurationSection("starter-upgrade") : null;

        return getIntCascade(arenaStarter, defStarter, "upgrade-count", 1);

    }

    public boolean isStackWithInterval(Arena arena) {

        ConfigurationSection arenaSec = getArenaUpgradeSection(arena);

        ConfigurationSection defSec = getDefaultUpgradeSection();

        if (arenaSec != null && arenaSec.contains("stack-with-interval")) {

            return arenaSec.getBoolean("stack-with-interval", false);

        }

        if (defSec != null && defSec.contains("stack-with-interval")) {

            return defSec.getBoolean("stack-with-interval", false);

        }

        return false;

    }

    /**

     * [v8.4.0]: Resolves the configurable WaveDraftRule (grant count, Normal/Super roll % rates,

     * guaranteed/max super slots, and pity threshold) for a given arena and wave reward type.

     */

    public WaveDraftRule getWaveDraftRule(Arena arena, UpgradeDraftType draftType) {

        UpgradeDraftType type = (draftType != null) ? draftType : UpgradeDraftType.NORMAL;

        String key = type.getConfigKey();

        // Sensible built-in defaults per wave type

        int defGrant = switch (type) {

            case BOSS -> 3;

            case SWARM -> 2;

            case STARTER -> 1;

            case NORMAL -> 1;

        };

        double defNormal = switch (type) {

            case BOSS -> 35.0;

            case SWARM -> 75.0;

            case STARTER -> 90.0;

            case NORMAL -> 90.0;

        };

        double defSuper = switch (type) {

            case BOSS -> 65.0;

            case SWARM -> 25.0;

            case STARTER -> 10.0;

            case NORMAL -> 10.0;

        };

        int defGuaranteed = (type == UpgradeDraftType.BOSS) ? 1 : 0;

        int defMaxSuper = switch (type) {

            case BOSS -> 3;

            case SWARM -> 2;

            case STARTER -> 1;

            case NORMAL -> 1;

        };

        int defPity = (type == UpgradeDraftType.NORMAL) ? 4 : 0;

        ConfigurationSection arenaSec = getArenaUpgradeSection(arena);

        ConfigurationSection defSec = getDefaultUpgradeSection();

        ConfigurationSection arenaStarter = (arenaSec != null) ? arenaSec.getConfigurationSection("starter-upgrade") : null;

        ConfigurationSection defStarter = (defSec != null) ? defSec.getConfigurationSection("starter-upgrade") : null;

        if (type == UpgradeDraftType.STARTER && (arenaStarter != null || defStarter != null)) {

            int grantCount = getIntCascade(arenaStarter, defStarter, "upgrade-count", defGrant);

            double normalChance = getDoubleCascade(arenaStarter, defStarter, "normal-chance", defNormal);

            double superChance = getDoubleCascade(arenaStarter, defStarter, "super-chance", defSuper);

            int guaranteedSuper = getIntCascade(arenaStarter, defStarter, "guaranteed-super-slots", defGuaranteed);

            int maxSuper = getIntCascade(arenaStarter, defStarter, "max-super-slots", defMaxSuper);

            int pityThreshold = getIntCascade(arenaStarter, defStarter, "pity-threshold", defPity);

            grantCount = Math.max(0, grantCount);

            normalChance = Math.max(0.0, normalChance);

            superChance = Math.max(0.0, superChance);

            maxSuper = Math.max(0, Math.min(3, maxSuper));

            guaranteedSuper = Math.max(0, Math.min(maxSuper, guaranteedSuper));

            pityThreshold = Math.max(0, pityThreshold);

            return new WaveDraftRule(grantCount, normalChance, superChance, guaranteedSuper, maxSuper, pityThreshold);

        }

        ConfigurationSection arenaRuleSec = (arenaSec != null) ? arenaSec.getConfigurationSection("draft-rates." + key) : null;

        ConfigurationSection defRuleSec = (defSec != null) ? defSec.getConfigurationSection("draft-rates." + key) : null;

        int grantCount = getIntCascade(arenaRuleSec, defRuleSec, "grant-count", defGrant);

        double normalChance = getDoubleCascade(arenaRuleSec, defRuleSec, "normal-chance", defNormal);

        double superChance = getDoubleCascade(arenaRuleSec, defRuleSec, "super-chance", defSuper);

        int guaranteedSuper = getIntCascade(arenaRuleSec, defRuleSec, "guaranteed-super-slots", defGuaranteed);

        int maxSuper = getIntCascade(arenaRuleSec, defRuleSec, "max-super-slots", defMaxSuper);

        int pityThreshold = getIntCascade(arenaRuleSec, defRuleSec, "pity-threshold", defPity);

        // Sanitize bounds safely

        grantCount = Math.max(0, grantCount);

        normalChance = Math.max(0.0, normalChance);

        superChance = Math.max(0.0, superChance);

        maxSuper = Math.max(0, Math.min(3, maxSuper));

        guaranteedSuper = Math.max(0, Math.min(maxSuper, guaranteedSuper));

        pityThreshold = Math.max(0, pityThreshold);

        return new WaveDraftRule(grantCount, normalChance, superChance, guaranteedSuper, maxSuper, pityThreshold);

    }

    private int getIntCascade(ConfigurationSection primary, ConfigurationSection fallback, String key, int defVal) {

        if (primary != null && primary.contains(key)) return primary.getInt(key, defVal);

        if (fallback != null && fallback.contains(key)) return fallback.getInt(key, defVal);

        return defVal;

    }

    private double getDoubleCascade(ConfigurationSection primary, ConfigurationSection fallback, String key, double defVal) {

        if (primary != null && primary.contains(key)) return primary.getDouble(key, defVal);

        if (fallback != null && fallback.contains(key)) return fallback.getDouble(key, defVal);

        return defVal;

    }

    /**

     * [v8.4.0 CENTRALIZED]: SMART UPGRADE WAVE EVALUATOR

     * Checks if current wave triggers normal upgrade menu via INTERVAL, SPECIFIC_WAVES, or STAGE_BASED modes.

     * Centrally configured in settings.yml -> upgrade-system.arenas.<arenaName> (fallback: upgrade-system.default).

     */

    public boolean isUpgradeWave(Arena arena, int wave) {

        if (wave <= 0) return false;

        if (!isUpgradeSystemEnabled(arena)) return false;

        ConfigurationSection arenaSec = getArenaUpgradeSection(arena);

        ConfigurationSection defSec = getDefaultUpgradeSection();

        if (arenaSec == null && defSec == null) {

            return triggerEvery > 0 && wave % triggerEvery == 0;

        }

        String mode = "INTERVAL";

        if (arenaSec != null && arenaSec.contains("mode")) {

            mode = arenaSec.getString("mode", "INTERVAL").toUpperCase();

        } else if (defSec != null && defSec.contains("mode")) {

            mode = defSec.getString("mode", "INTERVAL").toUpperCase();

        }

        switch (mode) {

            case "SPECIFIC_WAVES":

                List<Integer> specificWaves = (arenaSec != null && arenaSec.contains("specific-waves"))

                        ? arenaSec.getIntegerList("specific-waves")

                        : (defSec != null ? defSec.getIntegerList("specific-waves") : List.of());

                return specificWaves.contains(wave);

            case "STAGE_BASED":

                ConfigurationSection stages = (arenaSec != null && arenaSec.contains("stage-triggers"))

                        ? arenaSec.getConfigurationSection("stage-triggers")

                        : (defSec != null ? defSec.getConfigurationSection("stage-triggers") : null);

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

                return false;

            case "INTERVAL":

            default:

                int interval = getIntCascade(arenaSec, defSec, "trigger-every-waves", triggerEvery);

                return interval > 0 && wave % interval == 0;

        }

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

    // RPG SLOTS LOGIC (CENTRALIZED IN settings.yml)

    // ==========================================

    public int getMaxSlots(Player player, String className) {

        return getMaxSlots(player, className, null);

    }

    public int getMaxSlots(Player player, String className, Arena arena) {

        for (int i = 10; i >= 1; i--) {

            if (player.hasPermission("mobarenav2.upgradeslot." + i)) {

                return i;

            }

        }

        ConfigurationSection arenaSec = getArenaUpgradeSection(arena);

        ConfigurationSection defSec = getDefaultUpgradeSection();

        if (className != null) {

            String lowerClass = className.toLowerCase();

            // 1. Arena-specific class-slots in settings.yml -> upgrade-system.arenas.<arenaName>.class-slots

            if (arenaSec != null && arenaSec.isConfigurationSection("class-slots")) {

                ConfigurationSection cs = arenaSec.getConfigurationSection("class-slots");

                if (cs != null) {

                    for (String key : cs.getKeys(false)) {

                        if (key.equalsIgnoreCase(className)) {

                            int limit = cs.getInt(key, -1);

                            if (limit > 0) return limit;

                        }

                    }

                }

            }

            // 2. Default class-slots in settings.yml -> upgrade-system.default.class-slots

            if (defSec != null && defSec != arenaSec && defSec.isConfigurationSection("class-slots")) {

                ConfigurationSection cs = defSec.getConfigurationSection("class-slots");

                if (cs != null) {

                    for (String key : cs.getKeys(false)) {

                        if (key.equalsIgnoreCase(className)) {

                            int limit = cs.getInt(key, -1);

                            if (limit > 0) return limit;

                        }

                    }

                }

            }

            // 3. Legacy fallback from upgrades.yml

            if (classSlots.containsKey(lowerClass)) {

                return classSlots.get(lowerClass);

            }

        }

        // 4. Arena default-max-slots -> Global default-max-slots -> Legacy fallback

        int resolvedDefault = getIntCascade(arenaSec, defSec, "default-max-slots", -1);

        if (resolvedDefault > 0) return resolvedDefault;

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

    public void saveClassSlotLimit(String className, int slots) {

        if (plugin.getConfigManager() != null && plugin.getConfigManager().getSettings() != null) {

            plugin.getConfigManager().getSettings().set("upgrade-system.default.class-slots." + className, slots);

            plugin.getConfigManager().saveSettings();

        }

        if (config != null) {

            config.set("settings.class-slots." + className, slots);

            try {

                config.save(new File(plugin.getDataFolder(), "upgrades.yml"));

            } catch (Exception ignored) {}

        }

        classSlots.put(className.toLowerCase(), slots);

    }

    /**

     * [v8.4.0]: Immutable value object describing upgrade draft rules for a specific wave type.

     */

    public static class WaveDraftRule {

        public final int grantCount;

        public final double normalChance;

        public final double superChance;

        public final int guaranteedSuperSlots;

        public final int maxSuperSlots;

        public final int pityThreshold;

        public WaveDraftRule(int grantCount, double normalChance, double superChance,

                             int guaranteedSuperSlots, int maxSuperSlots, int pityThreshold) {

            this.grantCount = grantCount;

            this.normalChance = normalChance;

            this.superChance = superChance;

            this.guaranteedSuperSlots = guaranteedSuperSlots;

            this.maxSuperSlots = maxSuperSlots;

            this.pityThreshold = pityThreshold;

        }

        public double getNormalizedSuperProbability() {

            double total = normalChance + superChance;

            if (total <= 0.0) return 0.0;

            return Math.max(0.0, Math.min(1.0, superChance / total));

        }

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

        public ConfigurationSection section;

        public double getValueAtLevel(int currentLevel) {
            return getCurrentValue(currentLevel);
        }

        public double getDouble(String key, double def) {
            if (section != null && section.contains(key)) {
                return section.getDouble(key);
            }
            return def;
        }

        public int getInt(String key, int def) {
            if (section != null && section.contains(key)) {
                return section.getInt(key);
            }
            return def;
        }

        public boolean getBoolean(String key, boolean def) {
            if (section != null && section.contains(key)) {
                return section.getBoolean(key);
            }
            return def;
        }

        public String getString(String key, String def) {
            if (section != null && section.contains(key)) {
                return section.getString(key);
            }
            return def;
        }

        public String formatVal(double val) {
            if (val == (long) val) {
                return String.valueOf((long) val);
            }
            return String.format("%.1f", val);
        }

        public double getCurrentValue(int currentLevel) {
            if (currentLevel <= 0) return baseValue;
            return baseValue + (currentLevel - 1) * increment;
        }

        public double getNextValue(int currentLevel) {
            if (currentLevel >= maxLevel) return getCurrentValue(currentLevel);
            return baseValue + currentLevel * increment;
        }

        public java.util.List<String> getFormattedLore(int currentLevel) {
            if (lore == null || lore.isEmpty()) return java.util.Collections.emptyList();
            int displayLvl = Math.max(1, currentLevel);
            double curVal = getCurrentValue(currentLevel);
            double nextVal = getNextValue(currentLevel);

            java.util.List<String> formatted = new java.util.ArrayList<>();
            for (String line : lore) {
                formatted.add(vn.meowchan12.mobarenav2.utils.ColorUtils.colorize(line
                        .replace("%current_level%", String.valueOf(displayLvl))
                        .replace("%max_level%", String.valueOf(maxLevel))
                        .replace("%current_value%", formatVal(curVal))
                        .replace("%next_value%", formatVal(nextVal))));
            }
            return formatted;
        }
    }
}
