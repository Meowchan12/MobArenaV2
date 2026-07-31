package vn.meowchan12.mobarenav2.arena;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.MobFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class BossSkillExecutor {

    public static void castSkill(MobArenav2 plugin, Arena arena, LivingEntity boss, String skillId) {
        if (boss == null || boss.isDead() || !arena.isRunning()) return;

        ConfigurationSection skillConfig = plugin.getBossManager().getSkillsConfig().getConfigurationSection(skillId);
        if (skillConfig == null) {
            plugin.getLogger().warning("[MobArenav2] Boss tried to use unknown skill: " + skillId);
            return;
        }

        String type = skillConfig.getString("type", "").toUpperCase();
        String skillName = skillConfig.getString("display-name", skillId);
        String bossName = boss.getCustomName() != null ? boss.getCustomName() : "Boss";

        // --- CƠ CHẾ ĐỌC CUSTOM MESSAGE TỪ BOSSSKILL.YML ---
        if (skillConfig.contains("message")) {
            String customMsg = skillConfig.getString("message");
            if (customMsg != null && !customMsg.isEmpty()) {
                String formatted = customMsg.replace("%boss%", bossName).replace("%skill%", skillName);
                arena.broadcastMessage(ChatColor.translateAlternateColorCodes('&', formatted));
            }
        } else {
            // Thông báo mặc định nếu không set custom message
            arena.broadcastMessage("§c§l[BOSS] §4" + bossName + " §euses §c" + skillName + "§e!");
        }

        Location bossLoc = boss.getLocation();

        switch (type) {

            // ==========================================
            // V1 SKILLS (OLD SYSTEM) - RETAINED 100%
            // ==========================================
            case "SUPER_ARROW_DAMAGE":
                double saDmg = skillConfig.getDouble("damage", 8.0);
                double saTDmg = skillConfig.getDouble("true-damage-amount", 4.0);
                long activeUntil = System.currentTimeMillis() + (skillConfig.getInt("active-time", 5) * 1000L);

                NamespacedKey saKey = new NamespacedKey(plugin, "ma_super_arrow");
                boss.getPersistentDataContainer().set(saKey, PersistentDataType.STRING, saDmg + ";" + saTDmg + ";" + activeUntil);

                // [v5.6] Add explosion tag for enhanced AoE
                double superExpRadius = skillConfig.getDouble("explosion-radius", 2.5);
                if (superExpRadius > 0) {
                    NamespacedKey expKey = new NamespacedKey(plugin, "ma_arrow_explosion_radius");
                    boss.getPersistentDataContainer().set(expKey, PersistentDataType.DOUBLE, superExpRadius);
                }
                bossLoc.getWorld().spawnParticle(Particle.FLAME, bossLoc.clone().add(0, 2, 0), 30, 0.5, 0.5, 0.5, 0.1);
                break;

            case "ULTIMATE_ARROW_DAMAGE":
                String targets = skillConfig.getString("targets", "all");
                double uDmg = skillConfig.getDouble("damage", 10.0);
                double uTDmg = skillConfig.getDouble("true-damage-amount", 6.0);
                List<String> effects = skillConfig.getStringList("effects");
                String effectStr = String.join(",", effects);

                List<Player> targetPlayers = new ArrayList<>();
                for (Player p : arena.getPlayersAsObjects()) {
                    if (arena.containsLocation(p.getLocation())) {
                        targetPlayers.add(p);
                    }
                }
                Collections.shuffle(targetPlayers);

                int count = targetPlayers.size();
                if (targets.contains("-")) {
                    String[] parts = targets.split("-");
                    int min = Integer.parseInt(parts[0].trim());
                    int max = Integer.parseInt(parts[1].trim());
                    count = ThreadLocalRandom.current().nextInt(min, max + 1);
                } else if (!targets.equals("all")) {
                    try { count = Integer.parseInt(targets); } catch (Exception ignored) {}
                }

                for (int i = 0; i < Math.min(count, targetPlayers.size()); i++) {
                    Player p = targetPlayers.get(i);
                    Location spawnLoc = p.getLocation().add(0, 4, 0);

                    Arrow arrow = p.getWorld().spawn(spawnLoc, Arrow.class);
                    arrow.setVelocity(new Vector(0, -1.5, 0));
                    arrow.setShooter(boss);
                    arrow.setFireTicks(200);

                    NamespacedKey uaKey = new NamespacedKey(plugin, "ma_arrow_type");
                    arrow.getPersistentDataContainer().set(uaKey, PersistentDataType.STRING, "ultimate;" + uDmg + ";" + uTDmg + ";" + effectStr);
                    double ultExpRadius = skillConfig.getDouble("explosion-radius", 3.5);
                    if (ultExpRadius > 0) {
                        NamespacedKey expKey = new NamespacedKey(plugin, "ma_arrow_explosion_radius");
                        arrow.getPersistentDataContainer().set(expKey, PersistentDataType.DOUBLE, ultExpRadius);
                    }
                    p.getWorld().spawnParticle(Particle.PORTAL, spawnLoc, 50, 0.5, 0.5, 0.5, 0.1);
                }
                break;

            case "KNOCKBACKER":
                double kbRad = skillConfig.getDouble("radius", 6.0);
                double kbDmg = skillConfig.getDouble("damage", 5.0);
                double kbPower = skillConfig.getDouble("knockback-power", 2.0);
                double kbHeight = skillConfig.getDouble("knockback-height", 1.2);
                List<String> kbEffects = skillConfig.getStringList("effects");

                bossLoc.getWorld().spawnParticle(Particle.SONIC_BOOM, bossLoc, 1);
                bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 1.0f);

                for (Entity e : boss.getNearbyEntities(kbRad, kbRad, kbRad)) {
                    if (e instanceof Player p && arena.getActivePlayers().contains(p.getUniqueId()) && arena.containsLocation(p.getLocation())) {
                        // [v5.6] True Damage
                        double newHp = p.getHealth() - kbDmg;
                        if (newHp <= 0) p.damage(9999.0);
                        else p.setHealth(newHp);
                        Vector dir = p.getLocation().toVector().subtract(bossLoc.toVector()).normalize();
                        p.setVelocity(dir.multiply(kbPower).setY(kbHeight));

                        for (String eff : kbEffects) {
                            String[] data = eff.trim().split(":");
                            if (data.length >= 2) {
                                PotionEffectType pType = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(data[0].toLowerCase()));
                                if (pType != null) {
                                    p.addPotionEffect(new PotionEffect(pType, Integer.parseInt(data[1]), data.length > 2 ? Integer.parseInt(data[2]) : 0));
                                }
                            }
                        }
                    }
                }
                break;

            case "DEATH_MARK":
                double chance = skillConfig.getDouble("chance", 50.0);
                if (ThreadLocalRandom.current().nextDouble(100.0) <= chance) {
                    List<Player> dPlayers = new ArrayList<>();
                    for (Player p : arena.getPlayersAsObjects()) {
                        if (arena.containsLocation(p.getLocation())) dPlayers.add(p);
                    }

                    if (!dPlayers.isEmpty()) {
                        Player target = dPlayers.get(ThreadLocalRandom.current().nextInt(dPlayers.size()));
                        long dDuration = skillConfig.getInt("duration", 10) * 1000L;
                        long expiry = System.currentTimeMillis() + dDuration;

                        NamespacedKey dmKey = new NamespacedKey(plugin, "ma_death_mark");
                        target.getPersistentDataContainer().set(dmKey, PersistentDataType.LONG, expiry);

                        target.sendMessage("§4☠ YOU HAVE BEEN MARKED FOR DEATH! Totems disabled for " + (dDuration/1000) + "s!");
                        target.getWorld().spawnParticle(Particle.SCULK_SOUL, target.getLocation().add(0, 2, 0), 30, 0.5, 0.5, 0.5, 0.05);
                        target.playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
                    }
                }
                break;

            case "FREEZE":
                int fTime = skillConfig.getInt("freeze-time", 4);
                double baseLoss = skillConfig.getDouble("hp-loss-percent", 35.0);
                double hpLossMin = skillConfig.getDouble("hp-loss-percent-min", baseLoss);
                double hpLossMax = skillConfig.getDouble("hp-loss-percent-max", Math.max(baseLoss, 40.0));
                double freezeMinRange = skillConfig.getDouble("min-range", 0.0);
                double freezeMaxRange = skillConfig.getDouble("max-range", skillConfig.getDouble("radius", 32.0));
                List<String> fEffects = skillConfig.getStringList("after-effects");

                bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.3f);
                bossLoc.getWorld().spawnParticle(Particle.SNOWFLAKE, bossLoc, 200, 5.0, 3.0, 5.0, 0.1);

                for (Player p : arena.getPlayersAsObjects()) {
                    if (!arena.containsLocation(p.getLocation())) continue;
                    double dist = p.getLocation().distance(bossLoc);
                    if (dist < freezeMinRange || dist > freezeMaxRange) continue;

                    // Absolute freeze - cannot move, dash, or use lunge
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, fTime * 20, 255, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, fTime * 20, 128, false, false));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, fTime * 20, 255, false, false));
                    p.setVelocity(new Vector(0, 0, 0));
                    p.sendMessage("§b❄ §l[ABSOLUTE FREEZE] §bYou have been frozen solid!");
                    p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation(), 100, 1.0, 2.0, 1.0, 0.0);
                    p.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 2.0f, 0.5f);

                    // Spawn temporary ice blocks
                    Location pLoc = p.getLocation();
                    List<Location> iceBlocks = new ArrayList<>();
                    Location ice1 = pLoc.clone().add(1, 0, 0);
                    Location ice2 = pLoc.clone().add(-1, 0, 0);
                    
                    if (ice1.getBlock().getType().isAir()) {
                        ice1.getBlock().setType(Material.BLUE_ICE);
                        iceBlocks.add(ice1);
                    }
                    if (ice2.getBlock().getType().isAir()) {
                        ice2.getBlock().setType(Material.BLUE_ICE);
                        iceBlocks.add(ice2);
                    }

                    // Freeze tick - reset velocity every tick
                    new BukkitRunnable() {
                        int ticks = 0;
                        @Override
                        public void run() {
                            if (!p.isOnline() || !arena.getActivePlayers().contains(p.getUniqueId()) || ticks >= fTime * 20) {
                                this.cancel();
                                return;
                            }
                            p.setVelocity(new Vector(0, p.getVelocity().getY(), 0)); // Lock X/Z movement
                            if (ticks % 10 == 0) {
                                p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 5, 0.3, 0.5, 0.3, 0);
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);

                    // After freeze: apply effects + true damage + remove ice
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        // Remove ice blocks
                        for (Location iceLoc : iceBlocks) {
                            if (iceLoc.getBlock().getType() == Material.BLUE_ICE) {
                                iceLoc.getBlock().setType(Material.AIR);
                                iceLoc.getWorld().spawnParticle(Particle.BLOCK, iceLoc.clone().add(0.5, 0.5, 0.5), 20, 0.3, 0.3, 0.3, 0, Material.BLUE_ICE.createBlockData());
                            }
                        }

                        if (p.isOnline() && arena.getActivePlayers().contains(p.getUniqueId())) {
                            p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 2.0f, 1.5f);
                            p.getWorld().spawnParticle(Particle.SNOWFLAKE, p.getLocation(), 80, 1.5, 2.0, 1.5, 0.1);

                            // True damage: 35-40% max HP
                            AttributeInstance maxHpAttr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                            double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
                            double lossPercent = ThreadLocalRandom.current().nextDouble(hpLossMin, hpLossMax);
                            double loss = maxHp * (lossPercent / 100.0);
                            double newHp = p.getHealth() - loss;
                            if (newHp <= 0) p.damage(9999.0);
                            else p.setHealth(newHp);

                            p.sendMessage("§c❄ Freeze shattered! Lost §4" + String.format("%.0f", lossPercent) + "% §cMax HP!");

                            // After-effects
                            for (String eff : fEffects) {
                                String[] data = eff.trim().split(":");
                                if (data.length >= 2) {
                                    PotionEffectType pType = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(data[0].toLowerCase()));
                                    if (pType == null) {
                                        try { pType = PotionEffectType.getByName(data[0].toUpperCase()); } catch (Exception ignored) {}
                                    }
                                    if (pType != null) {
                                        p.addPotionEffect(new PotionEffect(pType, Integer.parseInt(data[1]), data.length > 2 ? Integer.parseInt(data[2]) : 0));
                                    }
                                }
                            }
                        }
                    }, fTime * 20L);
                }
                break;

            case "AREA_DAMAGE":
                double radius = skillConfig.getDouble("radius", 5.0);
                double damage = skillConfig.getDouble("damage", 8.0);
                double eqPen = skillConfig.getDouble("armor-penetration", skillConfig.getDouble("true-damage-percent", 0.90));

                bossLoc.getWorld().spawnParticle(Particle.EXPLOSION, bossLoc, 5, 1.0, 0.5, 1.0, 0.0);
                bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.5f);

                for (Entity e : boss.getNearbyEntities(radius, radius, radius)) {
                    if (e instanceof Player p && arena.getActivePlayers().contains(p.getUniqueId()) && arena.containsLocation(p.getLocation())) {
                        double trueDmg = damage * eqPen;
                        double physDmg = damage - trueDmg;

                        if (physDmg > 0) p.damage(physDmg, boss);
                        if (trueDmg > 0) {
                            double newHp = p.getHealth() - trueDmg;
                            if (newHp <= 0) p.damage(9999.0);
                            else p.setHealth(newHp);
                        }
                        p.getWorld().spawnParticle(Particle.CRIT, p.getLocation().add(0, 1, 0), 10);
                    }
                }
                break;

            case "LIGHTNING":
                int lTargets = skillConfig.getInt("targets", 3);
                List<Player> players = new ArrayList<>();
                for (Player p : arena.getPlayersAsObjects()) {
                    if (arena.containsLocation(p.getLocation())) players.add(p);
                }
                Collections.shuffle(players);

                for (int i = 0; i < lTargets && i < players.size(); i++) {
                    Player target = players.get(i);
                    target.getWorld().strikeLightning(target.getLocation());
                    target.damage(skillConfig.getDouble("damage", 4.0), boss);
                }
                break;

            case "PULL":
                double pRadius = skillConfig.getDouble("radius", 10.0);
                double pStrength = skillConfig.getDouble("strength", 1.0);

                bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 0.5f);
                bossLoc.getWorld().spawnParticle(Particle.PORTAL, bossLoc, 50, 2.0, 2.0, 2.0, 1.0);

                for (Entity e : boss.getNearbyEntities(pRadius, pRadius, pRadius)) {
                    if (e instanceof Player p && arena.getActivePlayers().contains(p.getUniqueId()) && arena.containsLocation(p.getLocation())) {
                        Vector direction = bossLoc.toVector().subtract(p.getLocation().toVector()).normalize();
                        p.setVelocity(direction.multiply(pStrength));
                    }
                }
                break;

            case "SUMMON":
                String minionId = skillConfig.getString("minion-id", "zombie");
                int amount = skillConfig.getInt("amount", 3);
                MobFactory mobFactory = new MobFactory(plugin);

                bossLoc.getWorld().spawnParticle(Particle.SMOKE, bossLoc, 30, 1.0, 1.0, 1.0, 0.1);
                bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);

                for (int i = 0; i < amount; i++) {
                    Location spawnLoc = bossLoc.clone().add((Math.random() - 0.5) * 4, 0, (Math.random() - 0.5) * 4);
                    mobFactory.spawnArenaMob(arena, minionId, spawnLoc, null, null);
                }
                break;

            case "METEOR_SHOWER":
                int mTargets = skillConfig.getInt("targets", 2);
                List<Player> targetList = new ArrayList<>();
                for (Player p : arena.getPlayersAsObjects()) {
                    if (arena.containsLocation(p.getLocation())) targetList.add(p);
                }
                Collections.shuffle(targetList);

                for (int i = 0; i < Math.min(mTargets, targetList.size()); i++) {
                    Player p = targetList.get(i);
                    Location startLoc = p.getLocation().add(0, 15, 0);
                    Vector direction = new Vector(0, -1, 0);

                    org.bukkit.entity.Fireball fireball = p.getWorld().spawn(startLoc, org.bukkit.entity.Fireball.class);
                    fireball.setShooter(boss);
                    fireball.setDirection(direction);
                    fireball.setYield((float) skillConfig.getDouble("explosion-power", 2.0));
                }
                break;

            case "TELEPORT_STRIKE":
                Player furthest = null;
                double maxDist = 0;
                for (Player p : arena.getPlayersAsObjects()) {
                    if (!arena.containsLocation(p.getLocation())) continue;
                    double dist = p.getLocation().distanceSquared(bossLoc);
                    if (dist > maxDist) {
                        maxDist = dist;
                        furthest = p;
                    }
                }

                if (furthest != null) {
                    bossLoc.getWorld().spawnParticle(Particle.PORTAL, bossLoc, 50, 1.0, 2.0, 1.0, 1.0);

                    Vector dir = furthest.getLocation().getDirection().setY(0).normalize();
                    Location behind = furthest.getLocation().subtract(dir.multiply(2));

                    boss.teleport(behind);
                    boss.getWorld().playSound(behind, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    boss.getWorld().spawnParticle(Particle.PORTAL, behind, 50, 1.0, 2.0, 1.0, 1.0);

                    furthest.damage(skillConfig.getDouble("damage", 10.0), boss);
                    furthest.setVelocity(dir.multiply(1.5));
                }
                break;

            case "ENRAGE":
                bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.5f);
                bossLoc.getWorld().spawnParticle(Particle.FLAME, bossLoc, 100, 1.0, 2.0, 1.0, 0.2);
                boss.setGlowing(true);

                String buffs = skillConfig.getString("effects", "speed:3, strength:2");
                String[] enrageEffects = buffs.split(",");
                for (String eff : enrageEffects) {
                    String[] data = eff.trim().split(":");
                    if (data.length == 2) {
                        NamespacedKey key = NamespacedKey.minecraft(data[0].toLowerCase());
                        PotionEffectType pType = Registry.POTION_EFFECT_TYPE.get(key);
                        if (pType != null) {
                            boss.addPotionEffect(new PotionEffect(pType, Integer.MAX_VALUE, Integer.parseInt(data[1]), false, false));
                        }
                    }
                }
                break;

            case "INVULNERABLE": {
                int godModeDur = skillConfig.getInt("duration", 5);
                long godExpiry = System.currentTimeMillis() + (godModeDur * 1000L);

                NamespacedKey godKey = new NamespacedKey(plugin, "ma_invulnerable");
                boss.getPersistentDataContainer().set(godKey, PersistentDataType.LONG, godExpiry);

                boss.setGlowing(true);
                bossLoc.getWorld().playSound(bossLoc, Sound.ITEM_SHIELD_BLOCK, 2.0f, 0.5f);
                bossLoc.getWorld().playSound(bossLoc, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.5f);
                bossLoc.getWorld().spawnParticle(Particle.END_ROD, bossLoc.clone().add(0, 1, 0), 50, 0.8, 1.2, 0.8, 0.1);

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (boss.isValid()) {
                        boss.setGlowing(false);
                        boss.getPersistentDataContainer().remove(godKey);
                        boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 1.0f);
                    }
                }, godModeDur * 20L);
                break;
            }

            // ==========================================
            // V2 SKILLS (NEW SYSTEM) - 9 ADVANCED SKILLS
            // ==========================================

            case "MAGNETIC_PULL":
                double mpRadius = skillConfig.getDouble("radius", 12.0);
                double mpStrength = skillConfig.getDouble("pull-strength", 1.5);

                bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 2.0f, 0.5f);
                bossLoc.getWorld().spawnParticle(Particle.PORTAL, bossLoc, 100, 3.0, 3.0, 3.0, 0.5);

                for (Player p : arena.getPlayersAsObjects()) {
                    if (arena.containsLocation(p.getLocation()) && p.getLocation().distance(bossLoc) <= mpRadius) {
                        Vector dir = bossLoc.toVector().subtract(p.getLocation().toVector()).normalize();
                        p.setVelocity(dir.multiply(mpStrength));
                        applyEffects(p, skillConfig.getStringList("effects"));
                    }
                }
                break;

            case "EARTHQUAKE_SMASH":
                double eqRadius = skillConfig.getDouble("radius", 8.0);
                double eqJump = skillConfig.getDouble("jump-velocity", 1.5);
                double eqKnockup = skillConfig.getDouble("knockup-power", 1.2);
                double eqDamage = skillConfig.getDouble("damage", 10.0);
                double eqSmashPen = skillConfig.getDouble("armor-penetration", skillConfig.getDouble("true-damage-percent", 0.90));

                boss.setVelocity(new Vector(0, eqJump, 0));

                new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (boss.isDead() || !arena.isRunning() || ticks++ > 40) { this.cancel(); return; }

                        if (ticks > 5 && boss.isOnGround()) {
                            Location landLoc = boss.getLocation();
                            landLoc.getWorld().playSound(landLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 0.8f);
                            landLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, landLoc, 1);

                            for (Player p : arena.getPlayersAsObjects()) {
                                if (arena.containsLocation(p.getLocation()) && p.getLocation().distance(landLoc) <= eqRadius) {
                                    double trueDmg = eqDamage * eqSmashPen;
                                    double physDmg = eqDamage - trueDmg;

                                    if (physDmg > 0) p.damage(physDmg, boss);
                                    if (trueDmg > 0) {
                                        double newHp = p.getHealth() - trueDmg;
                                        if (newHp <= 0) p.damage(9999.0);
                                        else p.setHealth(newHp);
                                    }
                                    p.setVelocity(new Vector(0, eqKnockup, 0));
                                }
                            }
                            this.cancel();
                        }
                    }
                }.runTaskTimer(plugin, 1L, 1L);
                break;

            case "TOXIC_NOVA":
                double maxNovaRadius = skillConfig.getDouble("max-radius", 10.0);
                double novaSpeed = skillConfig.getDouble("expansion-speed", 0.5);
                double novaDmg = skillConfig.getDouble("damage-per-tick", 2.0);
                List<String> novaEffects = skillConfig.getStringList("effects");

                new BukkitRunnable() {
                    double currentRadius = 1.0;
                    @Override
                    public void run() {
                        if (boss.isDead() || !arena.isRunning() || currentRadius > maxNovaRadius) { this.cancel(); return; }

                        Location center = boss.getLocation();
                        for (double theta = 0; theta < 2 * Math.PI; theta += Math.PI / 16) {
                            double x = currentRadius * Math.cos(theta);
                            double z = currentRadius * Math.sin(theta);
                            center.getWorld().spawnParticle(Particle.WITCH, center.clone().add(x, 0.5, z), 1, 0, 0, 0, 0);
                        }

                        for (Player p : arena.getPlayersAsObjects()) {
                            if (arena.containsLocation(p.getLocation()) && p.getLocation().distance(center) <= currentRadius) {
                                p.damage(novaDmg, boss);
                                applyEffects(p, novaEffects);
                            }
                        }
                        currentRadius += novaSpeed;
                    }
                }.runTaskTimer(plugin, 0L, 2L);
                break;

            case "MINION_CALL":
                String mType = skillConfig.getString("mob-type", "ZOMBIE");
                int mAmount = skillConfig.getInt("amount", 4);
                int mLifespan = skillConfig.getInt("lifespan", 15);

                bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.5f);
                MobFactory mFactory = new MobFactory(plugin);

                for (int i = 0; i < mAmount; i++) {
                    Location spawnLoc = bossLoc.clone().add((Math.random() - 0.5) * 4, 0, (Math.random() - 0.5) * 4);
                    LivingEntity minion = mFactory.spawnArenaMob(arena, mType.toLowerCase(), spawnLoc, null, null);
                    if (minion != null) {
                        applyEffects(minion, skillConfig.getStringList("minion-buffs"));
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (minion.isValid() && !minion.isDead()) {
                                minion.getWorld().spawnParticle(Particle.LARGE_SMOKE, minion.getLocation(), 10, 0.5, 0.5, 0.5, 0);
                                minion.remove();
                                arena.getActiveMobs().remove(minion);
                            }
                        }, mLifespan * 20L);
                    }
                }
                break;

            case "DASH_AND_RANGED":
                double dashPwr = skillConfig.getDouble("dash.power", 2.5);
                int invulnDur = skillConfig.getInt("dash.invulnerable-duration", 3);
                int rDuration = skillConfig.getInt("ranged-stance.duration", 10);
                double rDist = skillConfig.getDouble("ranged-stance.max-target-distance", 20.0);
                long rInterval = (long) (skillConfig.getDouble("ranged-stance.attack-interval", 1.0) * 20L);

                // [THÊM MỚI] Đọc damage từ config
                double rDamage = skillConfig.getDouble("ranged-stance.damage", 5.0);

                int maxDashTargets = skillConfig.getInt("dash.max-targets", 2);
                List<Player> dashTargets = new ArrayList<>(arena.getPlayersAsObjects());
                Collections.shuffle(dashTargets);
                int dashCount = Math.min(maxDashTargets, dashTargets.size());

                for (int di = 0; di < dashCount; di++) {
                    Player dashTarget = dashTargets.get(di);
                    final int dashIndex = di;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (boss.isDead() || !arena.isRunning()) return;
                        Vector dDir = dashTarget.getLocation().toVector().subtract(boss.getLocation().toVector()).setY(0.2).normalize().multiply(dashPwr);
                        boss.setVelocity(dDir);
                        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 2.0f, 1.5f);
                        boss.getWorld().spawnParticle(Particle.SONIC_BOOM, boss.getLocation(), 1);
                    }, di * 25L); // 1.25 seconds between dashes
                }

                NamespacedKey invKey = new NamespacedKey(plugin, "ma_invulnerable");
                boss.getPersistentDataContainer().set(invKey, PersistentDataType.LONG, System.currentTimeMillis() + (invulnDur * 1000L));

                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (boss.isDead() || !arena.isRunning()) return;

                    boss.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, rDuration * 20, 100, false, false));
                    boss.getWorld().spawnParticle(Particle.ENCHANTED_HIT, boss.getLocation(), 50, 1, 1, 1, 0.1);

                    new BukkitRunnable() {
                        int ticks = 0;
                        @Override
                        public void run() {
                            if (boss.isDead() || !arena.isRunning() || ticks >= (rDuration * 20)) {
                                this.cancel();
                                return;
                            }

                            if (ticks % rInterval == 0) {
                                Player target = getNearestPlayer(arena, boss.getLocation(), rDist);
                                if (target != null) {
                                    Location eye = boss.getEyeLocation();
                                    Location tEye = target.getEyeLocation();

                                    drawLaser(eye, tEye, Particle.DUST, 255, 0, 0);

                                    // [v5.6] True Damage - bypasses armor
                                    double newHp = target.getHealth() - rDamage;
                                    if (newHp <= 0) {
                                        target.damage(9999.0);
                                    } else {
                                        target.setHealth(newHp);
                                    }

                                    boss.getWorld().playSound(eye, Sound.ENTITY_GUARDIAN_ATTACK, 1.0f, 2.0f);
                                }
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);

                }, 20L);
                break;

            case "INVIS_TELEPORTER":
                int iDur = skillConfig.getInt("duration", 12);
                int tInterval = skillConfig.getInt("teleport-interval", 3) * 20;
                double tMin = skillConfig.getDouble("teleport-distance.min", 4.0);
                double tMax = skillConfig.getDouble("teleport-distance.max", 8.0);

                boss.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, iDur * 20, 1, false, false));

                new BukkitRunnable() {
                    int elapsed = 0;
                    @Override
                    public void run() {
                        if (boss.isDead() || !arena.isRunning() || elapsed >= (iDur * 20)) { this.cancel(); return; }

                        if (elapsed % tInterval == 0) {
                            Player target = getNearestPlayer(arena, boss.getLocation(), 50.0);
                            if (target != null) {
                                double angle = ThreadLocalRandom.current().nextDouble(0, 2 * Math.PI);
                                double dist = ThreadLocalRandom.current().nextDouble(tMin, tMax);
                                Location tpLoc = target.getLocation().clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);

                                if (!tpLoc.getBlock().getType().isSolid()) {
                                    boss.getWorld().spawnParticle(Particle.SMOKE, boss.getLocation(), 20, 0.5, 1, 0.5, 0);
                                    boss.teleport(tpLoc);
                                    boss.getWorld().playSound(tpLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                                }
                            }
                        }
                        elapsed += 5;
                    }
                }.runTaskTimer(plugin, 0L, 5L);
                break;

            case "SPAMMER_BOW":
                int sbDur = skillConfig.getInt("duration", 6);
                long sbInterval = (long) (skillConfig.getDouble("attack-interval", 1.5) * 20L);
                boolean hitscan = skillConfig.getBoolean("guaranteed-hit", true);
                double sbKnockback = skillConfig.getDouble("knockback-force", 1.2);
                double debuffChance = skillConfig.getDouble("debuff.chance", 15.0);
                List<String> sbEffects = skillConfig.getStringList("debuff.effects");

                // [THÊM MỚI] Đọc damage từ config (Mặc định là 4.0 nếu quên không ghi trong file yaml)
                double sbDamage = skillConfig.getDouble("damage", 4.0);

                new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (boss.isDead() || !arena.isRunning() || ticks >= (sbDur * 20)) { this.cancel(); return; }

                        if (ticks % sbInterval == 0) {
                            boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_SKELETON_SHOOT, 1.0f, 1.2f);
                            for (Player p : arena.getPlayersAsObjects()) {
                                if (arena.containsLocation(p.getLocation())) {
                                    if (hitscan) {
                                        double explosionRadius = skillConfig.getDouble("explosion-radius", 3.0);
                                        double trueDmgPercent = skillConfig.getDouble("true-damage-percent", 0.80);
                                        // ... in the hitscan block:
                                        drawLaser(boss.getEyeLocation(), p.getEyeLocation(), Particle.CRIT, 0, 0, 0);
                                        // Explosion at impact
                                        p.getWorld().spawnParticle(Particle.EXPLOSION, p.getLocation(), 1);
                                        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.5f);
                                        // True damage to target
                                        double trueDmg = sbDamage * trueDmgPercent;
                                        double newHp = p.getHealth() - trueDmg;
                                        if (newHp <= 0) p.damage(9999.0);
                                        else p.setHealth(newHp);
                                        // AoE damage to nearby players
                                        for (Entity nearby : p.getNearbyEntities(explosionRadius, explosionRadius, explosionRadius)) {
                                            if (nearby instanceof Player np && arena.getActivePlayers().contains(np.getUniqueId())) {
                                                double aoeDmg = trueDmg * 0.5;
                                                double nph = np.getHealth() - aoeDmg;
                                                if (nph <= 0) np.damage(9999.0);
                                                else np.setHealth(nph);
                                            }
                                        }

                                        Vector kbDir = p.getLocation().toVector().subtract(boss.getLocation().toVector()).normalize();
                                        p.setVelocity(kbDir.multiply(sbKnockback).setY(0.5));

                                        if (ThreadLocalRandom.current().nextDouble(100.0) <= debuffChance) {
                                            applyEffects(p, sbEffects);
                                        }
                                    } else {
                                        Arrow arrow = boss.launchProjectile(Arrow.class);

                                        // [THÊM MỚI] Gán sát thương thẳng vào mũi tên nếu tắt hitscan
                                        arrow.setDamage(sbDamage);

                                        Vector dir = p.getEyeLocation().toVector().subtract(boss.getEyeLocation().toVector()).normalize();
                                        arrow.setVelocity(dir.multiply(2.0));
                                    }
                                }
                            }
                        }
                        ticks++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
                break;

            case "SAITAMA_PUNCH":
                long spDur = skillConfig.getInt("duration", 5) * 1000L;
                double spDamage = skillConfig.getDouble("damage", skillConfig.getDouble("true-damage", 25.0));
                double spPen = skillConfig.getDouble("armor-penetration", 0.90);

                NamespacedKey sKey = new NamespacedKey(plugin, "ma_saitama_punch");
                boss.getPersistentDataContainer().set(sKey, PersistentDataType.STRING, spDamage + ";" + spPen + ";" + (System.currentTimeMillis() + spDur));

                boss.getWorld().playSound(boss.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1.0f, 0.5f);
                boss.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, boss.getLocation().add(0, 2, 0), 10, 0.5, 0.5, 0.5, 0);
                break;

            case "LIFESTEAL_AURA":
                long lsDur = skillConfig.getInt("duration", 10) * 1000L;
                double minLs = skillConfig.getDouble("heal-percent.min", 1.0);
                double maxLs = skillConfig.getDouble("heal-percent.max", 1.5);

                NamespacedKey lKey = new NamespacedKey(plugin, "ma_lifesteal");
                boss.getPersistentDataContainer().set(lKey, PersistentDataType.STRING, minLs + ";" + maxLs + ";" + (System.currentTimeMillis() + lsDur));

                boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 2.0f);
                new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (boss.isDead() || ticks >= (lsDur / 50)) { this.cancel(); return; }
                        boss.getWorld().spawnParticle(Particle.HEART, boss.getLocation().add(0, 1, 0), 1, 0.5, 0.5, 0.5, 0);
                        ticks += 10;
                    }
                }.runTaskTimer(plugin, 0L, 10L);
                break;

            case "SOUL_DRAIN":
                double sdRadius = skillConfig.getDouble("radius", 10.0);
                double sdDrainPercent = skillConfig.getDouble("drain-percent", 10.0);
                double sdHealMultiplier = skillConfig.getDouble("heal-multiplier", 1.5);

                bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.5f);
                double totalDrained = 0;

                for (Player p : arena.getPlayersAsObjects()) {
                    if (!arena.containsLocation(p.getLocation())) continue;
                    if (p.getLocation().distance(bossLoc) > sdRadius) continue;

                    AttributeInstance maxHpAttr = p.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                    double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
                    double drain = maxHp * (sdDrainPercent / 100.0);
                    double newHp = p.getHealth() - drain;
                    if (newHp <= 0) p.damage(9999.0);
                    else p.setHealth(newHp);

                    totalDrained += drain;
                    drawLaser(bossLoc.clone().add(0, 1, 0), p.getLocation().add(0, 1, 0), Particle.DUST, 139, 0, 0);
                    p.getWorld().spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.05);
                    p.sendMessage("§5☠ Your life force is being drained!");
                }

                // Heal boss
                double healAmount = totalDrained * sdHealMultiplier;
                AttributeInstance bossMaxHpAttr = boss.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                double bossMaxHp = (bossMaxHpAttr != null) ? bossMaxHpAttr.getValue() : 100.0;
                boss.setHealth(Math.min(boss.getHealth() + healAmount, bossMaxHp));
                boss.getWorld().spawnParticle(Particle.HEART, boss.getLocation().add(0, 2, 0), 10, 0.5, 0.5, 0.5, 0);
                break;

            case "SHADOW_CLONE":
                int cloneCount = skillConfig.getInt("clone-count", 3);
                int cloneLifespan = skillConfig.getInt("clone-lifespan", 8);
                double cloneDamage = skillConfig.getDouble("clone-damage", 3.0);
                String cloneMobType = skillConfig.getString("clone-mob-type", boss.getType().name());

                bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 2.0f, 1.0f);
                boss.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 20, 0, false, false));

                MobFactory cloneFactory = new MobFactory(plugin);
                for (int ci = 0; ci < cloneCount; ci++) {
                    double angle = (2 * Math.PI / cloneCount) * ci;
                    Location cloneLoc = bossLoc.clone().add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);

                    try {
                        org.bukkit.entity.EntityType cloneType = org.bukkit.entity.EntityType.valueOf(cloneMobType.toUpperCase());
                        LivingEntity clone = (LivingEntity) cloneLoc.getWorld().spawnEntity(cloneLoc, cloneType);
                        clone.setCustomName(boss.getCustomName() + " §7(Shadow)");
                        clone.setCustomNameVisible(true);
                        clone.setMetadata("MA_MOB", new org.bukkit.metadata.FixedMetadataValue(plugin, arena.getName()));

                        AttributeInstance cloneHp = clone.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                        if (cloneHp != null) {
                            cloneHp.setBaseValue(20.0);
                            clone.setHealth(20.0);
                        }
                        AttributeInstance cloneAtk = clone.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
                        if (cloneAtk != null) cloneAtk.setBaseValue(cloneDamage);

                        clone.getWorld().spawnParticle(Particle.LARGE_SMOKE, cloneLoc, 20, 0.5, 1, 0.5, 0.05);
                        arena.getActiveMobs().add(clone);

                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            if (clone.isValid() && !clone.isDead()) {
                                clone.getWorld().spawnParticle(Particle.LARGE_SMOKE, clone.getLocation(), 30, 0.5, 1, 0.5, 0.1);
                                clone.getWorld().playSound(clone.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 2.0f);
                                clone.remove();
                                arena.getActiveMobs().remove(clone);
                            }
                        }, cloneLifespan * 20L);
                    } catch (Exception e) {
                        plugin.getLogger().warning("[MobArenav2] Shadow Clone failed: " + e.getMessage());
                    }
                }
                break;

            case "CURSED_GROUND":
                double cgRadius = skillConfig.getDouble("radius", 6.0);
                double cgDmgPerTick = skillConfig.getDouble("damage-per-tick", 3.0);
                int cgDuration = skillConfig.getInt("duration", 8);
                List<String> cgEffects = skillConfig.getStringList("effects");

                // Place cursed ground at each player's location
                List<Location> cursedZones = new ArrayList<>();
                for (Player p : arena.getPlayersAsObjects()) {
                    if (arena.containsLocation(p.getLocation())) {
                        cursedZones.add(p.getLocation().clone());
                    }
                }

                bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.3f);

                new BukkitRunnable() {
                    int ticks = 0;
                    @Override
                    public void run() {
                        if (boss.isDead() || !arena.isRunning() || ticks >= cgDuration * 20) {
                            this.cancel();
                            return;
                        }

                        for (Location zone : cursedZones) {
                            // Draw particle circle
                            for (double theta = 0; theta < 2 * Math.PI; theta += Math.PI / 12) {
                                double x = cgRadius * Math.cos(theta);
                                double z = cgRadius * Math.sin(theta);
                                zone.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, zone.clone().add(x, 0.1, z), 1, 0, 0, 0, 0);
                            }

                            // Damage players inside zone
                            if (ticks % 20 == 0) {
                                for (Player p : arena.getPlayersAsObjects()) {
                                    if (arena.containsLocation(p.getLocation()) && p.getLocation().distance(zone) <= cgRadius) {
                                        double newHp = p.getHealth() - cgDmgPerTick;
                                        if (newHp <= 0) p.damage(9999.0);
                                        else p.setHealth(newHp);
                                        applyEffects(p, cgEffects);
                                        p.getWorld().spawnParticle(Particle.SOUL, p.getLocation().add(0, 0.5, 0), 5, 0.3, 0.3, 0.3, 0.02);
                                    }
                                }
                            }
                        }
                        ticks++;
                    }
                }.runTaskTimer(plugin, 0L, 1L);
                break;

            case "BERSERKER_CHARGE":
                double bcDamage = skillConfig.getDouble("damage", 12.0);
                double bcSpeed = skillConfig.getDouble("charge-speed", 2.0);
                double bcPathDmg = skillConfig.getDouble("path-damage", 4.0);

                // Target furthest player
                Player chargeTarget = null;
                double maxChargeDistSq = 0;
                for (Player p : arena.getPlayersAsObjects()) {
                    if (!arena.containsLocation(p.getLocation())) continue;
                    double distSq = p.getLocation().distanceSquared(bossLoc);
                    if (distSq > maxChargeDistSq) {
                        maxChargeDistSq = distSq;
                        chargeTarget = p;
                    }
                }

                if (chargeTarget != null) {
                    final Player finalTarget = chargeTarget;
                    boss.setGlowing(true);
                    bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_RAVAGER_ROAR, 2.0f, 0.8f);

                    Vector chargeDir = finalTarget.getLocation().toVector().subtract(bossLoc.toVector()).setY(0).normalize().multiply(bcSpeed);
                    boss.setVelocity(chargeDir.setY(0.15));

                    new BukkitRunnable() {
                        int chargeTicks = 0;
                        @Override
                        public void run() {
                            if (boss.isDead() || !arena.isRunning() || chargeTicks >= 40) {
                                boss.setGlowing(false);
                                this.cancel();
                                return;
                            }

                            Location current = boss.getLocation();
                            current.getWorld().spawnParticle(Particle.FLAME, current, 5, 0.3, 0.1, 0.3, 0.05);

                            // Damage players in path
                            for (Player p : arena.getPlayersAsObjects()) {
                                if (arena.containsLocation(p.getLocation()) && p.getLocation().distance(current) <= 2.5) {
                                    if (p.equals(finalTarget)) {
                                        double newHp = p.getHealth() - bcDamage;
                                        if (newHp <= 0) p.damage(9999.0);
                                        else p.setHealth(newHp);
                                        p.setVelocity(chargeDir.clone().normalize().multiply(1.5).setY(0.8));
                                        p.sendMessage("§c§l[IMPACT] §eYou were hit by a devastating charge!");
                                        boss.setGlowing(false);
                                        this.cancel();
                                        return;
                                    } else {
                                        double newHp = p.getHealth() - bcPathDmg;
                                        if (newHp <= 0) p.damage(9999.0);
                                        else p.setHealth(newHp);
                                        Vector push = p.getLocation().toVector().subtract(current.toVector()).normalize().multiply(1.2).setY(0.5);
                                        p.setVelocity(push);
                                    }
                                }
                            }
                            chargeTicks++;
                        }
                    }.runTaskTimer(plugin, 5L, 1L);
                }
                break;

            case "VOID_PRISON":
                int vpDuration = skillConfig.getInt("duration", 5);
                double vpDamage = skillConfig.getDouble("damage", 8.0);
                List<String> vpEffects = skillConfig.getStringList("effects");

                List<Player> vpTargets = new ArrayList<>(arena.getPlayersAsObjects());
                if (!vpTargets.isEmpty()) {
                    Player victim = vpTargets.get(ThreadLocalRandom.current().nextInt(vpTargets.size()));
                    Location vLoc = victim.getLocation().clone();

                    victim.sendMessage("§5§l[VOID PRISON] §dYou are trapped! Break free or wait " + vpDuration + "s!");
                    victim.playSound(victim.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 1.5f, 0.5f);

                    // Build barrier cage
                    List<Location> barrierBlocks = new ArrayList<>();
                    int[][] offsets = {{1,0,0},{-1,0,0},{0,0,1},{0,0,-1},{0,2,0},{1,1,0},{-1,1,0},{0,1,1},{0,1,-1}};
                    for (int[] off : offsets) {
                        Location bLoc = vLoc.clone().add(off[0], off[1], off[2]);
                        if (bLoc.getBlock().getType().isAir()) {
                            bLoc.getBlock().setType(Material.BARRIER);
                            barrierBlocks.add(bLoc);
                        }
                    }

                    // Particle effect + damage over time
                    new BukkitRunnable() {
                        int ticks = 0;
                        @Override
                        public void run() {
                            if (ticks >= vpDuration * 20 || !victim.isOnline() || !arena.getActivePlayers().contains(victim.getUniqueId())) {
                                // Remove barriers
                                for (Location bl : barrierBlocks) {
                                    if (bl.getBlock().getType() == Material.BARRIER) {
                                        bl.getBlock().setType(Material.AIR);
                                    }
                                }
                                this.cancel();
                                return;
                            }

                            // Particle cage effect
                            if (ticks % 5 == 0) {
                                for (Location bl : barrierBlocks) {
                                    bl.getWorld().spawnParticle(Particle.WITCH, bl.clone().add(0.5, 0.5, 0.5), 2, 0.2, 0.2, 0.2, 0);
                                }
                            }

                            // Damage every second
                            if (ticks % 20 == 0 && ticks > 0) {
                                double newHp = victim.getHealth() - (vpDamage / vpDuration);
                                if (newHp <= 0) victim.damage(9999.0);
                                else victim.setHealth(newHp);
                                victim.getWorld().spawnParticle(Particle.SCULK_SOUL, victim.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.02);
                            }
                            ticks++;
                        }
                    }.runTaskTimer(plugin, 0L, 1L);

                    applyEffects(victim, vpEffects);
                }
                break;

            default:
                plugin.getLogger().warning("[MobArenav2] Unknown Boss Skill type: " + type);
                break;
        }
    }

    // ==========================================
    // UTILITY METHODS FOR NEW SKILLS
    // ==========================================

    private static void applyEffects(LivingEntity entity, List<String> effects) {
        if (effects == null || effects.isEmpty()) return;
        for (String eff : effects) {
            String[] data = eff.trim().split(":");
            if (data.length >= 2) {
                PotionEffectType pType = Registry.POTION_EFFECT_TYPE.get(NamespacedKey.minecraft(data[0].toLowerCase()));
                if (pType != null) {
                    int duration = Integer.parseInt(data[1]);
                    int amp = data.length > 2 ? Integer.parseInt(data[2]) : 0;
                    entity.addPotionEffect(new PotionEffect(pType, duration, amp, false, false));
                }
            }
        }
    }

    private static Player getNearestPlayer(Arena arena, Location loc, double maxDistance) {
        Player nearest = null;
        double minDistSq = maxDistance * maxDistance;
        for (Player p : arena.getPlayersAsObjects()) {
            if (arena.containsLocation(p.getLocation())) {
                double distSq = p.getLocation().distanceSquared(loc);
                if (distSq <= minDistSq) {
                    minDistSq = distSq;
                    nearest = p;
                }
            }
        }
        return nearest;
    }

    private static void drawLaser(Location start, Location end, Particle particle, int r, int g, int b) {
        double distance = start.distance(end);
        Vector direction = end.toVector().subtract(start.toVector()).normalize().multiply(0.5);
        Location current = start.clone();

        for (double d = 0; d < distance; d += 0.5) {
            current.add(direction);
            if (particle == Particle.DUST) {
                Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.fromRGB(r, g, b), 1.0f);
                current.getWorld().spawnParticle(particle, current, 1, 0, 0, 0, 0, dust);
            } else {
                current.getWorld().spawnParticle(particle, current, 1, 0, 0, 0, 0);
            }
        }
    }
}