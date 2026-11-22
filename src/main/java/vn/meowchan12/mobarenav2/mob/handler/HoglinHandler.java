package vn.meowchan12.mobarenav2.mob.handler;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.Mob;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.mob.MobHandler;

/**
 * Dedicated handler for Hoglins in MobArena.
 * Retains permanent Hoglin form and prevents Zoglin zombification outside the Nether.
 */
public class HoglinHandler implements MobHandler {

    @Override
    public EntityType getEntityType() {
        return EntityType.HOGLIN;
    }

    @Override
    public void onSpawn(Mob mob, Arena arena) {
        if (mob instanceof Hoglin hoglin) {
            // [v7.5.2]: Permanently prevents transformation to Zoglin outside the Nether
            hoglin.setImmuneToZombification(true);
            hoglin.setAdult();
            hoglin.setIsAbleToBeHunted(false);
        }
    }
}
