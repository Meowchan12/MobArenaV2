package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mưa tên tối thượng từ cổng không gian giáng xuống toàn bộ người chơi.
 */
public class UltimateArrowSkill implements BossSkill {

    @Override
    public String getType() {
        return "ULTIMATE_ARROW_DAMAGE";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        String targets = config.getString("targets", "all");
        double uDmg = config.getDouble("damage", 10.0);
        double uTDmg = config.getDouble("true-damage-amount", 6.0);
        List<String> effects = config.getStringList("effects");
        String effectStr = String.join(",", effects);

        List<Player> targetPlayers = new ArrayList<>();
        for (Player p : arena.getPlayersAsObjects()) {
            if (arena.containsLocation(p.getLocation())) {
                targetPlayers.add(p);
            }
        }
        Collections.shuffle(targetPlayers);

        int count = targetPlayers.size();
        if (targets.contains("-")) {
            String[] parts = targets.split("-");
            int min = Integer.parseInt(parts[0].trim());
            int max = Integer.parseInt(parts[1].trim());
            count = ThreadLocalRandom.current().nextInt(min, max + 1);
        } else if (!targets.equals("all")) {
            try { count = Integer.parseInt(targets); } catch (Exception ignored) {}
        }

        for (int i = 0; i < Math.min(count, targetPlayers.size()); i++) {
            Player p = targetPlayers.get(i);
            Location spawnLoc = p.getLocation().add(0, 4, 0);

            Arrow arrow = p.getWorld().spawn(spawnLoc, Arrow.class);
            arrow.setVelocity(new Vector(0, -1.5, 0));
            arrow.setShooter(boss);
            arrow.setFireTicks(200);

            NamespacedKey uaKey = new NamespacedKey(plugin, "ma_arrow_type");
            arrow.getPersistentDataContainer().set(uaKey, PersistentDataType.STRING, "ultimate;" + uDmg + ";" + uTDmg + ";" + effectStr);
            double ultExpRadius = config.getDouble("explosion-radius", 3.5);
            if (ultExpRadius > 0) {
                NamespacedKey expKey = new NamespacedKey(plugin, "ma_arrow_explosion_radius");
                arrow.getPersistentDataContainer().set(expKey, PersistentDataType.DOUBLE, ultExpRadius);
            }
            p.getWorld().spawnParticle(Particle.PORTAL, spawnLoc, 50, 0.5, 0.5, 0.5, 0.1);
        }
    }
}
