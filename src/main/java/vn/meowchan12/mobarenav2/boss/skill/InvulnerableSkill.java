package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

/**
 * Bất tử tuyệt đối trong thời gian ngắn (God Mode).
 */
public class InvulnerableSkill implements BossSkill {

    @Override
    public String getType() {
        return "INVULNERABLE";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        Location bossLoc = boss.getLocation();
        int duration = config.getInt("duration", 5);
        long expiry = System.currentTimeMillis() + (duration * 1000L);

        NamespacedKey godKey = new NamespacedKey(plugin, "ma_invulnerable");
        boss.getPersistentDataContainer().set(godKey, PersistentDataType.LONG, expiry);

        boss.setGlowing(true);
        bossLoc.getWorld().playSound(bossLoc, Sound.ITEM_SHIELD_BLOCK, 2.0f, 0.5f);
        bossLoc.getWorld().playSound(bossLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.5f);
        bossLoc.getWorld().spawnParticle(Particle.END_ROD, bossLoc.clone().add(0, 1, 0), 50, 0.8, 1.2, 0.8, 0.1);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (boss.isValid() && !boss.isDead()) {
                boss.setGlowing(false);
                boss.getPersistentDataContainer().remove(godKey);
                boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 1.0f);
            }
        }, duration * 20L);
    }
}
