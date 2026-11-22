package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * Hồi phục 20 HP lập tức khi mua.
 */
public class InstantHealUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "HEAL";
    }

    @Override
    public void onApply(Player player, int newLevel, UpgradeData data, Arena arena) {
        AttributeInstance hpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
        double maxHp = (hpAttr != null) ? hpAttr.getValue() : 20.0;
        player.setHealth(Math.min(player.getHealth() + data.baseValue, maxHp));

        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.5, 0), 10, 0.5, 0.5, 0.5, 0);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
    }
}
