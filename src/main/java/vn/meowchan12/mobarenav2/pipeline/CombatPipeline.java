package vn.meowchan12.mobarenav2.pipeline;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;
import vn.meowchan12.mobarenav2.boss.BossCounterMeta;
import vn.meowchan12.mobarenav2.service.CombatDamageService;
import vn.meowchan12.mobarenav2.upgrade.UpgradeRegistry;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * [v8.1.0]: Master Unified Combat Pipeline.
 * Consolidates all combat logic into 4 cleanly decoupled phases:
 * 1. Pre-Check (Arena status, spectators, i-frames, wind burst guarantee, boss counter-meta)
 * 2. Damage Calculation (Base damage, shop stats, active upgrades, multipliers)
 * 3. Mitigation (Defensive upgrades, shields, armor penetration, absorption)
 * 4. Post-Effects (Lifesteal, reflections, sound effects, particle feedback, kill triggers)
 */
public class CombatPipeline implements Listener {

    private final MobArenav2 plugin;
    private final BossCounterMeta counterMeta;

    // Keys
    private final NamespacedKey bowSpeedKey;
    private final NamespacedKey explosionKey;
    private final NamespacedKey saitamaKey;
    private final NamespacedKey lifestealKey;
    private final NamespacedKey invulnKey;

    public CombatPipeline(MobArenav2 plugin) {
        this.plugin = plugin;
        this.counterMeta = new BossCounterMeta(plugin);

        this.bowSpeedKey = new NamespacedKey(plugin, "bow_speed_bonus");
        this.explosionKey = new NamespacedKey(plugin, "explosion_bonus");
        this.saitamaKey = new NamespacedKey(plugin, "ma_saitama_punch");
        this.lifestealKey = new NamespacedKey(plugin, "ma_lifesteal");
        this.invulnKey = new NamespacedKey(plugin, "ma_invulnerable");
    }

    // =========================================================================
    // MASTER EVENT DISPATCHER
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombatDamage(EntityDamageByEntityEvent event) {
        Entity damagerEntity = event.getDamager();
        Entity victimEntity = event.getEntity();

        // 1. MOB ATTACKING PLAYER
        if (victimEntity instanceof Player player) {
            LivingEntity attacker = resolveLivingEntity(damagerEntity);
            if (attacker != null && attacker.hasMetadata("MA_MOB")) {
                handleMobAttackPlayerPipeline(event, attacker, damagerEntity, player);
                return;
            }
        }

        // 2. PLAYER ATTACKING MOB OR BOSS
        if (victimEntity instanceof LivingEntity victim && victim.hasMetadata("MA_MOB")) {
            Player attacker = resolvePlayer(damagerEntity);
            if (attacker != null) {
                handlePlayerAttackMobPipeline(event, attacker, damagerEntity, victim);
            }
        }
    }

