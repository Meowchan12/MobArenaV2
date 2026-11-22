package vn.meowchan12.mobarenav2.mob.handler;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.PiglinBrute;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.mob.MobHandler;

/**
 * Dedicated handler for Piglin Brutes in MobArena.
 * Retains permanent netherite/axe combat form without zombification outside the Nether.
 */
public class PiglinBruteHandler implements MobHandler {

    @Override
    public EntityType getEntityType() {
        return EntityType.PIGLIN_BRUTE;
    }

    @Override
    public void onSpawn(Mob mob, Arena arena) {
        if (mob instanceof PiglinBrute brute) {
            // [v7.5.2]: Permanently prevents shaking and zombification outside the Nether
            brute.setImmuneToZombification(true);
            brute.setCanPickupItems(false);
        }
    }
}
