package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

/**
 * Nạp đạn Super Arrow cho Boss bắn cung gây sát thương chuẩn và nổ diện rộng.
 */
public class SuperArrowSkill implements BossSkill {

    @Override
    public String getType() {
        return "SUPER_ARROW_DAMAGE";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        Location bossLoc = boss.getLocation();
        double saDmg = config.getDouble("damage", 8.0);
        double saTDmg = config.getDouble("true-damage-amount", 4.0);
        long activeUntil = System.currentTimeMillis() + (config.getInt("active-time", 5) * 1000L);

        NamespacedKey saKey = new NamespacedKey(plugin, "ma_super_arrow");
        boss.getPersistentDataContainer().set(saKey, PersistentDataType.STRING, saDmg + ";" + saTDmg + ";" + activeUntil);

        double superExpRadius = config.getDouble("explosion-radius", 2.5);
        if (superExpRadius > 0) {
            NamespacedKey expKey = new NamespacedKey(plugin, "ma_arrow_explosion_radius");
            boss.getPersistentDataContainer().set(expKey, PersistentDataType.DOUBLE, superExpRadius);
        }
        bossLoc.getWorld().spawnParticle(Particle.FLAME, bossLoc.clone().add(0, 2, 0), 30, 0.5, 0.5, 0.5, 0.1);
    }
}
