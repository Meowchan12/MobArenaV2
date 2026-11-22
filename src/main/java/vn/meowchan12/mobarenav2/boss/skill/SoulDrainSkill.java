package vn.meowchan12.mobarenav2.boss.skill;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import vn.meowchan12.mobarenav2.MobArenav2;
import vn.meowchan12.mobarenav2.arena.Arena;
import vn.meowchan12.mobarenav2.utils.AttributeUtils;

/**
 * Hút 5% Max HP của tất cả người chơi xung quanh để hồi phục cho Boss.
 */
public class SoulDrainSkill implements BossSkill {

    @Override
    public String getType() {
        return "SOUL_DRAIN";
    }

    @Override
    public void execute(MobArenav2 plugin, Arena arena, LivingEntity boss, ConfigurationSection config) {
        Location bossLoc = boss.getLocation();
        double sdRadius = config.getDouble("radius", 10.0);
        double sdDrainPercent = config.getDouble("drain-percent", 5.0);
        double sdHealMultiplier = config.getDouble("heal-multiplier", 0.5);

        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_WITHER_AMBIENT, 2.0f, 0.5f);
        double totalDrained = 0;

        for (Player p : arena.getPlayersAsObjects()) {
            if (!arena.containsLocation(p.getLocation())) continue;
            if (p.getLocation().distance(bossLoc) > sdRadius) continue;

            AttributeInstance maxHpAttr = p.getAttribute(AttributeUtils.MAX_HEALTH);
            double maxHp = (maxHpAttr != null) ? maxHpAttr.getValue() : 20.0;
            double drain = maxHp * (sdDrainPercent / 100.0);
            vn.meowchan12.mobarenav2.service.CombatDamageService.applyLayeredDamage(p, drain, true, boss, arena, plugin);

            totalDrained += drain;
            drawLaser(bossLoc.clone().add(0, 1.5, 0), p.getLocation().add(0, 1.0, 0), 139, 0, 0);
            p.getWorld().spawnParticle(Particle.SCULK_SOUL, p.getLocation().add(0, 1, 0), 15, 0.5, 0.5, 0.5, 0.05);
            p.sendMessage("§5☠ Your life force is being drained!");
        }

        double healAmount = totalDrained * sdHealMultiplier;
        AttributeInstance bossMaxHpAttr = boss.getAttribute(AttributeUtils.MAX_HEALTH);
        double bossMaxHp = (bossMaxHpAttr != null) ? bossMaxHpAttr.getValue() : 100.0;
        boss.setHealth(Math.min(boss.getHealth() + healAmount, bossMaxHp));
        boss.getWorld().spawnParticle(Particle.HEART, boss.getLocation().add(0, 2, 0), 10, 0.5, 0.5, 0.5, 0);
    }

    private void drawLaser(Location start, Location end, int r, int g, int b) {
        double distance = start.distance(end);
        Vector direction = end.toVector().subtract(start.toVector()).normalize().multiply(0.5);
        Location current = start.clone();
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(r, g, b), 1.0f);

        for (double d = 0; d < distance; d += 0.5) {
            current.add(direction);
            current.getWorld().spawnParticle(Particle.DUST, current, 1, 0, 0, 0, 0, dust);
        }
    }
}
