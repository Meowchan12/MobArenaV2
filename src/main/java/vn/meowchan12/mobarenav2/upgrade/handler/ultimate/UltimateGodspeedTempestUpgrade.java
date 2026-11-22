package vn.meowchan12.mobarenav2.upgrade.handler.ultimate;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v8.3.0 ULTIMATE UPGRADE]: Godspeed Tempest
 * - Fuses: Swift Wind (Max) + Adrenaline Rush (Max)
 * - +60% Permanent Movement Speed boost.
 * - When Health drops below 40%: Triggers Godspeed Mode (Speed IV, Resistance II, and dodge-dash invulnerability).
 */
public class UltimateGodspeedTempestUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "ULTIMATE_GODSPEED_TEMPEST";
    }

    @Override
    public void onApply(Player player, int newLevel, vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData data, Arena arena) {
        if (player == null) return;
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(speed.getBaseValue() * 1.60);
        }
    }

    @Override
    public void onDefend(org.bukkit.event.entity.EntityDamageEvent event, Player player, LivingEntity attacker, int level, Arena arena) {
        if (level <= 0 || player == null) return;

        AttributeInstance maxHpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
        double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;

        if (player.getHealth() <= maxHp * 0.40) {
            // Godspeed Surge
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 3, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 100, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, 2, false, false));

            player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1.0, 0), 10, 0.4, 0.4, 0.4, 0.1);
            player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.0f, 1.5f);
        }
    }
}
