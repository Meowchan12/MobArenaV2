package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

/**
 * Hào quang hút máu của Boss: Hồi 30-50% lượng sát thương gây ra.
 */
public class LifestealAuraSkill implements BossSkill {

    @Override
    public String getType() {
        return "LIFESTEAL_AURA";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        long duration = config.getInt("duration", 10) * 1000L;
        double minLs = config.getDouble("heal-percent.min", 30.0);
        double maxLs = config.getDouble("heal-percent.max", 50.0);

        NamespacedKey lKey = new NamespacedKey(plugin, "ma_lifesteal");
        boss.getPersistentDataContainer().set(lKey, PersistentDataType.STRING, minLs + ";" + maxLs + ";" + (System.currentTimeMillis() + duration));

        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (boss.isDead() || (arena != null && !arena.isRunning()) || ticks >= (duration / 50)) {
                    this.cancel();
                    return;
                }
                boss.getWorld().spawnParticle(Particle.HEART, boss.getLocation().add(0, 1.5, 0), 2, 0.5, 0.5, 0.5, 0);
                ticks += 10;
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }
}
