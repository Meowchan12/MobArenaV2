package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

/**
 * [v8.4.3 DYNAMIC ENGINE]: Slaughterhouse Engine (SUPER_KILL_SCALING)
 * Reads scaling per kill, boss hit threshold, and damage cap from UpgradeData/upgrades.yml.
 */
public class SlaughterhouseEngineUpgrade implements UpgradeHandler {

    private static final String KEY_BOSS_HITS = "ma_slaughter_boss_hits";
    private static final String KEY_BOSS_BONUS = "ma_slaughter_boss_bonus";

    private final NamespacedKey bonusKey;
    private final NamespacedKey hitKey;

    public SlaughterhouseEngineUpgrade() {
        MobArenav2 plugin = MobArenav2.getInstance();
        this.bonusKey = new NamespacedKey(plugin, KEY_BOSS_BONUS);
        this.hitKey = new NamespacedKey(plugin, KEY_BOSS_HITS);
    }

    @Override
    public String getType() {
        return "SUPER_KILL_SCALING";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena, UpgradeData data) {
        if (level <= 0 || player == null || arena == null) return;

        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
        if (profile == null) return;

        // 1. Tích điểm khi đánh Boss
        int reqHits = (data != null) ? data.getInt("boss-hits-per-percent", 20) : 20;
        int bossBonus;
        if (victim != null && (victim.hasMetadata("MA_BOSS") || victim.hasMetadata("BOSS"))) {
            int hits = player.getPersistentDataContainer().getOrDefault(hitKey, PersistentDataType.INTEGER, 0) + 1;
            int currentBonus = player.getPersistentDataContainer().getOrDefault(bonusKey, PersistentDataType.INTEGER, 0);

            if (hits >= reqHits) {
                player.getPersistentDataContainer().set(hitKey, PersistentDataType.INTEGER, 0);
                currentBonus++;
                player.getPersistentDataContainer().set(bonusKey, PersistentDataType.INTEGER, currentBonus);
                player.sendMessage("§4§l[SLAUGHTERHOUSE] §cBoss blood consumed! +1% Permanent DMG (Total: +" + (profile.getKills() + currentBonus) + "%)");
                player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 0.6f, 1.8f);
            } else {
                player.getPersistentDataContainer().set(hitKey, PersistentDataType.INTEGER, hits);
            }
            bossBonus = currentBonus;
        } else {
            bossBonus = player.getPersistentDataContainer().getOrDefault(bonusKey, PersistentDataType.INTEGER, 0);
        }

        // 2. Tính toán tổng % tăng sát thương
        double perKill = (data != null) ? data.getDouble("percent-per-kill", 1.0) : 1.0;
        double totalPercent = (profile.getKills() * perKill) + bossBonus;
        double maxCap = (data != null) ? data.getDouble("max-damage-cap-percent", 0.0) : 0.0;
        if (maxCap > 0.0) {
            totalPercent = Math.min(maxCap, totalPercent);
        }

        if (totalPercent <= 0) return;

        double multiplier = 1.0 + (totalPercent / 100.0);
        double finalDamage = event.getDamage() * multiplier;

        event.setDamage(finalDamage);

        // 3. Hiệu ứng hạt tiến hóa theo cột mốc sát thương
        if (totalPercent >= 1000) {
            spawnSafeParticle(victim, Particle.DRAGON_BREATH, 10, 0.3, 0.5, 0.3, 0.05);
        } else if (totalPercent >= 500) {
            spawnSafeParticle(victim, Particle.FLAME, 8, 0.3, 0.4, 0.3, 0.05);
        }
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        onAttack(event, player, victim, level, arena, null);
    }

    private void spawnSafeParticle(LivingEntity target, Particle particle, int count, double ox, double oy, double oz, double speed) {
        if (target == null || target.getWorld() == null) return;
        try {
            if (particle.getDataType() == Void.class) {
                target.getWorld().spawnParticle(particle, target.getLocation().add(0, 1, 0), count, ox, oy, oz, speed);
            } else if (particle.getDataType() == Float.class) {
                target.getWorld().spawnParticle(particle, target.getLocation().add(0, 1, 0), count, ox, oy, oz, speed, 1.0f);
            } else {
                target.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0, 1, 0), count, ox, oy, oz, speed);
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void onKill(Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null || arena == null) return;
        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
        if (profile != null) {
            profile.addSlaughterhouseKill();
        }
    }

    @Override
    public void onReset(Player player, Arena arena) {
        if (player == null) return;
        player.getPersistentDataContainer().remove(hitKey);
        player.getPersistentDataContainer().remove(bonusKey);
    }
}
