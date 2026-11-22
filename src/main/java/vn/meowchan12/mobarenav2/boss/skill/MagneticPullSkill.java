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
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.List;

/**
 * Lực hút từ trường kéo người chơi và gây hiệu ứng Mù lòa (Blindness).
 */
public class MagneticPullSkill implements BossSkill {

    @Override
    public String getType() {
        return "MAGNETIC_PULL";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        Location bossLoc = boss.getLocation();
        double mpRadius = config.getDouble("radius", 12.0);
        double mpStrength = config.getDouble("pull-strength", 1.5);
        List<String> effects = config.getStringList("effects");

        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.5f);
        bossLoc.getWorld().spawnParticle(Particle.PORTAL, bossLoc, 100, 3.0, 3.0, 3.0, 0.5);

        for (Player p : arena.getPlayersAsObjects()) {
            if (arena.containsLocation(p.getLocation()) && p.getLocation().distance(bossLoc) <= mpRadius) {
                Vector dir = bossLoc.toVector().subtract(p.getLocation().toVector()).normalize();
                p.setVelocity(dir.multiply(mpStrength));

                for (String eff : effects) {
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
    }
}
