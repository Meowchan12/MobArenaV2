package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

/**
 * Dịch chuyển tức thời ra sau lưng người chơi xa nhất và tập kích bất ngờ.
 */
public class TeleportStrikeSkill implements BossSkill {

    @Override
    public String getType() {
        return "TELEPORT_STRIKE";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        Location bossLoc = boss.getLocation();
        double damage = config.getDouble("damage", 10.0);

        Player furthest = null;
        double maxDist = 0;
        for (Player p : arena.getPlayersAsObjects()) {
            if (!arena.containsLocation(p.getLocation())) continue;
            double dist = p.getLocation().distanceSquared(bossLoc);
            if (dist > maxDist) {
                maxDist = dist;
                furthest = p;
            }
        }

        if (furthest != null) {
            bossLoc.getWorld().spawnParticle(Particle.PORTAL, bossLoc, 50, 1.0, 2.0, 1.0, 1.0);

            Vector dir = furthest.getLocation().getDirection().setY(0).normalize();
            Location behind = furthest.getLocation().subtract(dir.multiply(2));

            boss.teleport(behind);
            boss.getWorld().playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
            boss.getWorld().spawnParticle(Particle.PORTAL, behind, 50, 1.0, 2.0, 1.0, 1.0);

            furthest.damage(damage, boss);
            furthest.setVelocity(dir.multiply(1.5));
        }
    }
}
