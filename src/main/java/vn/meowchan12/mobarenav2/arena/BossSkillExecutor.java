package vn.meowchan12.mobarenav2.arena;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import vn.meowchan12.mobarenav2.MobArenav2;

/**
 * [v7.3 DECOUPLED]: BossSkillExecutor
 * Lớp điều phối trung gian được refactor hoàn toàn sang Delegation Pattern.
 * Toàn bộ 27 kỹ năng Boss đã được tách độc lập thành các BossSkill Handler trong vn.meowchan12.mobarenav2.boss.skill.*
 * và được quản lý tập trung bởi BossSkillRegistry.
 */
public class BossSkillExecutor {

    public static void castSkill(MobArenav2 plugin, Arena arena, LivingEntity boss, String skillId) {
        if (boss == null || boss.isDead() || (arena != null && !arena.isRunning())) return;
        if (boss.hasMetadata(vn.meowchan12.mobarenav2.boss.CrushingBlowMechanic.METADATA_KEY)) return;

        ConfigurationSection skillConfig = plugin.getBossManager().getSkillsConfig().getConfigurationSection(skillId);
        if (skillConfig == null) {
            plugin.getLogger().warning("[MobArenav2] Boss tried to use unknown skill: " + skillId);
            return;
        }

        plugin.getBossSkillRegistry().executeSkill(arena, boss, skillId, skillConfig);
    }
}
