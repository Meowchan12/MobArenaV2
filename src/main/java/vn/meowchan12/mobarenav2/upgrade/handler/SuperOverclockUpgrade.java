package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v7.3 REWORK]: Overclocked Core (SUPER_OVERCLOCK)
 * Khắc phục hoàn toàn lỗi Placebo (trước đây chỉ áp Haste potion không xóa được nạp chiêu của vũ khí).
 * Đặt GENERIC_ATTACK_SPEED = 100.0, xóa bỏ 100% thời gian hồi chiêu của vũ khí 1.21+ (Spam Click như Minecraft 1.8).
 * Tự động hoàn trả 4.0 khi kết thúc trận đấu hoặc người chơi chết/rời arena.
 */
public class SuperOverclockUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "SUPER_OVERCLOCK";
    }

    @Override
    public void onApply(Player player, int newLevel, UpgradeData data, Arena arena) {
        if (player == null) return;
        AttributeInstance atkSpeedAttr = player.getAttribute(AttributeUtils.ATTACK_SPEED);
        if (atkSpeedAttr != null) {
            double speedVal = (data != null) ? data.getDouble("attack-speed", data.getDouble("attack-speed-boost", 100.0)) : 100.0;
            atkSpeedAttr.setBaseValue(speedVal);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.8f);
            player.sendMessage("§b§l[OVERCLOCKED CORE] §fWeapon cooldown removed! Infinite swing frequency!");
        }
    }

    @Override
    public void onReset(Player player, Arena arena) {
        if (player == null) return;
        AttributeInstance atkSpeedAttr = player.getAttribute(AttributeUtils.ATTACK_SPEED);
        if (atkSpeedAttr != null) {
            atkSpeedAttr.setBaseValue(4.0); // Vanilla default
        }
    }
}
