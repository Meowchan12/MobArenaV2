package vn.meowchan12.mobarenav2.listener;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;

import java.util.concurrent.ThreadLocalRandom;

public class ArenaCombatListener implements Listener {

    private final MobArenav2 plugin;

    public ArenaCombatListener(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    // ==========================================
    // [FIX BUG #7] THUẬT TOÁN XUYÊN GIÁP & NHÂN SÁT THƯƠNG
    // ==========================================
    // =====================================================================
    // [v5.2 REBALANCE]: SMART DYNAMIC ARMOR PENETRATION ALGORITHM
    // Dynamic True Damage counters Netherite Protection IV tankiness
    // =====================================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobAttackPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena == null || !arena.isRunning() || arena.getSpectators().contains(player.getUniqueId())) return;

        Entity damagerEntity = event.getDamager();
        LivingEntity attacker = null;

        if (damagerEntity instanceof LivingEntity) {
            attacker = (LivingEntity) damagerEntity;
        } else if (damagerEntity instanceof Projectile proj && proj.getShooter() instanceof LivingEntity) {
            attacker = (LivingEntity) proj.getShooter();
        }

        if (attacker != null) {
            NamespacedKey dmgKey = new NamespacedKey(plugin, "ma_damage_bonus");
            double rawDamage = event.getDamage();
            double totalDamage = rawDamage;
            double baseTrueDamagePercent = 0.0;

            if (attacker.getPersistentDataContainer().has(dmgKey, PersistentDataType.STRING)) {
                String bonusStr = attacker.getPersistentDataContainer().get(dmgKey, PersistentDataType.STRING);
                try {
                    if (bonusStr.startsWith("x")) {
                        double multiplier = Double.parseDouble(bonusStr.replace("x", "").trim());
                        totalDamage = rawDamage * multiplier;
                        baseTrueDamagePercent = Math.min(0.35, (multiplier - 1.0) * 0.12);
                    } else if (bonusStr.startsWith("+")) {
                        double addValue = Double.parseDouble(bonusStr.replace("+", "").trim());
                        totalDamage = rawDamage + addValue;
                        baseTrueDamagePercent = Math.min(0.35, addValue * 0.025);
                    }
                } catch (NumberFormatException ignored) {}
            }

            // 1. WAVE SCALING PENETRATION: +1.5% per wave after Wave 5
            double waveBonus = Math.max(0.0, (arena.getCurrentWave() - 5) * 0.02);

            // 2. ARMOR COUNTER BONUS: Evaluates target's armor points (Netherite/Diamond Prot IV)
            double armorPoints = 0.0;
            if (player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ARMOR) != null) {
                armorPoints = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_ARMOR).getValue();
            }
            double armorCounterBonus = 0.0;
            if (armorPoints > 10.0) {
                armorCounterBonus = Math.min(0.30, ((armorPoints - 10.0) / 20.0) * 0.25);
            }

            // 3. BOSS BONUS: Bosses deal +15% innate True Damage
            double bossBonus = 0.0;
            NamespacedKey bKey = new NamespacedKey(plugin, "arena_boss_id");
            if (attacker.getPersistentDataContainer().has(bKey, PersistentDataType.STRING)) {
                bossBonus = 0.15;
            }

            // FINAL TRUE DAMAGE PERCENT (Capped at 70%)
            double finalTrueDamagePercent = Math.min(0.70, baseTrueDamagePercent + waveBonus + armorCounterBonus + bossBonus);

