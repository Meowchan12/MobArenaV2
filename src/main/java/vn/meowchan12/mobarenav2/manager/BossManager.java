package vn.meowchan12.mobarenav2.manager;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.exceptions.InvalidConfigurationException;

import java.io.File;
import java.io.IOException;

public class BossManager {

    private final MobArenav2 plugin;

    private File bossesFile;
    private YamlConfiguration bossesConfig;

    private File skillsFile;
    private YamlConfiguration skillsConfig;

    public BossManager(MobArenav2 plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    private void loadConfigs() {
        // ... (Khối tạo file mặc định của bạn giữ nguyên, mình rút gọn để dễ nhìn) ...
        bossesFile = new File(plugin.getDataFolder(), "bosses.yml");
        if (!bossesFile.exists()) {
            try {
                bossesFile.createNewFile();
                // [Giữ nguyên code tạo dữ liệu mẫu của bạn ở đây]
            } catch (IOException e) {
                plugin.getLogger().severe("[MobArenav2] Could not create bosses.yml!");
            }
        }
        bossesConfig = YamlConfiguration.loadConfiguration(bossesFile);
        validateBossConfig(); // <-- CHẠY KIỂM TRA LỖI BOSS

        skillsFile = new File(plugin.getDataFolder(), "bossskill.yml");
        if (!skillsFile.exists()) {
            try {
                skillsFile.createNewFile();
                // [Giữ nguyên code tạo dữ liệu mẫu của bạn ở đây]
            } catch (IOException e) {
                plugin.getLogger().severe("[MobArenav2] Could not create bossskill.yml!");
            }
        }
        skillsConfig = YamlConfiguration.loadConfiguration(skillsFile);
        validateSkillConfig(); // <-- CHẠY KIỂM TRA LỖI SKILL
    }

    public void reloadConfigs() {
        if (!bossesFile.exists() || !skillsFile.exists()) {
            loadConfigs();
            return;
        }
        bossesConfig = YamlConfiguration.loadConfiguration(bossesFile);
        validateBossConfig();

        skillsConfig = YamlConfiguration.loadConfiguration(skillsFile);
        validateSkillConfig();

        plugin.getLogger().info("[MobArenav2] Boss and Skill configurations validated and reloaded.");
    }

    // --- [MỚI] BỘ LỌC LỖI CHO BOSSES.YML ---
    private void validateBossConfig() {
        for (String bossKey : new java.util.ArrayList<>(bossesConfig.getKeys(false))) {
            try {
                if (!bossesConfig.contains(bossKey + ".type")) {
                    throw new InvalidConfigurationException("Missing mandatory 'type' (Entity Type) for the boss.", "bosses.yml", bossKey);
                }

                String entityTypeStr = bossesConfig.getString(bossKey + ".type");
                // Normalize entity type: replace spaces with underscores, uppercase
                String normalizedType = entityTypeStr.replace(" ", "_").toUpperCase();
                try {
                    EntityType.valueOf(normalizedType);
                } catch (IllegalArgumentException e) {
                    throw new InvalidConfigurationException("Invalid EntityType '" + entityTypeStr + "'. This mob does not exist in Minecraft.", "bosses.yml", bossKey);
                }

                if (bossesConfig.contains(bossKey + ".HP") && !bossesConfig.isDouble(bossKey + ".HP") && !bossesConfig.isInt(bossKey + ".HP")) {
                    throw new InvalidConfigurationException("The 'HP' attribute must be a numeric value.", "bosses.yml", bossKey);
                }

            } catch (InvalidConfigurationException e) {
                plugin.getLogger().severe("========================================");
                plugin.getLogger().severe("[MobArenav2] ⛔ BOSS CONFIGURATION ERROR ⛔");
                plugin.getLogger().severe("[MobArenav2] File: " + e.getFileName() + " | Boss: " + e.getSection());
                plugin.getLogger().severe("[MobArenav2] Error: " + e.getMessage());
                plugin.getLogger().severe("[MobArenav2] Action: This boss will be kept but might not spawn correctly.");
                plugin.getLogger().severe("========================================");
                // [v5.5 FIX]: Do NOT remove boss from config. Keep it so MobFactory can
                // still attempt to spawn it. MobFactory has its own error handling.
            }
        }
    }

    // --- [MỚI] BỘ LỌC LỖI CHO BOSSSKILL.YML ---
    private void validateSkillConfig() {
        for (String skillKey : skillsConfig.getKeys(false)) {
            try {
                if (!skillsConfig.contains(skillKey + ".type")) {
                    throw new InvalidConfigurationException("Missing mandatory 'type' for the skill action.", "bossskill.yml", skillKey);
                }
            } catch (InvalidConfigurationException e) {
                plugin.getLogger().severe("========================================");
                plugin.getLogger().severe("[MobArenav2] ⛔ SKILL CONFIGURATION ERROR ⛔");
                plugin.getLogger().severe("[MobArenav2] File: " + e.getFileName() + " | Skill: " + e.getSection());
                plugin.getLogger().severe("[MobArenav2] Error: " + e.getMessage());
                plugin.getLogger().severe("========================================");
                // Xóa skill lỗi khỏi RAM
                skillsConfig.set(skillKey, null);
            }
        }
    }

    public void saveConfigs() {
        try {
            bossesConfig.save(bossesFile);
            skillsConfig.save(skillsFile);
        } catch (IOException e) {
            plugin.getLogger().severe("[MobArenav2] Could not save Boss configuration files!");
        }
    }

    public YamlConfiguration getBossesConfig() {
        return bossesConfig;
    }

    public YamlConfiguration getSkillsConfig() {
        return skillsConfig;
    }
}