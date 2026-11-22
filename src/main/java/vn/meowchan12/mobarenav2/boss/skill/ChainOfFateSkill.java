package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.service.CombatDamageService;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.util.List;

/**
 * [v8.3.0 TACTICAL BOSS SKILL]: CHAIN_OF_FATE (Co-op Positioning Mechanic)
 * - Chains 2 players together with a pulsating tether (or 1 player to the Boss if solo).
 * - Players have 5 seconds to run >16 blocks apart to snap the tether.
 * - Failure: Chain implodes, dealing 40% True Damage and pulling them inward.
 */
public class ChainOfFateSkill implements BossSkill {

    @Override
    public String getType() {
        return "CHAIN_OF_FATE";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection skillConfig) {
        if (boss == null || !boss.isValid() || boss.isDead()) return;

        List<Player> targets = getNearbyTargets(arena, boss, 40.0);
        if (targets.isEmpty()) return;

        Entity entityA;
        Entity entityB;

        if (targets.size() >= 2) {
            entityA = targets.get(0);
            entityB = targets.get(1);
        } else {
            entityA = targets.get(0);
            entityB = boss; // Solo mode: bound to boss
        }

        final double breakDistance = (skillConfig != null) ? skillConfig.getDouble("break-distance", 16.0) : 16.0;
        final int maxDurationSec = (skillConfig != null) ? skillConfig.getInt("duration", 5) : 5;
        final double penaltyDamagePercent = (skillConfig != null) ? skillConfig.getDouble("damage-percent", 0.40) : 0.40;

        String nameB = (entityB instanceof Player pb) ? pb.getName() : "the Boss";
        String alertA = ColorUtils.colorize("&4&l⛓ [CHAIN OF FATE] &cYou are bound to &e" + nameB + "&c! SPRINT APART (> " + (int) breakDistance + "m) IN " + maxDurationSec + "s!");
        entityA.sendMessage(alertA);

        if (entityB instanceof Player pb) {
            String alertB = ColorUtils.colorize("&4&l⛓ [CHAIN OF FATE] &cYou are bound to &e" + ((Player) entityA).getName() + "&c! SPRINT APART (> " + (int) breakDistance + "m) IN " + maxDurationSec + "s!");
            pb.sendMessage(alertB);
        }

        new BukkitRunnable() {
            int ticksElapsed = 0;
            final Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(255, 0, 50), 1.5f);

            @Override
            public void run() {
                if (!entityA.isValid() || !entityB.isValid() || entityA.isDead() || entityB.isDead()) {
                    cancel();
                    return;
                }

                Location locA = entityA.getLocation().add(0, 1.0, 0);
                Location locB = entityB.getLocation().add(0, 1.0, 0);

                if (!locA.getWorld().equals(locB.getWorld())) {
                    cancel();
                    return;
                }

                double currentDist = locA.distance(locB);

                // Check SUCCESS: distance exceeded breakDistance
                if (currentDist >= breakDistance) {
                    cancel();
                    playSuccess(locA, locB, entityA, entityB);
                    return;
                }

                // Draw beam particle chain between A and B
                Vector direction = locB.toVector().subtract(locA.toVector());
                double length = direction.length();
                direction.normalize();

                for (double d = 0; d < length; d += 0.8) {
                    Location point = locA.clone().add(direction.clone().multiply(d));
                    point.getWorld().spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, redDust);
                }

                ticksElapsed += 4;
                if (ticksElapsed >= maxDurationSec * 20) {
                    // FAILURE: Explode chain!
                    cancel();
                    playFailure(plugin, arena, boss, entityA, entityB, penaltyDamagePercent);
                }
            }
        }.runTaskTimer(plugin, 4L, 4L);
    }

    private void playSuccess(Location locA, Location locB, Entity a, Entity b) {
        locA.getWorld().playSound(locA, Sound.ITEM_TRIDENT_THUNDER, 1.2f, 1.5f);
        locA.getWorld().playSound(locA, Sound.ENTITY_ITEM_BREAK, 1.5f, 0.8f);
        locA.getWorld().spawnParticle(Particle.FIREWORK, locA, 15, 0.5, 0.5, 0.5, 0.1);
        locB.getWorld().spawnParticle(Particle.FIREWORK, locB, 15, 0.5, 0.5, 0.5, 0.1);

        String msg = ColorUtils.colorize("&a&l✔ [CHAIN BROKEN] &fYou severed the tether safely!");
        a.sendMessage(msg);
        if (b instanceof Player pb) pb.sendMessage(msg);
    }

    private void playFailure(MobArenav2 plugin, Arena arena, LivingEntity boss, Entity a, Entity b, double damagePercent) {
        Location locA = a.getLocation();
        locA.getWorld().playSound(locA, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);
        locA.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, locA, 2);

        applyChainPenalty(plugin, arena, boss, a, damagePercent);
        if (b instanceof Player) {
            applyChainPenalty(plugin, arena, boss, b, damagePercent);
        }
    }

    private void applyChainPenalty(MobArenav2 plugin, Arena arena, LivingEntity boss, Entity entity, double damagePercent) {
        if (!(entity instanceof Player player)) return;

        double maxHp = player.getAttribute(AttributeUtils.MAX_HEALTH).getValue();
        double dmg = maxHp * damagePercent;
        CombatDamageService.applyLayeredDamage(player, dmg, true, boss, arena, plugin);

        player.sendMessage(ColorUtils.colorize("&4&l☠ [TETHER DETONATION] &cFailed to break the chain! Suffered &e" + (int) dmg + " True Damage!"));

        // Pull toward boss
        if (boss != null && boss.isValid()) {
            Vector pull = boss.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.2).setY(0.4);
            player.setVelocity(pull);
        }
    }
}
