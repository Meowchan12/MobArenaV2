package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

/**
 * [v7.3 MODULAR]: PotionEffectUpgrade
 * Xử lý các loại nâng cấp tùy biến dạng Potion Effect từ cấu hình YAML.
 */
public class PotionEffectUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "POTION_EFFECT";
    }

    @Override
    public void onApply(Player player, int newLevel, UpgradeData data, Arena arena) {
        if (player == null || data == null || data.effectType == null) return;

        NamespacedKey pKey = NamespacedKey.minecraft(data.effectType.toLowerCase());
        PotionEffectType pType = Registry.POTION_EFFECT_TYPE.get(pKey);
        if (pType != null) {
            int duration = data.duration > 0 ? data.duration : 72000;
            int amp = Math.max(0, data.amplifier + (newLevel - 1));
            player.addPotionEffect(new PotionEffect(pType, duration, amp, false, false, true));
        }
    }

    @Override
    public void onReset(Player player, Arena arena) {
        // Tự động xóa sạch bởi arena reset
    }
}
