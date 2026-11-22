package vn.meowchan12.mobarenav2.listener;

import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
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
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

import java.util.concurrent.ThreadLocalRandom;

public class ArenaMobCombatListener implements Listener {

    private final MobArenav2 plugin;
    private final NamespacedKey bowSpeedKey;
    private final NamespacedKey explosionKey;

    // --- NEW: BOSS SKILLS KEYS ---
    private final NamespacedKey saitamaKey;
    private final NamespacedKey lifestealKey;
    private final NamespacedKey invulnKey;

    // [v7.0] Counter-Meta handler
    private final BossCounterMeta counterMeta;

    public ArenaMobCombatListener(MobArenav2 plugin) {
        this.plugin = plugin;
        this.bowSpeedKey = new NamespacedKey(plugin, "bow_speed_bonus");
        this.explosionKey = new NamespacedKey(plugin, "explosion_bonus");

        // Init Boss Skill Keys
        this.saitamaKey = new NamespacedKey(plugin, "ma_saitama_punch");
        this.lifestealKey = new NamespacedKey(plugin, "ma_lifesteal");
        this.invulnKey = new NamespacedKey(plugin, "ma_invulnerable");

        // [v7.0] Counter-Meta
        this.counterMeta = new BossCounterMeta(plugin);
    }

    // =====================================================================
    // [v7.2 FEATURE]: PROJECTILE LAUNCH TAGGING (BLAZE, BREEZE, GHAST)
    // Ensures fireball, wind charge, and tridents carry arena damage bonuses
    // =====================================================================
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

