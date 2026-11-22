package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.service.CombatDamageService;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;
import vn.meowchan12.mobarenav2.utils.ColorUtils;
import vn.meowchan12.mobarenav2.utils.ParticleUtils;

import java.util.List;

/**
 * [v8.3.0 TACTICAL BOSS SKILL]: SHIELD_BREAK_BURST (DPS Check Mechanic)
 * - Boss channels a massive protective barrier (default 800 Shield HP) for 5 seconds.
 * - Players must group DPS and break the shield before time expires.
 * - Success: Boss is Stunned for 4s, receives +50% incoming damage.
 * - Failure: World Ender Shockwave deals 60% Max HP true damage to all players in arena.
 */
public class ShieldBreakBurstSkill implements BossSkill {

    public static final String META_SHIELD_ACTIVE = "MA_SHIELD_BREAK_ACTIVE";
    public static final String META_SHIELD_HP = "MA_SHIELD_HP";
    public static final String META_STUN_BONUS = "MA_STUN_DAMAGE_VULN";

    @Override
    public String getType() {
        return "SHIELD_BREAK_BURST";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection skillConfig) {
        if (boss == null || !boss.isValid() || boss.isDead()) return;

        double shieldHp = (skillConfig != null) ? skillConfig.getDouble("shield-hp", 800.0) : 800.0;
        int durationSec = (skillConfig != null) ? skillConfig.getInt("duration", 5) : 5;
        double failDamagePercent = (skillConfig != null) ? skillConfig.getDouble("fail-damage-percent", 0.60) : 0.60;

        String bossName = boss.getCustomName() != null ? boss.getCustomName() : boss.getName();

        // 1. Tag Boss with Shield Metadata
        boss.setMetadata(META_SHIELD_ACTIVE, new FixedMetadataValue(plugin, true));
        boss.setMetadata(META_SHIELD_HP, new FixedMetadataValue(plugin, shieldHp));

        // Root boss in place during channel
        boss.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationSec * 20, 255, false, false));

        // 2. Broadcast Warning (Strict English)
        String warningMsg = ColorUtils.colorize("&c&l⚠ [DPS CHECK] " + bossName + " &cis channeling an apocalyptic shockwave!\n" +
                "&e&l⚡ BREAK THE SHIELD (" + (int) shieldHp + " HP) IN " + durationSec + "s OR FACE OBLIVION!");

        if (arena != null) {
            arena.broadcastMessage(warningMsg);
        } else {
            for (Player p : getNearbyTargets(arena, boss, 50.0)) {
                p.sendMessage(warningMsg);
            }
        }

        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 0.6f);

        // 3. Channeling Loop
        new BukkitRunnable() {
            int ticksLeft = durationSec * 20;

            @Override
            public void run() {
                if (!boss.isValid() || boss.isDead()) {
                    cancel();
                    return;
                }

                // Check if broken
                if (!boss.hasMetadata(META_SHIELD_ACTIVE)) {
                    cancel();
                    return;
                }

                double currentShield = boss.hasMetadata(META_SHIELD_HP) ?
                        boss.getMetadata(META_SHIELD_HP).get(0).asDouble() : 0.0;

                if (currentShield <= 0) {
                    // SUCCESS: Shield broken!
                    boss.removeMetadata(META_SHIELD_ACTIVE, plugin);
                    boss.removeMetadata(META_SHIELD_HP, plugin);
                    boss.removePotionEffect(PotionEffectType.SLOWNESS);

                    // Apply Stun + Vulnerability for 4s
                    boss.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 255, false, false));
                    boss.setMetadata(META_STUN_BONUS, new FixedMetadataValue(plugin, true));

                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (boss.isValid()) boss.removeMetadata(META_STUN_BONUS, plugin);
                    }, 80L);

                    Location loc = boss.getLocation();
                    loc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, loc.add(0, 1.5, 0), 2);
                    loc.getWorld().playSound(loc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.5f, 0.6f);
                    loc.getWorld().playSound(loc, Sound.ITEM_SHIELD_BREAK, 1.5f, 1.2f);

                    String successMsg = ColorUtils.colorize("&a&l✔ [SHIELD SHATTERED] &fWarriors broke the barrier! " +
                            bossName + " &fis &e&lSTUNNED &ffor 4s! (+50% DMG Taken)");
                    if (arena != null) arena.broadcastMessage(successMsg);
                    cancel();
                    return;
                }

                // Visual barrier sphere
                Location bLoc = boss.getLocation().add(0, 1.2, 0);
                bLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, bLoc, 8, 1.2, 1.2, 1.2, 0.05);

                ticksLeft -= 5;
                if (ticksLeft <= 0) {
                    // FAILURE: World Ender Shockwave unleashed!
                    boss.removeMetadata(META_SHIELD_ACTIVE, plugin);
                    boss.removeMetadata(META_SHIELD_HP, plugin);
                    boss.removePotionEffect(PotionEffectType.SLOWNESS);

                    Location loc = boss.getLocation();
                    ParticleUtils.spawnSafeParticle(loc.getWorld(), Particle.FLASH, loc.add(0, 1.0, 0), 5);
                    ParticleUtils.spawnSafeParticle(loc.getWorld(), Particle.EXPLOSION, loc, 5, 2.0, 1.0, 2.0, 0.1);
                    ParticleUtils.spawnSafeParticle(loc.getWorld(), Particle.SOUL_FIRE_FLAME, loc, 80, 4.0, 1.0, 4.0, 0.2);
                    ParticleUtils.spawnSafeParticle(loc.getWorld(), Particle.LAVA, loc, 30, 3.0, 1.0, 3.0, 0.0);
                    loc.getWorld().playSound(loc, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);
                    loc.getWorld().playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.5f, 0.8f);

                    String failMsg = ColorUtils.colorize("&4&l☠ [CATASTROPHIC FAILURE] &cShield was not broken in time! World Ender Shockwave unleashed!");
                    if (arena != null) arena.broadcastMessage(failMsg);

                    List<Player> targets = getNearbyTargets(arena, boss, 64.0);
                    for (Player p : targets) {
                        double maxHp = p.getAttribute(AttributeUtils.MAX_HEALTH).getValue();
                        double shockDmg = maxHp * failDamagePercent;
                        CombatDamageService.applyLayeredDamage(p, shockDmg, true, boss, arena, plugin);
                        p.sendMessage(ColorUtils.colorize("&c&l☠ You took &e" + (int) shockDmg + " True Damage &cfrom the unbroken shockwave!"));
                        p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1.0f, 0.5f);
                    }
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 5L, 5L);
    }
}
