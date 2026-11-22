package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.List;

/**
 * [v7.5.6]: ABYSSAL_TETHER
 * Creates a soul link between two players (or player and boss in solo).
 * If the tethered targets move further than the max distance, they are violently
 * pulled towards each other and suffer severe abyssal damage.
 */
public class AbyssalTetherSkill implements BossSkill {

    @Override
    public String getType() {
        return "ABYSSAL_TETHER";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        if (boss == null || !boss.isValid()) return;

        double maxDist = config != null ? config.getDouble("max-distance", 10.0) : 10.0;
        double breakDamage = config != null ? config.getDouble("damage", 5.0) : 5.0;
        int durationTicks = config != null ? config.getInt("duration", 120) : 120; // 6 seconds = 120 ticks

        List<Player> targets = getNearbyTargets(arena, boss, 35.0);
        if (targets.isEmpty()) return;

        Entity entityA;
        Entity entityB;

        if (targets.size() >= 2) {
            entityA = targets.get(0);
            entityB = targets.get(1);
        } else {
            // Solo mode: tether player to Boss
            entityA = targets.get(0);
            entityB = boss;
            maxDist += 2.0; // Slightly more leeway when tethered to boss
        }

        final double tetherLimit = maxDist;
        final Entity targetA = entityA;
        final Entity targetB = entityB;

        if (targetA instanceof Player pA) {
            pA.sendMessage("§4§l[ABYSSAL TETHER] §cYou have been bound by a dark soul tether! Do not wander too far!");
            pA.playSound(pA.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.2f, 0.8f);
        }
        if (targetB instanceof Player pB) {
            pB.sendMessage("§4§l[ABYSSAL TETHER] §cYou have been bound by a dark soul tether! Do not wander too far!");
            pB.playSound(pB.getLocation(), Sound.BLOCK_CHAIN_PLACE, 1.2f, 0.8f);
        }

        new BukkitRunnable() {
            int elapsed = 0;

            @Override
            public void run() {
                if ((arena != null && !arena.isRunning()) || !targetA.isValid() || !targetB.isValid() || elapsed >= durationTicks) {
                    this.cancel();
                    return;
                }

                elapsed += 5;

                Location locA = targetA.getLocation().add(0, 1.0, 0);
                Location locB = targetB.getLocation().add(0, 1.0, 0);

                if (!locA.getWorld().equals(locB.getWorld())) {
                    this.cancel();
                    return;
                }

                // Draw tether particle beam
                spawnTetherBeam(locA, locB);

                double dist = locA.distance(locB);
                if (dist > tetherLimit) {
                    // Punish: Pull entities towards each other & deal damage
                    Vector dirAtoB = locB.toVector().subtract(locA.toVector()).normalize().multiply(0.55);
                    Vector dirBtoA = locA.toVector().subtract(locB.toVector()).normalize().multiply(0.55);

                    targetA.setVelocity(targetA.getVelocity().add(dirAtoB));
                    if (!(targetB instanceof LivingEntity le && le == boss)) {
                        targetB.setVelocity(targetB.getVelocity().add(dirBtoA));
                    }

                    if (targetA instanceof LivingEntity leA) {
                        leA.damage(breakDamage, boss);
                    }
                    if (targetB instanceof Player pB) {
                        pB.damage(breakDamage, boss);
                    }

                    locA.getWorld().playSound(locA, Sound.BLOCK_CHAIN_BREAK, 1.0f, 1.2f);
                    locA.getWorld().spawnParticle(Particle.CRIT, locA, 15, 0.3, 0.3, 0.3, 0.2);
                    locB.getWorld().spawnParticle(Particle.CRIT, locB, 15, 0.3, 0.3, 0.3, 0.2);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void spawnTetherBeam(Location start, Location end) {
        if (start.getWorld() == null || !start.getWorld().equals(end.getWorld())) return;

        double distance = start.distance(end);
        Vector dir = end.toVector().subtract(start.toVector()).normalize();
        double step = 0.8;

        for (double d = 0; d < distance; d += step) {
            Location point = start.clone().add(dir.clone().multiply(d));
            point.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, point, 1, 0, 0, 0, 0);
        }
    }
}