    // =========================================================================
    // PIPELINE A: MOB / BOSS ATTACKS PLAYER
    // =========================================================================
    private void handleMobAttackPlayerPipeline(EntityDamageByEntityEvent event, LivingEntity attacker, Entity damagerEntity, Player player) {
        // --- PHASE 1: PRE-CHECK ---
        Arena arena = plugin.getArenaManager().getArenaByPlayer(player);
        if (arena == null || !arena.isRunning() || arena.getSpectators().contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        // I-Frame cadence protection: respect 10-tick recovery window
        if (player.getNoDamageTicks() > 10) {
            event.setCancelled(true);
            return;
        }

        ArenaPlayerProfile profile = arena.getPlayerProfiles().get(player.getUniqueId());

        // Player Dodge Check (Shop Stats + Upgrades)
        double shopDodge = (plugin.getShopManager() != null) ? plugin.getShopManager().getCustomStatBonus(player.getUniqueId(), "CUSTOM_DODGE") : 0.0;
        double dodgeChance = (profile != null ? profile.getUpgradeValue("DODGE", plugin) : 0.0) + shopDodge;
        if (dodgeChance > 0 && ThreadLocalRandom.current().nextDouble(100.0) <= dodgeChance) {
            event.setCancelled(true);
            player.sendMessage("§a§lDODGED!");
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 2.0f);
            return;
        }

        // --- PHASE 2: DAMAGE CALCULATION ---
        NamespacedKey dmgKey = new NamespacedKey(plugin, "ma_damage_bonus");
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
                    if (damagerEntity instanceof Projectile) totalDamage = rawDamage * multiplier;
                    configTrueBonus = Math.min(0.15, (multiplier - 1.0) * 0.10);
                } else if (bonusStr.startsWith("+")) {
                    double addValue = Double.parseDouble(bonusStr.replace("+", "").trim());
                    if (damagerEntity instanceof Projectile) totalDamage = rawDamage + addValue;
                    configTrueBonus = Math.min(0.15, addValue * 0.015);
                }
            } catch (NumberFormatException ignored) {}
        }

        // Projectile & Mob Type classification
        double baseTrueDamageRatio = 0.10;
        boolean isBlazeFireball = false;
        boolean isBreezeWindCharge = false;
        boolean isArrow = false;

        if (damagerEntity instanceof org.bukkit.entity.SmallFireball || attacker instanceof org.bukkit.entity.Blaze) {
            isBlazeFireball = true;
            baseTrueDamageRatio = 0.30;
        } else if (damagerEntity instanceof org.bukkit.entity.WindCharge || damagerEntity instanceof org.bukkit.entity.BreezeWindCharge || attacker instanceof org.bukkit.entity.Breeze) {
            isBreezeWindCharge = true;
            baseTrueDamageRatio = 0.30;
        } else if (damagerEntity instanceof org.bukkit.entity.LargeFireball || damagerEntity instanceof org.bukkit.entity.Fireball || attacker instanceof org.bukkit.entity.Ghast) {
            baseTrueDamageRatio = 0.35;
        } else if (damagerEntity instanceof Projectile proj) {
            isArrow = true;
            baseTrueDamageRatio = 0.20;
            NamespacedKey arrowDmgKey = new NamespacedKey(plugin, "ma_arrow_bonus_damage");
            if (proj.getPersistentDataContainer().has(arrowDmgKey, PersistentDataType.DOUBLE)) {
                Double arrowMult = proj.getPersistentDataContainer().get(arrowDmgKey, PersistentDataType.DOUBLE);
                if (arrowMult != null && arrowMult > 1.0) totalDamage = rawDamage * arrowMult;
            }
        } else if (damagerEntity instanceof org.bukkit.entity.EvokerFangs || attacker instanceof org.bukkit.entity.Evoker) {
            // [v8.0.4 EVOKER FANG BUFF]: High base damage, scaling with waves, 40% True Damage & Debuffs
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
            baseTrueDamageRatio = 0.20;
        } else if (attacker instanceof org.bukkit.entity.WitherSkeleton) {
            baseTrueDamageRatio = 0.15;
        }

        double waveBonus = Math.min(0.15, Math.max(0.0, (arena.getCurrentWave() - 5) * 0.01));
        double bossBonus = attacker.getPersistentDataContainer().has(new NamespacedKey(plugin, "arena_boss_id"), PersistentDataType.STRING) ? 0.15 : 0.0;
        double finalTrueRatio = Math.min(0.45, baseTrueDamageRatio + waveBonus + configTrueBonus + bossBonus);

        // --- PHASE 3: MITIGATION ---
        if (profile != null) {
            double dmgReduction = profile.getUpgradeValue("DAMAGE_REDUCTION", plugin);
            if (dmgReduction > 0) {
                totalDamage *= Math.max(0.30, 1.0 - (dmgReduction / 100.0));
            }
            double glassCannon = profile.getUpgradeValue("GLASS_CANNON", plugin);
            if (glassCannon > 0) {
                totalDamage *= 1.15;
            }
            if (profile.getUpgradeLevel("iron_skin") >= 4) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) player.setVelocity(player.getVelocity().multiply(0.5));
                });
            }
        }

        double trueDamageAmount = totalDamage * finalTrueRatio;
        double physicalDamage = totalDamage - trueDamageAmount;

        // Boss Saitama Punch
        if (attacker.getPersistentDataContainer().has(saitamaKey, PersistentDataType.STRING)) {
            String data = attacker.getPersistentDataContainer().get(saitamaKey, PersistentDataType.STRING);
            if (data != null) {
                String[] parts = data.split(";");
                double baseDmg = Double.parseDouble(parts[0]);
                double penRatio = parts.length > 2 ? Double.parseDouble(parts[1]) : 0.90;
                long expiry = parts.length > 2 ? Long.parseLong(parts[2]) : Long.parseLong(parts[1]);

                if (System.currentTimeMillis() <= expiry) {
                    trueDamageAmount += baseDmg * penRatio;
                    physicalDamage += baseDmg * (1.0 - penRatio);
                    player.sendMessage("§4§l[FATAL IMPACT] §cHit by Saitama Punch! (" + (int)(penRatio * 100) + "% Armor Penetration)");
                    player.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, player.getLocation().add(0, 1, 0), 1);
                    player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
                } else {
                    attacker.getPersistentDataContainer().remove(saitamaKey);
                }
            }
        }

        if (isBlazeFireball) {
            if (player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) trueDamageAmount *= 0.50;
            player.setFireTicks(Math.max(player.getFireTicks(), 80));
        } else if (isBreezeWindCharge) {
            player.setVelocity(player.getVelocity().setY(0.80));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.2f, 1.0f);
        } else if (isArrow && attacker instanceof org.bukkit.entity.Stray) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 1, false, true));
        } else if (attacker instanceof org.bukkit.entity.WitherSkeleton) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1, false, true));
        }

        event.setDamage(Math.max(0.1, physicalDamage));
        if (trueDamageAmount > 0) {
            CombatDamageService.applyLayeredDamage(player, trueDamageAmount, true, attacker, arena, plugin);
        }
        player.setNoDamageTicks(20);

        if (profile != null) {
            profile.addDamageTaken(event.getFinalDamage());
        }

        // --- PHASE 4: POST-EFFECTS & REACTION DISPATCH ---
        // Boss Lifesteal Aura
        if (attacker.getPersistentDataContainer().has(lifestealKey, PersistentDataType.STRING)) {
            String data = attacker.getPersistentDataContainer().get(lifestealKey, PersistentDataType.STRING);
            if (data != null) {
                String[] parts = data.split(";");
                double minPercent = Double.parseDouble(parts[0]);
                double maxPercent = Double.parseDouble(parts[1]);
                long expiry = Long.parseLong(parts[2]);
                if (System.currentTimeMillis() <= expiry) {
                    double healPercent = ThreadLocalRandom.current().nextDouble(minPercent, maxPercent);
                    double healAmount = event.getFinalDamage() * (healPercent / 100.0);
                    AttributeInstance maxHpAttr = attacker.getAttribute(AttributeUtils.MAX_HEALTH);
                    double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 100.0;
                    attacker.setHealth(Math.min(attacker.getHealth() + healAmount, maxHp));
                    attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 2, 0), 3, 0.5, 0.5, 0.5, 0);
                } else {
                    attacker.getPersistentDataContainer().remove(lifestealKey);
                }
            }
        }

        // Dispatch Defensive Upgrades (Phantom Barrier, Super Aegis, Thorns Aura, Retribution)
        if (plugin.getUpgradeRegistry() != null) {
            plugin.getUpgradeRegistry().dispatchDefend(event, player, attacker, arena);
        }
    }

    // =========================================================================
    // PIPELINE B: PLAYER ATTACKS MOB OR BOSS
    // =========================================================================
    private void handlePlayerAttackMobPipeline(EntityDamageByEntityEvent event, Player attacker, Entity damagerEntity, LivingEntity victim) {
        // --- PHASE 1: PRE-CHECK ---
        boolean isStandalone = victim.hasMetadata("MA_TEST_BOSS") ||
                (!victim.getMetadata("MA_MOB").isEmpty() && "STANDALONE".equals(victim.getMetadata("MA_MOB").get(0).asString()));

        Arena arena = plugin.getArenaManager().getArenaByPlayer(attacker);
        if (!isStandalone) {
            if (arena == null || !arena.isRunning() || arena.getSpectators().contains(attacker.getUniqueId())) {
                event.setCancelled(true);
                attacker.sendMessage("§c[MobArenav2] You cannot attack arena mobs from the outside!");
                return;
            }
        }

        // Anti-recursion check: secondary procs do not cascade
        if (UpgradeRegistry.isDispatchingAny()) {
            recordSecondaryDamageAndKills(event, attacker, victim, arena);
            return;
        }

        // Mace Wind Burst Vanilla Guarantee
        ItemStack mainHand = attacker.getInventory().getItemInMainHand();
        if (mainHand != null && mainHand.getType().name().equalsIgnoreCase("MACE")) {
            applyMaceWindBurstBounce(attacker, mainHand);
        }

        // Boss Counter-Meta Check (Reflection / Counter)
        if (counterMeta.processCounterMeta(event, attacker, victim, arena)) {
            return;
        }

        // Boss Invulnerable Phase Check
        if (victim.getPersistentDataContainer().has(invulnKey, PersistentDataType.LONG)) {
            long expiry = victim.getPersistentDataContainer().get(invulnKey, PersistentDataType.LONG);
            if (System.currentTimeMillis() <= expiry) {
                event.setCancelled(true);
                attacker.playSound(attacker.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
                return;
            } else {
                victim.getPersistentDataContainer().remove(invulnKey);
            }
        }

        // Configurable mob invulnerability delay
        int hitDelay = 10;
        if (plugin.getConfigManager() != null && plugin.getConfigManager().getSettings() != null) {
            hitDelay = plugin.getConfigManager().getSettings().getInt("combat.mob-hit-delay-ticks", 10);
        }
        if (hitDelay <= 0) {
            victim.setNoDamageTicks(0);
            victim.setMaximumNoDamageTicks(0);
        } else if (victim.getMaximumNoDamageTicks() != hitDelay) {
            victim.setMaximumNoDamageTicks(hitDelay);
        }

        // --- PHASE 2: DAMAGE CALCULATION ---
        ArenaPlayerProfile profile = (arena != null) ? arena.getPlayerProfiles().get(attacker.getUniqueId()) : null;

        // Shop standalone Crit bonus
        if (profile != null && plugin.getShopManager() != null && profile.getUpgradeLevel("critical_strike") <= 0) {
            double shopCrit = plugin.getShopManager().getCustomStatBonus(attacker.getUniqueId(), "CUSTOM_CRIT_BOOST");
            if (shopCrit > 0 && ThreadLocalRandom.current().nextDouble(100.0) <= shopCrit) {
                event.setDamage(event.getDamage() * 2.0);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
                victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 20);
            }
        }

        // Dispatch Offensive Upgrades (Critical Strike, Warrior's Might, Elemental Infusion, etc.)
        if (arena != null && plugin.getUpgradeRegistry() != null) {
            plugin.getUpgradeRegistry().dispatchAttack(event, attacker, victim, arena);
        }

        // --- PHASE 3: MITIGATION & STAT TRACKING ---
        double finalDamage = event.getFinalDamage();
        if (profile != null) {
            profile.addDamageDealt(finalDamage);
        }

        if (plugin.getBossRewardManager() != null && plugin.getBossRewardManager().isTracking(victim.getUniqueId())) {
            plugin.getBossRewardManager().recordDamage(victim.getUniqueId(), attacker.getUniqueId(), finalDamage);
        }

        // --- PHASE 4: POST-EFFECTS & KILL TRIGGERS ---
        // Shop standalone Lifesteal bonus
        if (profile != null && plugin.getShopManager() != null && profile.getUpgradeLevel("vampiric_strike") <= 0) {
            double shopLifesteal = plugin.getShopManager().getCustomStatBonus(attacker.getUniqueId(), "CUSTOM_LIFESTEAL_BOOST");
            if (shopLifesteal > 0 && ThreadLocalRandom.current().nextDouble(100.0) <= shopLifesteal) {
                AttributeInstance maxHpAttr = attacker.getAttribute(AttributeUtils.MAX_HEALTH);
                double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
                double healAmount = Math.max(1.0, event.getDamage() * 0.10);
                attacker.setHealth(Math.min(maxHp, attacker.getHealth() + healAmount));
                attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 2, 0), 4, 0.3, 0.3, 0.3, 0);
                attacker.playSound(attacker.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.5f, 1.8f);
            }
        }

        // Check fatal strike & dispatch kill triggers
        if (victim.getHealth() - finalDamage <= 0) {
            if (arena != null && plugin.getUpgradeRegistry() != null) {
                plugin.getUpgradeRegistry().dispatchKill(attacker, victim, arena);
            }
            NamespacedKey arenaBossKey = new NamespacedKey(plugin, "arena_boss_id");
            NamespacedKey maBossKey = new NamespacedKey(plugin, "ma_boss_id");
            boolean isBoss = victim.hasMetadata("MA_BOSS")
                    || victim.getPersistentDataContainer().has(arenaBossKey, PersistentDataType.STRING)
                    || victim.getPersistentDataContainer().has(maBossKey, PersistentDataType.STRING);
            if (isBoss && profile != null) {
                if (!victim.hasMetadata("MA_BOSS_CREDITED_" + attacker.getUniqueId())) {
                    victim.setMetadata("MA_BOSS_CREDITED_" + attacker.getUniqueId(), new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                    profile.addBossKill();
                }
            }
        }
    }

    private void recordSecondaryDamageAndKills(EntityDamageByEntityEvent event, Player attacker, LivingEntity victim, Arena arena) {
        ArenaPlayerProfile profile = (arena != null) ? arena.getPlayerProfiles().get(attacker.getUniqueId()) : null;
        if (profile != null) {
            profile.addDamageDealt(event.getFinalDamage());
        }
        if (plugin.getBossRewardManager() != null && plugin.getBossRewardManager().isTracking(victim.getUniqueId())) {
            plugin.getBossRewardManager().recordDamage(victim.getUniqueId(), attacker.getUniqueId(), event.getFinalDamage());
        }
        if (victim.getHealth() - event.getFinalDamage() <= 0) {
            if (arena != null && plugin.getUpgradeRegistry() != null) {
                plugin.getUpgradeRegistry().dispatchKill(attacker, victim, arena);
            }
            NamespacedKey arenaBossKey = new NamespacedKey(plugin, "arena_boss_id");
            NamespacedKey maBossKey = new NamespacedKey(plugin, "ma_boss_id");
            boolean isBoss = victim.hasMetadata("MA_BOSS")
                    || victim.getPersistentDataContainer().has(arenaBossKey, PersistentDataType.STRING)
                    || victim.getPersistentDataContainer().has(maBossKey, PersistentDataType.STRING);
            if (isBoss && profile != null) {
                if (!victim.hasMetadata("MA_BOSS_CREDITED_" + attacker.getUniqueId())) {
                    victim.setMetadata("MA_BOSS_CREDITED_" + attacker.getUniqueId(), new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                    profile.addBossKill();
                }
            }
        }
    }

    private void applyMaceWindBurstBounce(Player attacker, ItemStack mainHand) {
        int windBurstLevel = 0;
        try {
            org.bukkit.enchantments.Enchantment wbEnch = org.bukkit.enchantments.Enchantment.getByKey(NamespacedKey.minecraft("wind_burst"));
            if (wbEnch != null && mainHand.containsEnchantment(wbEnch)) {
                windBurstLevel = mainHand.getEnchantmentLevel(wbEnch);
            }
        } catch (Throwable ignored) {}

        if (windBurstLevel > 0 && attacker.getFallDistance() > 1.0f) {
            final int level = windBurstLevel;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (attacker.isOnline() && !attacker.isDead()) {
                    double yBoost = 0.75 + (level * 0.45);
                    attacker.setVelocity(new Vector(attacker.getVelocity().getX() * 0.5, yBoost, attacker.getVelocity().getZ() * 0.5));
                    attacker.setFallDistance(0.0f);
                    try {
                        attacker.getWorld().spawnParticle(Particle.valueOf("GUST"), attacker.getLocation(), 15, 0.5, 0.2, 0.5, 0.1);
                    } catch (Throwable ignored) {
                        try {
                            attacker.getWorld().spawnParticle(Particle.EXPLOSION, attacker.getLocation(), 2);
                        } catch (Throwable ignored2) {}
                    }
                    try {
                        attacker.playSound(attacker.getLocation(), Sound.valueOf("ENTITY_BREEZE_WIND_BURST"), 1.0f, 1.2f);
                    } catch (Throwable ignored) {
                        attacker.playSound(attacker.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
                    }
                }
            }, 1L);
        }
    }

    // =========================================================================
    // PROJECTILE & MOB UTILITY EVENTS
    // =========================================================================
    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof LivingEntity shooter)) return;
        if (!shooter.hasMetadata("MA_MOB")) return;

        Projectile projectile = event.getEntity();
        NamespacedKey dmgKey = new NamespacedKey(plugin, "ma_damage_bonus");
        if (shooter.getPersistentDataContainer().has(dmgKey, PersistentDataType.STRING)) {
            String bonus = shooter.getPersistentDataContainer().get(dmgKey, PersistentDataType.STRING);
            if (bonus != null) {
                projectile.getPersistentDataContainer().set(dmgKey, PersistentDataType.STRING, bonus);
            }
        }
    }

    @EventHandler
    public void onMobShootBow(EntityShootBowEvent event) {
        LivingEntity shooter = event.getEntity();
        Entity projectile = event.getProjectile();

        if (shooter.hasMetadata("MA_MOB")) {
            String arenaName = shooter.getMetadata("MA_MOB").get(0).asString();
            Arena arena = plugin.getArenaManager().getArena(arenaName);
            if (arena != null && arena.isRunning()) {
                int currentWave = arena.getCurrentWave();
                double arrowMultiplier = 1.15 + (currentWave - 1) * 0.02;

                NamespacedKey bossRangedKey = new NamespacedKey(plugin, "ma_boss_ranged_mult");
                if (shooter.getPersistentDataContainer().has(bossRangedKey, PersistentDataType.DOUBLE)) {
                    Double bossRangedMult = shooter.getPersistentDataContainer().get(bossRangedKey, PersistentDataType.DOUBLE);
                    if (bossRangedMult != null && bossRangedMult > 0) {
                        arrowMultiplier *= bossRangedMult;
                    }
                }

                NamespacedKey arrowDmgKey = new NamespacedKey(plugin, "ma_arrow_bonus_damage");
                projectile.getPersistentDataContainer().set(arrowDmgKey, PersistentDataType.DOUBLE, arrowMultiplier);

                NamespacedKey bossPenKey = new NamespacedKey(plugin, "ma_boss_arrow_pen");
                if (shooter.getPersistentDataContainer().has(bossPenKey, PersistentDataType.DOUBLE)) {
                    Double pen = shooter.getPersistentDataContainer().get(bossPenKey, PersistentDataType.DOUBLE);
                    if (pen != null && pen > 0) {
                        projectile.getPersistentDataContainer().set(new NamespacedKey(plugin, "ma_arrow_penetration"), PersistentDataType.DOUBLE, pen);
                    }
                }

                Vector velocity = projectile.getVelocity();
                if (velocity != null && velocity.length() > 0) {
                    projectile.setVelocity(velocity.multiply(1.05));
                }
            }
        }

        if (shooter.getPersistentDataContainer().has(bowSpeedKey, PersistentDataType.STRING)) {
            String bonusStr = shooter.getPersistentDataContainer().get(bowSpeedKey, PersistentDataType.STRING);
            if (bonusStr != null) {
                Vector velocity = projectile.getVelocity();
                double currentSpeed = velocity.length();
                double newSpeed = currentSpeed;
                try {
                    if (bonusStr.startsWith("x")) {
                        newSpeed = currentSpeed * Double.parseDouble(bonusStr.replace("x", "").trim());
                    } else if (bonusStr.startsWith("+")) {
                        newSpeed = currentSpeed + Double.parseDouble(bonusStr.replace("+", "").trim());
                    }
                    projectile.setVelocity(velocity.normalize().multiply(newSpeed));
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity explosive = event.getEntity();
        LivingEntity shooter = null;
        if (explosive instanceof Projectile proj && proj.getShooter() instanceof LivingEntity le) {
            shooter = le;
        }
        if (shooter != null && shooter.getPersistentDataContainer().has(explosionKey, PersistentDataType.STRING)) {
            String bonusStr = shooter.getPersistentDataContainer().get(explosionKey, PersistentDataType.STRING);
            if (bonusStr != null) {
                try {
                    float currentYield = event.getYield();
                    if (bonusStr.startsWith("x")) {
                        event.setYield(currentYield * Float.parseFloat(bonusStr.replace("x", "").trim()));
                    } else if (bonusStr.startsWith("+")) {
                        event.setYield(currentYield + Float.parseFloat(bonusStr.replace("+", "").trim()));
                    }
                } catch (NumberFormatException ignored) {}
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTransform(EntityTransformEvent event) {
        if (event.getEntity().hasMetadata("MA_MOB")) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getEntity().hasMetadata("MA_MOB")) {
            event.setCancelled(true);
        }
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================
    private LivingEntity resolveLivingEntity(Entity entity) {
        if (entity instanceof LivingEntity le) return le;
        if (entity instanceof Projectile proj && proj.getShooter() instanceof LivingEntity le) return le;
        if (entity instanceof org.bukkit.entity.EvokerFangs fangs && fangs.getOwner() != null) return fangs.getOwner();
        return null;
    }

    private Player resolvePlayer(Entity entity) {
        if (entity instanceof Player p) return p;
        if (entity instanceof Projectile proj && proj.getShooter() instanceof Player p) return p;
        return null;
    }
}
