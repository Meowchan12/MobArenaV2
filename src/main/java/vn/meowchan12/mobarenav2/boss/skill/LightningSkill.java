package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Giáng sấm sét trúng các người chơi trong đấu trường.
 */
public class LightningSkill implements BossSkill {

    @Override
    public String getType() {
        return "LIGHTNING";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        int lTargets = config.getInt("targets", 3);
        double damage = config.getDouble("damage", 10.0);

        List<Player> players = new ArrayList<>();
        for (Player p : arena.getPlayersAsObjects()) {
            if (arena.containsLocation(p.getLocation())) {
                players.add(p);
            }
        }
        Collections.shuffle(players);

        for (int i = 0; i < lTargets && i < players.size(); i++) {
            Player target = players.get(i);
            target.getWorld().strikeLightning(target.getLocation());
            target.damage(damage, boss);
        }
    }
}
