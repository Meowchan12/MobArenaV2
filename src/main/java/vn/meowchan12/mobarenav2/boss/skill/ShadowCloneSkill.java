package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * Tạo các ảo ảnh bóng tối đánh lừa và gây rối người chơi trong 8s.
 */
public class ShadowCloneSkill implements BossSkill {

    @Override
    public String getType() {
        return "SHADOW_CLONE";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        Location bossLoc = boss.getLocation();
        int cloneCount = config.getInt("clone-count", 3);
        int cloneLifespan = config.getInt("clone-lifespan", 8);
        double cloneDamage = config.getDouble("clone-damage", 3.0);
        String cloneMobType = config.getString("clone-mob-type", boss.getType().name());

        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 2.0f, 1.0f);
        boss.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20, 0, false, false));

        for (int ci = 0; ci < cloneCount; ci++) {
            double angle = (2 * Math.PI / cloneCount) * ci;
            Location cloneLoc = bossLoc.clone().add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);

            try {
                EntityType cloneType = EntityType.valueOf(cloneMobType.toUpperCase());
                LivingEntity clone = (LivingEntity) cloneLoc.getWorld().spawnEntity(cloneLoc, cloneType);
                clone.setCustomName(boss.getCustomName() + " §7(Shadow)");
                clone.setCustomNameVisible(true);
                clone.setMetadata("MA_MOB", new FixedMetadataValue(plugin, arena.getName()));

                AttributeInstance cloneHp = clone.getAttribute(AttributeUtils.MAX_HEALTH);
                if (cloneHp != null) {
                    cloneHp.setBaseValue(20.0);
                    clone.setHealth(20.0);
                }
                AttributeInstance cloneAtk = clone.getAttribute(AttributeUtils.ATTACK_DAMAGE);
                if (cloneAtk != null) cloneAtk.setBaseValue(cloneDamage);

                clone.getWorld().spawnParticle(Particle.LARGE_SMOKE, cloneLoc, 20, 0.5, 1, 0.5, 0.05);
                arena.getActiveMobs().add(clone);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (clone.isValid() && !clone.isDead()) {
                        clone.getWorld().spawnParticle(Particle.LARGE_SMOKE, clone.getLocation(), 30, 0.5, 1, 0.5, 0.1);
                        clone.getWorld().playSound(clone.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 2.0f);
                        clone.remove();
                        arena.getActiveMobs().remove(clone);
                    }
                }, cloneLifespan * 20L);
            } catch (Exception e) {
                plugin.getLogger().warning("[MobArenav2] Shadow Clone failed: " + e.getMessage());
            }
        }
    }
}
