package vn.meowchan12.mobarenav2.boss;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;
import vn.meowchan12.mobarenav2.utils.ColorUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * [v8.3.1 OVERHAUL]: Telegraphed Crushing Blow Mechanic.
 * Replaces instant 20% RNG shield breaks with a high-stakes, 2.0s - 2.5s telegraphed heavy strike.
 *
 * Tactical Mechanics:
 * - Wind-up: Boss roots for 45 ticks (2.25s) with a red circle indicator and ticking audio cadence.
 * - Dodge (>4.5m): 0 damage. Boss misses, creating a 1.0s Punish Window for players.
 * - Shield Guard (<=4.5m): Shield breaks (3s cooldown), but SHIELD GATE absorbs all true damage
 *   and caps physical damage to max 20% of player Max HP (preventing 1-shot overkill). Knocks player back 4 blocks.
 * - Direct Hit (<=4.5m unblocked): Heavy strike damage capped at 60% Max HP to prevent early-game one-shots.
 */
public class CrushingBlowMechanic {

    public static final String METADATA_KEY = "MA_CRUSHING_BLOW_CHARGING";
    public static final int WINDUP_TICKS = 45; // 2.25 seconds
    public static final double TELEGRAPH_RADIUS = 4.5;
    public static final int SHIELD_BREAK_COOLDOWN = 60; // 3.0 seconds (60 ticks)

    public static void startWindup(MobArenav2 plugin, Arena arena, LivingEntity boss, String displayName, Runnable onComplete) {
        startWindup(plugin, arena, boss, displayName, null, onComplete);
    }

