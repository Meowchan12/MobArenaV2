package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Bukkit;
import org.bukkit.Location;
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
import vn.meowchan12.mobarenav2.arena.MobFactory;

import java.util.List;

/**
 * Triệu hồi lâu la đệ tử trợ chiến cho Boss có giới hạn thời gian tồn tại.
 */
public class MinionCallSkill implements BossSkill {

    @Override
    public String getType() {
        return "MINION_CALL";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        Location bossLoc = boss.getLocation();
        String mType = config.getString("mob-type", "ZOMBIE");
        int mAmount = config.getInt("amount", 4);
        int mLifespan = config.getInt("lifespan", 15);
        List<String> buffs = config.getStringList("minion-buffs");

        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);
        MobFactory mFactory = new MobFactory(plugin);

        for (int i = 0; i < mAmount; i++) {
            Location spawnLoc = bossLoc.clone().add((Math.random() - 0.5) * 4, 0, (Math.random() - 0.5) * 4);
            LivingEntity minion = mFactory.spawnArenaMob(arena, mType.toLowerCase(), spawnLoc, null, null);
            if (minion != null) {
                for (String buff : buffs) {
                    String[] data = buff.trim().split(":");
                    if (data.length >= 2) {
                        try {
                            PotionEffectType pType = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(data[0].toLowerCase()));
                            if (pType != null) {
                                minion.addPotionEffect(new PotionEffect(pType, Integer.parseInt(data[1]), data.length > 2 ? Integer.parseInt(data[2]) : 0));
                            }
                        } catch (Exception ignored) {}
                    }
                }

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (minion.isValid() && !minion.isDead()) {
                        minion.getWorld().spawnParticle(Particle.LARGE_SMOKE, minion.getLocation(), 10, 0.5, 0.5, 0.5, 0);
                        minion.remove();
                        arena.getActiveMobs().remove(minion);
                    }
                }, mLifespan * 20L);
            }
        }
    }
}
