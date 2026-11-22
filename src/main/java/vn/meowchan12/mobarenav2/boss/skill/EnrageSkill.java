package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

/**
 * Kỹ năng Cuồng Nộ của Boss khi chuyển Phase hoặc dưới 30% HP.
 */
public class EnrageSkill implements BossSkill {

    @Override
    public String getType() {
        return "ENRAGE";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
        boss.getWorld().spawnParticle(Particle.FLAME, boss.getLocation(), 100, 1.0, 2.0, 1.0, 0.2);
        boss.setGlowing(true);

        String buffs = config.getString("effects", "speed:3, strength:2");
        String[] enrageEffects = buffs.split(",");
        for (String eff : enrageEffects) {
            String[] data = eff.trim().split(":");
            if (data.length >= 2) {
                try {
                    NamespacedKey key = NamespacedKey.minecraft(data[0].toLowerCase());
                    PotionEffectType pType = Registry.POTION_EFFECT_TYPE.get(key);
                    if (pType != null) {
                        boss.addPotionEffect(new PotionEffect(pType, Integer.MAX_VALUE, Integer.parseInt(data[1]), false, false));
                    }
                } catch (Exception ignored) {}
            }
        }
    }
}
