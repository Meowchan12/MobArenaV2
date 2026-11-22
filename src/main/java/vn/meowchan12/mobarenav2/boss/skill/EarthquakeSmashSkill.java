package vn.meowchan12.mobarenav2.boss.skill;

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

/**
 * [v7.3 FIX]: Cú nhảy đập đất hủy diệt (Earthquake Smash).
 * Bổ sung cơ chế timeout fallback đảm bảo kích hoạt 100% không bị kẹt điều kiện isOnGround.
 */
public class EarthquakeSmashSkill implements BossSkill {

    @Override
    public String getType() {
        return "EARTHQUAKE_SMASH";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        double eqRadius = config.getDouble("radius", 8.0);
        double eqJump = config.getDouble("jump-velocity", 1.5);
        double eqKnockup = config.getDouble("knockup-power", 1.2);
        double eqDamage = config.getDouble("damage", 15.0);
        double eqSmashPen = config.getDouble("armor-penetration", 0.90);

        boss.setVelocity(new Vector(0, eqJump, 0));

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (boss.isDead() || !arena.isRunning()) {
                    this.cancel();
                    return;
                }

                ticks++;
                boolean shouldLand = (ticks > 6 && boss.isOnGround()) || ticks >= 30;

                if (shouldLand) {
                    Location landLoc = boss.getLocation();
                    landLoc.getWorld().playSound(landLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.8f);
                    landLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, landLoc, 2);

                    for (Player p : arena.getPlayersAsObjects()) {
                        if (arena.containsLocation(p.getLocation()) && p.getLocation().distance(landLoc) <= eqRadius) {
                            double trueDmg = eqDamage * eqSmashPen;
                            double physDmg = eqDamage - trueDmg;

                            if (physDmg > 0) p.damage(physDmg, boss);
                            if (trueDmg > 0) {
                                vn.meowchan12.mobarenav2.service.CombatDamageService.applyLayeredDamage(p, trueDmg, false, boss, arena, plugin);
                            }
                            p.setVelocity(new Vector(0, eqKnockup, 0));
                        }
                    }
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }
}
