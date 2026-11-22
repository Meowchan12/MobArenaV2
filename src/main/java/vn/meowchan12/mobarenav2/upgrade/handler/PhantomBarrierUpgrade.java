package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [v7.5.0 BUFF]: Phantom Barrier
 * Cấp khiên vàng (Absorption) tự động ở đầu MỖI Wave mới (Lv.1: 6 HP -> Lv.4: 15 HP).
 * [Cơ chế mới]: Tự động tái tạo 50% Khiên Vàng mỗi 30 giây chiến đấu nếu khiên bị vỡ!
 * Milestone Lv.4: Giảm và phản xạ 30% sát thương đạn bay (tên, cầu lửa Blaze) về phía kẻ bắn.
 */
public class PhantomBarrierUpgrade implements UpgradeHandler {

    private final Map<UUID, BukkitTask> rechargeTasks = new ConcurrentHashMap<>();

    @Override
    public String getType() {
        return "WAVE_SHIELD";
    }

    @Override
    public void onApply(Player player, int newLevel, UpgradeData data, Arena arena) {
        if (player == null) return;
        double shieldHp = 6.0 + (newLevel - 1) * 3.0;
        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), shieldHp));

        startRechargeTask(player, newLevel, arena);
    }

    @Override
    public void onWaveStart(Player player, int level, Arena arena) {
        if (level <= 0 || player == null) return;
        double shieldHp = 6.0 + (level - 1) * 3.0;
        player.setAbsorptionAmount(Math.max(player.getAbsorptionAmount(), shieldHp));

        startRechargeTask(player, level, arena);
    }

    private void startRechargeTask(Player player, int level, Arena arena) {
        if (player == null || arena == null) return;

        BukkitTask existing = rechargeTasks.remove(player.getUniqueId());
        if (existing != null) existing.cancel();

        MobArenav2 plugin = (arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || (arena != null && !arena.isRunning())) {
                    cancel();
                    rechargeTasks.remove(player.getUniqueId());
                    return;
                }

                // Tự động phục hồi 50% khiên nếu khiên bị vỡ hết
                if (player.getAbsorptionAmount() <= 0) {
                    double maxShield = 6.0 + (level - 1) * 3.0;
                    double restore = maxShield * 0.50;
                    player.setAbsorptionAmount(restore);
                    player.sendMessage("§b§l[PHANTOM BARRIER] §eShield regenerated (+ " + String.format("%.0f", restore) + " Absorption)!");
                    player.playSound(player.getLocation(), Sound.ITEM_ARMOR_EQUIP_DIAMOND, 0.8f, 1.4f);
                }
            }
        }.runTaskTimer(plugin, 600L, 600L); // Mỗi 30 giây (600 ticks)

        rechargeTasks.put(player.getUniqueId(), task);
    }

    @Override
    public void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena) {
        if (level < 4 || player == null) return;

        if (event instanceof EntityDamageByEntityEvent byEntityEvent) {
            if (byEntityEvent.getDamager() instanceof Projectile proj && proj.getShooter() instanceof LivingEntity shooter) {
                double rawDmg = event.getDamage();
                event.setDamage(rawDmg * 0.80); // Giảm 20% sát thương đạn

                double reflectedDmg = rawDmg * 0.30;
                shooter.damage(reflectedDmg, player);
                player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.5f);
            }
        }
    }

    @Override
    public void onReset(Player player, Arena arena) {
        if (player == null) return;
        BukkitTask task = rechargeTasks.remove(player.getUniqueId());
        if (task != null) task.cancel();
        player.setAbsorptionAmount(0.0);
    }
}
