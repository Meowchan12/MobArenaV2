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
 * [v7.3 FIX]: Cú lao điên cuồng của Boss.
 * Duy trì vận tốc vector liên tục mỗi tick, khắc phục lỗi bị ma sát đứng im một chỗ.
 */
public class BerserkerChargeSkill implements BossSkill {

    @Override
    public String getType() {
        return "BERSERKER_CHARGE";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        Location bossLoc = boss.getLocation();
        double bcDamage = config.getDouble("damage", 14.0);
        double bcSpeed = config.getDouble("charge-speed", 1.8);
        double bcPathDmg = config.getDouble("path-damage", 5.0);

        // Nhắm vào người chơi xa nhất
        Player chargeTarget = null;
        double maxDistSq = 0;
        for (Player p : arena.getPlayersAsObjects()) {
            if (!arena.containsLocation(p.getLocation())) continue;
            double distSq = p.getLocation().distanceSquared(bossLoc);
            if (distSq > maxDistSq) {
                maxDistSq = distSq;
                chargeTarget = p;
            }
        }

        if (chargeTarget != null) {
            final Player finalTarget = chargeTarget;
            boss.setGlowing(true);
            bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_RAVAGER_ROAR, 2.0f, 0.8f);

            new BukkitRunnable() {
                int chargeTicks = 0;
                @Override
                public void run() {
                    if (boss.isDead() || !arena.isRunning() || chargeTicks >= 40 || !finalTarget.isOnline()) {
                        boss.setGlowing(false);
                        this.cancel();
                        return;
                    }

                    Location current = boss.getLocation();
                    Vector chargeDir = finalTarget.getLocation().toVector().subtract(current.toVector()).setY(0).normalize().multiply(bcSpeed);
                    boss.setVelocity(chargeDir.setY(0.1));

                    current.getWorld().spawnParticle(Particle.FLAME, current.add(0, 0.5, 0), 8, 0.4, 0.2, 0.4, 0.05);

                    // Sát thương trên đường lao
                    for (Player p : arena.getPlayersAsObjects()) {
                        if (arena.containsLocation(p.getLocation()) && p.getLocation().distance(current) <= 2.5) {
                            if (p.equals(finalTarget)) {
                                vn.meowchan12.mobarenav2.service.CombatDamageService.applyLayeredDamage(p, bcDamage, false, boss, arena, plugin);

                                p.setVelocity(chargeDir.clone().normalize().multiply(1.8).setY(0.8));
                                p.sendMessage("§c§l[IMPACT] §eYou were smashed by the Boss's brutal charge!");
                                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 1.2f);
                                boss.setGlowing(false);
                                this.cancel();
                                return;
                            } else {
                                vn.meowchan12.mobarenav2.service.CombatDamageService.applyLayeredDamage(p, bcPathDmg, false, boss, arena, plugin);

                                Vector push = p.getLocation().toVector().subtract(current.toVector()).normalize().multiply(1.2).setY(0.5);
                                p.setVelocity(push);
                            }
                        }
                    }
                    chargeTicks++;
                }
            }.runTaskTimer(plugin, 2L, 1L);
        }
    }
}
