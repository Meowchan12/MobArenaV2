package vn.meowchan12.mobarenav2.manager;

import com.google.common.base.Charsets;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class ConfigManager {

    private final MobArenav2 plugin;

    private File arenasFolder;
    private FileConfiguration settingsConfig;
    private FileConfiguration classesConfig;
    private FileConfiguration upgradesConfig;

    private FileConfiguration bossesConfig;
    private FileConfiguration bossSkillConfig;
    private FileConfiguration supplyConfig;

    public ConfigManager(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    public void loadAllConfigs() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }

        arenasFolder = new File(plugin.getDataFolder(), "arenas");
        if (!arenasFolder.exists()) {
            arenasFolder.mkdirs();
            try {
                plugin.saveResource("arenas/default.yml", false);
                plugin.getLogger().info("[MobArenav2] Successfully extracted default.yml template!");
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[MobArenav2] Could not find arenas/default.yml in the jar resources!");
            }
        }

        settingsConfig = loadAndUpdateConfig("settings.yml");
        classesConfig = loadAndUpdateConfig("classes.yml");
        upgradesConfig = loadAndUpdateConfig("upgrades.yml");

        bossesConfig = loadAndUpdateConfig("bosses.yml");
        bossSkillConfig = loadAndUpdateConfig("bossskill.yml");
        supplyConfig = loadAndUpdateConfig("supply.yml");

        plugin.getLogger().info("[MobArenav2] All configuration files loaded and auto-updated safely!");
    }

    private FileConfiguration loadAndUpdateConfig(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);

        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }

        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);

        // [v8.4.0 MIGRATION]: Migrate legacy flat 'upgrade-system.*' keys into 'upgrade-system.default.*'
        if (fileName.equals("settings.yml")) {
            migrateLegacyUpgradeSystemSection(config);
        }

        InputStream defConfigStream = plugin.getResource(fileName);

        if (defConfigStream != null) {
            YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defConfigStream, Charsets.UTF_8));
            config.setDefaults(defConfig);
            config.options().copyDefaults(true);

            try {
                config.save(file);
            } catch (IOException e) {
                plugin.getLogger().severe("[MobArenav2] Could not save auto-updated config to " + file.getName());
            }
        }

        return config;
    }

    /**
     * [v8.4.0]: Migrates legacy v8.3.x flat upgrade-system settings directly into upgrade-system.default
     * so existing server owners preserve their custom trigger/slot rules cleanly.
     */
    private void migrateLegacyUpgradeSystemSection(YamlConfiguration config) {
        ConfigurationSection upgSec = config.getConfigurationSection("upgrade-system");
        if (upgSec == null) return;

        String[] legacyKeys = {
                "enabled", "mode", "trigger-every-waves", "specific-waves",
                "stage-triggers", "default-max-slots", "class-slots", "stack-with-interval"
        };

        boolean hasLegacyKeys = false;
        for (String key : legacyKeys) {
            if (upgSec.contains(key)) {
                hasLegacyKeys = true;
                break;
            }
        }

        if (!hasLegacyKeys) return;

        ConfigurationSection defaultSec = upgSec.getConfigurationSection("default");
        if (defaultSec == null) {
            defaultSec = upgSec.createSection("default");
            for (String key : legacyKeys) {
                if (!upgSec.contains(key)) continue;
                Object val = upgSec.get(key);
                if (val instanceof ConfigurationSection nested) {
                    ConfigurationSection targetNested = defaultSec.createSection(key);
                    copySection(nested, targetNested);
                } else if (val instanceof List<?> list) {
                    defaultSec.set(key, new ArrayList<>(list));
                } else {
                    defaultSec.set(key, val);
                }
            }
            plugin.getLogger().info("[MobArenav2] Migrated legacy upgrade-system settings into 'upgrade-system.default' in settings.yml.");
        }

        // Remove obsolete flat keys to keep settings.yml clean
        for (String key : legacyKeys) {
            upgSec.set(key, null);
        }
    }

    /**
     * [v8.4.0 CENTRALIZED]: Ensures every non-default arena has its own config block under
     * settings.yml -> 'upgrade-system.arenas.<arena_name>' cloned from 'upgrade-system.default'.
     * Saves settings.yml to disk ONLY when at least one new arena block is created.
     */
    public void syncArenaUpgradeConfigs(Collection<Arena> arenas) {
        if (settingsConfig == null || arenas == null || arenas.isEmpty()) return;

        ConfigurationSection defaultSec = settingsConfig.getConfigurationSection("upgrade-system.default");
        if (defaultSec == null) return;

        ConfigurationSection arenasSec = settingsConfig.getConfigurationSection("upgrade-system.arenas");
        Set<String> existingLower = new HashSet<>();
        if (arenasSec != null) {
            for (String key : arenasSec.getKeys(false)) {
                existingLower.add(key.toLowerCase());
            }
        }

        List<String> newlyProvisioned = new ArrayList<>();
        for (Arena arena : arenas) {
            if (arena == null || arena.getName() == null) continue;
            String arenaName = arena.getName().trim();
            if (arenaName.isEmpty() || arenaName.equalsIgnoreCase("default")) continue;

            if (!existingLower.contains(arenaName.toLowerCase())) {
                if (arenasSec == null) {
                    arenasSec = settingsConfig.createSection("upgrade-system.arenas");
                }
                ConfigurationSection targetSec = arenasSec.createSection(arenaName);
                copySection(defaultSec, targetSec);
                existingLower.add(arenaName.toLowerCase());
                newlyProvisioned.add(arenaName);
            }
        }

        if (!newlyProvisioned.isEmpty()) {
            saveSettings();
            plugin.getLogger().info("[MobArenav2] Auto-generated upgrade-system config from 'default' in settings.yml for arena(s): " + String.join(", ", newlyProvisioned));
        }
    }

    /**
     * [v8.4.0 CENTRALIZED]: Ensures a single arena has an entry under settings.yml -> 'upgrade-system.arenas.<arenaName>'.
     */
    public void ensureArenaUpgradeConfig(String arenaName) {
        if (settingsConfig == null || arenaName == null) return;
        String cleanName = arenaName.trim();
        if (cleanName.isEmpty() || cleanName.equalsIgnoreCase("default")) return;

        ConfigurationSection defaultSec = settingsConfig.getConfigurationSection("upgrade-system.default");
        if (defaultSec == null) return;

        ConfigurationSection arenasSec = settingsConfig.getConfigurationSection("upgrade-system.arenas");
        if (arenasSec != null) {
            for (String existingKey : arenasSec.getKeys(false)) {
                if (existingKey.equalsIgnoreCase(cleanName)) {
                    return; // Already provisioned
                }
            }
        } else {
            arenasSec = settingsConfig.createSection("upgrade-system.arenas");
        }

        ConfigurationSection targetSec = arenasSec.createSection(cleanName);
        copySection(defaultSec, targetSec);
        saveSettings();
        plugin.getLogger().info("[MobArenav2] Auto-generated upgrade-system config from 'default' in settings.yml for new arena: " + cleanName);
    }

    private void copySection(ConfigurationSection source, ConfigurationSection target) {
        if (source == null || target == null) return;
        for (String key : source.getKeys(false)) {
            Object val = source.get(key);
            if (val instanceof ConfigurationSection nestedSource) {
                ConfigurationSection nestedTarget = target.createSection(key);
                copySection(nestedSource, nestedTarget);
            } else if (val instanceof List<?> list) {
                target.set(key, new ArrayList<>(list));
            } else {
                target.set(key, val);
            }
        }
    }

    public void saveSettings() {
        if (settingsConfig == null) return;
        File file = new File(plugin.getDataFolder(), "settings.yml");
        try {
            settingsConfig.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("[MobArenav2] Could not save settings.yml: " + e.getMessage());
        }
    }

    // ========================================================
    // SAFE SAVE MECHANISM (FILE PROTECTION FIREWALL)
    // [v8.2.0 ASYNC OPTIMIZATION]: Asynchronous disk flush
    // ========================================================
    public void saveArenaCoords(Arena arena) {
        File file = new File(arenasFolder, arena.getName() + ".yml");

        FileConfiguration config = arena.getConfig();
        if (config == null) {
            config = new YamlConfiguration();
        }

        if (file.exists()) {
            try {
                YamlConfiguration testSyntax = new YamlConfiguration();
                testSyntax.load(file);
            } catch (Exception e) {
                plugin.getLogger().severe("[MobArenav2] ⛔ SAVE OPERATION REJECTED ⛔");
                plugin.getLogger().severe("[MobArenav2] YAML syntax error detected in file: " + file.getName());
                plugin.getLogger().severe("[MobArenav2] Save action blocked to prevent the file from being wiped!");
                return;
            }
        }

        if (arena.getP1() != null) config.set("coords.p1", serializeLocation(arena.getP1()));
        if (arena.getP2() != null) config.set("coords.p2", serializeLocation(arena.getP2()));
        if (arena.getLobby() != null) config.set("coords.lobby", serializeLocation(arena.getLobby()));
        if (arena.getArenaSpawn() != null) config.set("coords.arena", serializeLocation(arena.getArenaSpawn()));
        if (arena.getSpectatorSpawn() != null) config.set("coords.spectator", serializeLocation(arena.getSpectatorSpawn()));

        config.set("coords.spawnpoints", null);
        int i = 1;
        for (Location loc : arena.getSpawnpoints()) {
            config.set("coords.spawnpoints." + i, serializeLocation(loc));
            i++;
        }

        config.set("coords.supplypoints", null);
        int j = 1;
        for (Location loc : arena.getSupplypoints()) {
            config.set("coords.supplypoints." + j, serializeLocation(loc));
            j++;
        }

        // [v8.2.0]: Keep memory instance updated on RAM, write to disk asynchronously off the main thread
        arena.setConfig(config);
        final FileConfiguration finalConfig = config;
        CompletableFuture.runAsync(() -> {
            try {
                finalConfig.save(file);
                plugin.getLogger().info("[MobArenav2] Successfully saved FULL config & data asynchronously for arena: " + arena.getName());
            } catch (IOException e) {
                plugin.getLogger().severe("[MobArenav2] Could not save arena data for " + arena.getName());
                e.printStackTrace();
            }
        });
    }

    public void createArenaTemplate(String arenaName) {
        File newFile = new File(arenasFolder, arenaName + ".yml");
        if (!newFile.exists()) {
            try {
                File defaultFile = new File(arenasFolder, "default.yml");
                if (defaultFile.exists()) {
                    com.google.common.io.Files.copy(defaultFile, newFile);
                } else {
                    plugin.saveResource("arenas/default.yml", true);
                    com.google.common.io.Files.copy(new File(arenasFolder, "default.yml"), newFile);
                }
            } catch (IOException e) {
                plugin.getLogger().severe("[MobArenav2] Could not create template for arena: " + arenaName);
            }
        }
    }

    private String serializeLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getX() + "," + loc.getY() + "," + loc.getZ() + "," + loc.getYaw() + "," + loc.getPitch() + "," + loc.getWorld().getName();
    }

    public File getArenasFolder() { return arenasFolder; }
    public FileConfiguration getSettings() { return settingsConfig; }
    public FileConfiguration getClasses() { return classesConfig; }
    public FileConfiguration getUpgrades() { return upgradesConfig; }
    public FileConfiguration getBosses() { return bossesConfig; }
    public FileConfiguration getBossSkill() { return bossSkillConfig; }
    public FileConfiguration getSupply() { return supplyConfig; }
}
