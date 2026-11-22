package vn.meowchan12.mobarenav2.listener;

import io.papermc.paper.event.entity.WardenAngerChangeEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Spellcaster;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.ArenaManager;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class MobAIListener implements Listener {

    private final MobArenav2 plugin;
    private final ArenaManager arenaManager;
    private final Map<UUID, Long> evokerSpellCooldown = new ConcurrentHashMap<>();

    // =====================================================================
    // DANH SÁCH CÁC HIỆU ỨNG XẤU BỊ CHẶN HOÀN TOÀN CHO MOB ARENA
    // Mob arena sẽ MIỄN NHIỄM các debuff này từ MỌI NGUỒN (Player, Potion, Splash, v.v.)
    // =====================================================================
    private static final Set<PotionEffectType> BLOCKED_DEBUFFS = Set.of(
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.WEAKNESS,
            PotionEffectType.SLOWNESS,
            PotionEffectType.MINING_FATIGUE,
            PotionEffectType.HUNGER,
            PotionEffectType.BLINDNESS,
            PotionEffectType.LEVITATION,
            PotionEffectType.BAD_OMEN,
            PotionEffectType.INSTANT_DAMAGE
    );

    private final Map<org.bukkit.block.Block, Integer> siegeBlockHits = new ConcurrentHashMap<>();

    public MobAIListener(MobArenav2 plugin, ArenaManager arenaManager) {
        this.plugin = plugin;
        this.arenaManager = arenaManager;

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Arena arena : arenaManager.getAllArenas()) {
                if (!arena.isRunning()) continue;

                java.util.List<Player> activePlayers = arena.getPlayersAsObjects();
                if (activePlayers.isEmpty()) continue;
                Set<UUID> activeUuids = arena.getActivePlayers();

                // [v8.0.5 OPTIMIZATION]: Purge dead/invalid entities before iteration & prune cooldown leaks
                arena.getActiveMobs().removeIf(e -> {
                    if (e == null || !e.isValid() || e.isDead()) {
                        if (e != null) {
                            evokerSpellCooldown.remove(e.getUniqueId());
                        }
                        return true;
                    }
                    return false;
                });

                if (arena.getActiveMobs().isEmpty()) {
                    siegeBlockHits.clear();
                    continue;
                }

                for (LivingEntity entity : arena.getActiveMobs()) {
                    if (entity instanceof Mob mob) {
                        Player target = null;
                        if (mob.getTarget() instanceof Player p && p.isValid() && activeUuids.contains(p.getUniqueId())) {
                            target = p;
                        } else {
                            target = getNearestPlayer(activePlayers, mob.getLocation());
                            if (target != null) {
                                mob.setTarget(target);
                            }
                        }

                        // [v8.0.4 WARDEN AI LOCK]: Strict player-only focus (immune to mob vibrations)
                        if (mob instanceof Warden warden) {
                            handleWardenAI(warden, target, arena);
                        }

                        // [v8.0.4 EVOKER AGGRESSIVE AI]: Active player pursuit & long-range fang barrage
                        if (mob instanceof Evoker evoker) {
                            handleEvokerAI(evoker, target, arena);
                        }

                        // [v8.0.0 SIEGE AI]: Anti-Nerdpole & Anti-Dugout / Mobfarm Camping
                        if (target != null && mob.getLocation().getWorld().equals(target.getLocation().getWorld())) {
                            handleSiegeAI(mob, target, arena);
                        }
                    }
                }
            }
        }, 20L, 20L);
    }

    private void handleSiegeAI(Mob mob, Player target, Arena arena) {
        double dx = target.getLocation().getX() - mob.getLocation().getX();
        double dz = target.getLocation().getZ() - mob.getLocation().getZ();
        double horizontalDistSq = dx * dx + dz * dz;

        // Condition 1: Player must be in proximity range (horizontal <= 4.5 blocks)
        if (horizontalDistSq > 20.25) return;

        // Condition 2: Vertical difference between -3.0 (dugout/hầm) and +4.0 (pillar/nerd-pole)
        double dy = target.getLocation().getY() - mob.getLocation().getY();
        if (dy < -3.0 || dy > 4.0) return;

        org.bukkit.block.Block targetObstacle = null;

        // Case A: Player is elevated on pillar (nerd-pole)
        if (dy > 1.2) {
            org.bukkit.block.Block pillar = target.getLocation().clone().subtract(0, 1, 0).getBlock();
            if (!isUnbreakableBlock(arena, pillar)) {
                targetObstacle = pillar;
            }
        }
        // Case B: Player is below in a dugout or behind a barrier slit
        else {
            Vector dir = target.getEyeLocation().toVector().subtract(mob.getEyeLocation().toVector());
            double dist = dir.length();
            if (dist > 0.5) {
                var rayTrace = mob.getWorld().rayTraceBlocks(mob.getEyeLocation(), dir.normalize(), Math.min(dist, 4.0));
                if (rayTrace != null && rayTrace.getHitBlock() != null) {
                    org.bukkit.block.Block hit = rayTrace.getHitBlock();
                    if (!isUnbreakableBlock(arena, hit)) {
                        targetObstacle = hit;
                    }
                }
            }
        }

        if (targetObstacle != null && arena.containsLocation(targetObstacle.getLocation())) {
            mob.swingMainHand();
            int hits = siegeBlockHits.getOrDefault(targetObstacle, 0) + 1;

            if (hits >= 3) {
                // Shatter the obstacle block
                siegeBlockHits.remove(targetObstacle);
                targetObstacle.getWorld().playSound(targetObstacle.getLocation(), org.bukkit.Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.0f, 1.0f);
                targetObstacle.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, targetObstacle.getLocation().add(0.5, 0.5, 0.5), 30, 0.3, 0.3, 0.3, 0.1, targetObstacle.getBlockData());
                targetObstacle.setType(org.bukkit.Material.AIR);

                target.sendActionBar(vn.meowchan12.mobarenav2.utils.ColorUtils.toAdventure("&c&l⚔ Siege Breaker: Monster shattered your barrier! ⚔"));
                target.playSound(target.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_LAND, 0.6f, 0.8f);
            } else {
                siegeBlockHits.put(targetObstacle, hits);
                targetObstacle.getWorld().playSound(targetObstacle.getLocation(), org.bukkit.Sound.BLOCK_STONE_HIT, 1.0f, 0.8f);
                targetObstacle.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, targetObstacle.getLocation().add(0.5, 0.5, 0.5), 10, 0.2, 0.2, 0.2, 0.05, targetObstacle.getBlockData());
                target.sendActionBar(vn.meowchan12.mobarenav2.utils.ColorUtils.toAdventure("&e&l⚠ Warning: Monsters are breaking your barrier! [" + hits + "/3] ⚠"));
            }
        }
    }

    /**
     * [v8.0.1 RULE]: Checks whether an obstacle block is unbreakable by Siege AI.
     * Bedrock and Barrier are strictly unbreakable by rule. Obsidian is allowed to be broken.
     */
    private boolean isUnbreakableBlock(Arena arena, org.bukkit.block.Block block) {
        if (block == null || !block.getType().isSolid()) return true;
        org.bukkit.Material mat = block.getType();

        // 1. Hardcoded core unbreakable blocks
        if (mat == org.bukkit.Material.BEDROCK || mat == org.bukkit.Material.BARRIER || mat == org.bukkit.Material.END_PORTAL_FRAME) {
            return true;
        }

        // 2. Arena-configurable rules (settings.rules.siege-unbreakable-blocks)
        if (arena != null && arena.getConfig() != null) {
            if (!arena.getConfig().getBoolean("settings.rules.mob-siege-break", true)) {
                return true;
            }
            java.util.List<String> unbreakables = arena.getConfig().getStringList("settings.rules.siege-unbreakable-blocks");
            if (unbreakables != null && !unbreakables.isEmpty()) {
                String matName = mat.name();
                for (String unb : unbreakables) {
                    if (unb.equalsIgnoreCase(matName)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private Player getNearestPlayer(Arena arena, Location loc) {
        if (arena == null) return null;
        return getNearestPlayer(arena.getPlayersAsObjects(), loc);
    }

    private Player getNearestPlayer(java.util.List<Player> activePlayers, Location loc) {
        Player nearest = null;
        double minDistanceSq = Double.MAX_VALUE;

        for (Player p : activePlayers) {
            if (p != null && p.isOnline() && !p.isDead()) {
                if (p.getWorld().equals(loc.getWorld())) {
                    double distSq = p.getLocation().distanceSquared(loc);
                    if (distSq < minDistanceSq) {
                        minDistanceSq = distSq;
                        nearest = p;
                    }
                }
            }
        }
        return nearest;
    }

    // =====================================================================
    // [v5.3 FIX]: CHO PHÉP END CRYSTAL, TNT MINECART, TNT VÀ PLAYER NỔ GÂY DAMAGE QUÁI
    // Chỉ hủy sát thương nổ nếu nguồn nổ là do Mob cùng phe gây ra (Creeper Friendly Fire)
    // =====================================================================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMobExplosionDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!victim.hasMetadata("MA_MOB")) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION) {

            if (event instanceof EntityDamageByEntityEvent damageByEntityEvent) {
                Entity damager = damageByEntityEvent.getDamager();

                // 1. Cho phép nổ từ End Crystal, TNT Minecart, TNT Primed, Firework, Anchor
                if (damager instanceof org.bukkit.entity.EnderCrystal ||
                    damager instanceof org.bukkit.entity.minecart.ExplosiveMinecart ||
                    damager instanceof org.bukkit.entity.TNTPrimed ||
                    damager instanceof org.bukkit.entity.Firework ||
                    damager instanceof Player) {
                    return; // DO NOT CANCEL! Allow full damage to mobs.
                }

                // 2. Nếu nguồn nổ là Mob thuộc Arena (Creeper/Ghast/Wither) -> Hủy Friendly Fire
                if (damager instanceof LivingEntity mobSource && mobSource.hasMetadata("MA_MOB")) {
                    event.setCancelled(true);
                    event.setDamage(0);
                    return;
                }
            }

            // Nếu là nổ khối tự do (TNT/Anchor/Bed) mà không có damager mob -> Cho phép gây sát thương
            // Không cancel nếu không phải do MobArena Mob kích hoạt!
        }
    }

    // ==========================================
    // TẮT FRIENDLY FIRE VẬT LÝ VÀ PHÉP THUẬT CỦA QUÁI
    // ==========================================
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMobFriendlyFire(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (!victim.hasMetadata("MA_MOB")) return;

        Entity damagerEntity = event.getDamager();
        LivingEntity source = null;

        if (damagerEntity instanceof LivingEntity) {
            source = (LivingEntity) damagerEntity;
        } else if (damagerEntity instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity) {
            source = (LivingEntity) projectile.getShooter();
        } else if (damagerEntity instanceof AreaEffectCloud cloud && cloud.getSource() instanceof LivingEntity) {
            source = (LivingEntity) cloud.getSource();
        } else if (damagerEntity instanceof org.bukkit.entity.EvokerFangs fangs && fangs.getOwner() != null) {
            source = fangs.getOwner();
        }

        if (source != null && source.hasMetadata("MA_MOB")) {
            String victimArena = victim.getMetadata("MA_MOB").get(0).asString();
            String sourceArena = source.getMetadata("MA_MOB").get(0).asString();

            if (victimArena.equals(sourceArena)) {
                event.setCancelled(true);
                event.setDamage(0);

                // [v8.0.4 WARDEN FIX]: Ensure Warden never retains anger against friendly arena mobs
                if (victim instanceof Warden w && source != null) {
                    w.clearAnger(source);
                }
                if (source instanceof Warden w && victim != null) {
                    w.clearAnger(victim);
                }
            }
        }
    }

    // =========================================================================
    // [v8.0.4 WARDEN EXCLUSIVE TARGETING & ANGER FILTER]
    // Blocks Warden from generating anger or targeting ANY non-player entity.
    // =========================================================================
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWardenAngerChange(WardenAngerChangeEvent event) {
        Warden warden = event.getEntity();
        if (!warden.hasMetadata("MA_MOB")) return;

        String arenaName = warden.getMetadata("MA_MOB").get(0).asString();
        Arena arena = arenaManager.getArena(arenaName);
        if (arena == null || !arena.isRunning()) return;

        Entity target = event.getTarget();
        if (target == null || !(target instanceof Player p) 
                || !arena.getActivePlayers().contains(p.getUniqueId()) 
                || arena.getSpectators().contains(p.getUniqueId())) {

            event.setCancelled(true);
            event.setNewAnger(0);
            if (target != null) {
                warden.clearAnger(target);
            }

            Player nearest = getNearestPlayer(arena, warden.getLocation());
            if (nearest != null) {
                warden.setAnger(nearest, 150);
                warden.setTarget(nearest);
                warden.setDisturbanceLocation(nearest.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPotionSplash(PotionSplashEvent event) {
        if (event.getEntity().getShooter() instanceof LivingEntity shooter) {
            if (shooter.hasMetadata("MA_MOB")) {
                String arenaName = shooter.getMetadata("MA_MOB").get(0).asString();

                event.getAffectedEntities().removeIf(entity ->
                        entity.hasMetadata("MA_MOB") &&
                                entity.getMetadata("MA_MOB").get(0).asString().equals(arenaName)
                );
            }
        }
    }

    // ==========================================
    // [FIX MỚI] CHẶN HIỆU ỨNG XẤU (DEBUFF) CHO MOB ARENA
    // Mob sẽ MIỄN NHIỄM Poison, Wither, Weakness, Slowness, v.v.
    // Ngăn chặn lỗi dụng meta (ném potion để debuff mob → dễ kill)
    // và ngăn Creeper nổ gây Wither/Poison cho mob cùng phe
    // ==========================================
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMobPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        if (!mob.hasMetadata("MA_MOB")) return;

        // Chỉ chặn khi effect đang được THÊM VÀO (ADDED hoặc CHANGED)
        EntityPotionEffectEvent.Action action = event.getAction();
        if (action != EntityPotionEffectEvent.Action.ADDED && action != EntityPotionEffectEvent.Action.CHANGED) {
            return;
        }

        // Kiểm tra loại effect mới đang bị áp lên mob
        if (event.getNewEffect() != null && BLOCKED_DEBUFFS.contains(event.getNewEffect().getType())) {
            // Cho phép hiệu ứng nếu chính plugin tự gán (ví dụ: boss enrage tự áp effect)
            // Chỉ chặn khi nguồn là ngoại tác (Player, Splash Potion, Arrow, v.v.)
            EntityPotionEffectEvent.Cause cause = event.getCause();
            if (cause == EntityPotionEffectEvent.Cause.POTION_SPLASH ||
                cause == EntityPotionEffectEvent.Cause.ARROW ||
                cause == EntityPotionEffectEvent.Cause.AREA_EFFECT_CLOUD ||
                cause == EntityPotionEffectEvent.Cause.ATTACK ||
                cause == EntityPotionEffectEvent.Cause.BEACON ||
                cause == EntityPotionEffectEvent.Cause.FOOD ||
                cause == EntityPotionEffectEvent.Cause.WITHER_ROSE ||
                cause == EntityPotionEffectEvent.Cause.POTION_DRINK) {

                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onMobTarget(EntityTargetEvent event) {
        if (!event.getEntity().hasMetadata("MA_MOB")) return;

        if (event.getEntity() instanceof Mob mob) {
            String arenaName = mob.getMetadata("MA_MOB").get(0).asString();
            Arena arena = arenaManager.getArena(arenaName);
            if (arena == null || !arena.isRunning()) return;

            // [v8.0.4 WARDEN TARGET LOCK]: Strictly prevent Warden from acquiring non-player targets
            if (mob instanceof Warden warden) {
                Entity target = event.getTarget();
                if (target == null || !(target instanceof Player p) 
                        || !arena.getActivePlayers().contains(p.getUniqueId()) 
                        || arena.getSpectators().contains(p.getUniqueId())) {

                    event.setCancelled(true);
                    if (target != null) {
                        warden.clearAnger(target);
                    }

                    Player nearest = getNearestPlayer(arena, warden.getLocation());
                    if (nearest != null) {
                        event.setTarget(nearest);
                        warden.setAnger(nearest, 150);
                        warden.setDisturbanceLocation(nearest.getLocation());
                    }
                    return;
                }
            }

            if (event.getTarget() != null && event.getTarget().hasMetadata("MA_MOB")) {
                event.setCancelled(true);
                Player nearest = getNearestPlayer(arena, mob.getLocation());
                if (nearest != null) {
                    mob.setTarget(nearest);
                }
                return;
            }

            if (!(event.getTarget() instanceof Player)) {
                Player nearest = getNearestPlayer(arena, mob.getLocation());
                if (nearest != null) {
                    event.setTarget(nearest);
                } else {
                    event.setCancelled(true);
                }
            }
        }
    }

    // =========================================================================
    // [v8.0.4 WARDEN AI LOGIC]
    // =========================================================================
    private void handleWardenAI(Warden warden, Player target, Arena arena) {
        if (warden == null || !warden.isValid() || warden.isDead()) return;

        if (target != null && target.isValid() && arena.getActivePlayers().contains(target.getUniqueId())) {
            // 1. Wipe anger against any non-player entity that might have caused a vibration
            LivingEntity angryAt = warden.getEntityAngryAt();
            if (angryAt != null && !(angryAt instanceof Player)) {
                warden.clearAnger(angryAt);
            }

            // 2. Clear target if it somehow latched onto a non-player
            if (warden.getTarget() != null && !(warden.getTarget() instanceof Player)) {
                warden.setTarget(null);
            }

            // 3. Keep Warden perpetually enraged (150 anger > 80 threshold) focused on player
            warden.setAnger(target, 150);
            warden.setTarget(target);

            // 4. Update disturbance location so scent & vibration tracker guides Warden directly to player
            warden.setDisturbanceLocation(target.getLocation());
        }
    }

    // =========================================================================
    // [v8.0.4 EVOKER AGGRESSIVE PURSUIT & LONG-RANGE FANG SYSTEM]
    // =========================================================================
    private void handleEvokerAI(Evoker evoker, Player target, Arena arena) {
        if (evoker == null || !evoker.isValid() || evoker.isDead()) return;

        if (target == null || !target.isValid() || !arena.getActivePlayers().contains(target.getUniqueId())) {
            return;
        }

        evoker.setTarget(target);
        double distSq = evoker.getLocation().distanceSquared(target.getLocation());

        // 1. Active pursuit: Move aggressively towards the player when > 8 blocks away
        if (distSq > 64.0) {
            evoker.getPathfinder().moveTo(target, 1.35);
        } else {
            // Face target directly
            Vector toTarget = target.getLocation().toVector().subtract(evoker.getLocation().toVector()).normalize();
            if (Double.isFinite(toTarget.getX()) && Double.isFinite(toTarget.getZ())) {
                Location faceLoc = evoker.getLocation().setDirection(toTarget);
                evoker.teleport(faceLoc);
            }
        }

        // 2. Enhanced Fang Attack Spellcasting (Up to 32 blocks range, 4s cadence)
        if (distSq <= 1024.0) {
            long now = System.currentTimeMillis();
            long lastCast = evokerSpellCooldown.getOrDefault(evoker.getUniqueId(), 0L);
            if (now - lastCast >= 4000L) {
                evokerSpellCooldown.put(evoker.getUniqueId(), now);
                castEvokerFangAttack(evoker, target, arena);
            }
        }
    }

    private void castEvokerFangAttack(Evoker evoker, Player target, Arena arena) {
        if (evoker == null || target == null || !evoker.isValid() || !target.isValid()) return;

        // Visual spellcast posture & sound
        evoker.setSpell(Spellcaster.Spell.FANGS);
        evoker.getWorld().playSound(evoker.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.3f, 0.9f);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (evoker.isValid()) {
                evoker.setSpell(Spellcaster.Spell.NONE);
            }
        }, 25L);

        double dist = evoker.getLocation().distance(target.getLocation());
        org.bukkit.World world = evoker.getWorld();

        // Pattern A: Hex Circle Trap (close to mid range, <= 12 blocks or 40% random)
        if (dist <= 12.0 || ThreadLocalRandom.current().nextDouble() < 0.40) {
            target.getWorld().playSound(target.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 1.2f, 1.1f);
            target.sendActionBar(vn.meowchan12.mobarenav2.utils.ColorUtils.toAdventure("&5&l⚠ EVOKER FANG TRAP! DODGE! ⚠"));

            // 1 Fang directly underneath player
            spawnArenaFang(world, target.getLocation().getX(), target.getLocation().getY(), target.getLocation().getZ(), evoker, arena, 6);

            // Ring of 10 fangs surrounding player
            int count = 10;
            double radius = 1.8;
            for (int i = 0; i < count; i++) {
                double angle = (2 * Math.PI / count) * i;
                double fx = target.getLocation().getX() + radius * Math.cos(angle);
                double fz = target.getLocation().getZ() + radius * Math.sin(angle);
                Location gLoc = findGroundLocation(world, fx, target.getLocation().getY(), fz);
                spawnArenaFang(world, gLoc.getX(), gLoc.getY(), gLoc.getZ(), evoker, arena, 8);
            }
        }
        // Pattern B: Long-Range Surge Line (reaching up to 32 blocks)
        else {
            Vector dir = target.getLocation().toVector().subtract(evoker.getLocation().toVector()).normalize();
            double maxLineDist = Math.min(dist + 4.0, 32.0);
            double step = 1.4;
            int totalSteps = (int) Math.round(maxLineDist / step);

            for (int i = 2; i <= totalSteps; i++) {
                double curDist = i * step;
                double fx = evoker.getLocation().getX() + dir.getX() * curDist;
                double fz = evoker.getLocation().getZ() + dir.getZ() * curDist;
                Location gLoc = findGroundLocation(world, fx, evoker.getLocation().getY(), fz);

                int attackDelay = Math.min(25, 4 + i);
                spawnArenaFang(world, gLoc.getX(), gLoc.getY(), gLoc.getZ(), evoker, arena, attackDelay);

                // Purple spell trail
                world.spawnParticle(Particle.WITCH, gLoc.clone().add(0, 0.2, 0), 4, 0.1, 0.1, 0.1, 0.05);
            }
        }
    }

    private void spawnArenaFang(org.bukkit.World world, double x, double y, double z, Evoker evoker, Arena arena, int attackDelay) {
        Location loc = new Location(world, x, y, z);
        world.spawn(loc, EvokerFangs.class, fangs -> {
            fangs.setOwner(evoker);
            fangs.setAttackDelay(attackDelay);
            fangs.setMetadata("MA_MOB", new FixedMetadataValue(plugin, arena.getName()));
            fangs.setMetadata("MA_BUFFED_FANG", new FixedMetadataValue(plugin, true));
        });
    }

    private Location findGroundLocation(org.bukkit.World world, double x, double startY, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        int sy = (int) Math.floor(startY);

        for (int y = sy + 3; y >= sy - 6; y--) {
            org.bukkit.block.Block b = world.getBlockAt(bx, y, bz);
            org.bukkit.block.Block above = world.getBlockAt(bx, y + 1, bz);
            if (b.getType().isSolid() && !above.getType().isSolid()) {
                return new Location(world, x, y + 1.0, z);
            }
        }
        return new Location(world, x, startY, z);
    }
}