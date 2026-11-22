package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * [v7.3 FIX]: Sniper Dash - Lướt nhanh và vào thế bắn tỉa laser.
 * Bổ sung kiểm tra tầm nhìn hasLineOfSight tránh bắn xuyên tường đấu trường.
 */
public class DashAndRangedSkill implements BossSkill {

    @Override
    public String getType() {
        return "DASH_AND_RANGED";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        double dashPwr = config.getDouble("dash.power", 2.5);
        int invulnDur = config.getInt("dash.invulnerable-duration", 3);
        int rDuration = config.getInt("ranged-stance.duration", 10);
        double rDist = config.getDouble("ranged-stance.max-target-distance", 20.0);
        long rInterval = (long) (config.getDouble("ranged-stance.attack-interval", 1.0) * 20L);
        double rDamage = config.getDouble("ranged-stance.damage", 8.0);
        int maxDashTargets = config.getInt("dash.max-targets", 2);

        List<Player> dashTargets = new ArrayList<>(arena.getPlayersAsObjects());
        Collections.shuffle(dashTargets);
        int dashCount = Math.min(maxDashTargets, dashTargets.size());

        for (int di = 0; di < dashCount; di++) {
            Player dashTarget = dashTargets.get(di);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (boss.isDead() || !arena.isRunning() || !dashTarget.isOnline()) return;
                Vector dDir = dashTarget.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0.2).normalize().multiply(dashPwr);
                boss.setVelocity(dDir);
                boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 1.5f);
                boss.getWorld().spawnParticle(Particle.SONIC_BOOM, boss.getLocation(), 1);
            }, di * 25L);
        }

        NamespacedKey invKey = new NamespacedKey(plugin, "ma_invulnerable");
        boss.getPersistentDataContainer().set(invKey, PersistentDataType.LONG, System.currentTimeMillis() + (invulnDur * 1000L));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (boss.isDead() || !arena.isRunning()) return;

            boss.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, rDuration * 20, 100, false, false));
            boss.getWorld().spawnParticle(Particle.ENCHANTED_HIT, boss.getLocation(), 50, 1, 1, 1, 0.1);

            new BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    if (boss.isDead() || !arena.isRunning() || ticks >= (rDuration * 20)) {
                        this.cancel();
                        return;
                    }

                    if (ticks % rInterval == 0) {
                        Player target = getNearestVisiblePlayer(arena, boss, rDist);
                        if (target != null) {
                            Location eye = boss.getEyeLocation();
                            Location tEye = target.getEyeLocation();

                            drawLaser(eye, tEye, 255, 0, 0);

                            vn.meowchan12.mobarenav2.service.CombatDamageService.applyLayeredDamage(target, rDamage, false, boss, arena, plugin);

                            boss.getWorld().playSound(eye, Sound.ENTITY_GUARDIAN_ATTACK, 1.0f, 2.0f);
                        }
                    }
                    ticks++;
                }
            }.runTaskTimer(plugin, 0L, 1L);

        }, 20L);
    }

    private Player getNearestVisiblePlayer(Arena arena, LivingEntity boss, double maxDistance) {
        Player nearest = null;
        double minDistSq = maxDistance * maxDistance;
        Location loc = boss.getLocation();

        for (Player p : arena.getPlayersAsObjects()) {
            if (arena.containsLocation(p.getLocation())) {
                double distSq = p.getLocation().distanceSquared(loc);
                if (distSq <= minDistSq && boss.hasLineOfSight(p)) {
                    minDistSq = distSq;
                    nearest = p;
                }
            }
        }
        return nearest;
    }

    private void drawLaser(Location start, Location end, int r, int g, int b) {
        double distance = start.distance(end);
        Vector direction = end.toVector().subtract(start.toVector()).normalize().multiply(0.5);
        Location current = start.clone();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(r, g, b), 1.0f);

        for (double d = 0; d < distance; d += 0.5) {
            current.add(direction);
            current.getWorld().spawnParticle(Particle.DUST, current, 1, 0, 0, 0, 0, dust);
        }
    }
}
