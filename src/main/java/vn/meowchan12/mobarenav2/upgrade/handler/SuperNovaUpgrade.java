package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [v8.4.1 REWORK]: Supernova Orb (SUPER_AUTO_STRIKE)
 * - Automatically scans and targets up to 6 priority monsters every 5 seconds.
 * - Detection Range: 18.0 blocks around the player.
 * - Smart Threat Prioritization:
 *     1. Raid Bosses & Mini-Bosses (MA_BOSS / BOSS)
 *     2. Support Mobs (Healer Witch / War Buffer Witch)
 *     3. Immediate danger (monsters within 8 blocks of player)
 *     4. Remaining monsters sorted by proximity to player
 * - Multi-Lock Orbital Strikes:
 *     + Strikes up to 6 distinct priority targets simultaneously with orbital lasers.
 *     + If fewer than 6 monsters exist, excess strikes converge onto existing high-priority targets.
 * - Hybrid True Damage (Unchanged from original balance):
 *     + Base True Damage: 75.0
 *     + Target Max HP True Damage: 20%
 *     + Boss True Damage Cap: max +600.0 (up to 675.0 True DMG)
 *     + Secondary converging strikes add +15% bonus true damage each
 * - Crowd Control on Hit: Slowness II (4s) & Weakness II (4s)
 * - Silent Execution: No chat spam on activation.
 * - Zero Memory Leak lifecycle management.
 */
public class SuperNovaUpgrade implements UpgradeHandler {

    private final Map<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();

    @Override
    public String getType() {
        return "SUPER_AUTO_STRIKE";
    }

