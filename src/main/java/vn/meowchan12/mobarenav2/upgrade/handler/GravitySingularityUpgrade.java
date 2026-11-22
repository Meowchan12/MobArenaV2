package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [v8.2.0 NEW UPGRADE]: Gravitational Singularity (CROWD_CONTROL_VORTEX)
 * - Lối chơi: Khống chế gom cụm quái vật (Vortex Grouping).
 * - Mỗi 12s - 8s, đòn đánh kích hoạt một hố đen trọng lực tại vị trí quái vật.
 * - Hút toàn bộ quái vật trong bán kính 5.5m gom về tâm trong 1.5 giây.
 * - Tier Milestone (Lv.5): Hố đen sụp đổ phát nổ gây 30.0 AoE Damage và tăng 20% sát thương nhận vào cho quái bị hút trong 3s!
 */
public class GravitySingularityUpgrade implements UpgradeHandler {

    private final MobArenav2 plugin;
    private final Map<UUID, Long> cooldowns = new ConcurrentHashMap<>();

    public GravitySingularityUpgrade(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getType() {
        return "CROWD_CONTROL_VORTEX";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null || victim == null) return;

        long now = System.currentTimeMillis();
        long cd = Math.max(8000L, (13 - level) * 1000L); // Lv.1: 12s, Lv.5: 8s
        long lastUsed = cooldowns.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastUsed < cd) return;

        cooldowns.put(player.getUniqueId(), now);

        Location center = victim.getLocation().clone();
        player.playSound(center, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.0f, 1.5f);

        // Kích hoạt lốc xoáy trọng trường trong 1.5s (30 ticks, tick mỗi 3 ticks)
        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                if (arena == null || !arena.isRunning() || ticks >= 30) {
                    // Milestone Lv.5: Sụp đổ hố đen phát nổ
                    if (level >= 5 && center.getWorld() != null) {
                        center.getWorld().spawnParticle(Particle.EXPLOSION, center.clone().add(0, 1.0, 0), 2);
                        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);

                        for (Entity near : center.getWorld().getNearbyEntities(center, 5.5, 3.0, 5.5)) {
                            if (near instanceof LivingEntity mob && mob.hasMetadata("MA_MOB") && !mob.isDead()) {
                                mob.damage(30.0, player);
                                mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1));
                            }
                        }
                    }
                    this.cancel();
                    return;
                }

                // Hút quái vật trong bán kính 5.5m về tâm
                if (center.getWorld() != null) {
                    center.getWorld().spawnParticle(Particle.PORTAL, center.clone().add(0, 1.0, 0), 25, 0.5, 0.5, 0.5, 0.1);
                    center.getWorld().spawnParticle(Particle.REVERSE_PORTAL, center.clone().add(0, 1.0, 0), 10, 0.3, 0.3, 0.3, 0.05);

                    for (Entity near : center.getWorld().getNearbyEntities(center, 5.5, 3.0, 5.5)) {
                        if (near instanceof LivingEntity mob && mob.hasMetadata("MA_MOB") && !mob.isDead()) {
                            Vector pullDir = center.toVector().subtract(mob.getLocation().toVector());
                            if (pullDir.lengthSquared() > 0.5) {
                                mob.setVelocity(pullDir.normalize().multiply(0.35));
                            }
                        }
                    }
                }
                ticks += 3;
            }
        }.runTaskTimer(plugin, 1L, 3L);
    }

    @Override
    public void onReset(Player player, Arena arena) {
        if (player != null) {
            cooldowns.remove(player.getUniqueId());
        }
    }
}
