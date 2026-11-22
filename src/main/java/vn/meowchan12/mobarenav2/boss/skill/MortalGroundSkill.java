package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.service.CombatDamageService;
import vn.meowchan12.mobarenav2.utils.ColorUtils;
import vn.meowchan12.mobarenav2.utils.ParticleUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * [v8.3.0 TACTICAL BOSS SKILL]: MORTAL_GROUND (Hazard / Zone Denial Mechanic)
 * - Boss infects the ground beneath active players with persistent void magma pools.
 * - Pools last 10s. Standing inside deals periodic true damage and inflicts Mortal Wound (-80% Healing Received).
 */
public class MortalGroundSkill implements BossSkill {

    public static final String META_MORTAL_WOUND = "MA_MORTAL_WOUND";

    @Override
    public String getType() {
        return "MORTAL_GROUND";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection skillConfig) {
        if (boss == null || !boss.isValid() || boss.isDead()) return;

        List<Player> targets = getNearbyTargets(arena, boss, 45.0);
        if (targets.isEmpty()) return;

        List<Location> poolLocations = new ArrayList<>();
        for (Player p : targets) {
            poolLocations.add(p.getLocation().clone());
        }

        final double poolRadius = (skillConfig != null) ? skillConfig.getDouble("radius", 3.5) : 3.5;
        final int durationSec = (skillConfig != null) ? skillConfig.getInt("duration", 10) : 10;
        final double dps = (skillConfig != null) ? skillConfig.getDouble("damage-per-second", 4.0) : 4.0;

        String bossName = boss.getCustomName() != null ? boss.getCustomName() : boss.getName();
        String alert = ColorUtils.colorize("&4&l☠ [MORTAL GROUND] " + bossName + " &ccorrupted the ground beneath your feet! REPOSITION!");
        if (arena != null) arena.broadcastMessage(alert);

        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 1.5f, 0.6f);

        new BukkitRunnable() {
            int ticksElapsed = 0;

            @Override
            public void run() {
                ticksElapsed += 10; // Runs every 0.5s

                for (Location poolLoc : poolLocations) {
                    if (poolLoc.getWorld() == null) continue;

                    // Spawn dark void/magma ground particles
                    Location particleLoc = poolLoc.clone().add(0, 0.1, 0);
                    ParticleUtils.spawnSafeParticle(poolLoc.getWorld(), Particle.SOUL_FIRE_FLAME, particleLoc, 8, poolRadius * 0.5, 0.1, poolRadius * 0.5, 0.02);
                    ParticleUtils.spawnSafeParticle(poolLoc.getWorld(), Particle.SMOKE, particleLoc, 6, poolRadius * 0.5, 0.1, poolRadius * 0.5, 0.02);
                    ParticleUtils.spawnSafeParticle(poolLoc.getWorld(), Particle.LAVA, particleLoc, 2, poolRadius * 0.3, 0.1, poolRadius * 0.3, 0.0);

                    // Check players standing in pool
                    List<Player> nearby = getNearbyTargets(arena, boss, 50.0);
                    for (Player p : nearby) {
                        if (p.getWorld().equals(poolLoc.getWorld()) && p.getLocation().distanceSquared(poolLoc) <= poolRadius * poolRadius) {
                            // Standing in pool
                            if (ticksElapsed % 20 == 0) {
                                CombatDamageService.applyLayeredDamage(p, dps, true, boss, arena, plugin);
                                p.sendMessage(ColorUtils.colorize("&c&l☠ Corrupted Ground burns you for &e" + (int) dps + " True Damage!"));
                            }

                            // Inflict Mortal Wound & Slowness
                            p.setMetadata(META_MORTAL_WOUND, new FixedMetadataValue(plugin, true));
                            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));

                            // Remove tag after 2s if player steps out
                            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                                if (p.isOnline() && p.getLocation().distanceSquared(poolLoc) > poolRadius * poolRadius) {
                                    p.removeMetadata(META_MORTAL_WOUND, plugin);
                                }
                            }, 40L);
                        }
                    }
                }

                if (ticksElapsed >= durationSec * 20) {
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }
}
