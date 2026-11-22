package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Đánh dấu Tử Thần: Khóa và phá vỡ Bùa Hồi Sinh (Totem of Undying) của người chơi.
 */
public class DeathMarkSkill implements BossSkill {

    @Override
    public String getType() {
        return "DEATH_MARK";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        double chance = config.getDouble("chance", 50.0);
        if (ThreadLocalRandom.current().nextDouble(100.0) <= chance) {
            List<Player> dPlayers = new ArrayList<>();
            for (Player p : arena.getPlayersAsObjects()) {
                if (arena.containsLocation(p.getLocation())) dPlayers.add(p);
            }

            if (!dPlayers.isEmpty()) {
                Player target = dPlayers.get(ThreadLocalRandom.current().nextInt(dPlayers.size()));
                long dDuration = config.getInt("duration", 10) * 1000L;
                long expiry = System.currentTimeMillis() + dDuration;

                NamespacedKey dmKey = new NamespacedKey(plugin, "ma_death_mark");
                target.getPersistentDataContainer().set(dmKey, PersistentDataType.LONG, expiry);

                target.sendMessage("§4☠ YOU HAVE BEEN MARKED FOR DEATH! Totems disabled for " + (dDuration / 1000) + "s!");
                target.getWorld().spawnParticle(Particle.SCULK_SOUL, target.getLocation().add(0, 2, 0), 30, 0.5, 0.5, 0.5, 0.05);
                target.playSound(target.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 0.5f);
            }
        }
    }
}