    public static void startWindup(MobArenav2 plugin, Arena arena, LivingEntity boss, String displayName, String bossConfigId, Runnable onComplete) {
        if (boss == null || !boss.isValid() || boss.isDead()) {
            if (onComplete != null) onComplete.run();
            return;
        }

        org.bukkit.configuration.ConfigurationSection bossCfg = (bossConfigId != null && plugin.getBossManager() != null && plugin.getBossManager().getBossesConfig() != null)
                ? plugin.getBossManager().getBossesConfig().getConfigurationSection(bossConfigId) : null;
        org.bukkit.configuration.ConfigurationSection settings = (plugin.getBossManager() != null && plugin.getBossManager().getBossesConfig() != null)
                ? plugin.getBossManager().getBossesConfig().getConfigurationSection("settings") : null;

        int windupTicks = (bossCfg != null && bossCfg.contains("crushing-blow.windup-ticks")) ? bossCfg.getInt("crushing-blow.windup-ticks") :
                (settings != null ? settings.getInt("crushing-blow.windup-ticks", WINDUP_TICKS) : WINDUP_TICKS);
        double telegraphRadius = (bossCfg != null && bossCfg.contains("crushing-blow.telegraph-radius")) ? bossCfg.getDouble("crushing-blow.telegraph-radius") :
                (settings != null ? settings.getDouble("crushing-blow.telegraph-radius", TELEGRAPH_RADIUS) : TELEGRAPH_RADIUS);
        int shieldBreakCooldown = (bossCfg != null && bossCfg.contains("crushing-blow.shield-break-cooldown")) ? bossCfg.getInt("crushing-blow.shield-break-cooldown") :
                (settings != null ? settings.getInt("crushing-blow.shield-break-cooldown", SHIELD_BREAK_COOLDOWN) : SHIELD_BREAK_COOLDOWN);
        double directHitCap = (bossCfg != null && bossCfg.contains("crushing-blow.direct-hit-max-hp-cap")) ? bossCfg.getDouble("crushing-blow.direct-hit-max-hp-cap") :
                (settings != null ? settings.getDouble("crushing-blow.direct-hit-max-hp-cap", 0.60) : 0.60);
        double shieldGateCap = (bossCfg != null && bossCfg.contains("crushing-blow.shield-gate-max-hp-cap")) ? bossCfg.getDouble("crushing-blow.shield-gate-max-hp-cap") :
                (settings != null ? settings.getDouble("crushing-blow.shield-gate-max-hp-cap", 0.20) : 0.20);

        boss.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, true));

        // Root boss in place during heavy strike wind-up
        boss.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, windupTicks + 5, 10, false, false));

        Location origin = boss.getLocation();
        origin.getWorld().playSound(origin, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 1.4f);
        origin.getWorld().playSound(origin, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.5f);

        String bossName = (displayName != null && !displayName.isEmpty()) ? displayName : boss.getName();

        // Broadcast initial warning to nearby players
        double secondsTotal = windupTicks / 20.0;
        for (Player p : getNearbyPlayers(arena, boss, 16.0)) {
            p.sendActionBar(ColorUtils.toAdventure("&c&l⚠ [HEAVY STRIKE] &e" + bossName + " &cis charging a &4&lCrushing Blow! &e(" + String.format("%.1f", secondsTotal) + "s) &c&lDODGE! &7(>" + telegraphRadius + "m)"));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.2f, 0.6f);
        }

        new BukkitRunnable() {
            int ticksElapsed = 0;
            final Particle.DustOptions redDust = new Particle.DustOptions(Color.fromRGB(255, 30, 30), 1.6f);

            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead() || (arena != null && !arena.isRunning())) {
                    cleanup(boss);
                    if (onComplete != null) onComplete.run();
                    cancel();
                    return;
                }

                ticksElapsed += 2;
                Location currentLoc = boss.getLocation();

                // Draw circular red warning telegraph ring on the floor
                for (int deg = 0; deg < 360; deg += 15) {
                    double rad = Math.toRadians(deg);
                    double x = currentLoc.getX() + telegraphRadius * Math.cos(rad);
                    double z = currentLoc.getZ() + telegraphRadius * Math.sin(rad);
                    currentLoc.getWorld().spawnParticle(Particle.DUST, x, currentLoc.getY() + 0.15, z, 1, 0, 0, 0, 0, redDust);
                }

                // Inner charging crit & smoke particles
                currentLoc.getWorld().spawnParticle(Particle.CRIT, currentLoc.clone().add(0, 0.5, 0), 3, 0.5, 0.5, 0.5, 0.1);

                // Audio ticking cadence and action bar refresh every 10 ticks (0.5s)
                if (ticksElapsed % 10 == 0) {
                    float pitch = 0.7f + (ticksElapsed / (float) windupTicks) * 1.1f;
                    currentLoc.getWorld().playSound(currentLoc, Sound.BLOCK_NOTE_BLOCK_PLING, 1.1f, pitch);

                    double secondsLeft = Math.max(0.1, (windupTicks - ticksElapsed) / 20.0);
                    for (Player p : getNearbyPlayers(arena, boss, 16.0)) {
                        p.sendActionBar(ColorUtils.toAdventure("&c&l⚠ [HEAVY STRIKE] &e" + bossName + " &cis charging... &f(" + String.format("%.1f", secondsLeft) + "s) &c&lDODGE! &7(>" + telegraphRadius + "m)"));
                    }
                }

                if (ticksElapsed >= windupTicks) {
                    cleanup(boss);
                    executeImpact(plugin, arena, boss, bossName, telegraphRadius, shieldBreakCooldown, directHitCap, shieldGateCap);
                    if (onComplete != null) onComplete.run();
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private static void executeImpact(MobArenav2 plugin, Arena arena, LivingEntity boss, String bossName) {
        executeImpact(plugin, arena, boss, bossName, TELEGRAPH_RADIUS, SHIELD_BREAK_COOLDOWN, 0.60, 0.20);
    }

    private static void executeImpact(MobArenav2 plugin, Arena arena, LivingEntity boss, String bossName,
                                      double telegraphRadius, int shieldBreakCooldown, double directHitCap, double shieldGateCap) {
        if (boss == null || !boss.isValid() || boss.isDead()) return;

        Location loc = boss.getLocation();
        if (loc.getWorld() == null) return;

        // Visual and audio impact explosion
        loc.getWorld().spawnParticle(Particle.EXPLOSION, loc.clone().add(0, 0.5, 0), 2);
        loc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, loc.clone().add(0, 1.0, 0), 8, 1.0, 0.2, 1.0, 0.1);
        loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 1.8f, 0.7f);
        loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_ATTACK_IRON_DOOR, 1.5f, 0.6f);

        double bossDmg = 35.0;
        if (boss.getAttribute(AttributeUtils.ATTACK_DAMAGE) != null) {
            bossDmg = boss.getAttribute(AttributeUtils.ATTACK_DAMAGE).getValue();
        }

        List<Player> nearby = getNearbyPlayers(arena, boss, 16.0);
        boolean anyPlayerHit = false;

        for (Player p : nearby) {
            if (!p.isOnline() || p.isDead()) continue;
            double distSq = p.getLocation().distanceSquared(loc);

            if (distSq <= telegraphRadius * telegraphRadius) {
                anyPlayerHit = true;

                // -------------------------------------------------------------
                // CASE 1: Player is BLOCKING with a Shield (Shield Gate)
                // -------------------------------------------------------------
                if (p.isBlocking()) {
                    // Shatter the shield
                    p.setCooldown(Material.SHIELD, shieldBreakCooldown);
                    p.playSound(p.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.5f, 0.8f);

                    // Push player backward 4 blocks safely to reset position
                    Vector push = p.getLocation().toVector().subtract(loc.toVector()).normalize();
                    if (Double.isNaN(push.getX()) || push.lengthSquared() == 0) {
                        push = p.getLocation().getDirection().multiply(-1);
                    }
                    push.setY(0.35).multiply(1.25);
                    p.setVelocity(push);

                    // SHIELD GATE: Absorb all true damage and cap physical damage to shieldGateCap Max HP
                    double maxHp = 20.0;
                    if (p.getAttribute(AttributeUtils.MAX_HEALTH) != null) {
                        maxHp = p.getAttribute(AttributeUtils.MAX_HEALTH).getValue();
                    }
                    double gateDamage = Math.min(25.0, maxHp * shieldGateCap);
                    p.damage(gateDamage, boss);

                    p.sendMessage(ColorUtils.colorize("&e&l🛡 [SHIELD GATE] &fYour shield absorbed the lethal impact, but shattered under the force! &7(Shield disabled for " + (shieldBreakCooldown / 20) + "s)"));
                    p.sendActionBar(ColorUtils.toAdventure("&e&l🛡 [SHIELD GATE] &fLethal impact absorbed! &cShield broken for " + (shieldBreakCooldown / 20) + "s!"));
                }
                // -------------------------------------------------------------
                // CASE 2: Player is NOT Blocking (Direct Hit)
                // -------------------------------------------------------------
                else {
                    double maxHp = 20.0;
                    if (p.getAttribute(AttributeUtils.MAX_HEALTH) != null) {
                        maxHp = p.getAttribute(AttributeUtils.MAX_HEALTH).getValue();
                    }
                    // Safety cap: Cannot exceed directHitCap Max HP in one hit
                    double heavyDamage = Math.min(bossDmg * 1.5, maxHp * directHitCap);
                    p.damage(heavyDamage, boss);

                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 1, false, false));
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.5f, 0.8f);

                    Vector push = p.getLocation().toVector().subtract(loc.toVector()).normalize();
                    if (Double.isNaN(push.getX()) || push.lengthSquared() == 0) {
                        push = p.getLocation().getDirection().multiply(-1);
                    }
                    push.setY(0.30).multiply(0.9);
                    p.setVelocity(push);

                    p.sendMessage(ColorUtils.colorize("&c&l💥 [CRUSHING BLOW] &cYou took a direct hit from " + bossName + "'s heavy strike! &7(-" + (int) heavyDamage + " DMG)"));
                    p.sendActionBar(ColorUtils.toAdventure("&c&l💥 [CRUSHING BLOW] &cDirect hit taken! &7(-" + (int) heavyDamage + " DMG)"));
                }
            } else if (distSq <= 100.0) {
                // -------------------------------------------------------------
                // CASE 3: Player Dodged outside telegraphRadius
                // -------------------------------------------------------------
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 1.2f);
                p.sendActionBar(ColorUtils.toAdventure("&a&l✔ [DODGED] &fYou successfully dodged " + bossName + "'s Crushing Blow!"));
            }
        }

        // -----------------------------------------------------------------
        // PUNISH WINDOW: Boss missed everyone and is temporarily off-balance!
        // -----------------------------------------------------------------
        if (!anyPlayerHit) {
            boss.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 5, false, false));
            loc.getWorld().playSound(loc, Sound.ENTITY_BLAZE_HURT, 1.0f, 0.7f);

            for (Player p : nearby) {
                p.sendActionBar(ColorUtils.toAdventure("&a&l⚔ [PUNISH WINDOW] &e" + bossName + " &amissed and is recovering! STRIKE NOW!"));
            }
        }
    }

    private static void cleanup(LivingEntity boss) {
        if (boss != null && boss.hasMetadata(METADATA_KEY)) {
            boss.removeMetadata(METADATA_KEY, boss.getServer().getPluginManager().getPlugin("MobArenav2"));
        }
    }

    private static List<Player> getNearbyPlayers(Arena arena, LivingEntity boss, double radius) {
        List<Player> targets = new ArrayList<>();
        if (arena != null) {
            for (Player p : arena.getPlayersAsObjects()) {
                if (p.isOnline() && !p.isDead() && p.getLocation().getWorld() == boss.getLocation().getWorld()
                        && p.getLocation().distanceSquared(boss.getLocation()) <= radius * radius) {
                    targets.add(p);
                }
            }
        } else if (boss.getWorld() != null) {
            targets.addAll(boss.getWorld().getNearbyPlayers(boss.getLocation(), radius));
        }
        return targets;
    }
}
