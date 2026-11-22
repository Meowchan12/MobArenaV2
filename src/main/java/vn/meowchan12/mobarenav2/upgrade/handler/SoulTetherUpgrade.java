package vn.meowchan12.mobarenav2.upgrade.handler;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.upgrade.UpgradeHandler;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * [v8.2.0 NEW UPGRADE]: Soul Tether (DAMAGE_SHARING_LINK)
 * - Lối chơi: Chia sẻ sát thương dây chuyền (Damage Link).
 * - Đòn đánh có 15% (Lv.1) đến 35% (Lv.5) tỷ lệ phóng xích linh hồn liên kết quái vật bị chém với 1-2 quái vật lân cận.
 * - Sát thương gây ra lên bất kỳ quái vật nào trong liên kết sẽ truyền 30% (Lv.1) đến 60% (Lv.5) sang các quái còn lại!
 * - Tier Milestone (Lv.5): Quái vật trong liên kết khi bị hạ gục sẽ phát nổ 25.0 Sát thương Chuẩn chia đều cho những quái vật còn lại!
 */
public class SoulTetherUpgrade implements UpgradeHandler {

    private final Map<UUID, Set<UUID>> tetherGroups = new ConcurrentHashMap<>();
    private final Map<UUID, Long> tetherExpiry = new ConcurrentHashMap<>();

    @Override
    public String getType() {
        return "DAMAGE_SHARING_LINK";
    }

    @Override
    public void onAttack(EntityDamageByEntityEvent event, Player player, LivingEntity victim, int level, Arena arena) {
        if (level <= 0 || player == null || victim == null) return;

        long now = System.currentTimeMillis();
        UUID victimId = victim.getUniqueId();

        // 1. Kiểm tra truyền sát thương nếu mục tiêu đã nằm trong liên kết
        if (tetherGroups.containsKey(victimId)) {
            Long exp = tetherExpiry.get(victimId);
            if (exp != null && now <= exp) {
                Set<UUID> group = tetherGroups.get(victimId);
                double shareRatio = 0.30 + (level - 1) * 0.075; // Lv.1: 30%, Lv.5: 60%
                double shareDmg = event.getFinalDamage() * shareRatio;

                if (shareDmg > 0 && group != null) {
                    for (UUID otherId : group) {
                        if (otherId.equals(victimId)) continue;
                        Entity other = org.bukkit.Bukkit.getEntity(otherId);
                        if (other instanceof LivingEntity otherMob && otherMob.hasMetadata("MA_MOB") && !otherMob.isDead()) {
                            otherMob.damage(shareDmg, player);
                            otherMob.getWorld().spawnParticle(Particle.SOUL, otherMob.getLocation().add(0, 1.0, 0), 3, 0.2, 0.2, 0.2, 0.02);
                        }
                    }
                }
            } else {
                tetherGroups.remove(victimId);
                tetherExpiry.remove(victimId);
            }
        }

        // 2. Tỉ lệ tạo liên kết mới nếu mục tiêu chưa bị xích
        double procChance = 0.15 + (level - 1) * 0.05; // Lv.1: 15%, Lv.5: 35%
        if (!tetherGroups.containsKey(victimId) && ThreadLocalRandom.current().nextDouble() <= procChance) {
            List<LivingEntity> nearbyMobs = new ArrayList<>();
            for (Entity near : victim.getWorld().getNearbyEntities(victim.getLocation(), 6.0, 3.0, 6.0)) {
                if (near instanceof LivingEntity mob && mob.hasMetadata("MA_MOB") && !mob.equals(victim) && !mob.isDead()) {
                    nearbyMobs.add(mob);
                }
            }

            if (!nearbyMobs.isEmpty()) {
                Set<UUID> newGroup = Collections.newSetFromMap(new ConcurrentHashMap<>());
                newGroup.add(victimId);

                int toLink = Math.min(nearbyMobs.size(), (level >= 5 ? 3 : 2));
                for (int i = 0; i < toLink; i++) {
                    newGroup.add(nearbyMobs.get(i).getUniqueId());
                }

                long expiry = now + 6000L; // Duy trì 6 giây
                for (UUID id : newGroup) {
                    tetherGroups.put(id, newGroup);
                    tetherExpiry.put(id, expiry);
                }

                // Visual tia xích linh hồn
                for (LivingEntity linked : nearbyMobs.subList(0, toLink)) {
                    drawTetherParticles(victim.getLocation().add(0, 1, 0), linked.getLocation().add(0, 1, 0));
                }
                player.playSound(victim.getLocation(), Sound.ITEM_CROSSBOW_SHOOT, 0.9f, 1.4f);
            }
        }
    }

    @Override
    public void onKill(Player player, LivingEntity victim, int level, Arena arena) {
        if (level < 5 || victim == null) return;
        UUID victimId = victim.getUniqueId();

        Set<UUID> group = tetherGroups.remove(victimId);
        tetherExpiry.remove(victimId);

        if (group != null && !group.isEmpty()) {
            List<LivingEntity> survivors = new ArrayList<>();
            for (UUID id : group) {
                if (id.equals(victimId)) continue;
                Entity e = org.bukkit.Bukkit.getEntity(id);
                if (e instanceof LivingEntity mob && mob.hasMetadata("MA_MOB") && !mob.isDead()) {
                    survivors.add(mob);
                }
            }

            if (!survivors.isEmpty()) {
                double splitDmg = 25.0 / survivors.size();
                for (LivingEntity s : survivors) {
                    s.damage(splitDmg, player);
                    s.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, s.getLocation().add(0, 1.0, 0), 10, 0.2, 0.2, 0.2, 0.05);
                }
                victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.8f);
            }
        }
    }

    private void drawTetherParticles(Location locA, Location locB) {
        if (locA.getWorld() == null || !locA.getWorld().equals(locB.getWorld())) return;
        Vector diff = locB.toVector().subtract(locA.toVector());
        double dist = diff.length();
        if (dist <= 0.1) return;

        Vector step = diff.clone().normalize().multiply(0.5);
        int steps = (int) (dist / 0.5);
        Location cur = locA.clone();

        for (int i = 0; i < steps; i++) {
            cur.add(step);
            locA.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, cur, 1, 0, 0, 0, 0);
        }
    }

    @Override
    public void onReset(Player player, Arena arena) {
        tetherGroups.clear();
        tetherExpiry.clear();
    }
}
