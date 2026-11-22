package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v8.4.3 DYNAMIC ENGINE]: Predator's Eye
 * Reads damage bonus, execute threshold, and haste duration from UpgradeData/upgrades.yml.
 */
public class PredatorEyeUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "PREDATOR_EYE";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena, UpgradeData data) {
        if (level <= 0 || player == null || victim == null) return;

        AttributeInstance maxHpAttr = victim.getAttribute(AttributeUtils.MAX_HEALTH);
        double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;

        double healthThreshold = (data != null) ? data.getDouble("health-threshold", 0.50) : 0.50;
        if (victim.getHealth() <= (maxHp * healthThreshold)) {
            double bonusPercent = (data != null) ? data.getValueAtLevel(level) : (15.0 + (level - 1) * 7.5);
            event.setDamage(event.getDamage() * (1.0 + bonusPercent / 100.0));

            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1.0, 0), 8, 0.2, 0.3, 0.2, 0.1);
        }
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        onAttack(event, player, victim, level, arena, null);
    }

    @Override
    public void onKill(Player player, LivingEntity victim, int level, Arena arena, UpgradeData data) {
        int hasteMilestone = (data != null) ? data.getInt("haste-milestone-level", 5) : 5;
        if (level < hasteMilestone || player == null || !player.isOnline()) return;

        AttributeInstance maxHpAttr = victim.getAttribute(AttributeUtils.MAX_HEALTH);
        double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;

        double healthThreshold = (data != null) ? data.getDouble("health-threshold", 0.50) : 0.50;
        if (victim.getHealth() <= (maxHp * healthThreshold)) {
            int durationTicks = (data != null) ? data.getInt("haste-duration-ticks", 80) : 80;
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, durationTicks, 1, false, false));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.8f, 1.6f);
        }
    }

    @Override
    public void onKill(Player player, LivingEntity victim, int level, Arena arena) {
        onKill(player, victim, level, arena, null);
    }
}
