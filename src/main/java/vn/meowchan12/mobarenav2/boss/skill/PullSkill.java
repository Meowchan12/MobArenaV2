package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

/**
 * Hút mọi người chơi trong tầm về phía Boss.
 */
public class PullSkill implements BossSkill {

    @Override
    public String getType() {
        return "PULL";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        Location bossLoc = boss.getLocation();
        double pRadius = config.getDouble("radius", 12.0);
        double pStrength = config.getDouble("strength", 1.5);

        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.5f);
        bossLoc.getWorld().spawnParticle(Particle.PORTAL, bossLoc, 80, 2.0, 2.0, 2.0, 1.0);

        for (Entity e : boss.getNearbyEntities(pRadius, pRadius, pRadius)) {
            if (e instanceof Player p && arena.getActivePlayers().contains(p.getUniqueId()) && arena.containsLocation(p.getLocation())) {
                Vector direction = bossLoc.toVector().subtract(p.getLocation().toVector()).normalize();
                p.setVelocity(direction.multiply(pStrength));
            }
        }
    }
}
