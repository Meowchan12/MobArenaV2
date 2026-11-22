package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

/**
 * Cú đấm Saitama: Đòn đánh chí tử 90% xuyên giáp (True Damage).
 */
public class SaitamaPunchSkill implements BossSkill {

    @Override
    public String getType() {
        return "SAITAMA_PUNCH";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        long duration = config.getInt("duration", 5) * 1000L;
        double damage = config.getDouble("damage", 25.0);
        double pen = config.getDouble("armor-penetration", 0.90);

        NamespacedKey sKey = new NamespacedKey(plugin, "ma_saitama_punch");
        boss.getPersistentDataContainer().set(sKey, PersistentDataType.STRING, damage + ";" + pen + ";" + (System.currentTimeMillis() + duration));

        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 0.5f);
        boss.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, boss.getLocation().add(0, 2, 0), 10, 0.5, 0.5, 0.5, 0);
    }
}
