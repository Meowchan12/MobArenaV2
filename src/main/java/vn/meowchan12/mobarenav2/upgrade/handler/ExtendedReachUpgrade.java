package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v8.2.1 UPDATE]: Extended Reach (EXTENDED_REACH)
 * - Tăng trực tiếp tầm với tấn công (Entity Interaction Range) của người chơi:
 *   + Mặc định sinh tồn: 3.0 blocks.
 *   + Tối đa 3 cấp độ (Lv.1: +0.4m -> 3.4m, Lv.2: +0.8m -> 3.8m, Lv.3: +1.2m -> 4.2m).
 * - Tận dụng thuộc tính ENTITY_INTERACTION_RANGE chính thức của Paper 1.21.x.
 * - Milestone Lv.3 (Longinus Thrust): Tấn công mục tiêu từ khoảng cách >= 3.6m gây thêm 20% sát thương và kích hoạt hiệu ứng Sonic Boom!
 */
public class ExtendedReachUpgrade implements UpgradeHandler {

    private static final double VANILLA_SURVIVAL_REACH = 3.0;

    @Override
    public String getType() {
        return "EXTENDED_REACH";
    }

    @Override
    public void onApply(Player player, int newLevel, UpgradeData data, Arena arena) {
        if (player == null) return;

        if (AttributeUtils.ENTITY_INTERACTION_RANGE != null) {
            AttributeInstance reachAttr = player.getAttribute(AttributeUtils.ENTITY_INTERACTION_RANGE);
            if (reachAttr != null) {
                // Tăng +0.4m mỗi cấp (Lv.1: 0.4m, Lv.2: 0.8m, Lv.3: 1.2m)
                double bonusReach = newLevel * 0.4;
                reachAttr.setBaseValue(VANILLA_SURVIVAL_REACH + bonusReach);
            }
        }

        player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1.2, 0), 3, 0.4, 0.2, 0.4, 0.05);
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 0.8f, 1.6f);
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        if (level < 3 || player == null || victim == null) return;

        // Milestone Lv.3 (Max): Đòn đâm tầm xa (Longinus Thrust)
        double distance = player.getLocation().distance(victim.getLocation());
        if (distance >= 3.6) {
            event.setDamage(event.getDamage() * 1.20);
            victim.getWorld().spawnParticle(Particle.SONIC_BOOM, victim.getLocation().add(0, 1.0, 0), 1);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.9f, 1.4f);
        }
    }

    @Override
    public void onReset(Player player, Arena arena) {
        if (player == null) return;
        if (AttributeUtils.ENTITY_INTERACTION_RANGE != null) {
            AttributeInstance reachAttr = player.getAttribute(AttributeUtils.ENTITY_INTERACTION_RANGE);
            if (reachAttr != null) {
                reachAttr.setBaseValue(VANILLA_SURVIVAL_REACH);
            }
        }
    }
}
