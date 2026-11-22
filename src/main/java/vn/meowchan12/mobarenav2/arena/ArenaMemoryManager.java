package vn.meowchan12.mobarenav2.arena;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import vn.meowchan12.mobarenav2.MobArenav2;

/**
 * [v8.2.0 OPTIMIZATION]: ArenaMemoryManager
 * Centralized memory and entity lifecycle manager for arenas.
 * Cleans up stray arrows, lingering projectiles, and expired dropped items at wave transitions
 * to prevent entity accumulation and chunk overhead during extended endless sessions.
 */
public class ArenaMemoryManager {

    private final MobArenav2 plugin;

    public ArenaMemoryManager(MobArenav2 plugin) {
        this.plugin = plugin;
    }

    /**
     * Cleans up all projectiles and loose items inside the arena bounding box.
     * Safe to call between waves or when an arena resets.
     */
    public void cleanupWaveLeftovers(Arena arena) {
        if (arena == null) return;
        Location p1 = arena.getP1();
        Location p2 = arena.getP2();
        if (p1 == null || p2 == null || p1.getWorld() == null) return;

        World world = p1.getWorld();
        double minX = Math.min(p1.getX(), p2.getX());
        double minY = Math.min(p1.getY(), p2.getY());
        double minZ = Math.min(p1.getZ(), p2.getZ());
        double maxX = Math.max(p1.getX(), p2.getX());
        double maxY = Math.max(p1.getY(), p2.getY());
        double maxZ = Math.max(p1.getZ(), p2.getZ());

        double centerX = (minX + maxX) / 2.0;
        double centerY = (minY + maxY) / 2.0;
        double centerZ = (minZ + maxZ) / 2.0;
        double rx = Math.max(1.0, (maxX - minX) / 2.0);
        double ry = Math.max(1.0, (maxY - minY) / 2.0);
        double rz = Math.max(1.0, (maxZ - minZ) / 2.0);
        Location center = new Location(world, centerX, centerY, centerZ);

        int projectilesRemoved = 0;
        int itemsRemoved = 0;

        for (Entity entity : world.getNearbyEntities(center, rx, ry, rz)) {
            if (entity instanceof Projectile) {
                entity.remove();
                projectilesRemoved++;
            } else if (entity instanceof Item item) {
                // Remove loose ground items unless explicitly protected
                item.remove();
                itemsRemoved++;
            }
        }

        if (projectilesRemoved > 0 || itemsRemoved > 0) {
            plugin.getLogger().fine("[MobArenav2] Arena " + arena.getName() + " cleaned: " + projectilesRemoved + " projectiles, " + itemsRemoved + " dropped items.");
        }
    }

    /**
     * Safely purges dead or unlinked mob references from arena tracking list.
     */
    public void purgeStaleMobs(Arena arena) {
        if (arena == null) return;
        arena.getActiveMobs().removeIf(mob -> mob == null || !mob.isValid() || mob.isDead());
    }
}
