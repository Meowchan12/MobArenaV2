package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mưa tên nổ liên thanh (Arrow Barrage).
 */
public class SpammerBowSkill implements BossSkill {

    @Override
    public String getType() {
        return "SPAMMER_BOW";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        int sbDur = config.getInt("duration", 6);
        long sbInterval = (long) (config.getDouble("attack-interval", 1.5) * 20L);
        boolean hitscan = config.getBoolean("guaranteed-hit", true);
        double sbKnockback = config.getDouble("knockback-force", 1.2);
        double debuffChance = config.getDouble("debuff.chance", 15.0);
        List<String> sbEffects = config.getStringList("debuff.effects");
        double sbDamage = config.getDouble("damage", 6.0);
        double explosionRadius = config.getDouble("explosion-radius", 3.0);
        double trueDmgPercent = config.getDouble("true-damage-percent", 0.80);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (boss.isDead() || !arena.isRunning() || ticks >= (sbDur * 20)) {
                    this.cancel();
                    return;
                }

                if (ticks % sbInterval == 0) {
                    boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.2f);
                    for (Player p : arena.getPlayersAsObjects()) {
                        if (arena.containsLocation(p.getLocation())) {
                            if (hitscan) {
                                drawLaser(boss.getEyeLocation(), p.getEyeLocation(), 255, 100, 0);
                                p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation(), 1);
                                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);

                                double trueDmg = sbDamage * trueDmgPercent;
                                vn.meowchan12.mobarenav2.service.CombatDamageService.applyLayeredDamage(p, trueDmg, false, boss, arena, plugin);

                                for (Entity nearby : p.getNearbyEntities(explosionRadius, explosionRadius, explosionRadius)) {
                                    if (nearby instanceof Player np && arena.getActivePlayers().contains(np.getUniqueId())) {
                                        double aoeDmg = trueDmg * 0.5;
                                        vn.meowchan12.mobarenav2.service.CombatDamageService.applyLayeredDamage(np, aoeDmg, false, boss, arena, plugin);
                                    }
                                }

                                Vector kbDir = p.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize();
                                p.setVelocity(kbDir.multiply(sbKnockback).setY(0.5));

                                if (ThreadLocalRandom.current().nextDouble(100.0) <= debuffChance) {
                                    for (String eff : sbEffects) {
                                        String[] data = eff.trim().split(":");
                                        if (data.length >= 2) {
                                            try {
                                                PotionEffectType pType = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(data[0].toLowerCase()));
                                                if (pType != null) {
                                                    p.addPotionEffect(new PotionEffect(pType, Integer.parseInt(data[1]), data.length > 2 ? Integer.parseInt(data[2]) : 0));
                                                }
                                            } catch (Exception ignored) {}
                                        }
                                    }
                                }
                            } else {
                                Arrow arrow = boss.launchProjectile(Arrow.class);
                                arrow.setDamage(sbDamage);
                                Vector dir = p.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector()).normalize();
                                arrow.setVelocity(dir.multiply(2.0));
                            }
                        }
                    }
                }
                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void drawLaser(Location start, Location end, int r, int g, int b) {
        double distance = start.distance(end);
        Vector direction = end.toVector().subtract(start.toVector()).normalize().multiply(0.5);
        Location current = start.clone();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(r, g, b), 1.0f);

        for (double d = 0; d < distance; d += 0.5) {
            current.add(direction);
            current.getWorld().spawnParticle(Particle.DUST, current, 1, 0, 0, 0, 0, dust);
        }
    }
}
