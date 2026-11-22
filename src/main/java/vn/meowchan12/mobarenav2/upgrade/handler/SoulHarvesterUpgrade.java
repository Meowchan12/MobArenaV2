package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v7.5.0 REWORK]: Soul Harvester
 * Hồi máu khi tiêu diệt quái vật (Lv.1: +2 HP, Lv.5: +8 HP).
 * Cơ chế đánh Boss: Cứ 3 đòn đánh trúng Boss sẽ hồi phục 3.0 HP!
 * Milestone Lv.5: Tiêu diệt Boss hồi phục 100% Máu Tối Đa lập tức.
 */
public class SoulHarvesterUpgrade implements UpgradeHandler {

    private static final String KEY_BOSS_HITS = "ma_harvester_boss_hits";

    @Override
    public String getType() {
        return "HEAL_ON_KILL";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null || arena == null) return;

        if (victim != null && victim.hasMetadata("MA_BOSS")) {
            MobArenav2 plugin = (arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
            NamespacedKey hitKey = new NamespacedKey(plugin, KEY_BOSS_HITS);
            int hits = player.getPersistentDataContainer().getOrDefault(hitKey, PersistentDataType.INTEGER, 0) + 1;

            if (hits >= 3) {
                player.getPersistentDataContainer().set(hitKey, PersistentDataType.INTEGER, 0);
                AttributeInstance maxHpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
                double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
                player.setHealth(Math.min(player.getHealth() + 3.0, maxHp));
                player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SATURATION, 30, 0, false, false));
                player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.5, 0), 2);
            } else {
                player.getPersistentDataContainer().set(hitKey, PersistentDataType.INTEGER, hits);
            }
        }
    }

    @Override
    public void onKill(Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null || victim == null) return;

        double healOnKill = 2.0 + (level - 1) * 1.5;
        AttributeInstance maxHpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
        double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;

        MobArenav2 plugin = (arena != null && arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
        NamespacedKey bossTag = new NamespacedKey(plugin, "arena_boss_id");
        if (level >= 5 && victim.getPersistentDataContainer().has(bossTag, PersistentDataType.STRING)) {
            player.setHealth(maxHp);
            player.sendMessage("§d§l★ SOUL HARVESTER CAPSTONE: Boss slain, FULL HP RESTORED!");
        } else {
            player.setHealth(Math.min(player.getHealth() + healOnKill, maxHp));
        }
        player.setFoodLevel(Math.min(20, player.getFoodLevel() + 1));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SATURATION, 30, 0, false, false));
        player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.5, 0), 3);
    }

    @Override
    public void onReset(Player player, Arena arena) {
        if (player == null) return;
        MobArenav2 plugin = (arena != null && arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
        player.getPersistentDataContainer().remove(new NamespacedKey(plugin, KEY_BOSS_HITS));
    }
}
