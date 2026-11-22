package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v7.3 REWORK]: Titan Heart
 * Tăng máu tối đa vĩnh viễn trong trận (+4 HP Lv.1, +3 HP mỗi cấp sau).
 * Milestone Lv.5: Luôn tự động cấp +4 tim vàng (Absorption) ở đầu MỖI Wave mới.
 */
public class TitanHeartUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "MAX_HEALTH";
    }

    @Override
    public void onApply(Player player, int newLevel, UpgradeData data, Arena arena) {
        AttributeInstance hpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
        if (hpAttr != null) {
            double add = (newLevel == 1) ? data.baseValue : data.increment;
            hpAttr.setBaseValue(hpAttr.getBaseValue() + add);
            player.setHealth(Math.min(player.getHealth() + add, hpAttr.getValue()));
        }
    }

    @Override
    public void onWaveStart(Player player, int level, Arena arena, UpgradeData data) {
        int milestone = (data != null) ? data.getInt("absorption-milestone-level", 5) : 5;
        if (level >= milestone) {
            double absAmount = (data != null) ? data.getDouble("wave-absorption", 4.0) : 4.0;
            player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), absAmount));
        }
    }

    @Override
    public void onWaveStart(Player player, int level, Arena arena) {
        onWaveStart(player, level, arena, null);
    }

    @Override
    public void onReset(Player player, Arena arena) {
        AttributeInstance hpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(20.0);
        }
    }
}