            // =====================================================================
            // [v5.3 FEATURE]: RANGED PROJECTILE DAMAGE MULTIPLIER & PENETRATION
            // Skeletons, Pillagers, and Ranged Mobs deal boosted arrow damage
            // =====================================================================
            if (damagerEntity instanceof Projectile proj) {
                NamespacedKey arrowDmgKey = new NamespacedKey(plugin, "ma_arrow_bonus_damage");
                if (proj.getPersistentDataContainer().has(arrowDmgKey, PersistentDataType.DOUBLE)) {
                    Double arrowMult = proj.getPersistentDataContainer().get(arrowDmgKey, PersistentDataType.DOUBLE);
                    if (arrowMult != null && arrowMult > 1.0) {
                        totalDamage = rawDamage * arrowMult;
                    }
                }

                // Ranged projectiles deal 25% True Damage + physical damage
                double trueDamageAmount = totalDamage * Math.min(0.60, 0.35 + waveBonus);
                double physicalDamage = totalDamage - trueDamageAmount;

                // [v5.6] Boss Arrow Penetration - additional true damage bypass
                NamespacedKey arrowPenKey = new NamespacedKey(plugin, "ma_arrow_penetration");
                if (proj.getPersistentDataContainer().has(arrowPenKey, PersistentDataType.DOUBLE)) {
                    Double extraPen = proj.getPersistentDataContainer().get(arrowPenKey, PersistentDataType.DOUBLE);
                    if (extraPen != null && extraPen > 0) {
                        trueDamageAmount += totalDamage * extraPen;
                        physicalDamage = totalDamage - trueDamageAmount;
                    }
                }

                event.setDamage(physicalDamage);

                if (trueDamageAmount > 0) {
                    double newHp = player.getHealth() - trueDamageAmount;
                    if (newHp <= 0) {
                        event.setCancelled(true);
                        plugin.getArenaManager().handlePlayerDeath(player, arena);
                        return;
                    } else {
                        player.setHealth(newHp);
                    }
                }
            } else {
                double trueDamageAmount = totalDamage * finalTrueDamagePercent;
                double physicalDamage = totalDamage - trueDamageAmount;

                event.setDamage(physicalDamage);

                if (trueDamageAmount > 0) {
                    double newHp = player.getHealth() - trueDamageAmount;
                    if (newHp <= 0) {
                        event.setCancelled(true);
                        plugin.getArenaManager().handlePlayerDeath(player, arena);
                        return;
                    } else {
                        player.setHealth(newHp);
                    }
                }

                // =====================================================================
                // [v5.2 UPGRADE]: THORNS_AURA (SPIKE RETALIATION)
                // Reflects % damage back to attacker & Stuns at Lv5 Capstone
                // =====================================================================
                ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());
                if (profile != null) {
                    double thornsPercent = profile.getUpgradeValue("THORNS_AURA", plugin);
                    if (thornsPercent > 0 && physicalDamage > 0) {
                        double reflectedDmg = physicalDamage * (thornsPercent / 100.0);
                        attacker.damage(reflectedDmg, player);
                        attacker.getWorld().spawnParticle(Particle.CRIT, attacker.getLocation().add(0, 1, 0), 5);

                        int thornsLvl = profile.getUpgradeLevel("thorns_aura");
                        if (thornsLvl >= 5 && ThreadLocalRandom.current().nextDouble(100.0) <= 25.0) {
                            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 30, 3, false, false));
                            attacker.getWorld().spawnParticle(Particle.SNOWFLAKE, attacker.getLocation().add(0, 1, 0), 10);
                        }
                    }

                    // =====================================================================
                    // [v5.2 UPGRADE]: SECOND_WIND (EMERGENCY SHIELD)
                    // Grants Resistance + Regen when HP drops below 25%
                    // =====================================================================
                    double maxHp = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null ?
                                   player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue() : 20.0;
                    if (player.getHealth() <= maxHp * 0.25) {
                        int swLvl = profile.getUpgradeLevel("second_wind");
                        if (swLvl > 0) {
                            NamespacedKey swCooldownKey = new NamespacedKey(plugin, "ma_second_wind_cd");
                            Long lastUsed = player.getPersistentDataContainer().get(swCooldownKey, PersistentDataType.LONG);
                            if (lastUsed == null || System.currentTimeMillis() - lastUsed >= 60000) {
                                player.getPersistentDataContainer().set(swCooldownKey, PersistentDataType.LONG, System.currentTimeMillis());
                                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 120, 1, false, true));
                                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 120, 1, false, true));
                                player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 1.2f);
                                player.sendMessage("§0§l[SECOND WIND] §bEmergency shield activated! (60s Cooldown)");

                                if (swLvl >= 3) { // Capstone knockback wave
                                    for (Entity near : player.getNearbyEntities(4, 4, 4)) {
                                        if (near instanceof LivingEntity mobTarget && mobTarget.hasMetadata("MA_MOB")) {
                                            Vector push = mobTarget.getLocation().toVector().subtract(player.getLocation().toVector()).normalize().multiply(1.5).setY(0.5);
                                            mobTarget.setVelocity(push);
                                        }
                                    }
                                    player.getWorld().spawnParticle(Particle.EXPLOSION, player.getLocation().add(0, 1, 0), 1);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==========================================
    // HỆ THỐNG CHẾT GIẢ (FAKE DEATH)
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena == null || !arena.isRunning()) return;

        if (arena.getSpectators().contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (player.getHealth() - event.getFinalDamage() <= 0) {
            event.setCancelled(true);
            plugin.getArenaManager().handlePlayerDeath(player, arena);
        }
    }
}