package vn.meowchan12.mobarenav2.upgrade.handler.ultimate;

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
import org.bukkit.scheduler.BukkitTask;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.manager.UpgradeManager.UpgradeData;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;
import vn.meowchan12.mobarenav2.upgrade.UpgradeRegistry;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * [v8.4.1 REWORK]: Ultimate Celestial Cataclysm (ULTIMATE_CELESTIAL_CATACLYSM)
 * - Fuses: Super Nova (Max) + Elemental Infusion (Max).
 * - Master Orbital Targeting:
 *     + Scans an extended 28.0-block sphere around the player every 5 seconds.
 *     + Targets up to 8 distinct priority monsters simultaneously.
 *     + Priority Hierarchy: Raid Bosses > Support Witches > Immediate Threat (<8m) > Proximity.
 * - Celestial Meteor Barrage:
 *     + Summons 8 celestial meteors slamming onto targeted monsters from the sky.
 *     + Converges excess meteors onto existing high-priority targets when fewer than 8 mobs exist.
 *     + Inflicts 80.0 True DMG + 20% Max HP True DMG (Boss cap +600) + 5s Fire Ticks.
 * - Elemental Secondary Chain Lightning:
 *     + Each meteor impact unleashes an elemental shockwave jumping to nearby mobs.
 * - Weapon Infusion: Basic attacks unleash Chain Lightning jumping across up to 8 targets.
 * - Silent Execution: No chat messages sent during combat.
 * - Clean lifecycle management with onReset.
 */
public class UltimateCelestialCataclysmUpgrade implements UpgradeHandler {

    private final Map<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();

    @Override
    public String getType() {
        return "ULTIMATE_CELESTIAL_CATACLYSM";
    }

    @Override
    public void onApply(Player player, int newLevel, UpgradeData data, Arena arena) {
        if (player == null || arena == null) return;

        // Cancel previous task if one exists
        onReset(player, arena);

        MobArenav2 plugin = (arena.getPlugin() != null) ? arena.getPlugin() : MobArenav2.getInstance();

        double detectionRange = (data != null) ? data.getDouble("radius", 28.0) : 28.0;
        int maxTargets = (data != null) ? data.getInt("max-targets", 8) : 8;
        double intervalSec = (data != null) ? data.getDouble("interval", 5.0) : 5.0;
        long intervalTicks = Math.max(20L, (long) (intervalSec * 20L));
        double baseDamage = (data != null) ? data.getDouble("damage", 80.0) : 80.0;
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

                // 1. Collect all valid arena monsters within 28 blocks of player
                List<LivingEntity> candidateMobs = new ArrayList<>();
                for (Entity entity : player.getNearbyEntities(detectionRange, 14.0, detectionRange)) {
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

                // 3. Select up to 8 distinct priority targets
                int targetCount = Math.min(maxTargets, candidateMobs.size());
                List<LivingEntity> targets = new ArrayList<>(candidateMobs.subList(0, targetCount));

                // 4. Distribute 8 meteor strikes across targets (convergence when < 8 targets)
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

                // 5. Execute Celestial Meteors on each target
                for (int i = 0; i < targetCount; i++) {
                    LivingEntity target = targets.get(i);
                    if (!target.isValid() || target.isDead()) continue;

                    int totalStrikes = strikeDistribution[i];
                    Location targetLoc = target.getLocation();

                    // Celestial Meteor descent particles
                    for (double y = targetLoc.getY() + 18.0; y >= targetLoc.getY(); y -= 0.8) {
                        Location beamLoc = targetLoc.clone();
                        beamLoc.setY(y);
                        beamLoc.getWorld().spawnParticle(Particle.END_ROD, beamLoc, 2, 0.1, 0.1, 0.1, 0.02);
                        beamLoc.getWorld().spawnParticle(Particle.FLAME, beamLoc, 2, 0.1, 0.1, 0.1, 0.03);
                        beamLoc.getWorld().spawnParticle(Particle.LAVA, beamLoc, 1, 0.05, 0.05, 0.05, 0.01);
                    }

                    // Meteor impact audio & visual effects
                    targetLoc.getWorld().strikeLightningEffect(targetLoc);
                    targetLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, targetLoc.clone().add(0, 0.5, 0), 1);
                    targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 0.9f);
                    targetLoc.getWorld().playSound(targetLoc, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.8f);

                    // True Damage calculation
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

                    // Crowd Control: Slowness II & Weakness II + Fire Ticks (5 seconds)
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 1, false, true));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 80, 1, false, true));
                    target.setFireTicks(100);

                    // Apply true damage bypassing armor
                    double remainingHp = target.getHealth() - finalTrueDmg;
                    if (remainingHp <= 0) {
                        target.setHealth(0.5);
                        target.damage(1000.0, player);
                    } else {
                        target.setHealth(Math.max(0.1, remainingHp));
                        target.damage(0.01, player);
                    }

                    // Elemental Impact Chain Lightning: jump to up to 3 nearby mobs within 6 blocks
                    int chains = 0;
                    for (Entity nearby : targetLoc.getWorld().getNearbyEntities(targetLoc, 6.0, 4.0, 6.0)) {
                        if (nearby instanceof LivingEntity nearbyLiving && !nearbyLiving.equals(target) && !(nearbyLiving instanceof Player)) {
                            if (nearbyLiving.hasMetadata("MA_MOB") && !nearbyLiving.isDead() && nearbyLiving.isValid()) {
                                nearbyLiving.damage(20.0, player);
                                nearbyLiving.setFireTicks(60);
                                nearbyLiving.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, nearbyLiving.getLocation().add(0, 1.0, 0), 6, 0.2, 0.2, 0.2, 0.05);
                                chains++;
                                if (chains >= 3) break;
                            }
                        }
                    }
                }

                // Subtle audio cue for player without chat spam
                player.playSound(playerLoc, Sound.BLOCK_BEACON_ACTIVATE, 0.5f, 2.0f);
            }
        }.runTaskTimer(plugin, intervalTicks, intervalTicks);

        activeTasks.put(player.getUniqueId(), task);
    }

    /**
     * Basic attack weapon infusion: unleashes Chain Lightning jumping across up to 8 targets.
     */
    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null || victim == null || arena == null) return;
        if (UpgradeRegistry.isDispatchingAny()) return;

        Location cur = victim.getLocation();
        int chained = 0;

        for (LivingEntity nearby : cur.getWorld().getNearbyLivingEntities(cur, 10.0)) {
            if (nearby.equals(victim) || nearby instanceof Player) continue;
            if (arena.getActiveMobs().contains(nearby) || nearby.hasMetadata("MA_MOB")) {
                nearby.damage(15.0, player);
                nearby.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, nearby.getLocation().add(0, 1.0, 0), 10, 0.3, 0.3, 0.3, 0.1);
                chained++;
                if (chained >= 8) break;
            }
        }

        if (chained > 0) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.8f);
        }
    }

    /**
     * Calculates deterministic threat priority for monster targeting.
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
        score += (int) Math.max(0, 1000 - (dist * 35));

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
