package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v7.5.0 REWORK]: Frenzy Strikes (Adrenaline Rush)
 * Tăng thực tế GENERIC_ATTACK_SPEED (+15% Lv.1, +30% Lv.2, +50% Lv.3).
 * Milestone Lv.3: Đánh trúng 5 đòn liên tiếp kích hoạt Cuồng Phong: Nhận Speed II trong 3s!
 */
public class AdrenalineRushUpgrade implements UpgradeHandler {

    private static final String KEY_COMBO = "ma_frenzy_combo";
    private static final String KEY_LAST_HIT = "ma_frenzy_last_hit";

    @Override
    public String getType() {
        return "HASTE_EFFECT";
    }

    @Override
    public void onApply(Player player, int newLevel, UpgradeData data, Arena arena) {
        if (player == null) return;
        AttributeInstance atkSpeedAttr = player.getAttribute(AttributeUtils.ATTACK_SPEED);
        if (atkSpeedAttr != null) {
            // Vanilla base: 4.0 (Lv.1: 4.6, Lv.2: 5.2, Lv.3: 6.0)
            double bonusSpeed = switch (newLevel) {
                case 1 -> 0.6;
                case 2 -> 1.2;
                default -> 2.0;
            };
            atkSpeedAttr.setBaseValue(4.0 + bonusSpeed);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.8f);
        }
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        if (level < 3 || player == null || arena == null) return;

        MobArenav2 plugin = (arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
        NamespacedKey comboKey = new NamespacedKey(plugin, KEY_COMBO);
        NamespacedKey timeKey = new NamespacedKey(plugin, KEY_LAST_HIT);

        long now = System.currentTimeMillis();
        long lastHit = player.getPersistentDataContainer().getOrDefault(timeKey, PersistentDataType.LONG, 0L);
        int combo = (now - lastHit <= 3000L) ? player.getPersistentDataContainer().getOrDefault(comboKey, PersistentDataType.INTEGER, 0) + 1 : 1;

        player.getPersistentDataContainer().set(timeKey, PersistentDataType.LONG, now);

        if (combo >= 5) {
            player.getPersistentDataContainer().set(comboKey, PersistentDataType.INTEGER, 0);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, false, true));
            player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 0.8f, 1.5f);
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, player.getLocation().add(0, 1, 0), 2);
        } else {
            player.getPersistentDataContainer().set(comboKey, PersistentDataType.INTEGER, combo);
        }
    }

    @Override
    public void onReset(Player player, Arena arena) {
        if (player == null) return;
        AttributeInstance atkSpeedAttr = player.getAttribute(AttributeUtils.ATTACK_SPEED);
        if (atkSpeedAttr != null) {
            atkSpeedAttr.setBaseValue(4.0);
        }
        MobArenav2 plugin = (arena != null && arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
        player.getPersistentDataContainer().remove(new NamespacedKey(plugin, KEY_COMBO));
        player.getPersistentDataContainer().remove(new NamespacedKey(plugin, KEY_LAST_HIT));
    }
}
