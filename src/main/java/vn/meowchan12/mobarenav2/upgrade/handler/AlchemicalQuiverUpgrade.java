package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

import java.util.concurrent.ThreadLocalRandom;

/**
 * [v8.2.0 NEW UPGRADE]: Alchemical Quiver (BOW_POTION_INFUSION)
 * - Nâng cấp chuyên dụng cho Cung Tên và Nỏ.
 * - Mũi tên bắn trúng có 20% (Lv.1) đến 50% (Lv.5) tỷ lệ tẩm ngẫu nhiên một độc dược:
 *   + Axit (Acid): Gây suy yếu Weakness II & bào giáp trong 4s.
 *   + Thiêu Đốt (Hellfire): Đốt cháy mục tiêu và phát nổ lửa nhỏ.
 *   + Băng Hàn (Frostbite): Làm chậm quái vật 50% (Slowness III) trong 3s.
 * - Tier Milestone (Lv.5): Bắn xa trên 20 block chắc chắn 100% kích hoạt đồng thời CẢ 3 hiệu ứng!
 */
public class AlchemicalQuiverUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "BOW_POTION_INFUSION";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null || victim == null) return;
        if (!(event.getDamager() instanceof Projectile)) return;

        double distance = player.getLocation().distance(victim.getLocation());
        boolean isSniperShot = (level >= 5 && distance >= 20.0);

        double procChance = 0.20 + (level - 1) * 0.075; // Lv.1: 20%, Lv.5: 50%
        if (!isSniperShot && ThreadLocalRandom.current().nextDouble() > procChance) return;

        if (isSniperShot) {
            // Milestone Lv.5: Bắn xa kích hoạt đồng thời cả 3 hiệu ứng
            applyAcid(victim);
            applyHellfire(victim);
            applyFrostbite(victim);
            player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.4f);
        } else {
            // Tẩm ngẫu nhiên 1 trong 3 hiệu ứng
            int roll = ThreadLocalRandom.current().nextInt(3);
            switch (roll) {
                case 0 -> applyAcid(victim);
                case 1 -> applyHellfire(victim);
                default -> applyFrostbite(victim);
            }
        }
    }

    private void applyAcid(LivingEntity victim) {
        victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1));
        victim.getWorld().spawnParticle(Particle.ITEM_SLIME, victim.getLocation().add(0, 1.0, 0), 12, 0.3, 0.3, 0.3, 0.05);
        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_BREWING_STAND_BREW, 0.7f, 1.8f);
    }

    private void applyHellfire(LivingEntity victim) {
        victim.setFireTicks(80);
        victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation().add(0, 1.0, 0), 15, 0.3, 0.3, 0.3, 0.05);
        victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.8f, 1.2f);
    }

    private void applyFrostbite(LivingEntity victim) {
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2));
        victim.getWorld().spawnParticle(Particle.SNOWFLAKE, victim.getLocation().add(0, 1.0, 0), 15, 0.3, 0.3, 0.3, 0.05);
        victim.getWorld().playSound(victim.getLocation(), Sound.BLOCK_POWDER_SNOW_STEP, 0.9f, 1.6f);
    }
}
