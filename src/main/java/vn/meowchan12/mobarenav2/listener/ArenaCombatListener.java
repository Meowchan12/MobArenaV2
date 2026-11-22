package vn.meowchan12.mobarenav2.listener;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
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
    private final vn.meowchan12.mobarenav2.boss.BossCounterMeta counterMeta;

    // [v8.0.5 OPTIMIZATION]: Cache NamespacedKeys to eliminate GC allocations on every combat event
    private final NamespacedKey dmgKey;
    private final NamespacedKey arrowDmgKey;
    private final NamespacedKey bossKey;
    private final NamespacedKey arrowPenKey;

    public ArenaCombatListener(MobArenav2 plugin) {
        this.plugin = plugin;
        this.counterMeta = new vn.meowchan12.mobarenav2.boss.BossCounterMeta(plugin);
        this.dmgKey = new NamespacedKey(plugin, "ma_damage_bonus");
        this.arrowDmgKey = new NamespacedKey(plugin, "ma_arrow_bonus_damage");
        this.bossKey = new NamespacedKey(plugin, "arena_boss_id");
        this.arrowPenKey = new NamespacedKey(plugin, "ma_arrow_penetration");
    }

    // ==========================================
    // [v7.2 REWORK] UNIFIED DYNAMIC COMBAT & DAMAGE SYSTEM
    // Fixes abnormal continuous damage bug (I-Frames) and balances late-game protection scaling
    // ==========================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobAttackPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena == null || !arena.isRunning() || arena.getSpectators().contains(player.getUniqueId())) return;

        // =====================================================================
        // [v7.2 FIX]: I-FRAME CADENCE & CONTINUOUS DAMAGE PREVENTION
        // Respects Minecraft's invulnerability frames (10-tick / 0.5s recovery).
        // Prevents multi-mob swarms from rapidly melting the player in milliseconds!
        // =====================================================================
        if (player.getNoDamageTicks() > 10) {
            event.setCancelled(true);
            return;
        }

        Entity damagerEntity = event.getDamager();
        LivingEntity attacker = null;

        if (damagerEntity instanceof LivingEntity) {
            attacker = (LivingEntity) damagerEntity;
        } else if (damagerEntity instanceof Projectile proj && proj.getShooter() instanceof LivingEntity) {
            attacker = (LivingEntity) proj.getShooter();
        } else if (damagerEntity instanceof org.bukkit.entity.EvokerFangs fangs && fangs.getOwner() != null) {
            attacker = fangs.getOwner();
        }

        if (attacker != null) {
            if (!attacker.hasMetadata("MA_MOB")) return;

            double rawDamage = event.getDamage();
            double totalDamage = rawDamage;
            double configTrueBonus = 0.0;
            String bonusStr = null;

            if (attacker.getPersistentDataContainer().has(dmgKey, PersistentDataType.STRING)) {
                bonusStr = attacker.getPersistentDataContainer().get(dmgKey, PersistentDataType.STRING);
            } else if (damagerEntity instanceof Projectile proj && proj.getPersistentDataContainer().has(dmgKey, PersistentDataType.STRING)) {
                bonusStr = proj.getPersistentDataContainer().get(dmgKey, PersistentDataType.STRING);
            }

            if (bonusStr != null) {
                try {
                    if (bonusStr.startsWith("x")) {
                        double multiplier = Double.parseDouble(bonusStr.replace("x", "").trim());
                        if (damagerEntity instanceof Projectile) {
                            totalDamage = rawDamage * multiplier;
                        }
                        configTrueBonus = Math.min(0.15, (multiplier - 1.0) * 0.10);
                    } else if (bonusStr.startsWith("+")) {
                        double addValue = Double.parseDouble(bonusStr.replace("+", "").trim());
                        if (damagerEntity instanceof Projectile) {
                            totalDamage = rawDamage + addValue;
                        }
                        configTrueBonus = Math.min(0.15, addValue * 0.015);
                    }
                } catch (NumberFormatException ignored) {}
            }

            // =====================================================================
            // [v7.2 SYSTEM]: MOB TYPE & PROJECTILE CLASSIFICATION
            // =====================================================================
            double baseTrueDamageRatio = 0.10; // Default melee: 10% True Damage
            boolean isBlazeFireball = false;
            boolean isBreezeWindCharge = false;
            boolean isArrow = false;

            if (damagerEntity instanceof org.bukkit.entity.SmallFireball || attacker instanceof org.bukkit.entity.Blaze) {
                isBlazeFireball = true;
                baseTrueDamageRatio = 0.30; // Blaze: 30% Thermobaric Fire True Damage
            } else if (damagerEntity instanceof org.bukkit.entity.WindCharge || damagerEntity instanceof org.bukkit.entity.BreezeWindCharge || attacker instanceof org.bukkit.entity.Breeze) {
                isBreezeWindCharge = true;
                baseTrueDamageRatio = 0.30; // Breeze: 30% Kinetic Shockwave True Damage
            } else if (damagerEntity instanceof org.bukkit.entity.LargeFireball || damagerEntity instanceof org.bukkit.entity.Fireball || attacker instanceof org.bukkit.entity.Ghast) {
                baseTrueDamageRatio = 0.35; // Ghast: 35% Artillery True Damage
            } else if (damagerEntity instanceof Projectile proj) {
                isArrow = true;
                baseTrueDamageRatio = 0.20; // Ranged arrows: 20% Base True Damage
                if (proj.getPersistentDataContainer().has(arrowDmgKey, PersistentDataType.DOUBLE)) {
                    Double arrowMult = proj.getPersistentDataContainer().get(arrowDmgKey, PersistentDataType.DOUBLE);
                    if (arrowMult != null && arrowMult > 1.0) {
                        totalDamage = rawDamage * arrowMult;
                    }
                }
            } else if (damagerEntity instanceof org.bukkit.entity.EvokerFangs || attacker instanceof org.bukkit.entity.Evoker) {
                // [v8.0.4 EVOKER FANG BUFF]
                baseTrueDamageRatio = 0.40;
                double baseFangDmg = Math.max(26.0, 18.0 + arena.getCurrentWave() * 0.6);
                totalDamage = baseFangDmg;
                if (bonusStr != null) {
                    try {
                        if (bonusStr.startsWith("x")) {
                            double multiplier = Double.parseDouble(bonusStr.replace("x", "").trim());
                            totalDamage = baseFangDmg * multiplier;
                            configTrueBonus = Math.min(0.20, (multiplier - 1.0) * 0.10);
                        } else if (bonusStr.startsWith("+")) {
                            double addValue = Double.parseDouble(bonusStr.replace("+", "").trim());
                            totalDamage = baseFangDmg + addValue;
                            configTrueBonus = Math.min(0.20, addValue * 0.015);
                        }
                    } catch (NumberFormatException ignored) {}
                }
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS, 60, 1, false, false));
                player.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 40, 0, false, false));
                player.getWorld().spawnParticle(Particle.CRIT, player.getLocation().add(0, 0.5, 0), 15, 0.3, 0.3, 0.3, 0.2);
                player.playSound(player.getLocation(), Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.3f, 0.8f);
            } else if (attacker instanceof org.bukkit.entity.PiglinBrute || attacker instanceof org.bukkit.entity.Ravager || attacker instanceof org.bukkit.entity.IronGolem) {
                baseTrueDamageRatio = 0.20; // Heavy Melee: 20% Base True Damage
            } else if (attacker instanceof org.bukkit.entity.WitherSkeleton) {
                baseTrueDamageRatio = 0.15; // Wither Skeleton: 15% Base True Damage + Wither DoT
            }

            // 1. WAVE SCALING PENETRATION: +1.0% per wave after Wave 5, capped at 15%
            double waveBonus = Math.min(0.15, Math.max(0.0, (arena.getCurrentWave() - 5) * 0.01));

            // 2. BOSS BONUS: Bosses deal configured or default True Damage (v8.3.0 Rework)
            double bossBonus = 0.0;
            boolean isBossAttacker = attacker.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING) || attacker.hasMetadata("MA_BOSS");
            if (isBossAttacker) {
                NamespacedKey meleePenKey = new NamespacedKey(plugin, "ma_boss_melee_pen");
                bossBonus = attacker.getPersistentDataContainer().getOrDefault(meleePenKey, PersistentDataType.DOUBLE, 0.35);
            }

            // 3. FINAL TRUE DAMAGE PERCENT
            double finalTrueDamagePercent = isBossAttacker ?
                    Math.min(0.70, Math.max(bossBonus, baseTrueDamageRatio + waveBonus + configTrueBonus)) :
                    Math.min(0.45, baseTrueDamageRatio + waveBonus + configTrueBonus);

            // =====================================================================
            // [UPGRADE PASSIVE]: DAMAGE_REDUCTION & GLASS_CANNON
            // =====================================================================
            ArenaPlayerProfile playerProfile = arena.getPlayerProfiles().get(player.getUniqueId());
            if (playerProfile != null) {
                double dmgReduction = playerProfile.getUpgradeValue("DAMAGE_REDUCTION", plugin);
                if (dmgReduction > 0) {
                    double factor = Math.max(0.30, 1.0 - (dmgReduction / 100.0));
                    totalDamage *= factor;
                }
                double glassCannon = playerProfile.getUpgradeValue("GLASS_CANNON", plugin);
                if (glassCannon > 0) {
                    totalDamage *= 1.15;
                }
                if (playerProfile.getUpgradeLevel("iron_skin") >= 4) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (player.isOnline()) {
                            player.setVelocity(player.getVelocity().multiply(0.5));
                        }
                    });
                }
            }

            // =====================================================================
            // SPECIAL TRAITS APPLICATION
            // =====================================================================
            double trueDamageAmount = totalDamage * finalTrueDamagePercent;
            double physicalDamage = totalDamage - trueDamageAmount;

            if (isBlazeFireball) {
                if (player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) {
                    trueDamageAmount *= 0.50; // Fire Resistance mitigates 50% of fire true damage
                }
                player.setFireTicks(Math.max(player.getFireTicks(), 80)); // 4 seconds of fire
            } else if (isBreezeWindCharge) {
                player.setVelocity(player.getVelocity().setY(0.80)); // Wind burst knock-up
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 1.0f);
            } else if (isArrow && attacker instanceof org.bukkit.entity.Stray) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true));
            } else if (attacker instanceof org.bukkit.entity.WitherSkeleton) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1, false, true));
            }

            // [v5.6] Boss Arrow Penetration override if present
            if (damagerEntity instanceof Projectile proj) {
                if (proj.getPersistentDataContainer().has(arrowPenKey, PersistentDataType.DOUBLE)) {
                    Double extraPen = proj.getPersistentDataContainer().get(arrowPenKey, PersistentDataType.DOUBLE);
                    if (extraPen != null && extraPen > 0) {
                        trueDamageAmount = Math.min(totalDamage * 0.50, trueDamageAmount + (totalDamage * extraPen));
                        physicalDamage = totalDamage - trueDamageAmount;
                    }
                }
            }

            // [v8.3.0]: Boss Melee Special Traits (Max HP Shred, Crushing Blow, Blood Frenzy)
            if (isBossAttacker && !(damagerEntity instanceof Projectile)) {
                // Max HP Shred
                NamespacedKey shredKey = new NamespacedKey(plugin, "ma_boss_max_hp_shred");
                if (attacker.getPersistentDataContainer().has(shredKey, PersistentDataType.DOUBLE)) {
                    double shredRatio = attacker.getPersistentDataContainer().get(shredKey, PersistentDataType.DOUBLE);
                    if (shredRatio > 0) {
                        double shredDmg = player.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH).getValue() * shredRatio;
                        trueDamageAmount += shredDmg;
                    }
                }

                // Blood Frenzy Lifesteal (reads configured lifesteal percent)
                if (attacker.hasMetadata("MA_BLOOD_FRENZY")) {
                    AttributeInstance bHpAttr = attacker.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH);
                    if (bHpAttr != null) {
                        double lifestealRatio = 0.01;
                        if (!attacker.getMetadata("MA_BLOOD_FRENZY").isEmpty()) {
                            Object metaVal = attacker.getMetadata("MA_BLOOD_FRENZY").get(0).value();
                            if (metaVal instanceof Number num) {
                                lifestealRatio = num.doubleValue() / 100.0;
                            }
                        }
                        double heal = bHpAttr.getValue() * lifestealRatio;
                        attacker.setHealth(Math.min(bHpAttr.getValue(), attacker.getHealth() + heal));
                        attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 2.0, 0), 2, 0.4, 0.4, 0.4, 0.05);
                        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PHANTOM_BITE, 0.8f, 1.2f);
                    }
                }
            }

            // [v8.3.1 TACTICAL GUARD]: Shield blocks absorb 70% of basic true damage from boss hits
            // (Shield break is now exclusively reserved for the 2.0-2.5s telegraphed Crushing Blow)
            if (player.isBlocking()) {
                trueDamageAmount *= 0.30;
                player.getWorld().playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
            }

            event.setDamage(Math.max(0.1, physicalDamage));

            // [v7.4.0 COMBAT REWORK]: True Damage is routed through CombatDamageService (Absorption consumed first!)
            if (trueDamageAmount > 0) {
                vn.meowchan12.mobarenav2.service.CombatDamageService.applyLayeredDamage(player, trueDamageAmount, true, attacker, arena, plugin);
            }

            // Grant standard invulnerability recovery window (20 ticks = 10 tick immunity)
            player.setNoDamageTicks(20);

            // =====================================================================
            // [v7.3 DECOUPLED]: Điều phối các Upgrade phòng thủ qua UpgradeRegistry
            // (Thorns Aura, Second Wind, Iron Skin, Phantom Barrier, Super Aegis...)
            // =====================================================================
            if (plugin.getUpgradeRegistry() != null) {
                plugin.getUpgradeRegistry().dispatchDefend(event, player, attacker, arena);
            }
        }
    }

    @SuppressWarnings({"deprecation", "removal"})
    private void playTotemEffect(Player player) {
        player.playEffect(org.bukkit.EntityEffect.TOTEM_RESURRECT);
    }
}