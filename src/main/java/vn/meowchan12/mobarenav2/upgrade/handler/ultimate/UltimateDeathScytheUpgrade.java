package vn.meowchan12.mobarenav2.upgrade.handler.ultimate;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.service.CombatDamageService;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * [v8.3.0 ULTIMATE UPGRADE]: Death Scythe
 * - Fuses: Super Executioner (Max) + Critical Strike (Max)
 * - Instantly executes any mob or Boss below 20% Max HP.
 * - Critical strikes deal 350% total damage and unleash a Death Nova explosion.
 */
public class UltimateDeathScytheUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "ULTIMATE_DEATH_SCYTHE";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null || victim == null || arena == null) return;

        AttributeInstance maxHpAttr = victim.getAttribute(AttributeUtils.MAX_HEALTH);
        double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
        double currentHp = victim.getHealth();

        // 1. 20% HP Instant Execution (Works on Mobs AND Bosses)
        if (currentHp <= maxHp * 0.20) {
            event.setDamage(99999.0);
            Location loc = victim.getLocation();
            loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.2f, 1.8f);
            loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc.add(0, 1.0, 0), 2);
            return;
        }

        // 2. 50% Chance Critical Strike: 350% Total Damage + Death Nova
        if (ThreadLocalRandom.current().nextDouble() <= 0.50) {
            double critDmg = event.getDamage() * 3.50;
            event.setDamage(critDmg);

            Location vLoc = victim.getLocation();
            vLoc.getWorld().spawnParticle(Particle.CRIT, vLoc.add(0, 1.2, 0), 25, 0.5, 0.5, 0.5, 0.2);
            vLoc.getWorld().playSound(vLoc, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.4f, 0.8f);

            // Death Nova AoE damage in 4m
            for (LivingEntity nearby : vLoc.getWorld().getNearbyLivingEntities(vLoc, 4.0)) {
                if (nearby.equals(victim) || nearby instanceof Player) continue;
                if (arena.getActiveMobs().contains(nearby) || nearby.hasMetadata("MA_MOB")) {
                    nearby.damage(critDmg * 0.40, player);
                }
            }
        }
    }
}
