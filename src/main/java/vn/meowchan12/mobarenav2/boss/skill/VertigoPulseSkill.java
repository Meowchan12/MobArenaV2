package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * [v7.5.6]: VERTIGO_PULSE
 * Emits a high-frequency sonic shockwave that twists players' camera aim,
 * causes disorienting nausea, deflects incoming projectiles, and deals concussive damage.
 */
public class VertigoPulseSkill implements BossSkill {

    @Override
    public String getType() {
        return "VERTIGO_PULSE";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        if (boss == null || !boss.isValid()) return;

        double radius = config != null ? config.getDouble("radius", 18.0) : 18.0;
        double damage = config != null ? config.getDouble("damage", 4.0) : 4.0;
        int nauseaDuration = config != null ? config.getInt("nausea-duration", 80) : 80; // 4 seconds

        Location bossLoc = boss.getLocation();
        if (bossLoc.getWorld() != null) {
            try {
                bossLoc.getWorld().spawnParticle(Particle.SONIC_BOOM, bossLoc.clone().add(0, 1.2, 0), 1);
            } catch (Throwable ignored) {
                bossLoc.getWorld().spawnParticle(Particle.EXPLOSION, bossLoc.clone().add(0, 1.2, 0), 2);
            }
            bossLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, bossLoc.clone().add(0, 0.5, 0), 25, 2.0, 0.2, 2.0, 0.1);
            try {
                bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 1.4f);
            } catch (Throwable ignored) {
                bossLoc.getWorld().playSound(bossLoc, Sound.BLOCK_BELL_RESONATE, 1.5f, 0.8f);
            }
            bossLoc.getWorld().playSound(bossLoc, Sound.BLOCK_BELL_RESONATE, 1.2f, 1.6f);

            // Deflect incoming projectiles near boss
            for (Entity ent : bossLoc.getWorld().getNearbyEntities(bossLoc, 6.0, 6.0, 6.0)) {
                if (ent instanceof Projectile proj && !(proj.getShooter() == boss)) {
                    Vector reverse = proj.getVelocity().multiply(-0.8).add(new Vector(0, 0.3, 0));
                    proj.setVelocity(reverse);
                }
            }
        }

        PotionEffectType nauseaType = PotionEffectType.getByName("NAUSEA");
        if (nauseaType == null) {
            nauseaType = PotionEffectType.getByName("CONFUSION");
        }

        List<Player> targets = getNearbyTargets(arena, boss, radius);
        for (Player p : targets) {
            if (p == null || !p.isOnline()) continue;

            // Twist player aim (yaw & pitch)
            Location pLoc = p.getLocation();
            float yawDelta = (float) ThreadLocalRandom.current().nextDouble(-65.0, 65.0);
            float pitchDelta = (float) ThreadLocalRandom.current().nextDouble(-30.0, 30.0);
            pLoc.setYaw(pLoc.getYaw() + yawDelta);
            pLoc.setPitch(Math.max(-90.0f, Math.min(90.0f, pLoc.getPitch() + pitchDelta)));
            p.teleport(pLoc);

            // Apply Nausea and Slowness
            if (nauseaType != null) {
                p.addPotionEffect(new PotionEffect(nauseaType, nauseaDuration, 1, false, true));
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, true));

            // Deal concussive sonic damage
            p.damage(damage, boss);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 0.5f);
            p.sendMessage("§c§l[VERTIGO] §eA disorienting sonic pulse distorted your aim!");
        }
    }
}
