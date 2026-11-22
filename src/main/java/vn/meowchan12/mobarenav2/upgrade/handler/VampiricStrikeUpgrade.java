package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * [v8.4.3 DYNAMIC ENGINE]: Vampiric Strike
 * Reads activation chance, lifesteal ratio, and overheal absorption cap from UpgradeData/upgrades.yml.
 */
public class VampiricStrikeUpgrade implements UpgradeHandler {

    @Override
    public String getType() {
        return "LIFESTEAL";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena, UpgradeData data) {
        if (level <= 0) return;

        double shopLifesteal = 0.0;
        if (arena != null && arena.getPlugin() != null && arena.getPlugin().getShopManager() != null) {
            shopLifesteal = arena.getPlugin().getShopManager().getCustomStatBonus(player.getUniqueId(), "CUSTOM_LIFESTEAL_BOOST");
        }

        // Tỉ lệ kích hoạt đọc từ config
        double chance = ((data != null) ? data.getValueAtLevel(level) : (8.0 + (level - 1) * 2.5)) + shopLifesteal;
        if (ThreadLocalRandom.current().nextDouble(100.0) <= chance) {
            AttributeInstance maxHpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
            double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;

            double lifestealRatio = (data != null) ? data.getDouble("lifesteal-ratio", 0.08 + (level - 1) * 0.02) : (0.08 + (level - 1) * 0.02);
            double healAmount = Math.max(1.0, event.getDamage() * lifestealRatio);
            if (player.hasMetadata("MA_MORTAL_WOUND")) {
                healAmount *= 0.20; // Mortal Wound: -80% healing received
            }
            double currentHp = player.getHealth();

            if (currentHp + healAmount <= maxHp) {
                player.setHealth(currentHp + healAmount);
            } else {
                player.setHealth(maxHp);
                int overhealMilestone = (data != null) ? data.getInt("overheal-milestone-level", 3) : 3;
                if (level >= overhealMilestone) {
                    double maxAbsorption = (data != null) ? data.getDouble("max-absorption-shield", 10.0) : 10.0;
                    double excess = (currentHp + healAmount) - maxHp;
                    double currentAbs = player.getAbsorptionAmount();
                    player.setAbsorptionAmount(Math.min(maxAbsorption, currentAbs + excess));
                }
            }
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 2, 0), 4, 0.3, 0.3, 0.3, 0);
            player.playSound(player.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.5f, 1.8f);
        }
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        onAttack(event, player, victim, level, arena, null);
    }
}
