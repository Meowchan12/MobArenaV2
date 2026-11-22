package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * [v7.5.6]: SHADOW_WARP
 * Teleports the boss directly behind a nearby player, disorienting them with Slowness
 * and striking with dark portal energy.
 */
public class ShadowWarpSkill implements BossSkill {

    @Override
    public String getType() {
        return "SHADOW_WARP";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        if (boss == null || !boss.isValid()) return;

        double radius = config != null ? config.getDouble("radius", 35.0) : 35.0;
        double damage = config != null ? config.getDouble("damage", 6.0) : 6.0;
        int slownessDuration = config != null ? config.getInt("slowness-duration", 60) : 60; // 3 seconds = 60 ticks

        List<Player> targets = getNearbyTargets(arena, boss, radius);
        if (targets.isEmpty()) return;

        // Pick a target (prefer random or nearest)
        Player target = targets.get(ThreadLocalRandom.current().nextInt(targets.size()));
        if (target == null || !target.isOnline()) return;

        Location oldLoc = boss.getLocation();
        if (oldLoc.getWorld() != null) {
            oldLoc.getWorld().spawnParticle(Particle.PORTAL, oldLoc.clone().add(0, 1, 0), 40, 0.5, 0.8, 0.5, 0.5);
            oldLoc.getWorld().playSound(oldLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
        }

        Vector dir = target.getLocation().getDirection().setY(0).normalize();
        if (dir.lengthSquared() < 0.01) {
            dir = new Vector(0, 0, -1);
        }
        Location behind = target.getLocation().subtract(dir.clone().multiply(1.5));
        behind.setDirection(dir);

        // Safety check: ensure behind location has air or is passable
        if (behind.getBlock().getType().isSolid()) {
            behind = target.getLocation().add(0, 0.1, 0);
        }

        boss.teleport(behind);

        if (behind.getWorld() != null) {
            behind.getWorld().spawnParticle(Particle.PORTAL, behind.clone().add(0, 1, 0), 60, 0.6, 1.0, 0.6, 0.8);
            behind.getWorld().spawnParticle(Particle.LARGE_SMOKE, behind.clone().add(0, 0.5, 0), 20, 0.3, 0.5, 0.3, 0.05);
            behind.getWorld().playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 1.2f);
            behind.getWorld().playSound(behind, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.9f);
        }

        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessDuration, 1, false, true));
        target.damage(damage, boss);
        target.sendMessage("§c§l[!] §5" + (boss.getCustomName() != null ? boss.getCustomName() : "The Boss") + " §dshadow-warped behind you!");
    }
}
