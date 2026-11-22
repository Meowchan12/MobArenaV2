package vn.meowchan12.mobarenav2.upgrade.handler.ultimate;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v8.3.0 ULTIMATE UPGRADE]: Adamantine Colossus
 * - Fuses: Titan Heart (Max) + Iron Skin (Max)
 * - Permanently grants +40 Max Health in this arena match.
 * - 35% Flat Damage Mitigation against all incoming damage.
 * - 100% Knockback Resistance (Unmovable Fortress).
 */
public class UltimateAdamantineColossusUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "ULTIMATE_ADAMANTINE_COLOSSUS";
    }

    @Override
    public void onApply(Player player, int newLevel, vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData data, Arena arena) {
        if (player == null) return;

        // +40 Max HP
        AttributeInstance hpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(hpAttr.getBaseValue() + 40.0);
            player.setHealth(Math.min(hpAttr.getValue(), player.getHealth() + 40.0));
        }

        // 100% Knockback Resistance
        try {
            AttributeInstance kbAttr = player.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kbAttr != null) {
                kbAttr.setBaseValue(1.0); // Max 1.0 = 100% resistance
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onDefend(org.bukkit.event.entity.EntityDamageEvent event, Player player, LivingEntity attacker, int level, Arena arena) {
        if (level <= 0 || player == null) return;

        // 35% Flat Damage Mitigation
        event.setDamage(event.getDamage() * 0.65);
    }
}
