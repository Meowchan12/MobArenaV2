package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * [v8.4.3 DYNAMIC ENGINE]: Swift Wind
 * Reads movement speed scaling, fall damage immunity, and dodge chance from UpgradeData/upgrades.yml.
 */
public class SwiftWindUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "SPEED_BOOST";
    }

    @Override
    public void onApply(Player player, int newLevel, UpgradeData data, Arena arena) {
        if (player == null) return;
        AttributeInstance speedAttr = player.getAttribute(AttributeUtils.MOVEMENT_SPEED);
        if (speedAttr != null) {
            double bonusVal = (data != null) ? data.getValueAtLevel(newLevel) : (15.0 + (newLevel - 1) * 8.0);
            speedAttr.setBaseValue(0.1 + (bonusVal * 0.001));
        }

        // Milestone Lv.3: Cấp Jump Boost I và miễn nhiễm sát thương rơi
        int jumpMilestone = (data != null) ? data.getInt("jump-boost-milestone-level", 3) : 3;
        if (newLevel >= jumpMilestone) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 72000, 0, false, false, true));
        }
    }

    @Override
    public void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena, UpgradeData data) {
        if (player == null) return;

        int dodgeMilestone = (data != null) ? data.getInt("dodge-milestone-level", 3) : 3;
        if (level < dodgeMilestone) return;

        // Miễn nhiễm hoàn toàn sát thương rơi
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            boolean fallImmunity = (data != null) ? data.getBoolean("fall-damage-immunity", true) : true;
            if (fallImmunity) {
                event.setCancelled(true);
                return;
            }
        }

        // Milestone: Tỉ lệ Né Đòn (Dodge Chance)
        if (damager != null && event instanceof EntityDamageByEntityEvent) {
            double dodgeChance = (data != null) ? data.getDouble("dodge-chance", 12.0) : 12.0;
            if (ThreadLocalRandom.current().nextDouble(100.0) <= dodgeChance) {
                event.setCancelled(true);
                player.sendMessage("§b§l[SWIFT WIND] §fEvaded attack with gale speed! (+" + (int)dodgeChance + "% Dodge)");
                player.playSound(player.getLocation(), Sound.ENTITY_BREEZE_SHOOT, 1.0f, 2.0f);
                player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation().add(0, 0.5, 0), 8, 0.3, 0.2, 0.3, 0.05);
            }
        }
    }

    @Override
    public void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena) {
        onDefend(event, player, damager, level, arena, null);
    }

    @Override
    public void onReset(Player player, Arena arena) {
        if (player == null) return;
        AttributeInstance speedAttr = player.getAttribute(AttributeUtils.MOVEMENT_SPEED);
        if (speedAttr != null) {
            speedAttr.setBaseValue(0.1);
        }
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
    }
}
