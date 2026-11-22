package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v8.4.3 DYNAMIC ENGINE]: Mirror of Retribution (SUPER_TRUE_REFLECT)
 * Reads normal reflect, low HP reflect, low HP threshold, and shield conversion from UpgradeData/upgrades.yml.
 */
public class MirrorOfRetributionUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "SUPER_TRUE_REFLECT";
    }

    @Override
    public void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena, UpgradeData data) {
        if (level <= 0 || player == null || damager == null || damager.isDead() || !damager.isValid()) return;

        try {
            AttributeInstance maxHpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
            double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
            double lowHpThreshold = (data != null) ? data.getDouble("low-hp-threshold", 0.50) : 0.50;
            boolean isLowHp = player.getHealth() <= maxHp * lowHpThreshold;

            double normalReflect = (data != null) ? (data.getDouble("normal-reflect-percent", 80.0) / 100.0) : 0.80;
            double lowHpReflect = (data != null) ? (data.getDouble("low-hp-reflect-percent", 150.0) / 100.0) : 1.50;
            double reflectPercent = isLowHp ? lowHpReflect : normalReflect;
            double rawDmg = event.getDamage();
            double trueReflect = rawDmg * reflectPercent;

            if (trueReflect <= 0) return;

            // Track stats cho /ma myupgrades
            if (arena != null) {
                ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
                if (profile != null) {
                    profile.addReflectedDamage(trueReflect);
                }
            }

            // Trừ máu chuẩn thẳng vào quái vật
            double remainingHp = damager.getHealth() - trueReflect;
            if (remainingHp <= 0) {
                damager.setHealth(0);
            } else {
                damager.setHealth(remainingHp);
            }
            try {
                damager.playHurtAnimation(0.0f);
            } catch (Throwable ignored) {}

            // Hiệu ứng âm thanh & hạt an toàn tuyệt đối (Không phụ thuộc DataType data class)
            try {
                damager.getWorld().spawnParticle(Particle.ENCHANTED_HIT, damager.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.2);
            } catch (Throwable ignored) {}
            try {
                player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.8f);
            } catch (Throwable ignored) {}

            if (isLowHp) {
                // Hồi thành khiên vàng
                double shieldRatio = (data != null) ? (data.getDouble("shield-conversion-percent", 15.0) / 100.0) : 0.15;
                double shieldBonus = trueReflect * shieldRatio;
                player.setAbsorptionAmount(Math.min(24.0, player.getAbsorptionAmount() + shieldBonus));

                // Choáng quái vật trong 1.5s (30 ticks)
                try {
                    damager.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 9, false, false));
                    damager.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 30, 4, false, false));
                } catch (Throwable ignored) {}

                spawnSafeElectricSpark(damager.getWorld(), damager.getLocation().add(0, 1.5, 0));
                try {
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.6f, 1.8f);
                } catch (Throwable ignored) {}

                player.sendMessage("§6§l[MIRROR OF RETRIBUTION] §cReflected " + (int)(reflectPercent * 100) + "% True Damage (§e" + String.format("%.1f", trueReflect) + " DMG§c) & Gained Shield!");
            } else {
                player.sendMessage("§e§l[RETRIBUTION] §fReflected " + (int)(reflectPercent * 100) + "% True Damage (§e" + String.format("%.1f", trueReflect) + " DMG§f)!");
            }
        } catch (Throwable t) {
            // Đảm bảo không bao giờ ném ngoại lệ làm spam console
        }
    }

    @Override
    public void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena) {
        onDefend(event, player, damager, level, arena, null);
    }

    private void spawnSafeElectricSpark(org.bukkit.World world, org.bukkit.Location loc) {
        if (world == null || loc == null) return;
        try {
            world.spawnParticle(Particle.ELECTRIC_SPARK, loc, 15, 0.4, 0.4, 0.4, 0.05);
            world.spawnParticle(Particle.CRIT, loc, 10, 0.3, 0.3, 0.3, 0.1);
        } catch (Throwable ignored) {}
    }
}
