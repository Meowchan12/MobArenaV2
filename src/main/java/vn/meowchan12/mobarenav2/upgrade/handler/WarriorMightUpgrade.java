package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

/**
 * [v8.4.3 DYNAMIC ENGINE]: Warrior's Might
 * Reads damage scaling, cleave radius, cleave ratio, and milestone from UpgradeData/upgrades.yml.
 */
public class WarriorMightUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "DAMAGE_MULTIPLIER";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena, UpgradeData data) {
        if (level <= 0) return;

        double bonusPercent = (data != null) ? data.getValueAtLevel(level) : (20.0 + (level - 1) * 10.0);
        double multiplier = 1.0 + (bonusPercent / 100.0);
        event.setDamage(event.getDamage() * multiplier);

        // Milestone: Shockwave Cleave
        int cleaveMilestone = (data != null) ? data.getInt("cleave-milestone-level", 5) : 5;
        if (level >= cleaveMilestone) {
            double cleavePercent = (data != null) ? data.getDouble("cleave-percent", 45.0) : 45.0;
            double cleaveRadius = (data != null) ? data.getDouble("cleave-radius", 4.5) : 4.5;
            double cleaveDmg = event.getDamage() * (cleavePercent / 100.0);
            if (cleaveDmg > 0.0) {
                for (Entity near : victim.getNearbyEntities(cleaveRadius, cleaveRadius, cleaveRadius)) {
                    if (near instanceof LivingEntity mobNear && mobNear.hasMetadata("MA_MOB") && !mobNear.equals(victim) && !mobNear.isDead()) {
                        mobNear.damage(cleaveDmg, player);
                        mobNear.getWorld().spawnParticle(Particle.SWEEP_ATTACK, mobNear.getLocation().add(0, 1, 0), 1);
                    }
                }
            }
        }
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        onAttack(event, player, victim, level, arena, null);
    }
}
