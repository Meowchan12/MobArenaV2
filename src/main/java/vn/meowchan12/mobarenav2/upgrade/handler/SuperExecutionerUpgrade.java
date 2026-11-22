package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v8.4.3 DYNAMIC ENGINE]: Executioner's Scythe (SUPER_EXECUTE)
 * Reads execute threshold, explosion damage, and radius from UpgradeData/upgrades.yml.
 */
public class SuperExecutionerUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "SUPER_EXECUTE";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena, UpgradeData data) {
        if (level <= 0 || victim == null || victim.isDead()) return;

        AttributeInstance maxHpAttr = victim.getAttribute(AttributeUtils.MAX_HEALTH);
        double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : victim.getMaxHealth();
        double remainingHp = victim.getHealth() - event.getFinalDamage();

        double executeThreshold = (data != null) ? data.getDouble("execute-threshold", 0.15) : 0.15;
        if (executeThreshold > 1.0) executeThreshold /= 100.0;
        if (remainingHp > 0 && remainingHp <= maxHp * executeThreshold) {
            boolean isBoss = victim.hasMetadata("MA_BOSS");

            // Sát thương dứt điểm tuyệt đối
            event.setDamage(victim.getHealth() + 500.0);

            if (isBoss) {
                // Hiệu ứng sấm sét & âm thanh tử thần khi trảm Boss
                victim.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, victim.getLocation().add(0, 1.5, 0), 2);
                victim.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, victim.getLocation().add(0, 1.0, 0), 50, 0.8, 1.0, 0.8, 0.1);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WITHER_DEATH, 1.0f, 1.0f);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.2f);
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);

                String bossName = victim.getCustomName() != null ? victim.getCustomName() : "the Boss";
                if (arena != null) {
                    arena.broadcastMessage("§4§l☠ BOSS EXECUTED! §f" + player.getName() + " §7has executed §c" + bossName + " §7with §4Executioner's Scythe§7!");
                }
            } else {
                // Hiệu ứng nổ & âm thanh trảm quyết quái thường
                victim.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, victim.getLocation().add(0, 1, 0), 1);
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.2f);
                player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 0.7f, 1.8f);

                double explosionDmg = (data != null) ? data.getDouble("aoe-damage", data.getDouble("explosion-damage", 10.0)) : 10.0;
                double aoeRadius = (data != null) ? data.getDouble("aoe-radius", 4.0) : 4.0;
                for (Entity near : victim.getNearbyEntities(aoeRadius, aoeRadius, aoeRadius)) {
                    if (near instanceof LivingEntity mobNear && mobNear.hasMetadata("MA_MOB") && !mobNear.equals(victim)) {
                        mobNear.damage(explosionDmg, player);
                        mobNear.getWorld().spawnParticle(Particle.FLAME, mobNear.getLocation().add(0, 1, 0), 5, 0.2, 0.2, 0.2, 0.05);
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
