package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v8.4.3 DYNAMIC ENGINE]: Armor Shredder (ARMOR_PIERCING)
 * Reads armor shred ratio, true damage percentage, and shatter milestone from UpgradeData/upgrades.yml.
 */
public class ArmorPiercingUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "ARMOR_PIERCING";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena, UpgradeData data) {
        if (level <= 0 || victim == null || victim.isDead() || player == null || arena == null) return;

        MobArenav2 plugin = (arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();

        // 1. Phá giáp thực thể (Armor Shredding)
        if (AttributeUtils.ARMOR != null) {
            AttributeInstance armorAttr = victim.getAttribute(AttributeUtils.ARMOR);
            if (armorAttr != null && armorAttr.getValue() > 0) {
                double shredRatio = (data != null)
                        ? (data.getDouble("shred-base", 15.0) + (level - 1) * data.getDouble("shred-increment", 7.5)) / 100.0
                        : (0.15 + (level - 1) * 0.075);
                double currentBase = armorAttr.getBaseValue();
                if (currentBase > 0) {
                    double reducedBase = Math.max(0.0, currentBase * (1.0 - shredRatio));
                    armorAttr.setBaseValue(reducedBase);

                    // Phục hồi lại giáp sau 4 giây (80 ticks) nếu quái còn sống
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (victim.isValid() && !victim.isDead()) {
                            armorAttr.setBaseValue(currentBase);
                        }
                    }, 80L);
                }
            }
        }

        // 2. Tính toán Sát Thương Chuẩn Bổ Sung (Bonus True Damage)
        double trueDmgRatio = (data != null)
                ? (data.getValueAtLevel(level) / 100.0)
                : (0.12 + (level - 1) * 0.07);
        double minTrueDmg = 3.0 * level; // Tối thiểu từ 3.0 đến 15.0 True DMG
        double totalTrueDmg = Math.max(minTrueDmg, event.getDamage() * trueDmgRatio);

        // Milestone Lv.5: Phá vỡ phòng ngự (Armor Shatter)
        int shatterMilestone = (data != null) ? data.getInt("shatter-milestone-level", 5) : 5;
        if (level >= shatterMilestone) {
            totalTrueDmg *= 1.20; // +20% bonus true damage
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1, false, false));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 80, 0, false, false));
        }

        final double finalTrueDmg = totalTrueDmg;

        // Ghi nhận chỉ số sát thương
        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
        if (profile != null) {
            profile.addDamageDealt(finalTrueDmg);
        }

        // 3. Thi hành Sát thương chuẩn an toàn (Delayed 1 tick để tránh xung đột Event FinalDamage)
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!victim.isValid() || victim.isDead()) return;

            double remaining = victim.getHealth() - finalTrueDmg;
            if (remaining <= 0) {
                victim.setHealth(0.5);
                victim.damage(1000.0, player);
            } else {
                victim.setHealth(Math.max(0.1, remaining));
                victim.playHurtAnimation(0.0f);
            }

            // Hiệu ứng phá vỡ giáp
            victim.getWorld().spawnParticle(Particle.BLOCK, victim.getLocation().add(0, 1.2, 0), 12, 0.25, 0.3, 0.25, 0.1, Material.IRON_BLOCK.createBlockData());
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.2, 0), 8, 0.2, 0.2, 0.2, 0.15);
            victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 0.8f, 1.4f);
        });
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        onAttack(event, player, victim, level, arena, null);
    }
}
