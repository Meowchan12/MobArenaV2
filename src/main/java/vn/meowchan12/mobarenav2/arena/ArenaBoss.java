package vn.meowchan12.mobarenav2.arena;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import vn.meowchan12.mobarenav2.MobArenav2;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ArenaBoss {

    private final MobArenav2 plugin;
    private final Arena arena;
    private LivingEntity entity; // Cập nhật có thể chỉnh sửa
    private final UUID bossUUID; // [FIX BUG #2] Sử dụng UUID làm gốc
    private final String displayName;

    private BossBar bossBar;

    private final Map<String, Integer> maxCooldowns = new HashMap<>();
    private final Map<String, Integer> currentCooldowns = new HashMap<>();

    private final List<String> enrageSkills = new ArrayList<>();
    private boolean hasEnraged = false;

    public ArenaBoss(MobArenav2 plugin, Arena arena, LivingEntity entity, String displayName, boolean showBossBar, boolean showNameTag, List<String> skills, List<String> enrageSkillsData) {
        this.plugin = plugin;
        this.arena = arena;
        this.entity = entity;
        this.bossUUID = entity.getUniqueId(); // Lưu lại UUID gốc
        this.displayName = displayName != null ? displayName.replace("&", "§") : "Boss";

        if (showNameTag) {
            this.entity.setCustomName(this.displayName);
            this.entity.setCustomNameVisible(true);
        }

        if (showBossBar) {
            this.bossBar = Bukkit.createBossBar(this.displayName, BarColor.RED, BarStyle.SOLID);
            for (Player p : arena.getPlayersAsObjects()) {
                this.bossBar.addPlayer(p);
            }
        }

        if (skills != null) {
            for (String skillData : skills) {
                String[] parts = skillData.split(":");
                String skillId = parts[0].trim();
                int cooldownTicks = 20 * 20;

                if (parts.length == 2) {
                    try {
                        cooldownTicks = (int) (Double.parseDouble(parts[1].trim()) * 20);
                    } catch (NumberFormatException ignored) {}
                } else {
                    ConfigurationSection skillCfg = plugin.getBossManager().getSkillsConfig().getConfigurationSection(skillId);
                    if (skillCfg != null && skillCfg.contains("cooldown")) {
                        cooldownTicks = skillCfg.getInt("cooldown") * 20;
                    }
                }

                maxCooldowns.put(skillId, cooldownTicks);
                currentCooldowns.put(skillId, ThreadLocalRandom.current().nextInt(60, 160));
            }
        }

        if (enrageSkillsData != null) {
            this.enrageSkills.addAll(enrageSkillsData);
        }

        startTask();
    }

    private void startTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                // [FIX BUG #2] Tìm kiếm lại Entity dựa trên UUID nếu bị mất do Chunk
                Entity currentEntity = Bukkit.getEntity(bossUUID);
                if (currentEntity instanceof LivingEntity) {
                    entity = (LivingEntity) currentEntity;
                }

                if (entity == null || entity.isDead() || !arena.isRunning()) {
                    cleanup();
                    this.cancel();
                    return;
                }

                if (!entity.isValid()) {
                    return; // Chờ Chunk Load xong, không được HỦY vòng lặp.
                }

                double maxHealth = 100.0;
                if (entity.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH) != null) {
                    maxHealth = entity.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH).getValue();
                }
                double health = Math.max(0.0, entity.getHealth());
                double progress = health / maxHealth;

                if (bossBar != null) {
                    bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));
                    String hpText = String.format("%.1f", health);
                    String maxHpText = String.format("%.1f", maxHealth);
                    bossBar.setTitle(displayName + " §7- §c" + hpText + " §7/ §c" + maxHpText);
                }

                if (!hasEnraged && progress <= 0.3) {
                    hasEnraged = true;
                    for (String eSkill : enrageSkills) {
                        BossSkillExecutor.castSkill(plugin, arena, entity, eSkill);
                    }
                }

                for (String skillId : currentCooldowns.keySet()) {
                    int left = currentCooldowns.get(skillId);
                    if (left > 0) {
                        currentCooldowns.put(skillId, left - 1);
                    } else {
                        BossSkillExecutor.castSkill(plugin, arena, entity, skillId);
                        currentCooldowns.put(skillId, maxCooldowns.get(skillId));
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void cleanup() {
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }
}