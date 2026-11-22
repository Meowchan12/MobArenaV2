package vn.meowchan12.mobarenav2.mob.handler;

import org.bukkit.entity.Enderman;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.mob.MobHandler;

/**
 * Dedicated handler for Endermen in MobArena.
 * Prevents picking up arena blocks.
 */
public class EndermanHandler implements MobHandler {

    @Override
    public EntityType getEntityType() {
        return EntityType.ENDERMAN;
    }

    @Override
    public void onSpawn(Mob mob, Arena arena) {
        if (mob instanceof Enderman enderman) {
            enderman.setCarriedBlock(null);
        }
    }
}
