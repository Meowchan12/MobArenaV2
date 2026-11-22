package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Location;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v8.4.3 DYNAMIC ENGINE]: Aegis of Immortality (SUPER_REVIVE)
 * Reads passive damage reduction, revive cooldown, invulnerability duration, and shockwave damage from UpgradeData/upgrades.yml.
 */
public class SuperAegisUpgrade implements UpgradeHandler {

    private static final String KEY_COOLDOWN_UNTIL = "ma_super_aegis_cooldown";

    @Override
    public String getType() {
        return "SUPER_REVIVE";
    }

    @Override
    public void onDefend(EntityDamageEvent event, Player player, LivingEntity damager, int level, Arena arena, UpgradeData data) {
        if (level <= 0 || player == null || arena == null) return;

        MobArenav2 plugin = (arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
        NamespacedKey cdKey = new NamespacedKey(plugin, KEY_COOLDOWN_UNTIL);

        long now = System.currentTimeMillis();
        long cooldownUntil = player.getPersistentDataContainer().getOrDefault(cdKey, PersistentDataType.LONG, 0L);

        // 1. Nội tại giảm sát thương thường trực
        double passiveReduction = (data != null) ? data.getDouble("passive-reduction-percent", 20.0) : 20.0;
        event.setDamage(event.getDamage() * (1.0 - passiveReduction / 100.0));

        // 2. Kiểm tra đòn đánh chí tử
        double predictedHp = player.getHealth() - event.getFinalDamage();
        if (predictedHp <= 0) {
            if (now < cooldownUntil) {
                // Đang trong thời gian hồi chiêu
                long remSec = (cooldownUntil - now) / 1000L;
                player.sendActionBar(vn.meowchan12.mobarenav2.utils.ColorUtils.toAdventure("&c⚠ Aegis of Immortality is recharging! (" + remSec + "s remaining)"));
                return;
            }

            // KÍCH HOẠT CỨU TỬ THẦN THÁNH
            event.setDamage(0); // Chặn đứng hoàn toàn cái chết

            // Thiết lập hồi chiêu
            long cdMs = (data != null) ? (long)(data.getDouble("cooldown-seconds", 90.0) * 1000L) : 90000L;
            long nextReady = now + cdMs;
            player.getPersistentDataContainer().set(cdKey, PersistentDataType.LONG, nextReady);

            // Hồi 100% HP tối đa
            AttributeInstance maxHpAttr = player.getAttribute(AttributeUtils.MAX_HEALTH);
            double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
            player.setHealth(maxHp);

            // Cấp hiệu ứng Bão Hòa (Saturation) + Khiên vàng 10 tim + Hồi máu V + Bất tử
            long invTicks = (data != null) ? (long)(data.getDouble("invulnerability-seconds", 6.0) * 20L) : 120L;
            player.setAbsorptionAmount(20.0);
            player.setFoodLevel(20);
            player.setSaturation(20.0f);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SATURATION, 40, 1, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, (int)invTicks, 4, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, (int)invTicks, 4, false, true));
            player.setInvulnerable(true);

            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        player.setInvulnerable(false);
                    }
                }
            }.runTaskLater(plugin, invTicks);

            // Thần Nộ Hoàng Kim (Divine Shockwave)
            double shockwaveDmg = (data != null) ? data.getDouble("shockwave-damage", 150.0) : 150.0;
            double radius = (data != null) ? data.getDouble("shockwave-radius", 10.0) : 10.0;
            Location centerLoc = player.getLocation();
            centerLoc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, centerLoc.clone().add(0, 1, 0), 200, 1.2, 1.8, 1.2, 0.4);
            centerLoc.getWorld().spawnParticle(Particle.FLASH, centerLoc.clone().add(0, 1, 0), 3);
            centerLoc.getWorld().playSound(centerLoc, Sound.ITEM_TOTEM_USE, 1.5f, 0.9f);
            centerLoc.getWorld().playSound(centerLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.2f, 1.2f);
            player.sendTitle("§6§l✦ DIVINE AEGIS AWAKENED ✦", "§eFully Restored! " + (invTicks / 20) + "s Invulnerability & " + (int)shockwaveDmg + " True DMG Burst!", 10, 60, 20);

            ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());

            for (Entity near : player.getNearbyEntities(radius, 8.0, radius)) {
                if (near instanceof LivingEntity mob && mob.hasMetadata("MA_MOB") && !mob.isDead()) {
                    // Thổi bay cực mạnh
                    Vector push = mob.getLocation().toVector().subtract(centerLoc.toVector()).normalize().multiply(2.8).setY(0.7);
                    mob.setVelocity(push);

                    // Làm choáng (Slowness V & Weakness III trong 4s)
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 4, false, false));
                    mob.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 2, false, false));

                    // Gây True DMG
                    double remaining = mob.getHealth() - shockwaveDmg;
                    if (profile != null) profile.addDamageDealt(shockwaveDmg);

                    if (remaining <= 0) {
                        mob.setHealth(0.5);
                        mob.damage(1000.0, player);
                    } else {
                        mob.setHealth(Math.max(0.1, remaining));
                        mob.damage(0.01, player);
                    }
                }
            }

            // Lập lịch thông báo khi hồi chiêu hoàn tất
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline() && arena.isRunning() && arena.getPlayers().contains(player)) {
                        player.sendMessage("§6§l✦ AEGIS OF IMMORTALITY ✦ §aRecharged and ready to protect you!");
                        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.5f);
                        player.getWorld().spawnParticle(Particle.END_ROD, player.getLocation().add(0, 1.5, 0), 20, 0.4, 0.4, 0.4, 0.1);
                    }
                }
            }.runTaskLater(plugin, 1800L); // 90 giây = 1800 ticks
        }
    }

    @Override
    public void onReset(Player player, Arena arena) {
        if (player == null) return;
        MobArenav2 plugin = (arena != null && arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();
        player.getPersistentDataContainer().remove(new NamespacedKey(plugin, KEY_COOLDOWN_UNTIL));
        player.setInvulnerable(false);
    }
}
