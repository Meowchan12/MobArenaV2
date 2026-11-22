package vn.meowchan12.mobarenav2.upgrade.handler.ultimate;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.upgrade.UpgradeRegistry;

/**
 * [v8.3.0 ULTIMATE UPGRADE]: Longinus Cleave
 * - Fuses: Extended Reach (Max) + Warrior's Might (Max)
 * - Grants +1.8m interaction attack reach (Paper generic.entity_interaction_range).
 * - Basic strikes unleash a 360-degree Sweeping Cleave in a 5m radius dealing 100% full weapon damage to all targets.
 */
public class UltimateLonginusCleaveUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "ULTIMATE_LONGINUS_CLEAVE";
    }

    @Override
    public void onApply(Player player, int newLevel, vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData data, Arena arena) {
        if (player == null) return;
        try {
            Attribute reachAttr = Attribute.valueOf("ENTITY_INTERACTION_RANGE");
            AttributeInstance instance = player.getAttribute(reachAttr);
            if (instance != null) {
                // Base 3.0 + 1.8 = 4.8m interaction reach
                instance.setBaseValue(4.8);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null || victim == null || arena == null) return;
        if (UpgradeRegistry.isDispatchingAny()) return; // Anti-recursion shield

        // +50% Warrior's Might Damage Boost
        double baseDmg = event.getDamage();
        double boostedDmg = baseDmg * 1.50;
        event.setDamage(boostedDmg);

        // 360-degree Sweeping Cleave (5m radius)
        Location center = victim.getLocation();
        center.getWorld().spawnParticle(Particle.SWEEP_ATTACK, center.add(0, 1.0, 0), 4, 1.5, 0.2, 1.5, 0);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.9f);

        for (LivingEntity nearby : center.getWorld().getNearbyLivingEntities(center, 5.0)) {
            if (nearby.equals(victim) || nearby instanceof Player) continue;
            if (arena.getActiveMobs().contains(nearby) || nearby.hasMetadata("MA_MOB")) {
                nearby.damage(boostedDmg, player);
                nearby.getWorld().spawnParticle(Particle.CRIT, nearby.getLocation().add(0, 1.0, 0), 5, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }
}
