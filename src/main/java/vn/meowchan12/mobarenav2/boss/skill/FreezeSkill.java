package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * [v8.0.0 REWORK]: Absolute Zero: Glacial Cataclysm (Băng Phong Đại Địa)
 * Kỹ năng tối thượng đóng băng tuyệt đối 100% không bị glitch nhảy thoát:
 * 1. Bão Tuyết Tụ Khí: Cảnh báo toàn màn hình, sương tuyết R=35m.
 * 2. Đóng Băng Tuyệt Đối: Dùng Invisible Rideable Marker khóa cứng WASD và Space jumping,
 *    kết hợp Vanilla Powder Snow vignette phủ kín 4 góc màn hình và lồng hạt băng bao bọc.
 * 3. Băng Vỡ Hủy Diệt (Glacial Shatter): Mảnh băng vỡ nổ tung, trừ 40% Max HP chuẩn, hất văng nạn nhân.
 */
public class FreezeSkill implements BossSkill {

    @Override
    public String getType() {
        return "FREEZE";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        Location bossLoc = boss.getLocation();
        int freezeTime = config.getInt("freeze-time", 4);
        double hpLossPercent = config.getDouble("hp-loss-percent", 40.0);
        double minRange = config.getDouble("min-range", 0.0);
        double maxRange = config.getDouble("max-range", 35.0);

        // GIAI ĐOẠN 1: BÃO TUYẾT TỤ KHÍ (Blizzard Charge)
        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.3f);
        bossLoc.getWorld().playSound(bossLoc, Sound.ITEM_ELYTRA_FLYING, 1.8f, 0.5f);
        bossLoc.getWorld().spawnParticle(Particle.SNOWFLAKE, bossLoc, 300, 8.0, 4.0, 8.0, 0.15);

        for (Player p : arena.getPlayersAsObjects()) {
            if (!arena.containsLocation(p.getLocation())) continue;
            double dist = p.getLocation().distance(bossLoc);
            if (dist < minRange || dist > maxRange) continue;

            p.sendTitle("§b§l❄ ABSOLUTE ZERO ❄", "§cThe air freezes solid! Prepare for the Frostbite!", 10, 40, 20);
            p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.5f, 0.5f);

            // True Vanilla Powder Snow freeze overlay (sương giá 4 góc màn hình, tim hóa xanh)
            p.setFreezeTicks(freezeTime * 20 + 80);

            // GIAI ĐOẠN 2: ĐÓNG BĂNG BẤT ĐỘNG 100% (Invisible Rideable Marker)
            Location pLoc = p.getLocation();
            ArmorStand iceAnchor = pLoc.getWorld().spawn(pLoc, ArmorStand.class, as -> {
                as.setVisible(false);
                as.setMarker(true);
                as.setGravity(false);
                as.setSmall(true);
                as.setInvulnerable(true);
            });
            iceAnchor.addPassenger(p);

            p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, freezeTime * 20, 255, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, freezeTime * 20, 255, false, false));

            // Hiệu ứng hạt lồng băng xoay quanh người chơi
            BukkitRunnable freezeVisual = new BukkitRunnable() {
                int tick = 0;
                @Override
                public void run() {
                    if (!p.isOnline() || !arena.isRunning() || !arena.getActivePlayers().contains(p.getUniqueId()) || tick >= freezeTime * 20) {
                        this.cancel();
                        return;
                    }
                    Location center = p.getLocation().add(0, 1.0, 0);
                    double angle = tick * 0.3;
                    double x = Math.cos(angle) * 0.85;
                    double z = Math.sin(angle) * 0.85;
                    p.getWorld().spawnParticle(Particle.SNOWFLAKE, center.clone().add(x, 0.2, z), 2, 0, 0, 0, 0);
                    p.getWorld().spawnParticle(Particle.SNOWFLAKE, center.clone().add(-x, -0.2, -z), 2, 0, 0, 0, 0);

                    if (tick % 10 == 0) {
                        p.getWorld().spawnParticle(Particle.BLOCK, center, 8, 0.4, 0.6, 0.4, 0, Material.ICE.createBlockData());
                        p.playSound(p.getLocation(), Sound.BLOCK_POWDER_SNOW_STEP, 0.6f, 1.5f);
                    }
                    tick++;
                }
            };
            freezeVisual.runTaskTimer(plugin, 0L, 1L);

            // GIAI ĐOẠN 3: BĂNG VỠ HỦY DIỆT (Glacial Shatter)
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                try {
                    iceAnchor.removePassenger(p);
                    iceAnchor.remove();
                } catch (Exception ignored) {}

                if (p.isOnline() && arena.isRunning() && arena.getActivePlayers().contains(p.getUniqueId())) {
                    p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 2.0f, 1.4f);
                    p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation().add(0, 1.0, 0), 120, 0.6, 0.8, 0.6, 0.15, Material.ICE.createBlockData());
                    p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation(), 80, 1.2, 1.5, 1.2, 0.1);

                    // Sát thương chuẩn 40% Max HP
                    AttributeInstance maxHpAttr = p.getAttribute(AttributeUtils.MAX_HEALTH);
                    double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
                    double loss = maxHp * (hpLossPercent / 100.0);
                    vn.meowchan12.mobarenav2.service.CombatDamageService.applyLayeredDamage(p, loss, true, boss, arena, plugin);

                    // Hất văng nạn nhân ra xa khỏi Boss
                    Vector knock = p.getLocation().toVector().subtract(bossLoc.toVector()).normalize().multiply(1.3).setY(0.45);
                    p.setVelocity(knock);

                    p.sendMessage("§c❄ §l[GLACIAL SHATTER] §cLost §4" + String.format("%.0f", hpLossPercent) + "% Max HP §cTrue Damage!");

                    // Hiệu ứng hạ thân nhiệt dư chấn
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1, false, false));
                    p.setFreezeTicks(120);
                }
            }, freezeTime * 20L);
        }
    }
}