    @Override
    public void onApply(Player player, int newLevel, UpgradeData data, Arena arena) {
        if (player == null || arena == null) return;

        // Cancel previous task if one exists
        onReset(player, arena);

        MobArenav2 plugin = (arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();

        double detectionRange = (data != null) ? data.getDouble("radius", 18.0) : 18.0;
        int maxTargets = (data != null) ? data.getInt("max-targets", 6) : 6;
        double intervalSec = (data != null) ? data.getDouble("interval", 5.0) : 5.0;
        long intervalTicks = Math.max(20L, (long) (intervalSec * 20L));
        double baseDamage = (data != null) ? data.getDouble("damage", 75.0) : 75.0;
        double percentHpRatio = (data != null) ? (data.getDouble("percent-hp", 20.0) / 100.0) : 0.20;
        double bossDamageCap = (data != null) ? data.getDouble("boss-damage-cap", 600.0) : 600.0;

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || player.isDead() || (arena != null && !arena.getPlayers().contains(player))) {
                    cancel();
                    activeTasks.remove(player.getUniqueId());
                    return;
                }

                Location playerLoc = player.getLocation();

                // 1. Collect all valid arena monsters within 18 blocks of player
                List<LivingEntity> candidateMobs = new ArrayList<>();
                for (Entity entity : player.getNearbyEntities(detectionRange, 12.0, detectionRange)) {
                    if (entity instanceof LivingEntity living && living.hasMetadata("MA_MOB") && !living.isDead() && living.isValid()) {
                        candidateMobs.add(living);
                    }
                }

                if (candidateMobs.isEmpty()) return;

                // 2. Sort candidate mobs by priority threat score (descending)
                candidateMobs.sort((m1, m2) -> Integer.compare(
                        calculatePriority(m2, playerLoc),
                        calculatePriority(m1, playerLoc)
                ));

                // 3. Select up to 6 distinct priority targets
                int targetCount = Math.min(maxTargets, candidateMobs.size());
                List<LivingEntity> targets = new ArrayList<>(candidateMobs.subList(0, targetCount));

                // 4. Distribute 6 orbital strikes across targets (convergence when < 6 targets)
                int[] strikeDistribution = new int[targetCount];
                for (int i = 0; i < targetCount; i++) {
                    strikeDistribution[i] = 1;
                }
                int remainingStrikes = maxTargets - targetCount;
                int distIdx = 0;
                while (remainingStrikes > 0) {
                    strikeDistribution[distIdx % targetCount]++;
                    remainingStrikes--;
                    distIdx++;
                }

                ArenaPlayerProfile prof = arena.getPlayerProfiles().get(player.getUniqueId());

                // 5. Execute Orbital Strikes on each target
                for (int i = 0; i < targetCount; i++) {
                    LivingEntity target = targets.get(i);
                    if (!target.isValid() || target.isDead()) continue;

                    int totalStrikes = strikeDistribution[i];
                    Location targetLoc = target.getLocation();

                    // Orbital Laser Beam descending from the sky
                    for (double y = targetLoc.getY() + 16.0; y >= targetLoc.getY(); y -= 0.8) {
                        Location beamLoc = targetLoc.clone();
                        beamLoc.setY(y);
                        beamLoc.getWorld().spawnParticle(Particle.END_ROD, beamLoc, 2, 0.08, 0.08, 0.08, 0.01);
                        beamLoc.getWorld().spawnParticle(Particle.FLAME, beamLoc, 1, 0.05, 0.05, 0.05, 0.01);
                    }

                    // Impact visual & sound effects
                    targetLoc.getWorld().strikeLightningEffect(targetLoc);
                    targetLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, targetLoc.clone().add(0, 0.5, 0), 1);
                    targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.7f, 1.2f);
                    targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);

                    // If multiple strikes converge, render satellite laser ring
                    if (totalStrikes > 1) {
                        for (int degree = 0; degree < 360; degree += 45) {
                            double rad = Math.toRadians(degree);
                            Location satLoc = targetLoc.clone().add(Math.cos(rad) * 0.8, 0.2, Math.sin(rad) * 0.8);
                            targetLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, satLoc, 2, 0, 0, 0, 0.02);
                        }
                    }

                    // Hybrid True Damage Calculation
                    double maxHp = (AttributeUtils.MAX_HEALTH != null && target.getAttribute(AttributeUtils.MAX_HEALTH) != null)
                            ? target.getAttribute(AttributeUtils.MAX_HEALTH).getValue()
                            : target.getMaxHealth();

                    double percentDmg = percentHpRatio * maxHp;
                    boolean isBoss = target.hasMetadata("MA_BOSS") || target.hasMetadata("BOSS");
                    if (isBoss) {
                        percentDmg = Math.min(bossDamageCap, percentDmg); // Boss Cap: max True DMG from % Max HP
                    }

                    double primaryTrueDmg = baseDamage + percentDmg;
                    double bonusTrueDmg = (totalStrikes > 1) ? (totalStrikes - 1) * (0.15 * primaryTrueDmg) : 0.0;
                    double finalTrueDmg = primaryTrueDmg + bonusTrueDmg;

                    // Record damage dealt credit for player
                    if (prof != null) {
                        prof.addDamageDealt(finalTrueDmg);
                    }

                    // Crowd Control: Slowness II (4s) & Weakness II (4s)
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1, false, true));

                    // Apply true damage bypassing armor, preserving killer credit
                    double remainingHp = target.getHealth() - finalTrueDmg;
                    if (remainingHp <= 0) {
                        target.setHealth(0.5);
                        target.damage(1000.0, player);
                    } else {
                        target.setHealth(Math.max(0.1, remainingHp));
                        target.damage(0.01, player);
                    }
                }

                // Subtle audio cue for the player without chat spam
                player.playSound(playerLoc, Sound.BLOCK_BEACON_ACTIVATE, 0.4f, 1.8f);
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);

        activeTasks.put(player.getUniqueId(), task);
    }

    /**
     * Calculates deterministic threat priority for monster targeting.
     * Higher score = higher priority.
     */
    private int calculatePriority(LivingEntity mob, Location playerLoc) {
        int score = 0;

        // Priority 1: Raid Bosses & Mini-Bosses
        if (mob.hasMetadata("MA_BOSS") || mob.hasMetadata("BOSS")) {
            score += 10000;
        }

        // Priority 2: Tactical Support Mobs (Healer Witch / War Buffer Witch)
        if (mob.hasMetadata("MA_SUPPORT_MOB") || mob.hasMetadata("MA_SUPPORT_TYPE")) {
            score += 5000;
        }

        // Priority 3: Immediate Threat (within 8 blocks of player)
        double distSq = mob.getLocation().distanceSquared(playerLoc);
        if (distSq <= 64.0) {
            score += 2000;
        }

        // Proximity Bonus: Closer mobs get higher score (up to 1000 pts)
        double dist = Math.sqrt(distSq);
        score += (int) Math.max(0, 1000 - (dist * 40));

        return score;
    }

    @Override
    public void onReset(Player player, Arena arena) {
        if (player == null) return;
        BukkitTask task = activeTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }
}
