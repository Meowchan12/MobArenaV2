package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.ArrayList;
import java.util.List;

/**
 * Đặt vòng tròn lửa linh hồn bị nguyền rủa dưới chân người chơi.
 */
public class CursedGroundSkill implements BossSkill {

    @Override
    public String getType() {
        return "CURSED_GROUND";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        Location bossLoc = boss.getLocation();
        double cgRadius = config.getDouble("radius", 6.0);
        double cgDmgPerTick = config.getDouble("damage-per-tick", 3.0);
        int cgDuration = config.getInt("duration", 8);
        List<String> cgEffects = config.getStringList("effects");

        List<Location> cursedZones = new ArrayList<>();
        for (Player p : arena.getPlayersAsObjects()) {
            if (arena.containsLocation(p.getLocation())) {
                cursedZones.add(p.getLocation().clone());
            }
        }

        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.3f);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (boss.isDead() || !arena.isRunning() || ticks >= cgDuration * 20) {
                    this.cancel();
                    return;
                }

                for (Location zone : cursedZones) {
                    for (double theta = 0; theta < 2 * Math.PI; theta += Math.PI / 12) {
                        double x = cgRadius * Math.cos(theta);
                        double z = cgRadius * Math.sin(theta);
                        zone.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, zone.clone().add(x, 0.1, z), 1, 0, 0, 0, 0);
                    }

                    if (ticks % 20 == 0) {
                        for (Player p : arena.getPlayersAsObjects()) {
                            if (arena.containsLocation(p.getLocation()) && p.getLocation().distance(zone) <= cgRadius) {
                                vn.meowchan12.mobarenav2.service.CombatDamageService.applyLayeredDamage(p, cgDmgPerTick, true, boss, arena, plugin);

                                for (String eff : cgEffects) {
                                    String[] data = eff.trim().split(":");
                                    if (data.length >= 2) {
                                        try {
                                            PotionEffectType pType = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(data[0].toLowerCase()));
                                            if (pType != null) {
                                                p.addPotionEffect(new PotionEffect(pType, Integer.parseInt(data[1]), data.length > 2 ? Integer.parseInt(data[2]) : 0));
                                            }
                                        } catch (Exception ignored) {}
                                    }
                                }
                                p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 0.5, 0), 5, 0.3, 0.3, 0.3, 0.02);
                            }
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
}
