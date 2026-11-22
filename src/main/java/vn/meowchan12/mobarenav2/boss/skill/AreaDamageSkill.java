package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

/**
 * Đập đất gây sát thương diện rộng xung quanh Boss (Earthquake).
 */
public class AreaDamageSkill implements BossSkill {

    @Override
    public String getType() {
        return "AREA_DAMAGE";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        Location bossLoc = boss.getLocation();
        double radius = config.getDouble("radius", 6.0);
        double damage = config.getDouble("damage", 12.0);
        double eqPen = config.getDouble("armor-penetration", 0.90);

        bossLoc.getWorld().spawnParticle(Particle.EXPLOSION, bossLoc, 5, 1.0, 0.5, 1.0, 0.0);
        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);

        for (Entity e : boss.getNearbyEntities(radius, radius, radius)) {
            if (e instanceof Player p && arena.getActivePlayers().contains(p.getUniqueId()) && arena.containsLocation(p.getLocation())) {
                double trueDmg = damage * eqPen;
                double physDmg = damage - trueDmg;

                if (physDmg > 0) p.damage(physDmg, boss);
                if (trueDmg > 0) {
                    vn.meowchan12.mobarenav2.service.CombatDamageService.applyLayeredDamage(p, trueDmg, false, boss, arena, plugin);
                }
                p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 10);
            }
        }
    }
}
