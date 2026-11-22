package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v8.4.3 DYNAMIC ENGINE]: Berserker's Pact
 * Reads outgoing bonus, incoming damage penalty, and low HP frenzy threshold from UpgradeData/upgrades.yml.
 */
public class BerserkersPactUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "GLASS_CANNON";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena, UpgradeData data) {
        if (level <= 0) return;

        double bonusPercent = (data != null) ? data.getValueAtLevel(level) : (25.0 + (level - 1) * 20.0);
        AttributeInstance maxHpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
        double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;

        // Milestone Lv.3: Máu dưới 35% kích hoạt cuồng bạo
        int frenzyMilestone = (data != null) ? data.getInt("frenzy-milestone-level", 3) : 3;
        double frenzyThreshold = (data != null) ? data.getDouble("frenzy-hp-threshold", 0.35) : 0.35;
        if (level >= frenzyMilestone && player.getHealth() <= maxHp * frenzyThreshold) {
            double frenzyBonus = (data != null) ? data.getDouble("frenzy-bonus-percent", 35.0) : 35.0;
            bonusPercent += frenzyBonus;
            player.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, player.getLocation().add(0, 1.5, 0), 2);
        }

        event.setDamage(event.getDamage() * (1.0 + bonusPercent / 100.0));
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        onAttack(event, player, victim, level, arena, null);
    }

    @Override
    public void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena, UpgradeData data) {
        if (level <= 0) return;
        double penalty = (data != null) ? data.getDouble("damage-penalty-percent", 15.0) : 15.0;
        event.setDamage(event.getDamage() * (1.0 + penalty / 100.0));
    }

    @Override
    public void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena) {
        onDefend(event, player, damager, level, arena, null);
    }
}
