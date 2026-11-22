package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

/**
 * [v8.4.3 DYNAMIC ENGINE]: Iron Skin
 * Reads physical damage reduction and knockback resist from UpgradeData/upgrades.yml.
 */
public class IronSkinUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "DAMAGE_REDUCTION";
    }

    @Override
    public void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena, UpgradeData data) {
        if (level <= 0 || player == null) return;

        double reductionPercent = (data != null) ? data.getValueAtLevel(level) : (8.0 + (level - 1) * 6.0);
        double minFactor = (data != null) ? data.getDouble("min-damage-factor", 0.30) : 0.30;
        double factor = Math.max(minFactor, 1.0 - (reductionPercent / 100.0));
        event.setDamage(event.getDamage() * factor);

        int knockbackMilestone = (data != null) ? data.getInt("knockback-milestone-level", 4) : 4;
        if (level >= knockbackMilestone) {
            double kbMultiplier = (data != null) ? data.getDouble("knockback-multiplier", 0.5) : 0.5;
            MobArenav2 plugin = (arena != null && arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.setVelocity(player.getVelocity().multiply(kbMultiplier));
                }
            });
        }
    }

    @Override
    public void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena) {
        onDefend(event, player, damager, level, arena, null);
    }
}
