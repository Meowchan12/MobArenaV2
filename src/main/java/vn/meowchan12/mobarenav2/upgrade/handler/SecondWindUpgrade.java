package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v7.3.1 HOTFIX]: Second Wind
 * Khi máu dưới 25%, kích hoạt lá chắn hồi phục và kháng cự trong 6 giây (Cooldown 60s).
 * Milestone Lv.3: Sóng xung kích hất tung toàn bộ quái vật xung quanh.
 */
public class SecondWindUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "SECOND_WIND";
    }

    @Override
    public void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena) {
        if (level <= 0 || player == null) return;

        AttributeInstance maxHpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
        double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
        double predictedHp = player.getHealth() - event.getFinalDamage();

        if (predictedHp > 0 && predictedHp <= maxHp * 0.25) {
            MobArenav2 plugin = (arena != null && arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
            NamespacedKey cdKey = new NamespacedKey(plugin, "ma_second_wind_cd");
            Long lastUsed = player.getPersistentDataContainer().get(cdKey, PersistentDataType.LONG);
            if (lastUsed == null || System.currentTimeMillis() - lastUsed >= 60000L) {
                player.getPersistentDataContainer().set(cdKey, PersistentDataType.LONG, System.currentTimeMillis());
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 1, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1, false, true));
                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.2f);
                player.sendMessage("§0§l[SECOND WIND] §bEmergency shield activated! (60s Cooldown)");

                if (level >= 3) {
                    for (Entity near : player.getNearbyEntities(4, 4, 4)) {
                        if (near instanceof LivingEntity mobTarget && mobTarget.hasMetadata("MA_MOB")) {
                            Vector push = mobTarget.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.5).setY(0.5);
                            mobTarget.setVelocity(push);
                        }
                    }
                    player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 1);
                }
            }
        }
    }

    @Override
    public void onReset(Player player, Arena arena) {
        if (player == null) return;
        MobArenav2 plugin = (arena != null && arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
        NamespacedKey cdKey = new NamespacedKey(plugin, "ma_second_wind_cd");
        player.getPersistentDataContainer().remove(cdKey);
    }
}
