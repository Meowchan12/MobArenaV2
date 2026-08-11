package vn.meowchan12.mobarenav2.listener;

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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.ArenaPlayerProfile;

import java.util.concurrent.ThreadLocalRandom;

public class ArenaMobCombatListener implements Listener {

    private final MobArenav2 plugin;
    private final NamespacedKey bowSpeedKey;
    private final NamespacedKey explosionKey;

    // --- NEW: BOSS SKILLS KEYS ---
    private final NamespacedKey saitamaKey;
    private final NamespacedKey lifestealKey;
    private final NamespacedKey invulnKey;

    public ArenaMobCombatListener(MobArenav2 plugin) {
        this.plugin = plugin;
        this.bowSpeedKey = new NamespacedKey(plugin, "bow_speed_bonus");
        this.explosionKey = new NamespacedKey(plugin, "explosion_bonus");

        // Init Boss Skill Keys
        this.saitamaKey = new NamespacedKey(plugin, "ma_saitama_punch");
        this.lifestealKey = new NamespacedKey(plugin, "ma_lifesteal");
        this.invulnKey = new NamespacedKey(plugin, "ma_invulnerable");
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

                // Base +40% ranged damage + 3.5% per wave
                double arrowMultiplier = 1.55 + (currentWave - 1) * 0.045;

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

                // +20% faster arrow velocity for sharper projectile trajectory
                Vector velocity = projectile.getVelocity();
                if (velocity != null && velocity.length() > 0) {
                    projectile.setVelocity(velocity.multiply(1.30));
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
                                    double newHp = p.getHealth() - trueDmg;
                                    if (newHp <= 0) p.damage(9999.0);
                                    else p.setHealth(newHp);
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
                                AttributeInstance maxHpAttr = damager.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH);
                                double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 100.0;
                                double healAmount = maxHp * (healPercent / 100.0);

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
                Arena arena = plugin.getArenaManager().getArenaByPlayer(attacker);
                if (arena == null || !arena.isRunning() || arena.getSpectators().contains(attacker.getUniqueId())) {
                    event.setCancelled(true);
                    attacker.sendMessage("§c[MobArenav2] You cannot attack arena mobs from the outside!");
                    return;
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

                ArenaPlayerProfile profile = arena.getPlayerProfiles().get(attacker.getUniqueId());

                // =====================================================================
                // [v5.2 ENHANCED]: PLAYER CRITICAL STRIKE (WITH LV5 MEGA CRIT)
                // =====================================================================
                boolean isCrit = false;
                double shopCrit = (plugin.getShopManager() != null) ? plugin.getShopManager().getCustomStatBonus(attacker.getUniqueId(), "CUSTOM_CRIT_BOOST") : 0.0;
                double critChance = profile.getUpgradeValue("CRITICAL_STRIKE", plugin) + shopCrit;
                if (critChance > 0 && ThreadLocalRandom.current().nextDouble(100.0) <= critChance) {
                    int critLvl = profile.getUpgradeLevel("critical_strike");
                    double critMultiplier = (critLvl >= 5) ? 3.0 : 2.0; // Lv5 Capstone: 3.0x Mega Crit
                    event.setDamage(event.getDamage() * critMultiplier);

                    if (critLvl >= 5) {
                        attacker.playSound(attacker.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.2f);
                        attacker.sendMessage("§6§l★ MEGA CRIT! (3.0x Damage)");
                    } else {
                        attacker.playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 1.0f);
                    }
                    attacker.getWorld().spawnParticle(Particle.CRIT, event.getEntity().getLocation().add(0, 1, 0), 20);
                    isCrit = true;
                }

                // =====================================================================
                // [v5.2 CAPSTONE]: WARRIOR'S MIGHT CLEAVE SHOCKWAVE
                // =====================================================================
                int wmLvl = profile.getUpgradeLevel("warrior_might");
                if (wmLvl >= 5 && event.getEntity() instanceof LivingEntity mainTarget) {
                    double cleaveDmg = event.getDamage() * 0.30;
                    for (Entity near : mainTarget.getNearbyEntities(3, 3, 3)) {
                        if (near instanceof LivingEntity mobNear && mobNear.hasMetadata("MA_MOB") && !mobNear.equals(mainTarget)) {
                            mobNear.damage(cleaveDmg, attacker);
                            mobNear.getWorld().spawnParticle(Particle.SWEEP_ATTACK, mobNear.getLocation().add(0, 1, 0), 1);
                        }
                    }
                }

                // =====================================================================
                // [v5.2 REBALANCED]: ENHANCED LIFESTEAL & OVERHEAL ABSORPTION
                // Heals 25% of Damage Dealt (min 2.0 HP). Lv3+ Overheal becomes Absorption!
                // =====================================================================
                double shopLifesteal = (plugin.getShopManager() != null) ? plugin.getShopManager().getCustomStatBonus(attacker.getUniqueId(), "CUSTOM_LIFESTEAL_BOOST") : 0.0;
                double lifestealChance = profile.getUpgradeValue("LIFESTEAL", plugin) + shopLifesteal;
                if (lifestealChance > 0 && ThreadLocalRandom.current().nextDouble(100.0) <= lifestealChance) {
                    double maxHp = (attacker.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH) != null) ? attacker.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH).getValue() : 20.0;
                    double healAmount = Math.max(2.0, event.getDamage() * 0.25);
                    double currentHp = attacker.getHealth();