    // =====================================================================
    // [v5.3 FEATURE]: ENHANCED MOB BOW & CROSSBOW ARROW DAMAGE
    // Skeletons, Pillagers, Piglins & Ranged mobs deal scaled arrow damage
    // =====================================================================
    @EventHandler
    public void onMobShootBow(EntityShootBowEvent event) {
        LivingEntity shooter = event.getEntity();
        Entity projectile = event.getProjectile();

        if (shooter.hasMetadata("MA_MOB")) {
            String arenaName = shooter.getMetadata("MA_MOB").get(0).asString();
            Arena arena = plugin.getArenaManager().getArena(arenaName);
            if (arena != null && arena.isRunning()) {
                int currentWave = arena.getCurrentWave();

                // [v7.2 REBALANCE]: Balanced arrow multiplier (1.15 base + 2.0% per wave)
                double arrowMultiplier = 1.15 + (currentWave - 1) * 0.02;

                // [v5.6] Boss Ranged Damage Multiplier from bosses.yml config
                NamespacedKey bossRangedKey = new NamespacedKey(plugin, "ma_boss_ranged_mult");
                if (shooter.getPersistentDataContainer().has(bossRangedKey, PersistentDataType.DOUBLE)) {
                    Double bossRangedMult = shooter.getPersistentDataContainer().get(bossRangedKey, PersistentDataType.DOUBLE);
                    if (bossRangedMult != null && bossRangedMult > 0) {
                        arrowMultiplier *= bossRangedMult;
                    }
                }

                NamespacedKey arrowDmgKey = new NamespacedKey(plugin, "ma_arrow_bonus_damage");
                projectile.getPersistentDataContainer().set(arrowDmgKey, PersistentDataType.DOUBLE, arrowMultiplier);

                // [v5.6] Boss Arrow Penetration from bosses.yml config
                NamespacedKey bossPenKey = new NamespacedKey(plugin, "ma_boss_arrow_pen");
                if (shooter.getPersistentDataContainer().has(bossPenKey, PersistentDataType.DOUBLE)) {
                    Double pen = shooter.getPersistentDataContainer().get(bossPenKey, PersistentDataType.DOUBLE);
                    if (pen != null && pen > 0) {
                        projectile.getPersistentDataContainer().set(new NamespacedKey(plugin, "ma_arrow_penetration"), PersistentDataType.DOUBLE, pen);
                    }
                }

                // [v7.2 REBALANCE]: Smooth arrow velocity (1.05x trajectory polish)
                Vector velocity = projectile.getVelocity();
                if (velocity != null && velocity.length() > 0) {
                    projectile.setVelocity(velocity.multiply(1.05));
                }
            }
        }

        NamespacedKey saKey = new NamespacedKey(plugin, "ma_super_arrow");
        if (shooter.getPersistentDataContainer().has(saKey, PersistentDataType.STRING)) {
            String data = shooter.getPersistentDataContainer().get(saKey, PersistentDataType.STRING);
            if (data != null) {
                String[] parts = data.split(";");
                long expiry = Long.parseLong(parts[2]);

                if (System.currentTimeMillis() <= expiry) {
                    NamespacedKey arrowKey = new NamespacedKey(plugin, "ma_arrow_type");
                    projectile.getPersistentDataContainer().set(arrowKey, PersistentDataType.STRING, "super;" + parts[0] + ";" + parts[1]);
                    projectile.setVisualFire(true);
                } else {
                    shooter.getPersistentDataContainer().remove(saKey);
                }
            }
        }

        if (shooter.getPersistentDataContainer().has(bowSpeedKey, PersistentDataType.STRING)) {
            String bonusStr = shooter.getPersistentDataContainer().get(bowSpeedKey, PersistentDataType.STRING);
            if (bonusStr == null) return;

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

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {

        // =========================================================
        // 1. PLAYER TAKES DAMAGE FROM BOSS OR MOBS
        // =========================================================
        if (event.getEntity() instanceof Player p) {
            Arena arena = plugin.getArenaManager().getArenaByPlayer(p);
            if (arena != null && arena.isRunning() && !arena.getSpectators().contains(p.getUniqueId())) {
                ArenaPlayerProfile profile = arena.getPlayerProfiles().get(p.getUniqueId());
                if (profile != null) {
                    profile.addDamageTaken(event.getFinalDamage());
                }

                // --- RETAINED: PLAYER DODGE ALGORITHM ---
                double shopDodge = (plugin.getShopManager() != null) ? plugin.getShopManager().getCustomStatBonus(p.getUniqueId(), "CUSTOM_DODGE") : 0.0;
                double dodgeChance = profile.getUpgradeValue("DODGE", plugin) + shopDodge;
                if (dodgeChance > 0 && ThreadLocalRandom.current().nextDouble(100.0) <= dodgeChance) {
                    event.setCancelled(true);
                    p.sendMessage("§a§lDODGED!");
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0f, 2.0f);
                    return;
                }

                // --- NEW: BOSS MELEE PASSIVE SKILLS ---
                LivingEntity damager = null;
                if (event.getDamager() instanceof LivingEntity) damager = (LivingEntity) event.getDamager();
                else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof LivingEntity) damager = (LivingEntity) proj.getShooter();
                else if (event.getDamager() instanceof org.bukkit.entity.EvokerFangs fangs && fangs.getOwner() != null) damager = fangs.getOwner();

                if (damager != null) {
                    // SAITAMA PUNCH (WITH 90% ARMOR PENETRATION)
                    if (damager.getPersistentDataContainer().has(saitamaKey, PersistentDataType.STRING)) {
                        String data = damager.getPersistentDataContainer().get(saitamaKey, PersistentDataType.STRING);
                        if (data != null) {
                            String[] parts = data.split(";");
                            double baseDmg = Double.parseDouble(parts[0]);
                            double penRatio = parts.length > 2 ? Double.parseDouble(parts[1]) : 0.90;
                            long expiry = parts.length > 2 ? Long.parseLong(parts[2]) : Long.parseLong(parts[1]);

                            if (System.currentTimeMillis() <= expiry) {
                                double trueDmg = baseDmg * penRatio;
                                double physDmg = baseDmg - trueDmg;

                                if (physDmg > 0) event.setDamage(physDmg);
                                if (trueDmg > 0) {
                                    vn.meowchan12.mobarenav2.service.CombatDamageService.applyLayeredDamage(p, trueDmg, false, damager, arena, plugin);
                                }

                                p.sendMessage("§4§l[FATAL IMPACT] §cHit by Saitama Punch! (" + (int)(penRatio * 100) + "% Armor Penetration)");
                                p.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, p.getLocation().add(0, 1, 0), 1);
                                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 0.5f);
                            } else {
                                damager.getPersistentDataContainer().remove(saitamaKey);
                            }
                        }
                    }

                    // LIFESTEAL AURA
                    if (damager.getPersistentDataContainer().has(lifestealKey, PersistentDataType.STRING)) {
                        String data = damager.getPersistentDataContainer().get(lifestealKey, PersistentDataType.STRING);
                        if (data != null) {
                            String[] parts = data.split(";");
                            double minPercent = Double.parseDouble(parts[0]);
                            double maxPercent = Double.parseDouble(parts[1]);
                            long expiry = Long.parseLong(parts[2]);

                            if (System.currentTimeMillis() <= expiry) {
                                double healPercent = ThreadLocalRandom.current().nextDouble(minPercent, maxPercent);
                                // [v7.0] NERF: Lifesteal heals based on Damage Dealt instead of Max HP
                                double healAmount = event.getFinalDamage() * (healPercent / 100.0);
                                // Fallback cap just in case
                                AttributeInstance maxHpAttr = damager.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH);
                                double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 100.0;

                                damager.setHealth(Math.min(damager.getHealth() + healAmount, maxHp));
                                damager.getWorld().spawnParticle(Particle.HEART, damager.getLocation().add(0, 2, 0), 3, 0.5, 0.5, 0.5, 0);
                            } else {
                                damager.getPersistentDataContainer().remove(lifestealKey);
                            }
                        }
                    }
                }
            }

            // --- RETAINED: OLD BOSS ARROW PROCESSING ---
            if (event.getDamager() instanceof Projectile proj) {
                NamespacedKey arrowKey = new NamespacedKey(plugin, "ma_arrow_type");
                if (proj.getPersistentDataContainer().has(arrowKey, PersistentDataType.STRING)) {
                    String data = proj.getPersistentDataContainer().get(arrowKey, PersistentDataType.STRING);
                    if (data == null) return;

                    String[] parts = data.split(";");
                    String type = parts[0];
                    double dmg = Double.parseDouble(parts[1]);
                    double trueDmg = Double.parseDouble(parts[2]);

                    event.setDamage(dmg);

                    if (trueDmg > 0) {
                        double newHp = p.getHealth() - trueDmg;
                        if (newHp <= 0) p.damage(9999.0);
                        else p.setHealth(newHp);
                    }

                    if (type.equals("ultimate") && parts.length > 3) {
                        String effectStr = parts[3];
                        if (!effectStr.isEmpty()) {
                            for (String eff : effectStr.split(",")) {
                                String[] eData = eff.trim().split(":");
                                if (eData.length >= 2) {
                                    PotionEffectType pType = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(eData[0].toLowerCase()));
                                    if (pType != null) {
                                        p.addPotionEffect(new PotionEffect(pType, Integer.parseInt(eData[1]), eData.length > 2 ? Integer.parseInt(eData[2]) : 0));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =========================================================
        // 2. PLAYER DEALS DAMAGE TO BOSS OR MOBS
        // =========================================================
        if (event.getEntity() instanceof LivingEntity victimTarget && victimTarget.hasMetadata("MA_MOB")) {
            Player attacker = null;
            if (event.getDamager() instanceof Player p) attacker = p;
            else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) attacker = p;

            if (attacker != null) {
                boolean isStandalone = victimTarget.hasMetadata("MA_TEST_BOSS") ||
                    (victimTarget.getMetadata("MA_MOB").size() > 0 && "STANDALONE".equals(victimTarget.getMetadata("MA_MOB").get(0).asString()));

                Arena arena = plugin.getArenaManager().getArenaByPlayer(attacker);
                if (!isStandalone) {
                    if (arena == null || !arena.isRunning() || arena.getSpectators().contains(attacker.getUniqueId())) {
                        event.setCancelled(true);
                        attacker.sendMessage("§c[MobArenav2] You cannot attack arena mobs from the outside!");
                        return;
                    }
                }

                // [v7.5.2 ANTI-RECURSION SHIELD]: If damage is generated from inside an active upgrade proc
                // (e.g. Shockwave Cleave, Thorns burst, Executioner explosion, Elemental shock), process as secondary damage:
                // Record damage dealt/boss stats, handle on-death if lethal, but NEVER recursively invoke counter-meta, shop crits, or dispatchAttack!
                if (vn.meowchan12.mobarenav2.upgrade.UpgradeRegistry.isDispatchingAny()) {
                    ArenaPlayerProfile profile = (arena != null) ? arena.getPlayerProfiles().get(attacker.getUniqueId()) : null;
                    if (profile != null) {
                        profile.addDamageDealt(event.getFinalDamage());
                    }
                    if (plugin.getBossRewardManager() != null && plugin.getBossRewardManager().isTracking(victimTarget.getUniqueId())) {
                        plugin.getBossRewardManager().recordDamage(victimTarget.getUniqueId(), attacker.getUniqueId(), event.getFinalDamage());
                    }
                    if (victimTarget.getHealth() - event.getFinalDamage() <= 0) {
                        if (arena != null && plugin.getUpgradeRegistry() != null) {
                            plugin.getUpgradeRegistry().dispatchKill(attacker, victimTarget, arena);
                        }
                        NamespacedKey arenaBossKey = new NamespacedKey(plugin, "arena_boss_id");
                        NamespacedKey maBossKey = new NamespacedKey(plugin, "ma_boss_id");
                        boolean isBoss = victimTarget.hasMetadata("MA_BOSS")
                                || victimTarget.getPersistentDataContainer().has(arenaBossKey, PersistentDataType.STRING)
                                || victimTarget.getPersistentDataContainer().has(maBossKey, PersistentDataType.STRING);
                        if (isBoss && profile != null) {
                            if (!victimTarget.hasMetadata("MA_BOSS_CREDITED_" + attacker.getUniqueId())) {
                                victimTarget.setMetadata("MA_BOSS_CREDITED_" + attacker.getUniqueId(), new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                                profile.addBossKill();
                            }
                        }
                    }
                    return;
                }

                // [v7.5.4]: Configurable mob invulnerability damage delay (i-frames)
                int hitDelay = 10;
                if (plugin.getConfigManager() != null && plugin.getConfigManager().getSettings() != null) {
                    hitDelay = plugin.getConfigManager().getSettings().getInt("combat.mob-hit-delay-ticks", 10);
                }
                if (hitDelay <= 0) {
                    victimTarget.setNoDamageTicks(0);
                    victimTarget.setMaximumNoDamageTicks(0);
                } else if (victimTarget.getMaximumNoDamageTicks() != hitDelay) {
                    victimTarget.setMaximumNoDamageTicks(hitDelay);
                }

                // [v7.5.6]: Mace Wind Burst Vanilla Guarantee
                ItemStack mainHand = attacker.getInventory().getItemInMainHand();
                if (mainHand != null && mainHand.getType().name().equalsIgnoreCase("MACE")) {
                    int windBurstLevel = 0;
                    try {
                        org.bukkit.enchantments.Enchantment wbEnch = org.bukkit.enchantments.Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft("wind_burst"));
                        if (wbEnch != null && mainHand.containsEnchantment(wbEnch)) {
                            windBurstLevel = mainHand.getEnchantmentLevel(wbEnch);
                        }
                    } catch (Throwable ignored) {}

                    if (windBurstLevel > 0 && attacker.getFallDistance() > 1.0f) {
                        final int level = windBurstLevel;
                        final Player p = attacker;
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (p.isOnline() && !p.isDead()) {
                                double yBoost = 0.75 + (level * 0.45);
                                p.setVelocity(new org.bukkit.util.Vector(p.getVelocity().getX() * 0.5, yBoost, p.getVelocity().getZ() * 0.5));
                                p.setFallDistance(0.0f);
                                try {
                                    p.getWorld().spawnParticle(Particle.valueOf("GUST"), p.getLocation(), 15, 0.5, 0.2, 0.5, 0.1);
                                } catch (Throwable ignored) {
                                    try {
                                        p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation(), 2);
                                    } catch (Throwable ignored2) {}
                                }
                                try {
                                    p.playSound(p.getLocation(), Sound.valueOf("ENTITY_BREEZE_WIND_BURST"), 1.0f, 1.2f);
                                } catch (Throwable ignored) {
                                    p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
                                }
                            }
                        }, 1L);
                    }
                }

                // [v7.0] Counter-Meta processing (Mace/Ranged reflection)
                if (counterMeta.processCounterMeta(event, attacker, victimTarget, arena)) {
                    return; // Attack was countered
                }
                
                // --- NEW: BOSS INVULNERABLE CHECK (DASH PHASE) ---
                if (victimTarget.getPersistentDataContainer().has(invulnKey, PersistentDataType.LONG)) {
                    long expiry = victimTarget.getPersistentDataContainer().get(invulnKey, PersistentDataType.LONG);
                    if (System.currentTimeMillis() <= expiry) {
                        event.setCancelled(true);
                        attacker.playSound(attacker.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 1.0f);
                        return;
                    } else {
                        victimTarget.getPersistentDataContainer().remove(invulnKey);
                    }
                }

                // [v7.0] Track damage for boss rewards
                if (plugin.getBossRewardManager() != null && plugin.getBossRewardManager().isTracking(victimTarget.getUniqueId())) {
                    plugin.getBossRewardManager().recordDamage(victimTarget.getUniqueId(), attacker.getUniqueId(), event.getFinalDamage());
                }

                // [v7.3 INTEGRATION]: ma shop standalone bonuses (Khi người chơi không có Upgrade tương ứng)
                ArenaPlayerProfile profile = (arena != null) ? arena.getPlayerProfiles().get(attacker.getUniqueId()) : null;
                if (profile != null) {
                    profile.addDamageDealt(event.getFinalDamage());
                }
                if (profile != null && plugin.getShopManager() != null) {
                    if (profile.getUpgradeLevel("critical_strike") <= 0) {
                        double shopCrit = plugin.getShopManager().getCustomStatBonus(attacker.getUniqueId(), "CUSTOM_CRIT_BOOST");
                        if (shopCrit > 0 && ThreadLocalRandom.current().nextDouble(100.0) <= shopCrit) {
                            event.setDamage(event.getDamage() * 2.0);
                            attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
                            victimTarget.getWorld().spawnParticle(Particle.CRIT, victimTarget.getLocation().add(0, 1, 0), 20);
                        }
                    }
                    if (profile.getUpgradeLevel("vampiric_strike") <= 0) {
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
                }

                // [v8.3.0]: Boss Stun Vulnerability (+50% DMG from broken shield)
                if (victimTarget.hasMetadata(vn.meowchan12.mobarenav2.boss.skill.ShieldBreakBurstSkill.META_STUN_BONUS)) {
                    event.setDamage(event.getDamage() * 1.50);
                }

                // [v7.3 DECOUPLED]: Điều phối các Upgrade tấn công thông qua UpgradeRegistry
                if (arena != null && plugin.getUpgradeRegistry() != null) {
                    plugin.getUpgradeRegistry().dispatchAttack(event, attacker, victimTarget, arena);

                    // [v8.2.0 OVERCAP STATS]: Apply Overpower % DMG & True Strike Flat True DMG
                    if (profile != null) {
                        if (profile.getOvercapDamageBonusPercent() > 0) {
                            event.setDamage(event.getDamage() * (1.0 + profile.getOvercapDamageBonusPercent() / 100.0));
                        }
                        if (profile.getOvercapFlatTrueDmg() > 0) {
                            final double flatTrue = profile.getOvercapFlatTrueDmg();
                            final Player finalAttacker = attacker;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (victimTarget.isValid() && !victimTarget.isDead()) {
                                    victimTarget.damage(flatTrue, finalAttacker);
                                }
                            });
                        }
                    }

                    // [v8.3.0]: Boss Shield Break Barrier Tracking
                    if (victimTarget.hasMetadata(vn.meowchan12.mobarenav2.boss.skill.ShieldBreakBurstSkill.META_SHIELD_ACTIVE)) {
                        double currentShield = victimTarget.getMetadata(vn.meowchan12.mobarenav2.boss.skill.ShieldBreakBurstSkill.META_SHIELD_HP).get(0).asDouble();
                        double remaining = currentShield - event.getFinalDamage();
                        victimTarget.setMetadata(vn.meowchan12.mobarenav2.boss.skill.ShieldBreakBurstSkill.META_SHIELD_HP, new org.bukkit.metadata.FixedMetadataValue(plugin, remaining));
                        if (remaining > 0) {
                            attacker.sendActionBar(vn.meowchan12.mobarenav2.utils.ColorUtils.toAdventure("&c&l[SHIELD] &e" + (int) remaining + " HP remaining!"));
                        }
                    }

                    // Điều phối các Upgrade tiêu diệt quái (Soul Harvester, Bloodlust, Soul Orb, Midas Touch...)
                    if (victimTarget.getHealth() - event.getFinalDamage() <= 0) {
                        plugin.getUpgradeRegistry().dispatchKill(attacker, victimTarget, arena);
                        NamespacedKey arenaBossKey = new NamespacedKey(plugin, "arena_boss_id");
                        NamespacedKey maBossKey = new NamespacedKey(plugin, "ma_boss_id");
                        boolean isBoss = victimTarget.hasMetadata("MA_BOSS")
                                || victimTarget.getPersistentDataContainer().has(arenaBossKey, PersistentDataType.STRING)
                                || victimTarget.getPersistentDataContainer().has(maBossKey, PersistentDataType.STRING);
                        if (isBoss && profile != null) {
                            if (!victimTarget.hasMetadata("MA_BOSS_CREDITED_" + attacker.getUniqueId())) {
                                victimTarget.setMetadata("MA_BOSS_CREDITED_" + attacker.getUniqueId(), new org.bukkit.metadata.FixedMetadataValue(plugin, true));
                                profile.addBossKill();
                            }
                        }
                    }
                }
            }
        }
    }

    // --- RETAINED: OLD EXPLOSION LOGIC ---
    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity explosive = event.getEntity();
        LivingEntity shooter = null;

        if (explosive instanceof Projectile proj && proj.getShooter() instanceof LivingEntity) {
            shooter = (LivingEntity) proj.getShooter();
        }

        if (shooter != null && shooter.getPersistentDataContainer().has(explosionKey, PersistentDataType.STRING)) {
            String bonusStr = shooter.getPersistentDataContainer().get(explosionKey, PersistentDataType.STRING);
            if (bonusStr == null) return;

            float currentYield = event.getYield();
            float newYield = currentYield;

            try {
                if (bonusStr.startsWith("x")) {
                    newYield = currentYield * Float.parseFloat(bonusStr.replace("x", "").trim());
                } else if (bonusStr.startsWith("+")) {
                    newYield = currentYield + Float.parseFloat(bonusStr.replace("+", "").trim());
                }
                event.setYield(newYield);
            } catch (NumberFormatException ignored) {}
        }
    }

    // =====================================================================
    // [v7.5.2 FAIL-SAFE BARRIER]: Prevent transformation of Arena Mobs & Bosses
    // (Piglin -> ZombifiedPiglin, Zombie -> Drowned, Skeleton -> Stray, etc.)
    // =====================================================================
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTransform(org.bukkit.event.entity.EntityTransformEvent event) {
        if (event.getEntity().hasMetadata("MA_MOB")) {
            event.setCancelled(true);
        }
    }

    // =====================================================================
    // [v7.5.2 ARENA INTEGRITY]: Prevent Endermen from griefing arena blocks
    // =====================================================================
    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(org.bukkit.event.entity.EntityChangeBlockEvent event) {
        if (event.getEntity().hasMetadata("MA_MOB")) {
            event.setCancelled(true);
        }
    }
}