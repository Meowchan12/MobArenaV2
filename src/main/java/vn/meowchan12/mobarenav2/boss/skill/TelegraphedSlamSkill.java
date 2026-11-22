package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.service.CombatDamageService;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * [v8.3.0 TACTICAL BOSS SKILL]: TELEGRAPHED_SLAM (Dodge or Die Mechanic)
 * - Boss targets a ground location and channels a massive seismic hammer strike for 2.5 seconds.
 * - Draws a pulsating red warning circle on the arena floor.
 * - Players have 2.5s to dash/escape. Anyone caught inside takes 70% Max HP damage + airborne launch.
 */
public class TelegraphedSlamSkill implements BossSkill {

    @Override
    public String getType() {
        return "TELEGRAPHED_SLAM";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection skillConfig) {
        if (boss == null || !boss.isValid() || boss.isDead()) return;

        List<Player> targets = getNearbyTargets(arena, boss, 40.0);
        if (targets.isEmpty()) return;

        Player targetPlayer = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        final Location slamCenter = targetPlayer.getLocation().clone();
        final double radius = (skillConfig != null) ? skillConfig.getDouble("radius", 6.0) : 6.0;
        final double damagePercent = (skillConfig != null) ? skillConfig.getDouble("damage-percent", 0.70) : 0.70;

        String bossName = boss.getCustomName() != null ? boss.getCustomName() : boss.getName();
        String alert = ColorUtils.colorize("&c&l⚠ [RED ZONE] " + bossName + " &cis targeting a devastating slam! &e&lESCAPE THE CIRCLE!");
        targetPlayer.sendMessage(alert);

        // 2.5 seconds warning loop (50 ticks)
        new BukkitRunnable() {
            int ticksElapsed = 0;
            final Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(255, 20, 20), 1.8f);

            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead()) {
                    cancel();
                    return;
                }

                ticksElapsed += 2;

                // Draw circular red telegraph ring on floor
                for (int degree = 0; degree < 360; degree += 15) {
                    double radians = Math.toRadians(degree);
                    double x = slamCenter.getX() + radius * Math.cos(radians);
                    double z = slamCenter.getZ() + radius * Math.sin(radians);
                    slamCenter.getWorld().spawnParticle(Particle.DUST, x, slamCenter.getY() + 0.15, z, 1, 0, 0, 0, 0, redDust);
                }

                // Audio ticking cadence
                if (ticksElapsed % 10 == 0) {
                    float pitch = 0.8f + (ticksElapsed / 50.0f) * 0.8f;
                    slamCenter.getWorld().playSound(slamCenter, Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, pitch);
                }

                if (ticksElapsed >= 50) {
                    // IMPACT!
                    cancel();
                    executeSlamImpact(plugin, arena, boss, slamCenter, radius, damagePercent);
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private void executeSlamImpact(MobArenav2 plugin, Arena arena, LivingEntity boss, Location center, double radius, double damagePercent) {
        if (center.getWorld() == null) return;

        // Visual and sound explosion
        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center.clone().add(0, 0.5, 0), 3);
        center.getWorld().spawnParticle(Particle.LAVA, center, 30, 2.0, 0.5, 2.0, 0.2);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.6f);
        center.getWorld().playSound(center, Sound.ENTITY_IRON_GOLEM_DEATH, 1.5f, 0.5f);

        List<Player> nearby = getNearbyTargets(arena, boss, radius * 1.5);
        for (Player p : nearby) {
            if (p.getLocation().distanceSquared(center) <= radius * radius) {
                // Caught inside!
                double maxHp = p.getAttribute(AttributeUtils.MAX_HEALTH).getValue();
                double damage = maxHp * damagePercent;
                CombatDamageService.applyLayeredDamage(p, damage, false, boss, arena, plugin);

                // Violent knockup launch
                p.setVelocity(new Vector(0, 1.4, 0));
                p.sendMessage(ColorUtils.colorize("&c&l☠ [DIRECT HIT] &fYou were crushed by the ground slam for &c" + (int) damage + " Damage!"));
                p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.0f, 0.6f);
            }
        }
    }
}
