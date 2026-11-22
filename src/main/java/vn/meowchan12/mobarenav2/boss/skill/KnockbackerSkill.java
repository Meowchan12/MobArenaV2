package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.List;

/**
 * [v7.3 FIX]: Sóng chấn động đẩy lùi người chơi + Hiệu ứng buồn nôn/chóng mặt (Nausea).
 * Tự động chuyển đổi alias "confusion" thành "nausea" tương thích chuẩn Minecraft 1.21+.
 */
public class KnockbackerSkill implements BossSkill {

    @Override
    public String getType() {
        return "KNOCKBACKER";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        Location bossLoc = boss.getLocation();
        double kbRad = config.getDouble("radius", 6.0);
        double kbDmg = config.getDouble("damage", 5.0);
        double kbPower = config.getDouble("knockback-power", 2.0);
        double kbHeight = config.getDouble("knockback-height", 1.2);
        List<String> kbEffects = config.getStringList("effects");

        bossLoc.getWorld().spawnParticle(Particle.SONIC_BOOM, bossLoc, 1);
        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.0f);

        for (Entity e : boss.getNearbyEntities(kbRad, kbRad, kbRad)) {
            if (e instanceof Player p && arena.getActivePlayers().contains(p.getUniqueId()) && arena.containsLocation(p.getLocation())) {
                vn.meowchan12.mobarenav2.service.CombatDamageService.applyLayeredDamage(p, kbDmg, false, boss, arena, plugin);

                Vector dir = p.getLocation().toVector().subtract(bossLoc.toVector()).normalize();
                p.setVelocity(dir.multiply(kbPower).setY(kbHeight));

                for (String eff : kbEffects) {
                    String[] data = eff.trim().split(":");
                    if (data.length >= 2) {
                        String effName = data[0].toLowerCase();
                        if (effName.equals("confusion")) effName = "nausea"; // Fix Paper 1.21 alias

                        try {
                            PotionEffectType pType = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(effName));
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
