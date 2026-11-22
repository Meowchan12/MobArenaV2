package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Kỹ năng giáng mưa thiên thạch cầu lửa từ trên cao xuống tọa độ của người chơi.
 */
public class MeteorShowerSkill implements BossSkill {

    @Override
    public String getType() {
        return "METEOR_SHOWER";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        int targets = config.getInt("targets", 3);
        float explosionPower = (float) config.getDouble("explosion-power", 2.5);

        List<Player> targetList = new ArrayList<>();
        for (Player p : arena.getPlayersAsObjects()) {
            if (arena.containsLocation(p.getLocation())) {
                targetList.add(p);
            }
        }
        Collections.shuffle(targetList);

        for (int i = 0; i < Math.min(targets, targetList.size()); i++) {
            Player p = targetList.get(i);
            Location startLoc = p.getLocation().add(0, 15, 0);
            Vector direction = new Vector(0, -1, 0);

            Fireball fireball = p.getWorld().spawn(startLoc, Fireball.class);
            fireball.setShooter(boss);
            fireball.setDirection(direction);
            fireball.setYield(explosionPower);
        }
    }
}
