package vn.meowchan12.mobarenav2.mob.handler;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Zombie;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.mob.MobHandler;

/**
 * Dedicated handler for Zombies in MobArena.
 * Prevents underwater drowning conversion to Drowned and door breaking.
 */
public class ZombieHandler implements MobHandler {

    @Override
    public EntityType getEntityType() {
        return EntityType.ZOMBIE;
    }

    @Override
    public void onSpawn(Mob mob, Arena arena) {
        if (mob instanceof Zombie zombie) {
            zombie.setCanBreakDoors(false);
            zombie.setConversionTime(-1); // Disables underwater drowning conversion
        }
    }
}
