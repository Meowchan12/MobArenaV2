package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Tàng hình và dịch chuyển sau lưng con mồi (Phantom Assassin).
 */
public class InvisTeleporterSkill implements BossSkill {

    @Override
    public String getType() {
        return "INVIS_TELEPORTER";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        int iDur = config.getInt("duration", 12);
        int tInterval = config.getInt("teleport-interval", 3) * 20;
        double tMin = config.getDouble("teleport-distance.min", 4.0);
        double tMax = config.getDouble("teleport-distance.max", 8.0);

        boss.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, iDur * 20, 1, false, false));

        new BukkitRunnable() {
            int elapsed = 0;
            @Override
            public void run() {
                if (boss.isDead() || !arena.isRunning() || elapsed >= (iDur * 20)) {
                    this.cancel();
                    return;
                }

                if (elapsed % tInterval == 0) {
                    Player target = getNearestPlayer(arena, boss.getLocation(), 50.0);
                    if (target != null) {
                        double angle = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
                        double dist = ThreadLocalRandom.current().nextDouble(tMin, tMax);
                        Location tpLoc = target.getLocation().clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);

                        if (!tpLoc.getBlock().getType().isSolid()) {
                            boss.getWorld().spawnParticle(Particle.SMOKE, boss.getLocation(), 20, 0.5, 1, 0.5, 0);
                            boss.teleport(tpLoc);
                            boss.getWorld().playSound(tpLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                        }
                    }
                }
                elapsed += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private Player getNearestPlayer(Arena arena, Location loc, double maxDistance) {
        Player nearest = null;
        double minDistSq = maxDistance * maxDistance;
        for (Player p : arena.getPlayersAsObjects()) {
            if (arena.containsLocation(p.getLocation())) {
                double distSq = p.getLocation().distanceSquared(loc);
                if (distSq <= minDistSq) {
                    minDistSq = distSq;
                    nearest = p;
                }
            }
        }
        return nearest;
    }
}
