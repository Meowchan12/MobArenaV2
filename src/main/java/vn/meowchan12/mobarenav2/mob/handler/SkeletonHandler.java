package vn.meowchan12.mobarenav2.mob.handler;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Skeleton;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.mob.MobHandler;

/**
 * Dedicated handler for Skeletons in MobArena.
 * Prevents powder snow freezing conversion to Stray.
 */
public class SkeletonHandler implements MobHandler {

    @Override
    public EntityType getEntityType() {
        return EntityType.SKELETON;
    }

    @Override
    public void onSpawn(Mob mob, Arena arena) {
        if (mob instanceof Skeleton skeleton) {
            skeleton.setConversionTime(-1); // Disables freezing conversion
        }
    }
}
