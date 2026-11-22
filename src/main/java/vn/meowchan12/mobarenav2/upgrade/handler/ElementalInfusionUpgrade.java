package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

import java.util.concurrent.ThreadLocalRandom;

/**
 * [v7.3 REWORK]: Elemental Strike (Elemental Infusion)
 * Cường hóa đòn đánh với Lửa và Làm Chậm (Tier 1-5).
 * Milestone Lv.5: Sấm sét giáng xuống khi đòn đánh bạo kích gây 12.0 sát thương diện rộng.
 */
public class ElementalInfusionUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "ELEMENTAL_INFUSION";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || victim == null) return;

        // Thiêu đốt mục tiêu theo cấp độ
        int fireTicks = 30 + level * 20; // 2.5s -> 6.5s
        victim.setFireTicks(Math.max(victim.getFireTicks(), fireTicks));

        // Làm chậm mục tiêu
        int slownessAmp = Math.min(level - 1, 2);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40 + level * 10, slownessAmp, false, false));

        // Hiệu ứng hạt
        victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation().add(0, 1, 0), 6, 0.2, 0.4, 0.2, 0.05);
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 0.8, 0), 4, 0.2, 0.4, 0.2, 0.02);

        // Milestone Lv.5: Sét đánh khi bạo kích (hoặc 25% xác suất với đòn nhảy đánh)
        if (level >= 5) {
            boolean isCrit = player.getFallDistance() > 0.0F && !player.isOnGround();
            if (isCrit || ThreadLocalRandom.current().nextDouble(100.0) <= 25.0) {
                victim.getWorld().strikeLightningEffect(victim.getLocation());
                victim.damage(12.0, player);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.2f);
            }
        }
    }
}
