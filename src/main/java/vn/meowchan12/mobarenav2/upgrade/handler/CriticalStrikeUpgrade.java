package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

import java.util.concurrent.ThreadLocalRandom;

/**
 * [v8.4.3 DYNAMIC ENGINE]: Critical Strike
 * Reads crit chance, base crit multiplier, and mega crit multiplier from UpgradeData/upgrades.yml.
 */
public class CriticalStrikeUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "CRITICAL_CHANCE";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena, UpgradeData data) {
        if (level <= 0) return;

        double shopCrit = 0.0;
        if (arena != null && arena.getPlugin() != null && arena.getPlugin().getShopManager() != null) {
            shopCrit = arena.getPlugin().getShopManager().getCustomStatBonus(player.getUniqueId(), "CUSTOM_CRIT_BOOST");
        }

        double critChance = ((data != null) ? data.getValueAtLevel(level) : (12.0 + (level - 1) * 6.0)) + shopCrit;
        if (ThreadLocalRandom.current().nextDouble(100.0) <= critChance) {
            int megaCritMilestone = (data != null) ? data.getInt("mega-crit-milestone-level", 5) : 5;
            double normalCrit = (data != null) ? data.getDouble("crit-multiplier", 2.0) : 2.0;
            double megaCrit = (data != null) ? data.getDouble("mega-crit-multiplier", 3.0) : 3.0;

            double multiplier = (level >= megaCritMilestone) ? megaCrit : normalCrit;
            event.setDamage(event.getDamage() * multiplier);

            if (level >= megaCritMilestone) {
                player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
                victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 40, 0.4, 0.4, 0.4, 0.2);
            } else {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
                victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.1);
            }
        }
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        onAttack(event, player, victim, level, arena, null);
    }
}
