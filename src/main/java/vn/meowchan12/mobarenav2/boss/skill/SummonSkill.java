package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.arena.MobFactory;

/**
 * Kỹ năng triệu hồi quái vật phụ trợ cơ bản.
 */
public class SummonSkill implements BossSkill {

    @Override
    public String getType() {
        return "SUMMON";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        Location bossLoc = boss.getLocation();
        String minionId = config.getString("minion-id", "zombie");
        int amount = config.getInt("amount", 3);
        MobFactory mobFactory = new MobFactory(plugin);

        bossLoc.getWorld().spawnParticle(Particle.SMOKE, bossLoc, 30, 1.0, 1.0, 1.0, 0.1);
        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);

        for (int i = 0; i < amount; i++) {
            Location spawnLoc = bossLoc.clone().add((Math.random() - 0.5) * 4, 0, (Math.random() - 0.5) * 4);
            mobFactory.spawnArenaMob(arena, minionId, spawnLoc, null, null);
        }
    }
}
