package vn.meowchan12.mobarenav2.mob;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.mob.handler.*;

import java.util.EnumMap;
import java.util.Map;

/**
 * Central registry for modular mob handlers.
 * Dispatches entity-specific biological configurations, immunity, and AI upon spawn.
 */
public class MobRegistry {

    private final MobArenav2 plugin;
    private final Map<EntityType, MobHandler> handlers = new EnumMap<>(EntityType.class);

    public MobRegistry(MobArenav2 plugin) {
        this.plugin = plugin;
        registerDefaults();
    }

    private void registerDefaults() {
        registerHandler(new PiglinHandler());
        registerHandler(new PiglinBruteHandler());
        registerHandler(new ZombifiedPiglinHandler());
        registerHandler(new HoglinHandler());
        registerHandler(new ZombieHandler());
        registerHandler(new SkeletonHandler());
        registerHandler(new EndermanHandler());
    }

    public void registerHandler(MobHandler handler) {
        if (handler != null && handler.getEntityType() != null) {
            handlers.put(handler.getEntityType(), handler);
        }
    }

    public void handleSpawn(Mob mob, Arena arena) {
        if (mob == null || !mob.isValid()) return;
        MobHandler handler = handlers.get(mob.getType());
        if (handler != null) {
            try {
                handler.onSpawn(mob, arena);
            } catch (Exception ex) {
                plugin.getLogger().warning("[MobArenav2] Error applying mob handler for " + mob.getType() + ": " + ex.getMessage());
            }
        }
    }

    public void clear() {
        handlers.clear();
    }
}
