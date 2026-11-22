package vn.meowchan12.mobarenav2.mob.handler;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PigZombie;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.mob.MobHandler;

/**
 * [v8.0.0]: Dedicated handler for Zombified Piglins (Zombie Pigmen) in MobArena.
 * Overrides neutral behavior: permanently enrages them with max anger level to hunt players aggressively.
 */
public class ZombifiedPiglinHandler implements MobHandler {

    @Override
    public EntityType getEntityType() {
        return EntityType.ZOMBIFIED_PIGLIN;
    }

    @Override
    public void onSpawn(Mob mob, Arena arena) {
        if (mob instanceof PigZombie zombified) {
            zombified.setAnger(99999);
            zombified.setAngry(true);
            zombified.setAdult();
            zombified.setCanPickupItems(false);
        }
    }
}
