package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

/**
 * [v7.5.0 REWORK]: Bloodlust Spirit
 * Tích lũy tốc độ di chuyển và tốc độ đánh trong 5 giây sau mỗi mạng hạ gục.
 * Cơ chế đánh Boss: Cứ 3 đòn đánh trúng Boss kích hoạt ngay 1 stack buff Bloodlust!
 * Milestone Lv.5 (Hellfire Eruption): Gây 12.0 sát thương nổ AoE + Thiêu đốt 4 giây lên quái xung quanh.
 */
public class BloodlustUpgrade implements UpgradeHandler {

    private static final String KEY_BOSS_HITS = "ma_bloodlust_boss_hits";

    @Override
    public String getType() {
        return "BLOODLUST";
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
                applyBloodlustBuff(player, level);
                player.getWorld().spawnParticle(Particle.FLAME, player.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
            } else {
                player.getPersistentDataContainer().set(hitKey, PersistentDataType.INTEGER, hits);
            }
        }
    }

    @Override
    public void onKill(Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null) return;

        applyBloodlustBuff(player, level);

        // Milestone Lv.5: Nổ hoả ngục gây sát thương thật
        if (level >= 5 && victim != null) {
            victim.getWorld().spawnParticle(Particle.FLAME, victim.getLocation().add(0, 1, 0), 30, 0.6, 0.6, 0.6, 0.1);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.0f, 1.2f);

            for (Entity near : victim.getNearbyEntities(3.5, 3.5, 3.5)) {
                if (near instanceof LivingEntity mobNear && mobNear.hasMetadata("MA_MOB") && !mobNear.equals(victim)) {
                    mobNear.damage(12.0, player);
                    mobNear.setFireTicks(Math.max(mobNear.getFireTicks(), 80)); // 4 giây cháy
                }
            }
        }
    }

    private void applyBloodlustBuff(Player player, int level) {
        int amp = Math.min(2, level - 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, amp, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, amp, false, false));
        player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 2.0f);
    }

    @Override
    public void onReset(Player player, Arena arena) {
        if (player == null) return;
        MobArenav2 plugin = (arena != null && arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
        player.getPersistentDataContainer().remove(new NamespacedKey(plugin, KEY_BOSS_HITS));
    }
}
