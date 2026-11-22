package vn.meowchan12.mobarenav2.mob.handler;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Piglin;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.mob.MobHandler;

/**
 * Dedicated handler for Piglins in MobArena.
 * Prevents zombification/shaking outside the Nether and keeps full combat focus.
 */
public class PiglinHandler implements MobHandler {

    @Override
    public EntityType getEntityType() {
        return EntityType.PIGLIN;
    }

    @Override
    public void onSpawn(Mob mob, Arena arena) {
        if (mob instanceof Piglin piglin) {
            // [v7.5.2]: Permanently prevents shaking and zombification outside the Nether
            piglin.setImmuneToZombification(true);

            // Force adult combat form and combat priority
            piglin.setAdult();
            piglin.setIsAbleToHunt(true);
            piglin.setCanPickupItems(false); // Prevents bartering distractions with gold drops
        }
    }
}
