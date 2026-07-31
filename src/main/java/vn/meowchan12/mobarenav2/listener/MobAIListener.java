package vn.meowchan12.mobarenav2.listener;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffectType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.manager.ArenaManager;

import java.util.Set;

public class MobAIListener implements Listener {

    private final ArenaManager arenaManager;

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

    public MobAIListener(MobArenav2 plugin, ArenaManager arenaManager) {
        this.arenaManager = arenaManager;

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Arena arena : arenaManager.getAllArenas()) {
                if (!arena.isRunning() || arena.getActivePlayers().isEmpty()) continue;

                // Performance Optimization: Purge dead/invalid entities before iteration
                arena.getActiveMobs().removeIf(e -> e == null || !e.isValid() || e.isDead());

                for (LivingEntity entity : arena.getActiveMobs()) {
                    if (entity instanceof Mob mob) {
                        if (mob.getTarget() == null || !mob.getTarget().isValid() || !arena.getActivePlayers().contains(mob.getTarget().getUniqueId())) {
                            Player nearest = getNearestPlayer(arena, mob.getLocation());
                            if (nearest != null) {
                                mob.setTarget(nearest);
                            }
                        }
                    }
                }
            }
        }, 20L, 20L);
    }

    private Player getNearestPlayer(Arena arena, Location loc) {
        Player nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Player p : arena.getPlayersAsObjects()) {
            if (arena.containsLocation(p.getLocation()) && !arena.getSpectators().contains(p.getUniqueId())) {
                if (p.getWorld().equals(loc.getWorld())) {
                    double dist = p.getLocation().distanceSquared(loc);
                    if (dist < minDistance) {
                        minDistance = dist;
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
            if (event.getTarget() != null && event.getTarget().hasMetadata("MA_MOB")) {
                event.setCancelled(true);

                String arenaName = mob.getMetadata("MA_MOB").get(0).asString();
                Arena arena = arenaManager.getArena(arenaName);
                if (arena != null) {
                    Player nearest = getNearestPlayer(arena, mob.getLocation());
                    if (nearest != null) {
                        mob.setTarget(nearest);
                    }
                }
                return;
            }

            if (!(event.getTarget() instanceof Player)) {
                String arenaName = mob.getMetadata("MA_MOB").get(0).asString();
                Arena arena = arenaManager.getArena(arenaName);
                if (arena != null) {
                    Player nearest = getNearestPlayer(arena, mob.getLocation());
                    if (nearest != null) {
                        event.setTarget(nearest);
                    } else {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }
}