package vn.meowchan12.mobarenav2.mob;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import vn.meowchan12.mobarenav2.arena.Arena;

/**
 * Contract for dedicated mob behavior handlers.
 * Each mob type has its own isolated handler class for modular maintenance.
 */
public interface MobHandler {

    /**
     * Target EntityType this handler governs.
     */
    EntityType getEntityType();

    /**
     * Applied immediately upon mob or boss spawn in an active arena.
     */
    void onSpawn(Mob mob, Arena arena);
}
