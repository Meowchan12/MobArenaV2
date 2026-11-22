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

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * [v7.5.6]: CHRONO_STASIS
 * Creates a temporal distortion dome with a 1.5-second telegraph warning.
 * Players caught inside suffer severe 80% slowness, mining fatigue, and periodic decay damage.
 */
public class ChronoStasisSkill implements BossSkill {

    @Override
    public String getType() {
        return "CHRONO_STASIS";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        if (boss == null || !boss.isValid()) return;

        double radius = config != null ? config.getDouble("radius", 6.0) : 6.0;
        double damagePerSecond = config != null ? config.getDouble("damage-per-second", 3.0) : 3.0;

        List<Player> targets = getNearbyTargets(arena, boss, 30.0);
        Location center;
        if (!targets.isEmpty() && ThreadLocalRandom.current().nextBoolean()) {
            center = targets.get(ThreadLocalRandom.current().nextInt(targets.size())).getLocation().clone();
        } else {
            center = boss.getLocation().clone();
        }

        if (center.getWorld() == null) return;

        // Phase 1: 1.5s (30 ticks) Telegraph Warning
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (arena != null && !arena.isRunning()) {
                    this.cancel();
                    return;
                }

                ticks += 5;
                if (center.getWorld() != null) {
                    // Draw telegraph circle
                    drawCircle(center, radius, Particle.ENCHANT, 20);
                    center.getWorld().playSound(center, Sound.BLOCK_NOTE_BLOCK_HAT, 1.0f, 1.5f + (ticks * 0.02f));
                }

                if (ticks >= 30) {
                    this.cancel();
                    startActiveDome(plugin, arena, boss, center, radius, damagePerSecond);
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void startActiveDome(MobArenav2 plugin, Arena arena, LivingEntity boss, Location center, double radius, double dps) {
        if (center.getWorld() == null) return;

        center.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.6f);
        center.getWorld().playSound(center, Sound.BLOCK_END_PORTAL_SPAWN, 0.8f, 1.8f);

        new BukkitRunnable() {
            int durationTicks = 0;

            @Override
            public void run() {
                if ((arena != null && !arena.isRunning()) || durationTicks >= 80) { // 4 seconds = 80 ticks
                    if (center.getWorld() != null) {
                        center.getWorld().playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 1.5f);
                    }
                    this.cancel();
                    return;
                }

                durationTicks += 5;

                // Visual dome boundary
                if (center.getWorld() != null) {
                    drawCircle(center, radius, Particle.END_ROD, 24);
                    drawCircle(center.clone().add(0, 1.5, 0), radius * 0.8, Particle.PORTAL, 16);
                }

                // Check players inside dome
                List<Player> nearby = getNearbyTargets(arena, boss, radius + 2.0);
                for (Player p : nearby) {
                    if (p.getWorld().equals(center.getWorld()) && p.getLocation().distanceSquared(center) <= radius * radius) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 25, 4, false, false)); // Slowness V (80%)
                        p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 25, 2, false, false));
                        if (durationTicks % 20 == 0) { // Every 1 second
                            p.damage(dps, boss);
                            p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 1.6f);
                            p.sendMessage("§e§l[CHRONO STASIS] §cTime dilation has slowed your movement!");
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void drawCircle(Location center, double radius, Particle particle, int points) {
        if (center.getWorld() == null) return;
        double slice = 2 * Math.PI / points;
        for (int i = 0; i < points; i++) {
            double angle = slice * i;
            double x = center.getX() + radius * Math.cos(angle);
            double z = center.getZ() + radius * Math.sin(angle);
            center.getWorld().spawnParticle(particle, x, center.getY() + 0.1, z, 1, 0, 0, 0, 0);
        }
    }
}
