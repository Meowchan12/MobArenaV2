package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.List;

/**
 * Vòng tròn độc tố màu tím mở rộng dần từ tâm Boss.
 */
public class ToxicNovaSkill implements BossSkill {

    @Override
    public String getType() {
        return "TOXIC_NOVA";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        double maxNovaRadius = config.getDouble("max-radius", 10.0);
        double novaSpeed = config.getDouble("expansion-speed", 0.5);
        double novaDmg = config.getDouble("damage-per-tick", 2.0);
        List<String> novaEffects = config.getStringList("effects");

        new BukkitRunnable() {
            double currentRadius = 1.0;
            @Override
            public void run() {
                if (boss.isDead() || !arena.isRunning() || currentRadius > maxNovaRadius) {
                    this.cancel();
                    return;
                }

                Location center = boss.getLocation();
                for (double theta = 0; theta < 2 * Math.PI; theta += Math.PI / 16) {
                    double x = currentRadius * Math.cos(theta);
                    double z = currentRadius * Math.sin(theta);
                    center.getWorld().spawnParticle(Particle.WITCH, center.clone().add(x, 0.5, z), 1, 0, 0, 0, 0);
                }

                for (Player p : arena.getPlayersAsObjects()) {
                    if (arena.containsLocation(p.getLocation()) && p.getLocation().distance(center) <= currentRadius) {
                        p.damage(novaDmg, boss);
                        for (String eff : novaEffects) {
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
                    }
                }
                currentRadius += novaSpeed;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }
}