                    if (currentHp + healAmount <= maxHp) {
                        attacker.setHealth(currentHp + healAmount);
                    } else {
                        attacker.setHealth(maxHp);
                        int vsLvl = profile.getUpgradeLevel("vampiric_strike");
                        if (vsLvl >= 3) { // Lv3+ Capstone: Overheal converts to Absorption Shield
                            double excess = (currentHp + healAmount) - maxHp;
                            double currentAbs = attacker.getAbsorptionAmount();
                            attacker.setAbsorptionAmount(Math.min(12.0, currentAbs + excess));
                        }
                    }
                    attacker.getWorld().spawnParticle(Particle.HEART, attacker.getLocation().add(0, 2, 0), 4, 0.3, 0.3, 0.3, 0);
                    attacker.playSound(attacker.getLocation(), Sound.ENTITY_WITCH_DRINK, 0.5f, 1.8f);
                }

                if (true) {
                    // =====================================================================
                    // [v5.2 UPGRADE]: ARMOR_PIERCING (ARMOR SHREDDER)
                    // Bypasses mob armor and deals True Damage
                    // =====================================================================
                    double armorPenPercent = profile.getUpgradeValue("ARMOR_PIERCING", plugin);
                    if (armorPenPercent > 0) {
                        double trueDmg = event.getDamage() * (armorPenPercent / 100.0);
                        double newHp = victimTarget.getHealth() - trueDmg;
                        if (newHp > 0) victimTarget.setHealth(newHp);
                        victimTarget.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victimTarget.getLocation().add(0, 1, 0), 2);
                    }

                    // =====================================================================
                    // [v5.2 UPGRADE]: ELEMENTAL_INFUSION (ELEMENTAL STRIKE)
                    // Applies Fire/Frost; Lv5 Capstone on Crit strikes Lightning!
                    // =====================================================================
                    int elemRank = profile.getUpgradeLevel("elemental_infusion");
                    if (elemRank > 0) {
                        victimTarget.setFireTicks(40 * elemRank);
                        victimTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, Math.min(2, elemRank - 1), false, false));

                        if (elemRank >= 5 && isCrit) { // Capstone Lightning Strike
                            victimTarget.getWorld().strikeLightningEffect(victimTarget.getLocation());
                            double extraDmg = 15.0;
                            double nHp = victimTarget.getHealth() - extraDmg;
                            if (nHp > 0) victimTarget.setHealth(nHp);
                            attacker.sendMessage("§e⚡ §lELEMENTAL CAPSTONE: LIGHTNING CRIT!");
                        }
                    }

                    // =====================================================================
                    // [v5.2 UPGRADE]: BLOODLUST & SOUL_ORB (ON KILL/DAMAGE HANDLERS)
                    // =====================================================================
                    if (victimTarget.getHealth() - event.getFinalDamage() <= 0) {
                        int bloodlustLvl = profile.getUpgradeLevel("bloodlust");
                        if (bloodlustLvl > 0) {
                            attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, Math.min(2, bloodlustLvl - 1), false, false));
                            attacker.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 100, Math.min(2, bloodlustLvl - 1), false, false));
                            attacker.playSound(attacker.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 0.5f, 2.0f);

                            if (bloodlustLvl >= 5) { // Capstone Fire explosion on kill
                                victimTarget.getWorld().spawnParticle(Particle.FLAME, victimTarget.getLocation().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.1);
                                attacker.getWorld().playSound(victimTarget.getLocation(), Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 0.8f, 1.2f);
                            }
                        }

                        double soulDropChance = profile.getUpgradeValue("SOUL_ORB", plugin);
                        if (soulDropChance > 0 && ThreadLocalRandom.current().nextDouble(100.0) <= soulDropChance) {
                            double maxHp = attacker.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH) != null ?
                                           attacker.getAttribute(vn.meowchan12.mobarenav2.utils.AttributeUtils.MAX_HEALTH).getValue() : 20.0;
                            attacker.setHealth(Math.min(attacker.getHealth() + 3.0, maxHp));
                            attacker.getWorld().spawnParticle(Particle.SOUL, attacker.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.05);
                            attacker.playSound(attacker.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.5f);
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
}